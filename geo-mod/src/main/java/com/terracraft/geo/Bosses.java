package com.terracraft.geo;

import com.terracraft.geo.content.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boss rares des bunkers : le « Commandant du bunker » (un bunker sur cinq), zombie d'élite à 120 PV, armé et
 * cuirassé, qui ne disparaît jamais. Barre de boss pour les joueurs à moins de 32 blocs. À sa mort : butin unique
 * (insigne du commandant, diamants, netherite…), prime de 300 crédits au tueur et annonce au serveur.
 */
public final class Bosses {
    public static final String TAG = "terracraft_boss";
    private static final int REWARD = 300;
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private Bosses() {
    }

    /** Crée le commandant (génération d'un bunker ou commande d'administration). */
    public static Zombie commander(ServerLevelAccessor level, BlockPos pos) {
        Zombie boss = EntityTypes.ZOMBIE.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (boss == null) {
            return null;
        }
        boss.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        boss.addTag(TAG);
        boss.setCustomName(Component.literal("☠ Commandant du bunker").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        boss.setCustomNameVisible(true);
        boss.setPersistenceRequired();
        set(boss, Attributes.MAX_HEALTH, 120);
        set(boss, Attributes.ATTACK_DAMAGE, 9);
        set(boss, Attributes.ARMOR, 12);
        set(boss, Attributes.KNOCKBACK_RESISTANCE, 0.8);
        set(boss, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.25);
        boss.setHealth(120);
        boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        boss.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        boss.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        boss.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            boss.setDropChance(slot, 0f); // Le butin vient de onDeath, pas de l'équipement.
        }
        return boss;
    }

    private static void set(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                            double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static boolean isBoss(Entity entity) {
        return entity.entityTags().contains(TAG);
    }

    /** Commandants chargés (événements de chargement d'entité) : la barre ne parcourt pas tout le monde. */
    private static final Map<UUID, LivingEntity> LOADED = new HashMap<>();

    static void onLoad(Entity entity) {
        if (entity instanceof LivingEntity living && isBoss(entity)) {
            LOADED.put(entity.getUUID(), living);
        }
    }

    static void onUnload(Entity entity) {
        if (LOADED.remove(entity.getUUID()) != null) {
            ServerBossEvent bar = BARS.remove(entity.getUUID());
            if (bar != null) {
                bar.removeAllPlayers();
            }
        }
    }

    /** Barres de boss : suivent la vie des commandants chargés, montrées aux joueurs à moins de 32 blocs. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (LivingEntity boss : java.util.List.copyOf(LOADED.values())) {
            if (!boss.isAlive() || !(boss.level() instanceof ServerLevel level)) {
                onUnload(boss);
                continue;
            }
            ServerBossEvent bar = BARS.computeIfAbsent(boss.getUUID(), id -> new ServerBossEvent(id,
                    boss.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
            bar.setProgress(Math.max(0, boss.getHealth() / boss.getMaxHealth()));
            for (ServerPlayer player : level.players()) {
                boolean near = player.distanceToSqr(boss) < 32 * 32 && !player.isSpectator();
                if (near && !bar.getPlayers().contains(player)) {
                    bar.addPlayer(player);
                } else if (!near && bar.getPlayers().contains(player)) {
                    bar.removePlayer(player);
                }
            }
            for (ServerPlayer player : java.util.List.copyOf(bar.getPlayers())) {
                if (player.level() != level) {
                    bar.removePlayer(player);
                }
            }
        }
    }

    /** Mort d'un commandant : butin unique, prime au tueur, annonce. */
    static void onDeath(LivingEntity entity, DamageSource source, AuctionHouse bank) {
        if (!isBoss(entity) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        onUnload(entity);
        var random = level.getRandom();
        ItemStack[] loot = {
                new ItemStack(ModBlocks.COMMANDER_BADGE),
                new ItemStack(Items.DIAMOND, 3 + random.nextInt(4)),
                new ItemStack(Items.NETHERITE_SCRAP, 1 + random.nextInt(2)),
                new ItemStack(Items.GOLDEN_APPLE, 2),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + random.nextInt(8))};
        for (ItemStack stack : loot) {
            level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
        }
        if (source.getEntity() instanceof ServerPlayer killer) {
            bank.credit(killer, REWARD, "prime : Commandant du bunker abattu");
            Progression.get().discover(killer, "bunker_boss");
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("☠ " + killer.getName().getString()
                    + " a abattu un Commandant de bunker !").withStyle(ChatFormatting.GOLD), false);
        }
        GeoMod.LOGGER.info("[BOSS] Commandant abattu en {} par {}", entity.blockPosition(),
                source.getEntity() == null ? "?" : source.getEntity().getName().getString());
    }
}
