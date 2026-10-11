package com.terracraft.geo.content;

import com.mojang.serialization.Codec;
import com.terracraft.geo.GeoMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Viseurs : point rouge et lunettes x2, x4, x6, montés sur une arme à feu, un arc ou une arbalète (composant
 * {@code terracraft_geo:sight}). Montage comme un sac : dans l'inventaire, poser le viseur sur l'arme (il remplace
 * l'ancien, rendu au joueur) ; clic droit main vide sur l'arme : le viseur revient en main. On vise en maintenant la
 * touche « Viser » (bouton du milieu par défaut) : zoom, souris plus lente, réticule ; pour une arme à feu, dispersion
 * et recul réduits. L'arc et l'arbalète zooment aussi pendant qu'on les bande.
 */
public final class Sights {
    /** Viseur : grossissement, dispersion et recul en visée (facteurs), lunette (réticule rond) ou point rouge. */
    public enum Sight {
        RED_DOT("red_dot_sight", 1.25f, 0.5, 0.8f, false),
        X2("scope_x2", 2f, 0.35, 0.7f, true),
        X4("scope_x4", 4f, 0.2, 0.6f, true),
        X6("scope_x6", 6f, 0.12, 0.5f, true);

        public final String id;
        public final float zoom;
        public final double spread;
        public final float recoil;
        public final boolean scope;

        Sight(String id, float zoom, double spread, float recoil, boolean scope) {
            this.id = id;
            this.zoom = zoom;
            this.spread = spread;
            this.recoil = recoil;
            this.scope = scope;
        }

        public Item item() {
            return switch (this) {
                case RED_DOT -> RED_DOT_SIGHT;
                case X2 -> SCOPE_X2;
                case X4 -> SCOPE_X4;
                case X6 -> SCOPE_X6;
            };
        }

        static @Nullable Sight byId(@Nullable String id) {
            for (Sight sight : values()) {
                if (sight.id.equals(id)) {
                    return sight;
                }
            }
            return null;
        }
    }

    public static final DataComponentType<String> SIGHT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "sight"),
            DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static final Item RED_DOT_SIGHT = ModContent.item("red_dot_sight", SightItem::new, new Item.Properties().stacksTo(16));
    public static final Item SCOPE_X2 = ModContent.item("scope_x2", SightItem::new, new Item.Properties().stacksTo(16));
    public static final Item SCOPE_X4 = ModContent.item("scope_x4", SightItem::new, new Item.Properties().stacksTo(16));
    public static final Item SCOPE_X6 = ModContent.item("scope_x6", SightItem::new, new Item.Properties().stacksTo(16));

    /** Joueurs qui visent (touche maintenue, signalée par le client). */
    private static final Map<UUID, Boolean> AIMING = new ConcurrentHashMap<>();
    /** Côté client : le joueur local vise (le recul se calcule sur le client). */
    private static volatile boolean clientAiming;

    private Sights() {
    }

    public static void init() {
        GeoMod.LOGGER.info("TerraCraft : viseurs enregistrés.");
    }

    /** Armes qui reçoivent un viseur : armes à feu du mod, arc et arbalète. */
    public static boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof GunItem || stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
    }

    public static @Nullable Sight of(ItemStack weapon) {
        return Sight.byId(weapon.get(SIGHT));
    }

    public static @Nullable Sight ofItem(ItemStack stack) {
        for (Sight sight : Sight.values()) {
            if (stack.is(sight.item())) {
                return sight;
            }
        }
        return null;
    }

    public static void setAiming(ServerPlayer player, boolean aiming) {
        if (aiming) {
            AIMING.put(player.getUUID(), true);
        } else {
            AIMING.remove(player.getUUID());
        }
    }

    public static void forget(ServerPlayer player) {
        AIMING.remove(player.getUUID());
    }

    public static void setClientAiming(boolean aiming) {
        clientAiming = aiming;
    }

    /** Viseur utilisé en ce moment par le joueur (visée maintenue avec cette arme), ou null. */
    public static @Nullable Sight aimingWith(Player player, ItemStack weapon) {
        boolean aiming = player.level().isClientSide() ? clientAiming : AIMING.containsKey(player.getUUID());
        return aiming ? of(weapon) : null;
    }

    /** Clic droit main vide sur une arme équipée (inventaire) : le viseur revient en main. */
    public static boolean detach(ItemStack weapon, ItemStack carried, ClickAction action, Player player, SlotAccess carriedAccess) {
        Sight sight = of(weapon);
        if (sight == null || action != ClickAction.SECONDARY || !carried.isEmpty()) {
            return false;
        }
        weapon.remove(SIGHT);
        carriedAccess.set(new ItemStack(sight.item()));
        player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6f, 1.4f);
        return true;
    }

    /** Viseur posé sur une arme dans l'inventaire. */
    public static class SightItem extends Item {
        public SightItem(Properties properties) {
            super(properties);
        }

        @Override
        public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
            ItemStack weapon = slot.getItem();
            Sight sight = ofItem(self);
            if (action != ClickAction.PRIMARY || sight == null || !accepts(weapon) || !slot.allowModification(player)) {
                return false;
            }
            Sight previous = of(weapon);
            if (previous == sight) {
                return false;
            }
            String name = self.getHoverName().getString();
            weapon.set(SIGHT, sight.id);
            self.shrink(1);
            if (previous != null) {
                ItemStack old = new ItemStack(previous.item());
                if (!player.getInventory().add(old) && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    player.spawnAtLocation(level, old); // Inventaire plein : l'ancien viseur tombe aux pieds.
                }
            }
            player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6f, 1.2f);
            if (player instanceof ServerPlayer server) {
                server.sendOverlayMessage(Component.literal(weapon.getHoverName().getString() + " : " + name
                        + " montée (bouton du milieu pour viser)").withStyle(ChatFormatting.GREEN));
            }
            return true;
        }
    }
}
