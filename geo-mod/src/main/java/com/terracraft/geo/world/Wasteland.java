package com.terracraft.geo.world;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.Vehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;


/**
 * Le monde dangereux : butin et monstres dans les ruines, caves à monstres, bunkers enterrés
 * et épaves de véhicules incomplètes. Tout est déterministe (grilles + hachage).
 *
 * <p>Deux étapes : {@link #carve} pendant la génération du terrain (creuse les caves et coule
 * le béton des bunkers dans le chunk), puis {@link #populate} pendant la décoration (coffres,
 * générateurs de monstres, mobs et véhicules, qui ont besoin du monde).
 */
public final class Wasteland {
    private Wasteland() {
    }

    static final ResourceKey<LootTable> RUIN_LOOT = loot("chests/ruin");
    static final ResourceKey<LootTable> BUNKER_LOOT = loot("chests/bunker");

    private static final int CAVE_GRID = 192;
    private static final int BUNKER_GRID = 320;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState CONCRETE = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
    private static final BlockState CRACKED = Blocks.POLISHED_ANDESITE.defaultBlockState();

    interface Setter {
        void set(int localX, int y, int localZ, BlockState state);
    }

    interface Surface {
        int at(int blockX, int blockZ);
    }

    // --- Caves à monstres --------------------------------------------------------------------

    /** Chapelet de salles sphériques reliées, avec parfois un puits d'accès vers la surface. */
    record Cave(int[] x, int[] y, int[] z, int[] r, boolean entrance) {
    }

    static @Nullable Cave cave(EarthTerrain terrain, int ax, int az) {
        long h = Apocalypse.hash(ax, az, 101);
        if (Math.floorMod(h, 100) >= 35) {
            return null;
        }
        int cx = ax * CAVE_GRID + 32 + (int) Math.floorMod(h >>> 8, CAVE_GRID - 64);
        int cz = az * CAVE_GRID + 32 + (int) Math.floorMod(h >>> 20, CAVE_GRID - 64);
        int surface = terrain.surfaceY(cx, cz);
        if (surface < EarthTerrain.SEA_LEVEL + 2) {
            return null;
        }
        int cy = Math.max(EarthTerrain.MIN_Y + 10, surface - 28 - (int) Math.floorMod(h >>> 32, 16));
        int n = 7;
        int[] x = new int[n];
        int[] y = new int[n];
        int[] z = new int[n];
        int[] r = new int[n];
        double angle = Math.floorMod(h >>> 40, 360) * Math.PI / 180;
        for (int i = 0; i < n; i++) {
            long s = Apocalypse.hash(ax * 31L + i, az, 103);
            angle += (Math.floorMod(s, 90) - 45) * Math.PI / 180;
            x[i] = i == 0 ? cx : x[i - 1] + (int) Math.round(Math.cos(angle) * 7);
            z[i] = i == 0 ? cz : z[i - 1] + (int) Math.round(Math.sin(angle) * 7);
            y[i] = i == 0 ? cy : y[i - 1] + (int) Math.floorMod(s >>> 8, 5) - 2;
            r[i] = 4 + (int) Math.floorMod(s >>> 16, 4);
        }
        return new Cave(x, y, z, r, Math.floorMod(h >>> 48, 100) < 60);
    }

    static void carve(ChunkAccess chunk, EarthTerrain terrain, OsmCells.@Nullable Cell osm, Surface surface, Setter setter) {
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        int gx = Math.floorDiv(minX, CAVE_GRID);
        int gz = Math.floorDiv(minZ, CAVE_GRID);
        for (int ax = gx - 1; ax <= gx + 1; ax++) {
            for (int az = gz - 1; az <= gz + 1; az++) {
                Cave cave = cave(terrain, ax, az);
                if (cave != null) {
                    carveCave(chunk, cave, terrain, osm, surface, setter, minX, minZ);
                }
            }
        }
        Bunker bunker = bunker(terrain, osm, pos);
        if (bunker != null) {
            buildBunker(bunker, chunk, surface, setter, minX, minZ);
        }
    }

    private static void carveCave(ChunkAccess chunk, Cave cave, EarthTerrain terrain, OsmCells.@Nullable Cell osm,
                                  Surface surface, Setter setter, int minX, int minZ) {
        int floor = chunk.getMinY() + 1;
        for (int i = 0; i < cave.x.length; i++) {
            int r = cave.r[i];
            if (cave.x[i] + r < minX || cave.x[i] - r > minX + 15 || cave.z[i] + r < minZ || cave.z[i] - r > minZ + 15) {
                continue;
            }
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int dx = minX + lx - cave.x[i];
                    int dz = minZ + lz - cave.z[i];
                    int roof = surface.at(minX + lx, minZ + lz) - 6;
                    for (int dy = -r; dy <= r; dy++) {
                        int y = cave.y[i] + dy;
                        // Sphère un peu aplatie (salles plus larges que hautes).
                        if (dx * dx + dz * dz + dy * dy * 2 <= r * r && y > floor && y < roof) {
                            setter.set(lx, y, lz, AIR);
                        }
                    }
                }
            }
        }
        // Puits d'accès 2×2 depuis la dernière salle jusqu'à la surface (hors rue, eau, bâtiment).
        int last = cave.x.length - 1;
        int ex = cave.x[last];
        int ez = cave.z[last];
        if (cave.entrance && ex + 1 >= minX && ex <= minX + 15 && ez + 1 >= minZ && ez <= minZ + 15
                && isOpenGround(terrain, ex, ez)) {
            for (int sx = ex; sx <= ex + 1; sx++) {
                for (int sz = ez; sz <= ez + 1; sz++) {
                    if (sx < minX || sx > minX + 15 || sz < minZ || sz > minZ + 15) {
                        continue;
                    }
                    int top = surface.at(sx, sz);
                    for (int y = cave.y[last]; y <= top; y++) {
                        setter.set(sx - minX, y, sz - minZ, AIR);
                    }
                }
            }
        }
    }

    private static boolean isOpenGround(EarthTerrain terrain, int x, int z) {
        OsmCells.Cell cell = terrain.osm().cellAt(x, z);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                byte s = cell.surface(x + dx, z + dz);
                if (s != OsmCells.NONE || cell.building(x + dx, z + dz) != null || terrain.elevation(x + dx, z + dz) <= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- Bunkers ------------------------------------------------------------------------------

    /** Bunker de 12×12 enterré dans un seul chunk ; échelle et trappe en (3, 3) local. */
    record Bunker(int chunkX, int chunkZ, int floorY, int surfaceY) {
    }

    static @Nullable Bunker bunker(EarthTerrain terrain, OsmCells.@Nullable Cell osm, ChunkPos pos) {
        int gx = Math.floorDiv(pos.getMinBlockX(), BUNKER_GRID);
        int gz = Math.floorDiv(pos.getMinBlockZ(), BUNKER_GRID);
        long h = Apocalypse.hash(gx, gz, 131);
        if (Math.floorMod(h, 100) >= 30) {
            return null;
        }
        int bx = gx * BUNKER_GRID + 48 + (int) Math.floorMod(h >>> 8, BUNKER_GRID - 96);
        int bz = gz * BUNKER_GRID + 48 + (int) Math.floorMod(h >>> 24, BUNKER_GRID - 96);
        if (Math.floorDiv(bx, 16) != pos.x() || Math.floorDiv(bz, 16) != pos.z() || osm == null) {
            return null;
        }
        int hatchX = pos.getMinBlockX() + 3;
        int hatchZ = pos.getMinBlockZ() + 3;
        int surface = terrain.surfaceY(hatchX, hatchZ);
        if (surface < EarthTerrain.SEA_LEVEL + 2 || osm.surface(hatchX, hatchZ) != OsmCells.NONE
                || osm.building(hatchX, hatchZ) != null) {
            return null;
        }
        int lowest = surface;
        for (int lx = 2; lx <= 13; lx += 11) {
            for (int lz = 2; lz <= 13; lz += 11) {
                lowest = Math.min(lowest, terrain.surfaceY(pos.getMinBlockX() + lx, pos.getMinBlockZ() + lz));
            }
        }
        return new Bunker(pos.x(), pos.z(), lowest - 14, surface);
    }

    private static void buildBunker(Bunker b, ChunkAccess chunk, Surface surface, Setter setter, int minX, int minZ) {
        int f = b.floorY;
        for (int lx = 2; lx <= 13; lx++) {
            for (int lz = 2; lz <= 13; lz++) {
                boolean wall = lx == 2 || lx == 13 || lz == 2 || lz == 13;
                for (int y = f; y <= f + 6; y++) {
                    boolean shell = wall || y == f || y == f + 6;
                    BlockState state = shell
                            ? (Apocalypse.roll(minX + lx, minZ + lz * 7L + y, 137) < 15 ? CRACKED : CONCRETE)
                            : AIR;
                    setter.set(lx, y, lz, state);
                }
            }
        }
        // Puits : échelle accrochée au mur nord, de l'intérieur jusqu'à la surface, trappe en haut.
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = f + 1; y < b.surfaceY; y++) {
            setter.set(3, y, 3, ladder);
            setter.set(3, y, 2, CONCRETE);
        }
        setter.set(3, b.surfaceY, 3, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.OPEN, false));
        // Éclairage de secours et cloisons.
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        setter.set(7, f + 5, 7, lantern);
        setter.set(10, f + 5, 10, lantern);
        for (int lz = 3; lz <= 7; lz++) {
            setter.set(9, f + 1, lz, Blocks.IRON_BARS.defaultBlockState());
            setter.set(9, f + 2, lz, Blocks.IRON_BARS.defaultBlockState());
        }
        setter.set(5, f + 1, 12, Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    // --- Peuplement (étape décoration) --------------------------------------------------------

    static void populate(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator, EarthTerrain terrain,
                         OsmCells.Cell osm) {
        ChunkPos pos = chunk.getPos();
        RandomSource random = RandomSource.create(Apocalypse.hash(pos.x(), pos.z(), 151));
        populateBuildings(level, pos, osm, random);
        populateCaves(level, pos, terrain, random);
        Bunker bunker = bunker(terrain, osm, pos);
        if (bunker != null) {
            int x = pos.getMinBlockX();
            int z = pos.getMinBlockZ();
            int f = bunker.floorY;
            chest(level, new BlockPos(x + 12, f + 1, z + 11), BUNKER_LOOT, random, Direction.WEST);
            chest(level, new BlockPos(x + 11, f + 1, z + 12), BUNKER_LOOT, random, Direction.NORTH);
            chest(level, new BlockPos(x + 4, f + 1, z + 12), RUIN_LOOT, random, Direction.NORTH);
            if (random.nextBoolean()) {
                spawner(level, new BlockPos(x + 11, f + 1, z + 5), EntityTypes.ZOMBIE, random);
            }
        }
        wreck(level, pos, terrain, osm, random);
    }

    /** Par colonne intérieure : coffre (0,4 %), générateur (0,15 %), zombie posté (0,8 %). */
    private static void populateBuildings(WorldGenLevel level, ChunkPos pos, OsmCells.Cell osm, RandomSource random) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = pos.getMinBlockX() + lx;
                int z = pos.getMinBlockZ() + lz;
                OsmCells.Building b = osm.building(x, z);
                if (b == null || osm.isWall(x, z) || osm.inset(x, z) < 2 || Apocalypse.ruin(b) == Apocalypse.Ruin.RUBBLE) {
                    continue;
                }
                int roll = (int) Math.floorMod(Apocalypse.hash(x, z, 211), 1000);
                if (roll >= 15) {
                    continue;
                }
                int top = Apocalypse.effectiveTop(b, x, z);
                int floors = Math.max(1, (top - b.baseY() - 1) / b.floorStep());
                int floor = roll < 6 ? 0 : (int) Math.floorMod(Apocalypse.hash(x, z, 223), floors);
                BlockPos at = new BlockPos(x, b.baseY() + floor * b.floorStep() + 1, z);
                if (!level.getBlockState(at).isAir() || level.getBlockState(at.below()).isAir()) {
                    continue;
                }
                if (roll < 4) {
                    chest(level, at, RUIN_LOOT, random, Direction.from2DDataValue(random.nextInt(4)));
                } else if (roll < 6) {
                    spawner(level, at, pickMonster(random), random);
                } else {
                    mob(level, at, EntityTypes.ZOMBIE);
                }
            }
        }
    }

    private static void populateCaves(WorldGenLevel level, ChunkPos pos, EarthTerrain terrain, RandomSource random) {
        int gx = Math.floorDiv(pos.getMinBlockX(), CAVE_GRID);
        int gz = Math.floorDiv(pos.getMinBlockZ(), CAVE_GRID);
        for (int ax = gx - 1; ax <= gx + 1; ax++) {
            for (int az = gz - 1; az <= gz + 1; az++) {
                Cave cave = cave(terrain, ax, az);
                if (cave == null) {
                    continue;
                }
                for (int i = 0; i < cave.x.length; i += 3) {
                    if (Math.floorDiv(cave.x[i], 16) != pos.x() || Math.floorDiv(cave.z[i], 16) != pos.z()) {
                        continue;
                    }
                    BlockPos floor = new BlockPos(cave.x[i], cave.y[i] - cave.r[i] / 2, cave.z[i]);
                    while (floor.getY() > cave.y[i] - cave.r[i] - 1 && level.getBlockState(floor.below()).isAir()) {
                        floor = floor.below();
                    }
                    if (!level.getBlockState(floor).isAir()) {
                        continue;
                    }
                    spawner(level, floor, pickMonster(random), random);
                    if (i == 0) {
                        chest(level, floor.east(), RUIN_LOOT, random, Direction.WEST);
                    }
                    for (int k = 0; k < 6; k++) {
                        BlockPos web = floor.offset(random.nextInt(7) - 3, random.nextInt(4) + 1, random.nextInt(7) - 3);
                        if (level.getBlockState(web).isAir() && level.getChunk(web).getPos().equals(pos)) {
                            level.setBlock(web, Apocalypse.COBWEB, 2);
                        }
                    }
                }
            }
        }
    }

    /** Épave incomplète sur la chaussée : environ 1 chunk sur 80 qui a une route. */
    private static void wreck(WorldGenLevel level, ChunkPos pos, EarthTerrain terrain, OsmCells.Cell osm, RandomSource random) {
        if (Math.floorMod(Apocalypse.hash(pos.x(), pos.z(), 307), 1000) >= 12) {
            return;
        }
        for (int lx = 4; lx < 12; lx++) {
            for (int lz = 4; lz < 12; lz++) {
                int x = pos.getMinBlockX() + lx;
                int z = pos.getMinBlockZ() + lz;
                byte s = osm.surface(x, z);
                if ((s != OsmCells.ROAD_MAJOR && s != OsmCells.ROAD_MINOR) || osm.deck(x, z) != OsmCells.NO_LEVEL) {
                    continue;
                }
                boolean truck = random.nextInt(4) == 0;
                Vehicle vehicle = (truck ? ModContent.TRUCK : ModContent.CAR).create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
                if (vehicle == null) {
                    return;
                }
                int parts = 0;
                for (int flag : new int[]{Vehicle.ENGINE, Vehicle.RADIATOR, Vehicle.BATTERY, Vehicle.TURBO}) {
                    if (random.nextInt(100) < 40) {
                        parts |= flag;
                    }
                }
                // Jamais complète : au moins une roue manque toujours.
                vehicle.setParts(parts, random.nextInt(4), 0);
                vehicle.snapTo(x + 0.5, terrain.surfaceY(x, z) + 1, z + 0.5, random.nextFloat() * 360, 0);
                level.addFreshEntity(vehicle);
                return;
            }
        }
    }

    /**
     * Lieu découvert à cette position : "bunker:x,z", "cave:x,z", ou null. Sert à la
     * progression (une découverte par bunker ou par cave).
     */
    public static @Nullable String locate(EarthTerrain terrain, int x, int y, int z) {
        ChunkPos chunk = ChunkPos.containing(new BlockPos(x, y, z));
        Bunker bunker = bunker(terrain, terrain.osm().cellAt(x, z), chunk);
        if (bunker != null && y > bunker.floorY && y < bunker.floorY + 6) {
            return "bunker:" + chunk.x() + "," + chunk.z();
        }
        int gx = Math.floorDiv(x, CAVE_GRID);
        int gz = Math.floorDiv(z, CAVE_GRID);
        for (int ax = gx - 1; ax <= gx + 1; ax++) {
            for (int az = gz - 1; az <= gz + 1; az++) {
                Cave cave = cave(terrain, ax, az);
                if (cave == null) {
                    continue;
                }
                for (int i = 0; i < cave.x.length; i++) {
                    int dx = x - cave.x[i];
                    int dy = y - cave.y[i];
                    int dz = z - cave.z[i];
                    if (dx * dx + dy * dy + dz * dz <= cave.r[i] * cave.r[i]) {
                        return "cave:" + ax + "," + az;
                    }
                }
            }
        }
        return null;
    }

    // --- Utilitaires --------------------------------------------------------------------------

    private static EntityType<? extends Mob> pickMonster(RandomSource random) {
        int r = random.nextInt(100);
        return r < 65 ? EntityTypes.ZOMBIE : r < 80 ? EntityTypes.SKELETON : r < 92 ? EntityTypes.SPIDER : EntityTypes.ZOMBIE_VILLAGER;
    }

    private static void chest(WorldGenLevel level, BlockPos pos, ResourceKey<LootTable> loot, RandomSource random, Direction facing) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
    }

    private static void spawner(WorldGenLevel level, BlockPos pos, EntityType<?> type, RandomSource random) {
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(type, random);
        }
    }

    private static void mob(WorldGenLevel level, BlockPos pos, EntityType<? extends Mob> type) {
        Mob mob = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mob == null) {
            return;
        }
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        mob.setPersistenceRequired();
        level.addFreshEntityWithPassengers(mob);
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, path));
    }
}
