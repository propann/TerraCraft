package com.terracraft.geo.world;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.content.ModMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Le sous-sol lunaire : cavernes géantes, sanctuaires extraterrestres (une salle en dôme, une pyramide à degrés avec
 * sa chambre au trésor, des piliers à glyphes et un puits d'accès marqué en surface) et petits donjons enfouis.
 * Tout est déterministe : la géométrie est calculée colonne par colonne pendant la génération du terrain, les coffres,
 * générateurs et gardiens sont posés à la décoration du chunk qui les contient.
 */
public final class MoonUnderground {
    /** Un sanctuaire au plus par carré de 384 blocs (60 % des carrés). */
    static final int CELL = 384;
    static final int HALL_RADIUS = 34;
    static final int HALL_HEIGHT = 36;
    static final int HALL_FLOOR = -10;
    static final int PYRAMID = 15;
    /** Le puits d'accès est à 22 blocs du centre, vers l'est (côté de l'entrée de la pyramide). */
    static final int SHAFT_OFFSET = 22;
    private static final int[][] PILLARS = new int[8][2];

    static {
        for (int k = 0; k < 8; k++) {
            PILLARS[k][0] = (int) Math.round(25 * Math.cos(k * Math.PI / 4 + Math.PI / 8));
            PILLARS[k][1] = (int) Math.round(25 * Math.sin(k * Math.PI / 4 + Math.PI / 8));
        }
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState STONE = ModBlocks.ALIEN_STONE.defaultBlockState();
    static final BlockState GLYPH = ModBlocks.ALIEN_GLYPH.defaultBlockState();
    static final BlockState CRYSTAL = ModBlocks.LUNAR_CRYSTAL.defaultBlockState();
    private static final BlockState LADDER = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
    static final ResourceKey<LootTable> SANCTUARY_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "chests/alien_sanctuary"));
    static final ResourceKey<LootTable> RUINS_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "chests/alien_ruins"));

    /** Centre d'un sanctuaire (sol de la salle en {@code y}). */
    public record Site(int x, int y, int z) {
        public BlockPos chest() {
            return new BlockPos(x, y + 1, z);
        }

        /** Pied du puits, dans la salle : là où l'on arrive en descendant l'échelle. */
        public BlockPos entrance() {
            return new BlockPos(x + SHAFT_OFFSET - 3, y, z);
        }
    }

    private MoonUnderground() {
    }

    /** Sanctuaire du carré contenant (x, z), ou null. */
    public static Site site(int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        long h = Apocalypse.hash(cx, cz, 71);
        if (Math.floorMod(h, 100) >= 60) {
            return null;
        }
        int margin = 64;
        return new Site(cx * CELL + margin + (int) Math.floorMod(h >>> 8, CELL - 2 * margin), HALL_FLOOR,
                cz * CELL + margin + (int) Math.floorMod(h >>> 28, CELL - 2 * margin));
    }

    /** Sanctuaire le plus proche de (x, z), cherché dans les carrés voisins (commande d'administration). */
    public static Site nearest(int x, int z) {
        Site best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Site site = site(x + dx * CELL, z + dz * CELL);
                if (site != null) {
                    double d = Math.hypot(site.x - x, site.z - z);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = site;
                    }
                }
            }
        }
        return best;
    }

    /** Le joueur est-il dans la salle d'un sanctuaire ? */
    public static boolean inHall(BlockPos pos) {
        Site site = site(pos.getX(), pos.getZ());
        return site != null && inDome(pos.getX() - site.x, pos.getY() - site.y, pos.getZ() - site.z);
    }

    private static boolean inDome(int dx, int dy, int dz) {
        double r = (dx * dx + dz * dz) / (double) (HALL_RADIUS * HALL_RADIUS);
        double v = dy / (double) HALL_HEIGHT;
        return dy >= 0 && r + v * v < 1;
    }

    /** Hauteur maximale construite dans la colonne (le puits dépasse la surface de 3 blocs). */
    static int top(Site site, int x, int z, int surface) {
        if (site != null && Math.abs(x - site.x - SHAFT_OFFSET) <= 2 && Math.abs(z - site.z) <= 2) {
            return surface + 3;
        }
        return surface;
    }

    /** Bloc du sanctuaire en (x, y, z), ou null si le sanctuaire ne touche pas ce bloc. */
    static BlockState sanctuary(Site site, int x, int y, int z, int surface) {
        if (site == null) {
            return null;
        }
        int dx = x - site.x;
        int dy = y - site.y;
        int dz = z - site.z;
        // Puits d'accès : tour creuse 5 × 5 du sol de la salle jusqu'à la surface, échelle sur la face est.
        int sx = dx - SHAFT_OFFSET;
        if (Math.abs(sx) <= 2 && Math.abs(dz) <= 2 && dy >= 0 && y <= surface + 3) {
            boolean wall = Math.abs(sx) == 2 || Math.abs(dz) == 2;
            boolean corner = Math.abs(sx) == 2 && Math.abs(dz) == 2;
            if (y > surface) {
                // Balise de surface : quatre piliers dont le sommet luit, visibles de loin ; l'échelle mène au niveau du sol.
                return corner ? (y == surface + 3 ? GLYPH : STONE) : AIR;
            }
            if (wall) {
                if (sx == -2 && dz == 0 && dy <= 2) {
                    return AIR; // Porte vers la pyramide.
                }
                return dy % 6 == 3 ? GLYPH : STONE;
            }
            return sx == 1 && dz == 0 ? LADDER : AIR;
        }
        if (dy == -1 && dx * dx + dz * dz < HALL_RADIUS * HALL_RADIUS) {
            double r = Math.sqrt(dx * dx + dz * dz);
            return Math.abs(r - 20) < 0.6 ? GLYPH : STONE; // Dallage, avec un anneau de glyphes.
        }
        if (!inDome(dx, dy, dz)) {
            return null;
        }
        // Pyramide à degrés : bandes de glyphes, chambre au trésor, couloir d'entrée vers l'est.
        int half = PYRAMID - dy;
        int m = Math.max(Math.abs(dx), Math.abs(dz));
        if (dy < PYRAMID && m <= half) {
            if (dy >= 1 && dy <= 5 && m <= 4) {
                return AIR;
            }
            if (dx > 0 && Math.abs(dz) <= 1 && dy >= 1 && dy <= 3) {
                return AIR;
            }
            if (dy == PYRAMID - 1 || m == half && dy % 4 == 2) {
                return GLYPH;
            }
            return STONE;
        }
        for (int[] p : PILLARS) {
            if (Math.abs(dx - p[0]) <= 1 && Math.abs(dz - p[1]) <= 1 && dy <= 14) {
                return dy % 3 == 0 ? GLYPH : STONE;
            }
        }
        return AIR;
    }

    /** Cavernes géantes : deux échelles de bruit, sous la croûte (14 blocs au moins sous la surface). */
    static boolean cave(int x, int y, int z, int surface) {
        if (y < -52 || y > surface - 14) {
            return false;
        }
        double fade = Math.min(1, Math.min((y + 52) / 8.0, (surface - 14 - y) / 8.0));
        double big = MoonChunkGenerator.fbm(x / 700.0, z / 700.0, 2, 67);
        double halls = MoonChunkGenerator.noise3(x / 90.0, y / 30.0, z / 90.0, 63);
        if (halls * fade > 0.74 - 0.08 * Math.max(0, big)) {
            return true;
        }
        return MoonChunkGenerator.noise3(x / 38.0, y / 18.0, z / 38.0, 61) * fade > 0.76;
    }

    /** Pose les cristaux au sol et au plafond des cavités creusées dans la colonne (indices de {@code states}). */
    static void crystals(BlockState[] states, boolean[] carved, int x, int z, Site site) {
        if (site != null && Math.abs(x - site.x) <= PYRAMID + 1 && Math.abs(z - site.z) <= PYRAMID + 1) {
            return; // Pas de cristal sur la pyramide ni dans son couloir.
        }
        if (site != null && Math.abs(x - site.x - SHAFT_OFFSET) <= 3 && Math.abs(z - site.z) <= 3) {
            return;
        }
        for (int i = 1; i < states.length - 1; i++) {
            if (!carved[i] || !states[i].isAir()) {
                continue;
            }
            int roll = (int) Math.floorMod(Apocalypse.hash(x * 31L + i, z, 79), 1000);
            if (!states[i - 1].isAir() && states[i - 1] != CRYSTAL) {
                if (roll < 18) {
                    states[i] = CRYSTAL;
                } else if (roll < 24) {
                    states[i] = ModBlocks.HELIUM3_CRYSTALS.defaultBlockState();
                }
            } else if (!states[i + 1].isAir() && roll < 14) {
                states[i] = CRYSTAL;
            }
        }
    }

    // --- Décoration : coffres, générateurs, gardiens ---------------------------------------

    static void decorate(WorldGenLevel level, ChunkPos chunk) {
        Site site = site(chunk.getMinBlockX() + 8, chunk.getMinBlockZ() + 8);
        if (site != null && contains(chunk, site.x, site.z)) {
            treasure(level, site);
        }
        long h = Apocalypse.hash(chunk.x(), chunk.z(), 73);
        if (Math.floorMod(h, 1000) < 25) {
            dungeon(level, chunk, h);
        }
    }

    private static boolean contains(ChunkPos chunk, int x, int z) {
        return Math.floorDiv(x, 16) == chunk.x() && Math.floorDiv(z, 16) == chunk.z();
    }

    /** Chambre de la pyramide : coffre au trésor, coffre secondaire et gardiens qui ne disparaissent pas. */
    private static void treasure(WorldGenLevel level, Site site) {
        long seed = Apocalypse.hash(site.x, site.z, 83);
        chest(level, site.chest(), SANCTUARY_LOOT, seed);
        chest(level, site.chest().offset(-3, 0, 0), RUINS_LOOT, seed + 1);
        int[][] guards = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
        for (int i = 0; i < guards.length; i++) {
            Mob guard = (i % 2 == 0 ? ModMobs.LOST_ASTRONAUT : ModMobs.MOON_CRAWLER)
                    .create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (guard != null) {
                guard.snapTo(site.x + guards[i][0] + 0.5, site.y + 1, site.z + guards[i][1] + 0.5, 0, 0);
                guard.setPersistenceRequired();
                level.addFreshEntityWithPassengers(guard);
            }
        }
    }

    /** Donjon enfoui : salle 9 × 9 en pierre extraterrestre, générateur de rampants, deux coffres. */
    private static void dungeon(WorldGenLevel level, ChunkPos chunk, long h) {
        int cx = chunk.getMinBlockX() + 8;
        int cz = chunk.getMinBlockZ() + 8;
        int cy = 10 + (int) Math.floorMod(h >>> 12, 30);
        if (cy + 12 > MoonChunkGenerator.height(cx, cz) || inHall(new BlockPos(cx, cy, cz))) {
            return;
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 5; dy++) {
                    boolean shell = Math.abs(dx) == 4 || Math.abs(dz) == 4 || dy == -1 || dy == 5;
                    boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 4;
                    boolean door = (dx == 0 || dz == 0) && dy >= 0 && dy <= 1 && !corner;
                    BlockState state = !shell || door && (Math.abs(dx) == 4 || Math.abs(dz) == 4) ? AIR
                            : corner || dy == 5 && dx == 0 && dz == 0 ? GLYPH : STONE;
                    level.setBlock(new BlockPos(cx + dx, cy + dy, cz + dz), state, 2);
                }
            }
        }
        BlockPos spawner = new BlockPos(cx, cy, cz);
        level.setBlock(spawner, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(spawner) instanceof SpawnerBlockEntity entity) {
            entity.setEntityId(ModMobs.MOON_CRAWLER, RandomSource.create(h));
        }
        chest(level, new BlockPos(cx + 3, cy, cz + 2), RUINS_LOOT, h);
        chest(level, new BlockPos(cx - 3, cy, cz - 2), RUINS_LOOT, h + 1);
    }

    private static void chest(WorldGenLevel level, BlockPos pos, ResourceKey<LootTable> loot, long seed) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(seed), pos, loot);
    }
}
