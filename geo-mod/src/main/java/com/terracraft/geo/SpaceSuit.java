package com.terracraft.geo;

import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.OxygenTankItem;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combinaison spatiale : un équipement à part de l'armure, avec ses propres emplacements.
 * Plus simple que Galacticraft (pas de masque, d'engrenage ni de fréquence) :
 * <ul>
 *   <li>casque spatial — obligatoire pour respirer hors de la Terre ;</li>
 *   <li>combinaison — consommation d'oxygène réduite de 25 % ;</li>
 *   <li>bottes magnétiques — adhérence en orbite, pas de dégâts de chute dans l'espace ;</li>
 *   <li>deux réserves de bouteilles d'oxygène qui se branchent seules quand le casque est à moitié vide ;</li>
 *   <li>module dorsal — jetpack (poussée en maintenant saut, recharge au bidon d'essence).</li>
 * </ul>
 * L'équipement est dessiné sur le joueur comme une armure, par-dessus l'armure normale.
 */
public final class SpaceSuit {
    public static final int HELMET = 0;
    public static final int SUIT = 1;
    public static final int BOOTS = 2;
    public static final int TANK_A = 3;
    public static final int TANK_B = 4;
    /** Module dorsal : jetpack. */
    public static final int BACK = 5;
    public static final int SIZE = 6;

    /** Le casque se recharge automatiquement quand il reste moins de cette part d'oxygène. */
    private static final double AUTO_REFILL_BELOW = 0.5;
    /** Recharge dans la bulle d'un distributeur, en secondes d'air par seconde. */
    static final int DISTRIBUTOR_REFILL = 10;

    /** Équipement spatial porté : sauvegardé avec le joueur, visible par les autres joueurs. */
    public static final AttachmentType<List<ItemStack>> EQUIPMENT = AttachmentRegistry.<List<ItemStack>>builder()
            .persistent(ItemStack.OPTIONAL_CODEC.listOf())
            .copyOnDeath()
            .syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.all())
            .buildAndRegister(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "space_suit"));

    /** Ancienne réserve (0.5 et 0.6) : lue une fois pour migrer les bouteilles. */
    private static final AttachmentType<ItemStack> LEGACY_RESERVE = AttachmentRegistry.<ItemStack>builder()
            .persistent(ItemStack.OPTIONAL_CODEC)
            .buildAndRegister(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "oxygen_reserve"));

    public static final MenuType<Menu> MENU = Registry.register(BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "space_suit"), new MenuType<>(Menu::new, FeatureFlags.VANILLA_SET));

    private SpaceSuit() {
    }

    /** Force le chargement de la classe (enregistrements) depuis l'initialiseur du mod. */
    static void register() {
    }

    // --- Accès à l'équipement --------------------------------------------------------------------

    /** Objet porté dans un emplacement (instance réelle : appeler {@link #changed} après l'avoir modifié). */
    public static ItemStack get(Player player, int slot) {
        List<ItemStack> items = player.getAttached(EQUIPMENT);
        return items == null || slot >= items.size() ? ItemStack.EMPTY : items.get(slot);
    }

    static void set(Player player, int slot, ItemStack stack) {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(SIZE, ItemStack.EMPTY));
        List<ItemStack> current = player.getAttached(EQUIPMENT);
        if (current != null) {
            for (int i = 0; i < Math.min(SIZE, current.size()); i++) {
                items.set(i, current.get(i));
            }
        }
        items.set(slot, stack);
        if (items.stream().allMatch(ItemStack::isEmpty)) {
            player.removeAttached(EQUIPMENT);
        } else {
            player.setAttached(EQUIPMENT, items);
        }
    }

    /** Réenregistre l'équipement après une modification sur place (oxygène consommé…) pour le synchroniser. */
    static void changed(Player player) {
        List<ItemStack> current = player.getAttached(EQUIPMENT);
        if (current != null) {
            player.setAttached(EQUIPMENT, new ArrayList<>(current));
        }
    }

    /** Jetpack porté (instance réelle), ou vide. */
    public static ItemStack jetpack(Player player) {
        ItemStack jetpack = get(player, BACK);
        return jetpack.is(ModContent.JETPACK) ? jetpack : ItemStack.EMPTY;
    }

    /** Jetpack porté avec du carburant. */
    public static boolean hasJetpackFuel(Player player) {
        ItemStack jetpack = jetpack(player);
        return !jetpack.isEmpty() && jetpack.getDamageValue() < jetpack.getMaxDamage() - 1;
    }

    public static ItemStack helmet(Player player) {
        ItemStack helmet = get(player, HELMET);
        return helmet.is(ModContent.SPACE_HELMET) ? helmet : ItemStack.EMPTY;
    }

    static boolean hasSuit(Player player) {
        return get(player, SUIT).is(ModContent.SPACE_SUIT);
    }

    static boolean hasBoots(Player player) {
        return get(player, BOOTS).is(ModContent.MAGNETIC_BOOTS);
    }

    public static int tanks(Player player) {
        return countTanks(get(player, TANK_A)) + countTanks(get(player, TANK_B));
    }

    private static int countTanks(ItemStack stack) {
        return stack.is(ModContent.OXYGEN_TANK) ? stack.getCount() : 0;
    }

    /** Emplacement acceptant cet objet, ou -1. */
    public static int slotFor(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ModContent.SPACE_HELMET) {
            return HELMET;
        }
        if (item == ModContent.SPACE_SUIT) {
            return SUIT;
        }
        if (item == ModContent.MAGNETIC_BOOTS) {
            return BOOTS;
        }
        if (item == ModContent.JETPACK) {
            return BACK;
        }
        return item == ModContent.OXYGEN_TANK ? TANK_A : -1;
    }

    public static boolean accepts(int slot, ItemStack stack) {
        int wanted = slotFor(stack);
        return wanted == slot || wanted == TANK_A && slot == TANK_B;
    }

    /** Clic droit avec une pièce en main : elle est portée, l'ancienne revient dans la main. */
    public static boolean equipFromHand(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        int slot = slotFor(held);
        if (slot < 0 || slot == TANK_A) {
            return false;
        }
        ItemStack previous = get(player, slot);
        set(player, slot, held.copyWithCount(1));
        held.shrink(1);
        if (!previous.isEmpty()) {
            if (held.isEmpty()) {
                player.setItemInHand(hand, previous);
            } else {
                player.getInventory().placeItemBackInInventory(previous, Prediction.SERVER_ONLY);
            }
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.PLAYERS, 0.8f, 1.1f);
        return true;
    }

    // --- Oxygène ------------------------------------------------------------------------------

    /** Clic droit avec des bouteilles : rangées dans les réserves, sinon branchées sur le casque. */
    public static void useTank(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        int before = held.getCount();
        for (int slot = TANK_A; slot <= TANK_B && !held.isEmpty(); slot++) {
            ItemStack current = get(player, slot);
            if (current.isEmpty()) {
                set(player, slot, held.split(16));
            } else if (current.is(ModContent.OXYGEN_TANK) && current.getCount() < 16) {
                int moved = Math.min(16 - current.getCount(), held.getCount());
                set(player, slot, current.copyWithCount(current.getCount() + moved));
                held.shrink(moved);
            }
        }
        if (held.getCount() < before) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                    SoundSource.PLAYERS, 0.7f, 1.4f);
            player.sendOverlayMessage(Component.literal((before - held.getCount()) + " bouteille(s) rangée(s) dans la combinaison — "
                    + tanks(player) + " en réserve").withStyle(ChatFormatting.AQUA));
            return;
        }
        ItemStack helmet = helmet(player);
        if (helmet.isEmpty()) {
            player.sendOverlayMessage(Component.literal("Réserves pleines et pas de casque spatial : touche J").withStyle(ChatFormatting.GOLD));
        } else if (helmet.getDamageValue() > 0) {
            helmet.setDamageValue(Math.max(0, helmet.getDamageValue() - OxygenTankItem.REFILL));
            changed(player);
            held.shrink(1);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8f, 1.6f);
            player.sendOverlayMessage(Component.literal("Casque rechargé").withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * Branche une bouteille si le casque est à moitié vide (ou vide). Renvoie vrai si une
     * bouteille a été utilisée.
     */
    static boolean autoRefill(ServerPlayer player) {
        ItemStack helmet = helmet(player);
        if (helmet.isEmpty() || helmet.getDamageValue() < helmet.getMaxDamage() * AUTO_REFILL_BELOW) {
            return false;
        }
        int slot = countTanks(get(player, TANK_A)) > 0 ? TANK_A : countTanks(get(player, TANK_B)) > 0 ? TANK_B : -1;
        if (slot < 0) {
            return false;
        }
        helmet.setDamageValue(Math.max(0, helmet.getDamageValue() - OxygenTankItem.REFILL));
        ItemStack tanks = get(player, slot).copy();
        tanks.shrink(1);
        set(player, slot, tanks);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.7f, 1.8f);
        int left = tanks(player);
        player.sendOverlayMessage(Component.literal("Bouteille d'oxygène branchée — " + (left == 0 ? "réserve vide !"
                : left + (left > 1 ? " bouteilles restantes" : " bouteille restante")))
                .withStyle(left == 0 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        return true;
    }

    /** Casque spatial avec de l'air, ou au moins une bouteille prête à se brancher. */
    static boolean hasAirSupply(Player player) {
        ItemStack helmet = helmet(player);
        return !helmet.isEmpty() && (helmet.getDamageValue() < helmet.getMaxDamage() - 1 || tanks(player) > 0);
    }

    /** Secondes d'air disponibles : casque + bouteilles (la combinaison économise 25 %). */
    public static int autonomySeconds(Player player) {
        ItemStack helmet = helmet(player);
        if (helmet.isEmpty()) {
            return 0;
        }
        int seconds = Math.max(0, helmet.getMaxDamage() - 1 - helmet.getDamageValue()) + tanks(player) * OxygenTankItem.REFILL;
        return hasSuit(player) ? seconds * 4 / 3 : seconds;
    }

    /** Ancienne version : casque porté sur la tête et réserve unique. Déplacés dans la combinaison. */
    static void migrate(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(ModContent.SPACE_HELMET) && get(player, HELMET).isEmpty()) {
            set(player, HELMET, head.copy());
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.sendSystemMessage(Component.literal("Ton casque spatial a rejoint ta combinaison (touche J) : "
                    + "l'emplacement tête est libre pour un casque normal.").withStyle(ChatFormatting.AQUA));
        }
        ItemStack legacy = player.getAttached(LEGACY_RESERVE);
        if (legacy != null) {
            player.removeAttached(LEGACY_RESERVE);
            if (get(player, TANK_A).isEmpty()) {
                set(player, TANK_A, legacy);
            } else if (get(player, TANK_B).isEmpty()) {
                set(player, TANK_B, legacy);
            } else {
                player.getInventory().placeItemBackInInventory(legacy, Prediction.SERVER_ONLY);
            }
        }
    }

    /** Vue « inventaire » de la combinaison d'un joueur (emplacements 0 à 4). */
    public static Container container(Player player) {
        return new EquipmentContainer(player);
    }

    static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new Menu(id, inventory, new EquipmentContainer(owner)),
                Component.literal("Combinaison spatiale")));
    }

    // --- Inventaire de la combinaison -----------------------------------------------------------

    /**
     * Vue directe sur l'équipement du joueur (pas de copie) : si une bouteille se branche pendant
     * que la fenêtre est ouverte, aucune copie périmée ne peut la recréer en se refermant.
     */
    private static final class EquipmentContainer implements Container {
        private final Player player;

        EquipmentContainer(Player player) {
            this.player = player;
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < SIZE; i++) {
                if (!get(player, i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return get(player, slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = get(player, slot).copy();
            ItemStack taken = current.split(amount);
            set(player, slot, current);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = get(player, slot);
            set(player, slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            set(player, slot, stack);
        }

        @Override
        public void setChanged() {
            changed(player);
        }

        @Override
        public boolean stillValid(Player player) {
            return player == this.player && player.isAlive();
        }

        @Override
        public void clearContent() {
            player.removeAttached(EQUIPMENT);
        }
    }

    // --- Menu ----------------------------------------------------------------------------------

    /** Emplacements : 0-5 = combinaison, 6-32 = inventaire, 33-41 = barre rapide. */
    public static final class Menu extends AbstractContainerMenu {
        /** Positions des emplacements de la combinaison dans la fenêtre. */
        public static final int[][] POSITIONS = {{68, 18}, {68, 38}, {68, 58}, {132, 18}, {152, 18}, {68, 78}};
        public static final int INVENTORY_Y = 118;
        private static final int INVENTORY_END = SIZE + 27;
        private static final int HOTBAR_END = INVENTORY_END + 9;

        /** Côté client : le contenu arrive par la synchronisation du menu. */
        public Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(SIZE));
        }

        Menu(int id, Inventory inventory, Container equipment) {
            super(MENU, id);
            for (int i = 0; i < SIZE; i++) {
                addSlot(new SpaceSuitSlot(equipment, i, POSITIONS[i][0], POSITIONS[i][1]));
            }
            addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            if (index < SIZE) {
                if (!moveItemStackTo(stack, SIZE, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int target = slotFor(stack);
                boolean moved = false;
                if (target == TANK_A) {
                    moved = moveItemStackTo(stack, TANK_A, TANK_B + 1, false);
                } else if (target >= 0 && !slots.get(target).hasItem()) {
                    moved = moveItemStackTo(stack, target, target + 1, false);
                }
                if (!moved) {
                    moved = index < INVENTORY_END
                            ? moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)
                            : moveItemStackTo(stack, SIZE, INVENTORY_END, false);
                }
                if (!moved) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
            return original;
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive();
        }
    }
}
