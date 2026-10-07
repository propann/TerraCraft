package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Hôtel des ventes : annonces des joueurs filtrées par catégorie (avec le prix moyen des
 * dernières ventes), comptoir du serveur, et mise en vente de l'objet tenu en main.
 */
public final class MarketScreen extends Screen {
    private static final int ROW = 22;
    private static final int CATEGORY_WIDTH = 96;
    private static final int GREEN = 0xFF7EE08A;

    /** Onglet affiché : gardé quand le serveur renvoie l'écran après un achat. */
    private static boolean shopTab;

    private final JsonObject data;
    private EditBox price;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public MarketScreen(String json) {
        super(Component.literal("Hôtel des ventes"));
        data = JsonParser.parseString(json).getAsJsonObject();
    }

    private JsonArray array(String key) {
        return data.has(key) ? data.getAsJsonArray(key) : new JsonArray();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(600, width - 16);
        panelHeight = Math.min(height - 16, 240);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        addRenderableWidget(Button.builder(Component.literal(shopTab ? "Annonces" : "▶ Annonces"), b -> switchTab(false))
                .bounds(left + 8, top + 22, CATEGORY_WIDTH, 18).build());
        addRenderableWidget(Button.builder(Component.literal(shopTab ? "▶ Comptoir" : "Comptoir"), b -> switchTab(true))
                .bounds(left + 8, top + 42, CATEGORY_WIDTH, 18).build());

        int listLeft = left + CATEGORY_WIDTH + 16;
        int listRight = left + panelWidth - 8;
        if (shopTab) {
            JsonArray shop = array("shop");
            for (int i = 0; i < shop.size(); i++) {
                int index = shop.get(i).getAsJsonObject().get("index").getAsInt();
                addRenderableWidget(Button.builder(Component.literal("Acheter"), b -> send(new BuyShopPayload(index)))
                        .bounds(listRight - 64, top + 24 + i * ROW, 64, 18).build());
            }
        } else {
            JsonArray categories = array("categories");
            String current = data.get("category").getAsString();
            for (int i = 0; i < categories.size(); i++) {
                JsonObject category = categories.get(i).getAsJsonObject();
                String key = category.get("key").getAsString();
                String label = (key.equals(current) ? "▶ " : "") + category.get("label").getAsString()
                        + " (" + category.get("count").getAsInt() + ")";
                addRenderableWidget(Button.builder(Component.literal(label), b -> request(1, key))
                        .bounds(left + 8, top + 66 + i * 17, CATEGORY_WIDTH, 16).build());
            }
            JsonArray rows = array("listings");
            for (int i = 0; i < rows.size(); i++) {
                JsonObject row = rows.get(i).getAsJsonObject();
                long id = row.get("id").getAsLong();
                boolean own = row.get("own").getAsBoolean();
                addRenderableWidget(Button.builder(Component.literal(own ? "Retirer" : "Acheter"),
                                b -> send(own ? new RemoveListingPayload(id) : new BuyListingPayload(id)))
                        .bounds(listRight - 64, top + 24 + i * ROW, 64, 18).build());
            }
            int page = data.get("page").getAsInt();
            int pages = data.get("pages").getAsInt();
            int navY = top + 24 + 6 * ROW + 2;
            if (page > 1) {
                addRenderableWidget(Button.builder(Component.literal("←"), b -> request(page - 1, current))
                        .bounds(listLeft, navY, 20, 16).build());
            }
            if (page < pages) {
                addRenderableWidget(Button.builder(Component.literal("→"), b -> request(page + 1, current))
                        .bounds(listLeft + 70, navY, 20, 16).build());
            }
        }

        // Vente de l'objet tenu en main.
        int sellY = top + panelHeight - 24;
        if (data.has("held")) {
            price = new EditBox(font, listRight - 160, sellY, 70, 18, Component.literal("Prix"));
            price.setMaxLength(10);
            price.setHint(Component.literal("Prix"));
            price.setResponder(text -> {
                String digits = text.replaceAll("[^0-9]", "");
                if (!digits.equals(text)) {
                    price.setValue(digits); // Seulement des chiffres.
                }
            });
            long average = data.get("heldAverage").getAsLong();
            if (average > 0) {
                price.setValue(Long.toString(average * data.get("heldCount").getAsInt()));
            }
            addRenderableWidget(price);
            addRenderableWidget(Button.builder(Component.literal("Vendre"), b -> sell())
                    .bounds(listRight - 84, sellY, 84, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(left + 8, sellY, CATEGORY_WIDTH, 18).build());
    }

    private void switchTab(boolean shop) {
        shopTab = shop;
        rebuildWidgets();
    }

    private void sell() {
        String value = price == null ? "" : price.getValue();
        if (!value.isEmpty() && value.length() <= 10) {
            send(new SellPayload(Long.parseLong(value)));
        }
    }

    private void request(int page, String category) {
        send(new RequestMarketPayload(page, category));
    }

    private static void send(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }

    private static ItemStack icon(String itemId) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        Ui.frame(g, left, top, panelWidth, panelHeight, 18);
        g.text(font, Component.literal(shopTab ? "COMPTOIR DU SERVEUR" : "HÔTEL DES VENTES"), left + 8, top + 5, SuitScreen.GOLD, false);
        Component balance = Component.literal("Solde : " + data.get("balance").getAsLong() + " crédits");
        g.text(font, balance, left + panelWidth - 8 - font.width(balance), top + 5, GREEN, false);

        int listLeft = left + CATEGORY_WIDTH + 16;
        int listRight = left + panelWidth - 8;
        int textWidth = listRight - 72 - (listLeft + 22);
        if (shopTab) {
            JsonArray shop = array("shop");
            for (int i = 0; i < shop.size(); i++) {
                JsonObject offer = shop.get(i).getAsJsonObject();
                int y = top + 24 + i * ROW;
                rowBackground(g, listLeft, listRight, y, i);
                g.item(icon(offer.get("itemId").getAsString()), listLeft + 2, y + 1);
                g.text(font, offer.get("label").getAsString() + " x" + offer.get("count").getAsInt(), listLeft + 22, y + 1,
                        SuitScreen.TEXT, false);
                g.text(font, offer.get("price").getAsLong() + " crédits", listLeft + 22, y + 10, SuitScreen.GOLD, false);
            }
            g.text(font, Component.literal("Le comptoir vend des consommables à prix fixe."), listLeft,
                    top + 24 + 6 * ROW + 6, SuitScreen.GREY, false);
        } else {
            JsonArray rows = array("listings");
            if (rows.isEmpty()) {
                g.text(font, Component.literal("Aucune annonce dans cette catégorie."), listLeft, top + 28, SuitScreen.GREY, false);
            }
            for (int i = 0; i < rows.size(); i++) {
                JsonObject row = rows.get(i).getAsJsonObject();
                int y = top + 24 + i * ROW;
                rowBackground(g, listLeft, listRight, y, i);
                g.item(icon(row.get("itemId").getAsString()), listLeft + 2, y + 1);
                int count = row.get("count").getAsInt();
                long cost = row.get("price").getAsLong();
                String line = row.get("item").getAsString() + " x" + count + "  —  " + cost + " cr.";
                g.text(font, font.plainSubstrByWidth(line, textWidth), listLeft + 22, y + 1, SuitScreen.TEXT, false);
                long average = row.get("average").getAsLong();
                String info = row.get("seller").getAsString();
                int color = SuitScreen.GREY;
                if (average > 0) {
                    long unit = Math.round((double) cost / Math.max(1, count));
                    info += "  ·  " + unit + "/u, moyenne " + average + "/u";
                    color = unit <= average ? GREEN : unit > average * 5 / 4 ? SuitScreen.GOLD : SuitScreen.GREY;
                }
                g.text(font, font.plainSubstrByWidth(info, textWidth), listLeft + 22, y + 11, color, false);
            }
            g.text(font, Component.literal(data.get("page").getAsInt() + " / " + data.get("pages").getAsInt()),
                    listLeft + 30, top + 24 + 6 * ROW + 6, SuitScreen.GREY, false);
        }

        int sellY = top + panelHeight - 24;
        if (data.has("held")) {
            int fee = data.get("feePerMille").getAsInt();
            String held = "En main : " + data.get("held").getAsString() + " x" + data.get("heldCount").getAsInt()
                    + "  ·  frais " + fee / 10 + " %";
            g.text(font, font.plainSubstrByWidth(held, listRight - 166 - (listLeft)), listLeft, sellY + 5, SuitScreen.TEXT, false);
        } else {
            g.text(font, Component.literal("Tiens un objet en main pour le mettre en vente ici."), listLeft, sellY + 5,
                    SuitScreen.GREY, false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private static void rowBackground(GuiGraphicsExtractor g, int x0, int x1, int y, int index) {
        g.fill(x0, y - 2, x1, y + ROW - 3, index % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (minecraft != null && minecraft.player != null) {
            minecraft.mouseHandler.grabMouse();
        }
    }
}
