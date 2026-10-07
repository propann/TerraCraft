package com.terracraft.geo;

import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.Vehicle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Convois militaires en panne : régulièrement, un camion militaire tombe en panne près d'un joueur sur Terre. Il
 * manque une roue et il n'a plus de carburant, mais il est à prendre : le premier qui le répare le garde. Une caisse
 * de matériel est tombée à côté, et une escorte armée (zombies et squelettes casqués, pillards) le garde. Position
 * annoncée à tous, fumée pendant 15 minutes. Tout est posé dans des chunks déjà chargés.
 */
final class Convoys {
    /** Délai entre deux convois (ticks) : 50 à 80 minutes, décalé des largages. */
    private static final int MIN_DELAY = 50 * 60 * 20;
    private static final int MAX_DELAY = 80 * 60 * 20;
    private static final int SIGNAL_TICKS = 15 * 60 * 20;
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "chests/convoy"));

    private record Convoy(ServerLevel level, BlockPos pos, int expires) {
    }

    private final List<Convoy> active = new ArrayList<>();
    private long next = -1;

    void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (next < 0) {
            next = now + 35L * 60 * 20; // Premier convoi 35 minutes après le démarrage.
        }
        if (now >= next) {
            next = now + MIN_DELAY + server.overworld().getRandom().nextInt(MAX_DELAY - MIN_DELAY);
            spawn(server, null);
        }
        if (now % 20 == 0) {
            Iterator<Convoy> it = active.iterator();
            while (it.hasNext()) {
                Convoy convoy = it.next();
                if (now > convoy.expires()) {
                    it.remove();
                    continue;
                }
                for (int i = 0; i < 5; i++) {
                    convoy.level().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, convoy.pos().getX() + 0.5,
                            convoy.pos().getY() + 2 + i * 3, convoy.pos().getZ() + 0.5, 1, 0.2, 0.5, 0.2, 0.01);
                }
            }
        }
    }

    /** Convoi en panne près de {@code target} (ou d'un joueur au hasard) ; renvoie la position du camion, ou null. */
    BlockPos spawn(MinecraftServer server, ServerPlayer target) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> candidates = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (target == null) {
            if (candidates.isEmpty()) {
                return null;
            }
            target = candidates.get(level.getRandom().nextInt(candidates.size()));
        }
        RandomSource random = level.getRandom();
        int reach = Math.max(32, Math.min(110, server.getPlayerList().getSimulationDistance() * 16 - 24));
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = reach / 2 + random.nextInt(reach / 2 + 1);
            int x = target.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = target.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.isPositionEntityTicking(new BlockPos(x, target.getBlockY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos).canBeReplaced()) {
                continue;
            }
            Vehicle truck = ModContent.TRUCK.create(level, EntitySpawnReason.EVENT);
            if (truck == null) {
                return null;
            }
            truck.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!level.noCollision(truck, truck.getBoundingBox())) {
                continue;
            }
            // En panne : une roue manquante et réservoir vide ; sans propriétaire, il revient au premier qui le prend.
            truck.setParts(Vehicle.ENGINE | Vehicle.RADIATOR | Vehicle.BATTERY, 3, 0);
            truck.storage().addItem(new ItemStack(ModContent.FUEL_CAN)); // De quoi repartir une fois la roue remplacée.
            level.addFreshEntity(truck);
            BlockPos crate = findCrateSpot(level, pos);
            if (crate != null) {
                level.setBlockAndUpdate(crate, Blocks.BARREL.defaultBlockState());
                RandomizableContainer.setBlockEntityLootTable(level, random, crate, LOOT);
            }
            int escorts = 4 + random.nextInt(3) + Math.min(3, candidates.size() - 1);
            for (int i = 0; i < escorts; i++) {
                escort(level, pos, random, i);
            }
            active.add(new Convoy(level, pos.immutable(), server.getTickCount() + SIGNAL_TICKS));
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.5f);
            server.getPlayerList().broadcastSystemMessage(Component.literal("⚠ Un convoi militaire est tombé en panne en "
                    + x + " / " + y + " / " + z + " (près de " + target.getName().getString() + "). Escorte armée : "
                    + "matériel et camion à prendre (il manque une roue) !").withStyle(ChatFormatting.RED), false);
            GeoMod.LOGGER.info("[CONVOI] camion en {} {} {} près de {}, {} gardes", x, y, z, target.getName().getString(), escorts);
            return pos;
        }
        return null;
    }

    private static BlockPos findCrateSpot(ServerLevel level, BlockPos truck) {
        for (int[] offset : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {-3, -3}}) {
            int x = truck.getX() + offset[0];
            int z = truck.getZ() + offset[1];
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos.below()).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    /** Garde du convoi : équipé (casque contre le soleil), il disparaît normalement si personne ne vient. */
    private static void escort(ServerLevel level, BlockPos center, RandomSource random, int index) {
        EntityType<? extends Mob> type = index % 4 == 3 ? EntityTypes.PILLAGER : index % 2 == 0 ? EntityTypes.ZOMBIE : EntityTypes.SKELETON;
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return;
        }
        int x = center.getX() + random.nextInt(13) - 6;
        int z = center.getZ() + random.nextInt(13) - 6;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        mob.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
        if (type == EntityTypes.ZOMBIE) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        } else if (type == EntityTypes.SKELETON) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0.08f);
        }
        if (level.noCollision(mob, mob.getBoundingBox())) {
            level.addFreshEntityWithPassengers(mob);
        }
    }
}
