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
    /** Base de surface : le terrain naturel (régolithe, roche, sable…) peut être creusé, jamais une construction. */
    private final boolean carve;

    private StationBuilder(ServerLevel level, BlockPos origin) {
        this(level, origin, false);
    }

    private StationBuilder(ServerLevel level, BlockPos origin, boolean carve) {
        this.level = level;
        this.origin = origin;
        this.carve = carve;
    }

    /** Bloc remplaçable : l'air, ou (base de surface) un bloc de terrain naturel sans contenu. */
    private boolean replaceable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!carve || state.hasBlockEntity() || state.is(Blocks.BEDROCK)) {
            return false;
        }
        return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.tags.BlockTags.SAND)
                || state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.TERRACOTTA)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SANDSTONE)
                || state.getBlock() instanceof net.minecraft.world.level.block.ConcretePowderBlock
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.is(ModBlocks.TITANIUM_ORE) || state.is(ModBlocks.HELIUM3_CRYSTALS);
    }

    /** Vide un bloc de terrain naturel (intérieur des salles, dégagement au-dessus du quai). */
    private void clear(int dx, int dy, int dz) {
        BlockPos pos = origin.offset(dx, dy, dz);
        BlockState current = level.getBlockState(pos);
        if (carve && !current.isAir() && replaceable(current)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Base de surface (Lune ou Mars), déployée par un kit à l'atterrissage : aire d'atterrissage 9 × 9 avec pinces
     * et balise, tunnel avec sas, salle de vie 11 × 11 (atelier, oxygène, coffres). La balise est en {@code beacon},
     * au niveau du sol, sous le point d'arrivée de la fusée. Le reste de la base est un bloc plus haut que l'aire :
     * un vaisseau posé sur l'aire a son plancher au niveau de la base et sa porte en face du sas (voir ShipDocking).
     */
    static BlockPos buildSurfaceBase(ServerLevel level, BlockPos beacon) {
        StationBuilder b = new StationBuilder(level, beacon.above(), true);
        b.pad(0, 4);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 7; dy++) {
                    b.clear(dx, dy, dz);
                }
            }
        }
        b.tunnel(-8, -5);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                b.put(-5, dy, dz, ModBlocks.STATION_HULL);   // Bout du tunnel côté aire d'atterrissage.
            }
        }
        b.room(-14, 5, 5, true);
        b.door(-5, Direction.EAST);
        b.door(-9, Direction.EAST);
        b.floor(-14, 0, ModBlocks.OXYGEN_DISTRIBUTOR);
        b.put(-18, 1, -3, ModBlocks.STATION_WORKSHOP);
        b.put(-18, 1, 3, Blocks.CRAFTING_TABLE);
        b.put(-10, 1, -4, Blocks.CHEST);
        b.put(-10, 1, 4, Blocks.CHEST);
        b.put(-18, 1, 0, Blocks.FURNACE);
        level.setBlock(beacon, ModBlocks.STATION_BEACON.defaultBlockState(), Block.UPDATE_ALL);
        return beacon;
    }

    /**
     * Aire d'atterrissage carrée centrée en x = {@code cx}, un bloc sous le plancher de la station (dy = -1), de
     * demi-largeur {@code half} : liseré lumineux et pinces d'amarrage encastrées autour du point d'arrivée des fusées
     * (balise + 2,5 blocs). Rien ne dépasse de l'aire : un vaisseau peut s'y poser à plat.
     */
    private void pad(int cx, int half) {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                boolean clamp = Math.abs(dz) == 2 && (dx == 1 || dx == 4);
                Block block = clamp ? ModBlocks.DOCKING_CLAMP
                        : edge && Math.floorMod(dx + dz, 2) == 0 ? ModBlocks.STATION_LIGHT : ModBlocks.STATION_FLOOR;
                put(cx + dx, -1, dz, block);
            }
        }
    }

    /**
     * Construit la station de sorte que la balise du quai soit en {@code beacon} ; renvoie la position de la
     * balise (le quai), où se posent les fusées.
     */
    static BlockPos buildOrbital(ServerLevel level, BlockPos beacon) {
        // La station est un bloc plus haut que l'aire d'atterrissage (voir pad).
        StationBuilder b = new StationBuilder(level, beacon.offset(-DOCK_OFFSET, 1, 0));
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
        if (replaceable(level.getBlockState(pos))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Remplace un bloc du plancher (intégré au sol, il ne gêne pas le passage). */
    private void floor(int dx, int dz, Block block) {
        BlockPos pos = origin.offset(dx, 0, dz);
        BlockState current = level.getBlockState(pos);
        if (replaceable(current) || current.is(ModBlocks.STATION_FLOOR)) {
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
                        clear(cx + dx, dy, dz); // Intérieur : vidé du terrain naturel en surface.
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
                        clear(dx, dy, dz);
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

    /** Quai d'amarrage 11 × 11 ouvert sur l'espace, un bloc sous la station ; mur de raccordement avec le sas. */
    private void dock(int cx) {
        pad(cx, 5);
        // Mur de raccordement côté tunnel (le sas est posé dedans), sur un plancher au niveau de la station.
        for (int dz = -2; dz <= 2; dz++) {
            put(cx - 5, 0, dz, ModBlocks.STATION_FLOOR);
            for (int dy = 1; dy <= 3; dy++) {
                put(cx - 5, dy, dz, ModBlocks.STATION_HULL);
            }
        }
    }

    /** Sas étanche (porte de 1 × 2) dans le mur situé en x = {@code dx}, au milieu (z = 0). */
    private void door(int dx, Direction facing) {
        BlockPos lower = origin.offset(dx, 1, 0);
        BlockPos upper = lower.above();
        // Le sas remplace la coque du mur à cet endroit (ouverture voulue), jamais autre chose.
        for (BlockPos pos : new BlockPos[]{lower, upper}) {
            BlockState current = level.getBlockState(pos);
            if (!replaceable(current) && !current.is(ModBlocks.STATION_HULL) && !current.is(ModBlocks.STATION_WINDOW)) {
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
