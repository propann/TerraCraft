package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Fenêtre de dialogue PNJ : identité, humeur, historique et actions validées par le serveur. */
public final class NpcChatScreen extends Screen {
    private final int entityId;
    private JsonObject state;
    private EditBox input;
    private Button claimButton;
    private int left, top, panelWidth, panelHeight;

    public NpcChatScreen(int entityId, String json) {
        super(Component.literal("Conversation"));
        this.entityId = entityId;
        update(json);
    }

    public int entityId() {
        return entityId;
    }

    public void update(String json) {
        try {
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            if (parsed.has("identity") && parsed.has("history")) {
                state = parsed;
                if (claimButton != null) claimButton.active = state.has("hasQuest") && state.get("hasQuest").getAsBoolean();
            }
        } catch (RuntimeException ignored) {
            // Un état mal formé ne remplace pas la dernière conversation lisible.
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(620, width - 16);
        panelHeight = Math.min(440, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        int actionY = top + 60;
        addRenderableWidget(Button.builder(Component.literal("Objectif"), b -> action(false))
                .bounds(left + 10, actionY, 88, 20).build());
        claimButton = Button.builder(Component.literal("Réclamer"), b -> action(true))
                .bounds(left + 104, actionY, 88, 20).build();
        claimButton.active = state != null && state.has("hasQuest") && state.get("hasQuest").getAsBoolean();
        addRenderableWidget(claimButton);

        input = new EditBox(font, left + 10, top + panelHeight - 38, panelWidth - 112, 20,
                Component.literal("Écrire au personnage"));
        input.setMaxLength(500);
        input.setHint(Component.literal("Écris ton message…"));
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal("Envoyer"), b -> send())
                .bounds(left + panelWidth - 94, top + panelHeight - 38, 84, 20).build());

        JsonObject identity = state != null ? state.getAsJsonObject("identity") : new JsonObject();
        JsonArray topics = identity != null && identity.has("topics") ? identity.getAsJsonArray("topics") : new JsonArray();
        int count = Math.min(4, topics.size());
        if (count > 0) {
            int gap = 4;
            int topicWidth = Math.min(100, (panelWidth - 20 - gap * (count - 1)) / count);
            for (int i = 0; i < count; i++) {
                String topic = topics.get(i).getAsString();
                int x = left + 10 + i * (topicWidth + gap);
                addRenderableWidget(Button.builder(Component.literal(topic), b -> startTopic(topic))
                        .bounds(x, top + 87, topicWidth, 20).build());
            }
        }
    }

    private void startTopic(String topic) {
        if (input == null) return;
        input.setValue("J’aimerais parler de « " + topic + " ».");
        input.setFocused(true);
    }

    private void send() {
        if (input == null || minecraft == null || minecraft.player == null || !ClientPlayNetworking.canSend(NpcChatMessagePayload.TYPE)) return;
        String message = input.getValue().strip();
        if (message.isEmpty()) return;
        ClientPlayNetworking.send(new NpcChatMessagePayload(entityId, message));
        input.setValue("");
        input.setFocused(true);
    }

    private void action(boolean claim) {
        if (ClientPlayNetworking.canSend(NpcMissionActionPayload.TYPE)) {
            ClientPlayNetworking.send(new NpcMissionActionPayload(entityId, claim));
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (input != null && input.isFocused() && event.key() == 257) {
            send();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        Ui.frame(g, left, top, panelWidth, panelHeight, 52);
        JsonObject identity = state == null ? new JsonObject() : state.getAsJsonObject("identity");
        String name = identity.has("name") ? identity.get("name").getAsString() : "Contact du refuge";
        String role = identity.has("role") ? identity.get("role").getAsString() : "Habitant";
        String faction = identity.has("faction") ? identity.get("faction").getAsString() : "";
        String mood = identity.has("mood") ? identity.get("mood").getAsString() : "";
        g.text(font, font.plainSubstrByWidth(name, panelWidth - 120), left + 12, top + 7, SuitScreen.GOLD, true);
        g.text(font, font.plainSubstrByWidth(role + (faction.isBlank() ? "" : " · " + faction), panelWidth - 24),
                left + 12, top + 22, SuitScreen.TEXT, false);
        g.text(font, font.plainSubstrByWidth(mood, panelWidth - 24), left + 12, top + 36, SuitScreen.GREY, false);
        String mode = "À l’écoute";
        int modeWidth = font.width(mode);
        g.text(font, mode, left + panelWidth - modeWidth - 12, top + 7, SuitScreen.CYAN, false);
        g.text(font, "NOTRE CONVERSATION", left + 12, top + 113, SuitScreen.GOLD, false);

        int logTop = top + 131;
        int logBottom = top + panelHeight - 52;
        g.fill(left + 8, logTop, left + panelWidth - 8, logBottom, 0xA009111A);
        g.outline(left + 8, logTop, panelWidth - 16, logBottom - logTop, SuitScreen.SLOT_EDGE);
        String quest = state != null && state.has("quest_title") ? state.get("quest_title").getAsString() : "";
        if (!quest.isBlank()) {
            g.text(font, font.plainSubstrByWidth("Objectif lié : " + quest, panelWidth - 32),
                    left + 16, logTop + 4, 0xFF7EE08A, false);
        }

        int y = logBottom - 10;
        if (state != null && state.has("busy") && state.get("busy").getAsBoolean()) {
            String waiting = name + " réfléchit…";
            g.text(font, waiting, left + 16, y - 10, SuitScreen.CYAN, false);
            y -= 18;
        }
        JsonArray history = state != null && state.has("history") ? state.getAsJsonArray("history") : new JsonArray();
        int first = Math.max(0, history.size() - 8);
        for (int i = history.size() - 1; i >= first && y > logTop + 18; i--) {
            JsonObject turn = history.get(i).getAsJsonObject();
            boolean player = turn.has("speaker") && turn.get("speaker").getAsString().equals("player");
            String label = player ? "TOI" : name.toUpperCase(java.util.Locale.ROOT);
            String message = turn.has("text") ? turn.get("text").getAsString() : "";
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(message), panelWidth - 40);
            int blockHeight = (lines.size() + 1) * 10 + 4;
            int blockTop = y - blockHeight + 4;
            int colour = player ? 0x70304958 : 0x70413528;
            g.fill(left + 13, blockTop, left + panelWidth - 13, y + 2, colour);
            g.text(font, label, left + 18, blockTop + 3, player ? SuitScreen.CYAN : SuitScreen.GOLD, true);
            int lineY = blockTop + 14;
            for (var line : lines) {
                g.text(font, line, left + 18, lineY, SuitScreen.TEXT, false);
                lineY += 10;
            }
            y = blockTop - 5;
        }
        if (history.isEmpty() && (state == null || !state.has("busy") || !state.get("busy").getAsBoolean())) {
            g.centeredText(font, Component.literal("Choisis un sujet pour commencer, ou écris simplement ce qui te préoccupe."), width / 2,
                    logTop + (logBottom - logTop) / 2, SuitScreen.GREY);
        }
        g.text(font, "Échap : fermer", left + panelWidth - 76, top + panelHeight - 13, SuitScreen.GREY, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (ClientPlayNetworking.canSend(NpcClosePayload.TYPE)) {
            ClientPlayNetworking.send(new NpcClosePayload(entityId));
        }
        if (minecraft != null && minecraft.player != null) minecraft.mouseHandler.grabMouse();
    }
}
