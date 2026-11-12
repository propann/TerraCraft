package com.terracraft.geo;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Journal paginé : campagne, contrats et missions libres. */
public final class MissionsScreen extends Screen {
    private final List<List<JsonObject>> tabs=List.of(new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
    private final String job;
    private static int lastTab, lastPage;
    private int tab=lastTab,page=lastPage,selected=-1;
    private int left,top,panelWidth,panelHeight,rows;
    private static final int ROW=30;
    public MissionsScreen(String json) {
        super(Component.literal("Journal TerraCraft"));
        JsonElement parsed=JsonParser.parseString(json);
        if(parsed.isJsonArray()) { add(2,parsed.getAsJsonArray());job="";tab=2; }
        else {
            JsonObject object=parsed.getAsJsonObject();
            add(0,object.getAsJsonArray("campaign"));add(1,object.getAsJsonArray("contracts"));add(2,object.getAsJsonArray("missions"));
            job=object.has("job")?object.get("job").getAsString():"";
        }
    }
    private void add(int index,JsonArray array) {
        if(array!=null) array.forEach(e -> tabs.get(index).add(e.getAsJsonObject()));
    }
    private static boolean ready(JsonObject m) {
        return !m.get("claimed").getAsBoolean() && m.get("progress").getAsLong()>=m.get("target").getAsLong();
    }
    private List<JsonObject> current() { return tabs.get(tab); }
    @Override protected void init() {
        panelWidth=Math.min(620,width-16);panelHeight=Math.min(height-16,400);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        rows=Math.max(1,(panelHeight-160)/ROW);
        page=Math.max(0,Math.min(page,Math.max(0,(current().size()-1)/rows)));
        lastTab=tab;lastPage=page;
        for(int i=0;i<3;i++) {
            final int which=i;
            addRenderableWidget(Button.builder(Component.literal(new String[]{"Campagne","Contrats","Libres"}[i]),b -> {
                tab=which;page=0;selected=-1;rebuild();
            }).bounds(left+8+i*90,top+22,86,20).build());
        }
        int end=Math.min(current().size(),(page+1)*rows);
        for(int i=page*rows;i<end;i++) {
            JsonObject mission=current().get(i);final int index=i;int y=top+64+(i-page*rows)*ROW;
            addRenderableWidget(Button.builder(Component.literal("Lire"),b -> {selected=index;rebuild();})
                    .bounds(left+panelWidth-136,y,48,18).build());
            Button claim=Button.builder(Component.literal(mission.get("claimed").getAsBoolean()?"Reçue":"Réclamer"),b -> {
                if(ClientPlayNetworking.canSend(ClaimMissionPayload.TYPE)) ClientPlayNetworking.send(new ClaimMissionPayload(mission.get("id").getAsString()));
            }).bounds(left+panelWidth-82,y,74,18).build();
            claim.active=ready(mission);addRenderableWidget(claim);
        }
        Button previous=Button.builder(Component.literal("←"),b -> {page--;selected=-1;rebuild();})
                .bounds(left+8,top+panelHeight-26,32,20).build();previous.active=page>0;addRenderableWidget(previous);
        Button next=Button.builder(Component.literal("→"),b -> {page++;selected=-1;rebuild();})
                .bounds(left+46,top+panelHeight-26,32,20).build();next.active=end<current().size();addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Fermer"),b -> onClose())
                .bounds(left+panelWidth-90,top+panelHeight-26,82,20).build());
    }
    private void rebuild() { clearWidgets();init(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        extractTransparentBackground(g);Ui.frame(g,left,top,panelWidth,panelHeight,18);
        g.text(font,Component.literal("TERRA EXODUS · JOURNAL"),left+8,top+5,SuitScreen.GOLD,false);
        g.text(font,font.plainSubstrByWidth(job,panelWidth-16),left+8,top+48,SuitScreen.CYAN,false);
        int end=Math.min(current().size(),(page+1)*rows);
        for(int i=page*rows;i<end;i++) {
            JsonObject m=current().get(i);int y=top+64+(i-page*rows)*ROW;
            String title=(m.get("claimed").getAsBoolean()?"✓ ":"")+m.get("title").getAsString();
            g.text(font,font.plainSubstrByWidth(title,Math.max(30,panelWidth-150)),left+8,y,ready(m)?0xFF7EE08A:SuitScreen.TEXT,false);
            long progress=m.get("progress").getAsLong(),target=m.get("target").getAsLong();
            g.text(font,progress+"/"+target+" · "+m.get("reward").getAsLong()+" crédits",left+8,y+12,SuitScreen.GREY,false);
        }
        String detail=selected>=0 && selected<current().size()?current().get(selected).get("description").getAsString():"Choisis Lire pour consulter tous les objectifs. /exodus livre actualise ton carnet.";
        int y=top+panelHeight-86;
        for(var line:font.split(Component.literal(detail),panelWidth-16)) {
            if(y>=top+panelHeight-32) break;
            g.text(font,line,left+8,y,SuitScreen.TEXT,false);y+=10;
        }
        g.text(font,"Page "+(page+1)+"/"+Math.max(1,(current().size()+rows-1)/rows),left+90,top+panelHeight-20,SuitScreen.GREY,false);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen() {return false;}
    @Override public void removed() {
        super.removed();if(minecraft!=null && minecraft.player!=null) minecraft.mouseHandler.grabMouse();
    }
}
