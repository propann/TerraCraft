package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.terracraft.geo.content.ModTabs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Catalogue TerraCraft (menu O) : nos objets par catégorie (les mêmes que le menu créatif), avec leur description,
 * leur recette et ce qu'ils permettent de fabriquer. Tout est lu dans le jar du mod : aucune donnée du serveur.
 * Clic sur un ingrédient du mod : on passe à sa fiche.
 */
public final class CatalogScreen extends Screen {
    private static final int CELL = 20;
    private static final int SIDE = 112;
    private static final int DETAIL = 176;

    /** Recette simplifiée : grille 3 × 3 (ingrédients, vides compris) ou liste, et résultat. */
    private record Recipe(String kind, List<List<ItemStack>> slots, int width, ItemStack result) {
    }

    private static Map<Item, List<Recipe>> recipes;
    private static Map<Item, List<Item>> usedIn;
    private static int category;
    private static Item selected;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int recipeIndex;
    private final List<Hit> hits = new ArrayList<>();

    /** Zone cliquable de l'écran (catégorie, objet, ingrédient). */
    private record Hit(int x, int y, int w, int h, Runnable action, ItemStack hover) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    public CatalogScreen() {
        super(Component.literal("Catalogue TerraCraft"));
        if (recipes == null) {
            loadRecipes();
        }
        if (selected == null) {
            selected = items().getFirst();
        }
    }

    private static List<Item> items() {
        return ModTabs.CATEGORIES.get(category).items().get().stream().map(ItemLike::asItem).toList();
    }

    // --- Recettes du jar -----------------------------------------------------------------------

    private static void loadRecipes() {
        recipes = new HashMap<>();
        usedIn = new HashMap<>();
        FabricLoader.getInstance().getModContainer(GeoMod.MOD_ID).flatMap(c -> c.findPath("data/" + GeoMod.MOD_ID + "/recipe"))
                .ifPresent(dir -> {
                    try (Stream<Path> files = Files.list(dir)) {
                        files.filter(f -> f.toString().endsWith(".json")).sorted().forEach(CatalogScreen::loadRecipe);
                    } catch (IOException e) {
                        GeoMod.LOGGER.warn("Catalogue : recettes illisibles", e);
                    }
                });
    }

    private static void loadRecipe(Path file) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String kind = json.get("type").getAsString().replace("minecraft:", "");
            JsonElement resultJson = json.get("result");
            Item result = item(resultJson.isJsonObject() ? resultJson.getAsJsonObject().get("id").getAsString() : resultJson.getAsString());
            int count = resultJson.isJsonObject() && resultJson.getAsJsonObject().has("count") ? resultJson.getAsJsonObject().get("count").getAsInt() : 1;
            List<List<ItemStack>> slots = new ArrayList<>();
            int width = 3;
            if (kind.equals("crafting_shaped")) {
                JsonArray pattern = json.getAsJsonArray("pattern");
                JsonObject key = json.getAsJsonObject("key");
                width = pattern.get(0).getAsString().length();
                for (JsonElement row : pattern) {
                    for (char c : row.getAsString().toCharArray()) {
                        slots.add(c == ' ' ? List.of() : ingredient(key.get(String.valueOf(c))));
                    }
                }
            } else if (kind.equals("crafting_shapeless")) {
                for (JsonElement element : json.getAsJsonArray("ingredients")) {
                    slots.add(ingredient(element));
                }
            } else {
                slots.add(ingredient(json.get("ingredient")));
                width = 1;
            }
            Recipe recipe = new Recipe(kind, slots, width, new ItemStack(result, count));
            recipes.computeIfAbsent(result, k -> new ArrayList<>()).add(recipe);
            for (List<ItemStack> slot : slots) {
                for (ItemStack stack : slot) {
                    List<Item> uses = usedIn.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
                    if (!uses.contains(result)) {
                        uses.add(result);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            GeoMod.LOGGER.warn("Catalogue : recette {} ignorée", file, e);
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    /** Un ingrédient : objet, tag (« #minecraft:planks ») ou liste ; toutes les variantes, pour les faire défiler. */
    private static List<ItemStack> ingredient(JsonElement element) {
        List<ItemStack> stacks = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> stacks.addAll(ingredient(e)));
            return stacks;
        }
        String ref = element.isJsonObject() ? element.getAsJsonObject().has("tag") ? "#" + element.getAsJsonObject().get("tag").getAsString()
                : element.getAsJsonObject().get("item").getAsString() : element.getAsString();
        if (ref.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(ref.substring(1)));
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                stacks.add(new ItemStack(holder.value()));
            }
        } else {
            stacks.add(new ItemStack(item(ref)));
        }
        return stacks.isEmpty() ? List.of(new ItemStack(Items.BARRIER)) : stacks;
    }

    // --- Mise en page --------------------------------------------------------------------------

    @Override
    protected void init() {
        panelWidth = Math.min(SIDE + 7 * CELL + DETAIL + 28, width - 12);
        panelHeight = Math.min(250, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    private int gridLeft() {
        return left + SIDE + 12;
    }

    private int gridWidth() {
        return panelWidth - SIDE - DETAIL - 24;
    }

    private int detailLeft() {
        return left + panelWidth - DETAIL - 6;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        hits.clear();
        Ui.frame(g, left, top, panelWidth, panelHeight, 18);
        g.text(font, "CATALOGUE TERRACRAFT · OBJETS ET RECETTES", left + 8, top + 5, SuitScreen.GOLD, false);
        extractCategories(g);
        extractGrid(g);
        extractDetail(g);
        g.text(font, "Clic : fiche de l'objet · Échap : fermer", left + 8, top + panelHeight - 12, SuitScreen.GREY, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY) && !hit.hover().isEmpty()) {
                g.setTooltipForNextFrame(font, hit.hover(), mouseX, mouseY);
            }
        }
    }

    private void extractCategories(GuiGraphicsExtractor g) {
        int y = top + 24;
        for (int i = 0; i < ModTabs.CATEGORIES.size(); i++) {
            ModTabs.Category c = ModTabs.CATEGORIES.get(i);
            boolean active = i == category;
            g.fill(left + 4, y, left + SIDE, y + 19, active ? 0xC0233847 : 0x60182430);
            if (active) {
                g.fill(left + 4, y, left + 6, y + 19, SuitScreen.GOLD);
            }
            g.item(new ItemStack(c.icon().get()), left + 8, y + 1);
            String title = c.title().getString().replace("TerraCraft — ", "");
            g.text(font, font.plainSubstrByWidth(title, SIDE - 32), left + 28, y + 6, active ? SuitScreen.TEXT : SuitScreen.GREY, false);
            int index = i;
            hits.add(new Hit(left + 4, y, SIDE - 4, 19, () -> {
                category = index;
                selected = items().getFirst();
                recipeIndex = 0;
            }, ItemStack.EMPTY));
            y += 21;
        }
    }

    private void extractGrid(GuiGraphicsExtractor g) {
        int columns = Math.max(1, gridWidth() / CELL);
        int x0 = gridLeft();
        int y0 = top + 24;
        Ui.section(g, font, ModTabs.CATEGORIES.get(category).title().getString().replace("TerraCraft — ", ""), x0, y0, gridWidth());
        List<Item> items = items();
        for (int i = 0; i < items.size(); i++) {
            int x = x0 + (i % columns) * CELL;
            int y = y0 + 12 + (i / columns) * CELL;
            Item item = items.get(i);
            g.fill(x, y, x + 18, y + 18, item == selected ? SuitScreen.GOLD : SuitScreen.SLOT_EDGE);
            g.fill(x + 1, y + 1, x + 17, y + 17, SuitScreen.SLOT_BG);
            ItemStack stack = new ItemStack(item);
            g.item(stack, x + 1, y + 1);
            hits.add(new Hit(x, y, 18, 18, () -> select(item), stack));
        }
    }

    private void select(Item item) {
        selected = item;
        recipeIndex = 0;
        for (int i = 0; i < ModTabs.CATEGORIES.size(); i++) {
            if (ModTabs.CATEGORIES.get(i).items().get().stream().anyMatch(like -> like.asItem() == item)) {
                category = i;
                return;
            }
        }
    }

    private void extractDetail(GuiGraphicsExtractor g) {
        int x = detailLeft();
        int y = top + 24;
        g.fill(x - 4, y - 2, x - 3, top + panelHeight - 16, SuitScreen.SLOT_EDGE);
        ItemStack stack = new ItemStack(selected);
        g.item(stack, x, y);
        g.text(font, font.plainSubstrByWidth(stack.getHoverName().getString(), DETAIL - 22), x + 20, y + 4, SuitScreen.TEXT, false);
        y += 22;
        String key = "desc." + BuiltInRegistries.ITEM.getKey(selected).toLanguageKey();
        if (Language.getInstance().has(key)) {
            for (FormattedCharSequence line : font.split(Component.translatable(key), DETAIL - 4)) {
                g.text(font, line, x, y, SuitScreen.GREY, false);
                y += 10;
            }
        }
        y += 6;
        List<Recipe> list = recipes.getOrDefault(selected, List.of());
        if (list.isEmpty()) {
            Ui.section(g, font, "Fabrication", x, y, DETAIL - 4);
            g.text(font, "Ne se fabrique pas : à trouver.", x, y + 12, SuitScreen.GREY, false);
            y += 28;
        } else {
            Recipe recipe = list.get(Math.floorMod(recipeIndex, list.size()));
            Ui.section(g, font, list.size() > 1 ? "Recette " + (Math.floorMod(recipeIndex, list.size()) + 1) + "/" + list.size() + " (clic)"
                    : "Recette", x, y, DETAIL - 4);
            if (list.size() > 1) {
                hits.add(new Hit(x, y, DETAIL - 4, 9, () -> recipeIndex++, ItemStack.EMPTY));
            }
            y += 12;
            y = extractRecipe(g, recipe, x, y);
        }
        List<Item> uses = usedIn.getOrDefault(selected, List.of());
        if (!uses.isEmpty() && y + 30 < top + panelHeight - 14) {
            Ui.section(g, font, "Sert à fabriquer", x, y, DETAIL - 4);
            y += 12;
            int perRow = (DETAIL - 4) / CELL;
            for (int i = 0; i < uses.size() && y + (i / perRow) * CELL + 18 < top + panelHeight - 14; i++) {
                int sx = x + (i % perRow) * CELL;
                int sy = y + (i / perRow) * CELL;
                ItemStack use = new ItemStack(uses.get(i));
                slot(g, sx, sy, use);
                Item target = uses.get(i);
                hits.add(new Hit(sx, sy, 18, 18, () -> select(target), use));
            }
        }
    }

    /** Grille de la recette, flèche et résultat ; renvoie le bas du dessin. */
    private int extractRecipe(GuiGraphicsExtractor g, Recipe recipe, int x, int y) {
        long tick = Util.getMillis() / 1000;
        if (recipe.kind().equals("smelting") || recipe.kind().equals("blasting")) {
            g.text(font, recipe.kind().equals("blasting") ? "Haut fourneau :" : "Four :", x, y + 5, SuitScreen.TEXT, false);
            int sx = x + 74;
            ItemStack input = cycle(recipe.slots().getFirst(), tick);
            slot(g, sx, y, input);
            hit(sx, y, input);
            g.text(font, "→", sx + 22, y + 5, SuitScreen.GOLD, false);
            slot(g, sx + 34, y, recipe.result());
            return y + 26;
        }
        int columns = recipe.kind().equals("crafting_shapeless") ? 3 : recipe.width();
        for (int i = 0; i < recipe.slots().size(); i++) {
            int sx = x + (i % columns) * 19;
            int sy = y + (i / columns) * 19;
            ItemStack ingredient = cycle(recipe.slots().get(i), tick);
            slot(g, sx, sy, ingredient);
            hit(sx, sy, ingredient);
        }
        int rows = (recipe.slots().size() + columns - 1) / columns;
        int mid = y + (rows * 19) / 2 - 9;
        g.text(font, "→", x + 3 * 19 + 6, mid + 5, SuitScreen.GOLD, false);
        slot(g, x + 3 * 19 + 18, mid, recipe.result());
        boolean pocket = recipe.kind().equals("crafting_shaped") ? rows <= 2 && columns <= 2 : recipe.slots().size() <= 4;
        g.text(font, pocket ? "Inventaire" : "Établi", x + 3 * 19 + 40, mid + 5, SuitScreen.GREY, false);
        return y + rows * 19 + 8;
    }

    private static ItemStack cycle(List<ItemStack> options, long tick) {
        return options.isEmpty() ? ItemStack.EMPTY : options.get((int) (tick % options.size()));
    }

    private void slot(GuiGraphicsExtractor g, int x, int y, ItemStack stack) {
        g.fill(x, y, x + 18, y + 18, SuitScreen.SLOT_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SuitScreen.SLOT_BG);
        if (!stack.isEmpty()) {
            g.item(stack, x + 1, y + 1);
            g.itemDecorations(font, stack, x + 1, y + 1);
        }
    }

    /** Ingrédient cliquable : les objets du mod mènent à leur fiche. */
    private void hit(int x, int y, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        boolean ours = BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(GeoMod.MOD_ID);
        hits.add(new Hit(x, y, 18, 18, ours ? () -> select(item) : () -> {
        }, stack));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Hit hit : List.copyOf(hits)) {
                if (hit.contains(event.x(), event.y())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
