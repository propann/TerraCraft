package com.terracraft.geo;

import com.terracraft.geo.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Station orbitale de départ, déployée d'un coup par le kit de station : d'ouest en est,
 * salle de stockage — tunnel vitré — salle de travail (atelier, oxygène, coffres, grandes baies) — tunnel vitré —
 * quai d'amarrage ouvert sur l'espace (pinces d'amarrage, balise au centre). Des sas étanches séparent chaque partie.
 * Seul l'air est remplacé : le déploiement ne détruit jamais rien.
 */
final class StationBuilder {
    /** Position du quai par rapport au centre de la salle de travail (axe est). */
    static final int DOCK_OFFSET = 22;

    private final ServerLevel level;
    private final BlockPos origin;

    private StationBuilder(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
    }

    /**
     * Construit la station de sorte que la balise du quai soit en {@code beacon} ; renvoie la position de la
     * balise (le quai), où se posent les fusées.
     */
    static BlockPos buildOrbital(ServerLevel level, BlockPos beacon) {
        StationBuilder b = new StationBuilder(level, beacon.offset(-DOCK_OFFSET, 0, 0));
        b.room(0, 6, 6, true);                    // Salle de travail 13 × 13.
        b.tunnel(7, 16);                          // Tunnel vers le quai.
        b.tunnel(-12, -7);                        // Tunnel vers le stockage.
        b.room(-17, 4, 5, false);                 // Salle de stockage 9 × 9 centrée en x = -17.
        b.dock(DOCK_OFFSET);
        // Sas étanches entre les parties.
        b.door(6, Direction.EAST);
        b.door(-6, Direction.WEST);
        b.door(-13, Direction.WEST);
        b.door(17, Direction.EAST);
        // Équipement de la salle de travail.
        b.put(-4, 1, -4, ModBlocks.STATION_WORKSHOP);
        b.put(-4, 1, 4, Blocks.CRAFTING_TABLE);
        b.put(4, 1, -4, Blocks.CHEST);
        b.put(4, 1, 4, Blocks.CHEST);
        b.put(-2, 1, -5, Blocks.FURNACE);
        b.floor(0, 0, ModBlocks.OXYGEN_DISTRIBUTOR);
        b.floor(12, 0, ModBlocks.OXYGEN_DISTRIBUTOR);   // Tunnel est.
        b.floor(-17, 0, ModBlocks.OXYGEN_DISTRIBUTOR);  // Stockage (couvre aussi le tunnel ouest).
        for (int z = -3; z <= 3; z += 2) {
            b.put(-20, 1, z, Blocks.CHEST);
        }
        b.put(-14, 1, -3, Blocks.BARREL);
        b.put(-14, 1, 3, Blocks.BARREL);
        b.level.setBlock(beacon, ModBlocks.STATION_BEACON.defaultBlockState(), Block.UPDATE_ALL);
        return beacon;
    }

    private void put(int dx, int dy, int dz, Block block) {
        put(dx, dy, dz, block.defaultBlockState());
    }

    private void put(int dx, int dy, int dz, BlockState state) {
        BlockPos pos = origin.offset(dx, dy, dz);
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Remplace un bloc du plancher (intégré au sol, il ne gêne pas le passage). */
    private void floor(int dx, int dz, Block block) {
        BlockPos pos = origin.offset(dx, 0, dz);
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || current.is(ModBlocks.STATION_FLOOR)) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Salle carrée centrée en x = {@code cx}, demi-largeur {@code half}, hauteur {@code height}. */
    private void room(int cx, int half, int height, boolean bigWindows) {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    boolean wall = Math.abs(dx) == half || Math.abs(dz) == half;
                    Block block;
                    if (dy == 0) {
                        block = ModBlocks.STATION_FLOOR;
                    } else if (dy == height) {
                        block = (Math.abs(dx) % 4 == 2 && Math.abs(dz) % 4 == 2) ? ModBlocks.STATION_LIGHT : ModBlocks.STATION_HULL;
                    } else if (wall) {
                        boolean corner = Math.abs(dx) == half && Math.abs(dz) == half;
                        boolean band = bigWindows ? dy >= 2 && dy <= 3 : dy == 2;
                        block = !corner && band && Math.abs(Math.abs(dx) == half ? dz : dx) < half - 1
                                ? ModBlocks.STATION_WINDOW : ModBlocks.STATION_HULL;
                    } else {
                        continue;
                    }
                    put(cx + dx, dy, dz, block);
                }
            }
        }
    }

    /** Tunnel vitré sur l'axe est-ouest, de x1 à x2 : section extérieure 5 × 5, passage 3 × 3. */
    private void tunnel(int x1, int x2) {
        for (int dx = x1; dx <= x2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    boolean shell = Math.abs(dz) == 2 || dy == 0 || dy == 4;
                    if (!shell) {
                        continue;
                    }
                    Block block;
                    if (dy == 0) {
                        block = ModBlocks.STATION_FLOOR;
                    } else if (dy == 4) {
                        block = dz == 0 && Math.floorMod(dx, 4) == 0 ? ModBlocks.STATION_LIGHT : ModBlocks.STATION_HULL;
                    } else {
                        block = dy == 2 && Math.floorMod(dx, 2) == 0 ? ModBlocks.STATION_WINDOW : ModBlocks.STATION_HULL;
                    }
                    put(dx, dy, dz, block);
                }
            }
        }
    }

    /** Quai d'amarrage 11 × 11 ouvert sur l'espace, pinces autour du point d'arrivée des fusées. */
    private void dock(int cx) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                boolean edge = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                put(cx + dx, 0, dz, edge && Math.floorMod(dx + dz, 2) == 0 ? ModBlocks.STATION_LIGHT : ModBlocks.STATION_FLOOR);
            }
        }
        // Mur de raccordement côté tunnel (le sas est posé dedans).
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                put(cx - 5, dy, dz, ModBlocks.STATION_HULL);
            }
        }
        // Pinces autour du point d'arrivée (balise + 2,5 blocs).
        for (int[] c : new int[][]{{1, -2}, {1, 2}, {4, -2}, {4, 2}}) {
            put(cx + c[0], 1, c[1], ModBlocks.DOCKING_CLAMP);
        }
    }

    /** Sas étanche (porte de 1 × 2) dans le mur situé en x = {@code dx}, au milieu (z = 0). */
    private void door(int dx, Direction facing) {
        BlockPos lower = origin.offset(dx, 1, 0);
        BlockPos upper = lower.above();
        // Le sas remplace la coque du mur à cet endroit (ouverture voulue), jamais autre chose.
        for (BlockPos pos : new BlockPos[]{lower, upper}) {
            BlockState current = level.getBlockState(pos);
            if (!current.isAir() && !current.is(ModBlocks.STATION_HULL) && !current.is(ModBlocks.STATION_WINDOW)) {
                return;
            }
        }
        BlockState door = ModBlocks.AIRLOCK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        level.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
    }
}
