package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ennemis lunaires. Rôdeur lunaire : araignée grise, rapide, qui chasse la nuit dans les
 * cratères. Astronaute perdu : zombie en combinaison, plus résistant, qui ne brûle pas au soleil.
 */
public final class ModMobs {
    private ModMobs() {
    }

    public static class MoonCrawler extends Spider {
        public MoonCrawler(EntityType<? extends Spider> type, Level level) {
            super(type, level);
        }

        /** Nuit lunaire : à découvert, le rôdeur devient plus rapide et plus fort. */
        @Override
        public void aiStep() {
            super.aiStep();
            if (!level().isClientSide() && tickCount % 40 == 0 && level().isDarkOutside() && level().canSeeSky(blockPosition())) {
                addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 60, 0, false, false));
                addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 60, 0, false, false));
            }
        }
    }

    public static class LostAstronaut extends Zombie {
        public LostAstronaut(EntityType<? extends Zombie> type, Level level) {
            super(type, level);
        }

        @Override
        protected boolean isSunSensitive() {
            return false; // Sa visière le protège.
        }
    }

    public static final EntityType<MoonCrawler> MOON_CRAWLER = register("moon_crawler",
            EntityType.Builder.<MoonCrawler>of(MoonCrawler::new, MobCategory.MONSTER).sized(1.4f, 0.9f).clientTrackingRange(8));
    public static final EntityType<LostAstronaut> LOST_ASTRONAUT = register("lost_astronaut",
            EntityType.Builder.<LostAstronaut>of(LostAstronaut::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(8));

    public static void init() {
        FabricDefaultAttributeRegistry.register(MOON_CRAWLER, Spider.createAttributes()
                .add(Attributes.MAX_HEALTH, 22).add(Attributes.MOVEMENT_SPEED, 0.36).add(Attributes.ATTACK_DAMAGE, 4));
        FabricDefaultAttributeRegistry.register(LOST_ASTRONAUT, Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 30).add(Attributes.ARMOR, 6).add(Attributes.ATTACK_DAMAGE, 5));
        SpawnPlacements.register(MOON_CRAWLER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        SpawnPlacements.register(LOST_ASTRONAUT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
