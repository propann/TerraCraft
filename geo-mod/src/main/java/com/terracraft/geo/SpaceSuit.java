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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Combinaison spatiale : le casque (emplacement tête) et une réserve de bouteilles d'oxygène
 * qui se branchent toutes seules quand le casque descend à moitié. Plus simple que Galacticraft :
 * pas de masque, d'engrenage ni de fréquence, seulement de l'air et de l'autonomie.
 */
public final class SpaceSuit {
    /** Le casque se recharge automatiquement quand il reste moins de cette part d'oxygène. */
    private static final double AUTO_REFILL_BELOW = 0.5;
    /** Recharge dans la bulle d'un distributeur, en secondes d'air par seconde. */
    static final int DISTRIBUTOR_REFILL = 10;

    /** Réserve de bouteilles portée sur le dos : sauvegardée avec le joueur, envoyée à son client (HUD). */
    public static final AttachmentType<ItemStack> RESERVE = AttachmentRegistry.<ItemStack>builder()
            .persistent(ItemStack.OPTIONAL_CODEC)
            .copyOnDeath()
            .syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "oxygen_reserve"));

    public static final MenuType<Menu> MENU = Registry.register(BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "space_suit"), new MenuType<>(Menu::new, FeatureFlags.VANILLA_SET));

    private SpaceSuit() {
    }

    /** Force le chargement de la classe (enregistrements) depuis l'initialiseur du mod. */
    static void register() {
    }

    public static ItemStack reserve(Player player) {
        return player.getAttachedOrElse(RESERVE, ItemStack.EMPTY);
    }

    static void setReserve(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            player.removeAttached(RESERVE);
        } else {
            player.setAttached(RESERVE, stack.copy());
        }
    }

    static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new Menu(id, inventory, new ReserveContainer(owner)),
                Component.literal("Combinaison spatiale")));
    }

    /**
     * Branche une bouteille de la réserve si le casque porté est à moitié vide (ou vide).
     * Renvoie vrai si une bouteille a été utilisée.
     */
    static boolean autoRefill(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.is(ModContent.SPACE_HELMET) || helmet.getDamageValue() < helmet.getMaxDamage() * AUTO_REFILL_BELOW) {
            return false;
        }
        ItemStack reserve = reserve(player);
        if (!reserve.is(ModContent.OXYGEN_TANK)) {
            return false;
        }
        helmet.setDamageValue(Math.max(0, helmet.getDamageValue() - OxygenTankItem.REFILL));
        reserve.shrink(1);
        setReserve(player, reserve);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.7f, 1.8f);
        int left = reserve.getCount();
        player.sendOverlayMessage(Component.literal("Bouteille d'oxygène branchée — " + (left == 0 ? "réserve vide !"
                : left + (left > 1 ? " bouteilles restantes" : " bouteille restante")))
                .withStyle(left == 0 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        return true;
    }

    /** Casque porté avec de l'air, ou au moins une bouteille prête à se brancher. */
    static boolean hasAirSupply(Player player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(ModContent.SPACE_HELMET)
                && (helmet.getDamageValue() < helmet.getMaxDamage() - 1 || reserve(player).is(ModContent.OXYGEN_TANK));
    }

    /** Secondes d'air disponibles : casque + bouteilles de la réserve. */
    public static int autonomySeconds(Player player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.is(ModContent.SPACE_HELMET)) {
            return 0;
        }
        int inHelmet = Math.max(0, helmet.getMaxDamage() - 1 - helmet.getDamageValue());
        ItemStack reserve = reserve(player);
        return inHelmet + (reserve.is(ModContent.OXYGEN_TANK) ? reserve.getCount() * OxygenTankItem.REFILL : 0);
    }

    // --- Inventaire de la réserve ----------------------------------------------------------------

    /**
     * Vue directe sur la réserve du joueur (pas de copie) : si une bouteille se branche pendant
     * que la fenêtre est ouverte, aucune copie périmée ne peut la recréer en se refermant.
     */
    private static final class ReserveContainer implements Container {
        private final Player player;

        ReserveContainer(Player player) {
            this.player = player;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return reserve(player).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return reserve(player);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = reserve(player);
            ItemStack taken = current.split(amount);
            setReserve(player, current);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = reserve(player);
            setReserve(player, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            setReserve(player, stack);
        }

        @Override
        public void setChanged() {
            // Modifications faites directement sur la pile : on la réenregistre pour la synchroniser.
            setReserve(player, reserve(player));
        }

        @Override
        public boolean stillValid(Player player) {
            return player == this.player && player.isAlive();
        }

        @Override
        public void clearContent() {
            setReserve(player, ItemStack.EMPTY);
        }
    }

    // --- Menu ----------------------------------------------------------------------------------

    /** Emplacements : 0 = casque, 1 = réserve d'oxygène, 2-28 = inventaire, 29-37 = barre rapide. */
    public static final class Menu extends AbstractContainerMenu {
        public static final int HELMET_X = 80;
        public static final int HELMET_Y = 22;
        public static final int RESERVE_X = 80;
        public static final int RESERVE_Y = 62;
        public static final int INVENTORY_Y = 114;
        private static final int SUIT_SLOTS = 2;
        private static final int INVENTORY_END = SUIT_SLOTS + 27;
        private static final int HOTBAR_END = INVENTORY_END + 9;

        /** Côté client : la réserve est remplie par la synchronisation du menu. */
        public Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(1));
        }

        Menu(int id, Inventory inventory, Container reserve) {
            super(MENU, id);
            addSlot(new ArmorSlot(inventory, inventory.player, EquipmentSlot.HEAD, 39, HELMET_X, HELMET_Y, null));
            addSlot(new Slot(reserve, 0, RESERVE_X, RESERVE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModContent.OXYGEN_TANK);
                }
            });
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
            if (index < SUIT_SLOTS) {
                if (!moveItemStackTo(stack, SUIT_SLOTS, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean moved = false;
                if (slots.get(0).mayPlace(stack) && !slots.get(0).hasItem()) {
                    moved = moveItemStackTo(stack, 0, 1, false);
                } else if (stack.is(ModContent.OXYGEN_TANK)) {
                    moved = moveItemStackTo(stack, 1, 2, false);
                }
                if (!moved) {
                    moved = index < INVENTORY_END
                            ? moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)
                            : moveItemStackTo(stack, SUIT_SLOTS, INVENTORY_END, false);
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
