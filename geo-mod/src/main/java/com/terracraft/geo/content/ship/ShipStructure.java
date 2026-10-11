package com.terracraft.geo.content.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.geo.content.ShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Un vaisseau construit, vu comme un ensemble de blocs : repérage (de proche en proche, uniquement à travers les blocs
 * du tag {@link ShipBlocks#SHIP_BLOCKS}), prélèvement dans le monde et repose ailleurs, à l'identique (états et
 * contenus des coffres), éventuellement tourné d'un quart de tour.
 */
public final class ShipStructure {
    /** Taille maximale : au-delà, la construction touche probablement autre chose qu'un vaisseau. */
    public static final int MAX_BLOCKS = 400;
    /** Chaque réacteur soulève ce nombre de blocs. */
    public static final int BLOCKS_PER_THRUSTER = 60;
    /** Prélever ou poser sans effets de bord : ni butin, ni contenu de coffre répandu, ni mise à jour de forme. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_KNOWN_SHAPE;

    private ShipStructure() {
    }

    /** Un bloc du vaisseau, relatif à son ancre, avec le contenu de son bloc-entité (coffre…). */
    public record ShipBlock(BlockPos rel, BlockState state, Optional<CompoundTag> data) {
        public static final Codec<ShipBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("rel").forGetter(ShipBlock::rel),
                BlockState.CODEC.fieldOf("state").forGetter(ShipBlock::state),
                CompoundTag.CODEC.optionalFieldOf("data").forGetter(ShipBlock::data)
        ).apply(i, ShipBlock::new));

        ShipBlock rotated(Rotation rotation) {
            return new ShipBlock(rel.rotate(rotation), state.rotate(rotation), data);
        }
    }

    /**
     * Résultat du repérage. L'ancre est au centre de la construction, au niveau de son bloc le plus bas : tous les
     * blocs ont un {@code rel.y} positif ou nul.
     */
    public record Scan(BlockPos anchor, List<BlockPos> blocks, List<BlockPos> seats, List<BlockPos> consoles,
                       List<BlockPos> thrusters, List<BlockPos> tanks, int fuel, @Nullable BlockPos airlock,
                       @Nullable Direction outward, boolean tooBig) {

        public int thrustersNeeded() {
            return Math.max(1, (blocks.size() + BLOCKS_PER_THRUSTER - 1) / BLOCKS_PER_THRUSTER);
        }

        public int maxFuel() {
            return tanks.size() * ShipBlocks.FuelTankBlock.CAPACITY;
        }

        /** Ce qui manque pour que la construction soit un vaisseau, ou null. */
        public @Nullable String problem() {
            if (tooBig) {
                return "Construction trop grande (plus de " + MAX_BLOCKS + " blocs) ou collée à autre chose : "
                        + "un vaisseau ne touche que des blocs de vaisseau.";
            }
            List<String> missing = new ArrayList<>();
            if (seats.isEmpty()) {
                missing.add("un siège de pilote");
            }
            if (consoles.isEmpty()) {
                missing.add("une console de navigation");
            }
            if (tanks.isEmpty()) {
                missing.add("un réservoir de vaisseau");
            }
            if (thrusters.size() < thrustersNeeded()) {
                missing.add((thrustersNeeded() - thrusters.size()) + " réacteur(s) (1 pour " + BLOCKS_PER_THRUSTER
                        + " blocs, " + blocks.size() + " blocs ici)");
            }
            return missing.isEmpty() ? null : "Il manque " + String.join(", ", missing) + ".";
        }
    }

    /** Repère le vaisseau qui contient {@code start} (un bloc de vaisseau). */
    public static Scan scan(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> blocks = new ArrayList<>();
        boolean tooBig = false;
        if (level.getBlockState(start).is(ShipBlocks.SHIP_BLOCKS)) {
            seen.add(start.immutable());
            queue.add(start.immutable());
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            blocks.add(pos);
            if (blocks.size() > MAX_BLOCKS) {
                tooBig = true;
                break;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next) && level.getBlockState(next).is(ShipBlocks.SHIP_BLOCKS)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        List<BlockPos> seats = new ArrayList<>();
        List<BlockPos> consoles = new ArrayList<>();
        List<BlockPos> thrusters = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();
        int fuel = 0;
        BlockPos airlock = null;
        Direction outward = null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            BlockState state = level.getBlockState(pos);
            if (state.is(ShipBlocks.PILOT_SEAT)) {
                seats.add(pos);
            } else if (state.is(ShipBlocks.NAV_CONSOLE)) {
                consoles.add(pos);
            } else if (state.is(ShipBlocks.SHIP_THRUSTER)) {
                thrusters.add(pos);
            } else if (state.is(ShipBlocks.SHIP_FUEL_TANK)) {
                tanks.add(pos);
                fuel += state.getValue(ShipBlocks.FuelTankBlock.FUEL);
            } else if (state.is(ShipBlocks.SHIP_AIRLOCK) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && airlock == null) {
                outward = outward(seen, pos, state.getValue(DoorBlock.FACING));
                airlock = outward != null ? pos : null;
            }
        }
        BlockPos anchor = blocks.isEmpty() ? start : new BlockPos((minX + maxX) >> 1, minY, (minZ + maxZ) >> 1);
        return new Scan(anchor, blocks, seats, consoles, thrusters, tanks, fuel, airlock, outward, tooBig);
    }

    /** Côté extérieur d'un sas : celui de ses deux faces (porte ouverte vers l'avant ou l'arrière) hors du vaisseau. */
    private static @Nullable Direction outward(Set<BlockPos> ship, BlockPos lower, Direction facing) {
        for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
            if (!ship.contains(lower.relative(side)) && !ship.contains(lower.above().relative(side))) {
                return side;
            }
        }
        return null;
    }

    /**
     * Retire le vaisseau du monde et le renvoie relatif à son ancre. Les coffres gardent leur contenu (rien n'est
     * répandu au sol), les réservoirs perdent {@code fuelCost} doses au total.
     */
    public static List<ShipBlock> capture(ServerLevel level, Scan scan, int fuelCost) {
        List<ShipBlock> captured = new ArrayList<>(scan.blocks().size());
        int toBurn = fuelCost;
        for (BlockPos pos : scan.blocks()) {
            BlockState state = level.getBlockState(pos);
            if (toBurn > 0 && state.is(ShipBlocks.SHIP_FUEL_TANK)) {
                int fuel = state.getValue(ShipBlocks.FuelTankBlock.FUEL);
                int burnt = Math.min(fuel, toBurn);
                state = state.setValue(ShipBlocks.FuelTankBlock.FUEL, fuel - burnt);
                toBurn -= burnt;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            Optional<CompoundTag> data = entity == null ? Optional.empty() : Optional.of(entity.saveWithFullMetadata(level.registryAccess()));
            captured.add(new ShipBlock(pos.subtract(scan.anchor()), state, data));
        }
        for (BlockPos pos : scan.blocks()) {
            level.removeBlockEntity(pos);
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), QUIET);
        }
        return captured;
    }

    /** Pose le vaisseau, ancre en {@code origin}, tourné de {@code rotation}. */
    public static void place(ServerLevel level, BlockPos origin, Rotation rotation, List<ShipBlock> blocks) {
        for (ShipBlock block : blocks) {
            ShipBlock turned = block.rotated(rotation);
            level.setBlock(origin.offset(turned.rel()), turned.state(), QUIET);
        }
        for (ShipBlock block : blocks) {
            if (block.data().isEmpty()) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(origin.offset(block.rel().rotate(rotation)));
            if (entity != null) {
                entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), block.data().get()));
                entity.setChanged();
            }
        }
    }

    /** Quart de tour qui amène la direction {@code from} sur {@code to} (directions horizontales). */
    public static Rotation rotationTo(Direction from, Direction to) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(from) == to) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    /** Tous les emplacements sont-ils libres (air ou végétation remplaçable) ? */
    public static boolean fits(ServerLevel level, BlockPos origin, Rotation rotation, List<ShipBlock> blocks) {
        for (ShipBlock block : blocks) {
            BlockPos pos = origin.offset(block.rel().rotate(rotation));
            if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ancre où poser le vaisseau en ({@code x}, {@code z}) : posé sur le sol le plus haut sous sa coque, puis remonté
     * jusqu'à trouver la place (64 blocs au plus).
     */
    public static BlockPos restingOrigin(ServerLevel level, int x, int z, Rotation rotation, List<ShipBlock> blocks) {
        int y = level.getMinY();
        for (ShipBlock block : blocks) {
            BlockPos rel = block.rel().rotate(rotation);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + rel.getX(), z + rel.getZ());
            y = Math.max(y, ground - rel.getY());
        }
        BlockPos origin = new BlockPos(x, y, z);
        for (int lift = 0; lift < 64 && !fits(level, origin, rotation, blocks); lift++) {
            origin = origin.above();
        }
        return origin;
    }
}
