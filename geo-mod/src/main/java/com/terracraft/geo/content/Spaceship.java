package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.Stations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Petit vaisseau spatial : une fusée lourde livrée montée (quatre réservoirs, quatre charges utiles), au modèle de
 * vaisseau. Posé à une station ou à une base, il déploie un tunnel-sas pressurisé entre le sas de la station et son
 * flanc ouest ; le tunnel se replie au décollage ou si le vaisseau est détruit. Les blocs du tunnel ne donnent aucun
 * butin et seuls ceux posés par le vaisseau sont retirés.
 */
public class Spaceship extends Rocket {
    /** Le sas de la station est à 5 blocs à l'ouest de la balise : le tunnel commence juste après. */
    private static final int TUNNEL_FROM = -4;
    /** Demi-largeur de la coque (voir ModContent.spaceshipType) : la porte est sur le flanc ouest. */
    private static final double HALF_WIDTH = 1.25;
    /** Écart toléré entre le vaisseau et l'axe du quai ; au-delà, le tunnel ne rejoindrait pas la porte. */
    private static final double MAX_OFFSET = 1.5;

    private final List<BlockPos> tunnel = new ArrayList<>();

    public Spaceship(EntityType<? extends Spaceship> type, Level level) {
        super(type, level);
        assembleComplete(MAX_TANKS);
    }

    @Override
    public String tierName() {
        return "Vaisseau";
    }

    @Override
    protected void onLanded(ServerLevel level) {
        BlockPos beacon = Stations.get().beaconNear(level, blockPosition(), 8);
        if (beacon == null || !tunnel.isEmpty()) {
            return;
        }
        double offsetX = getX() - (beacon.getX() + 0.5);
        double offsetZ = getZ() - (beacon.getZ() + 0.5);
        // Le tunnel s'arrête au flanc ouest du vaisseau, où qu'il se soit posé sur l'aire.
        int to = (int) Math.floor(offsetX - HALF_WIDTH);
        if (Math.abs(offsetZ) > MAX_OFFSET || to < TUNNEL_FROM || to > 0) {
            return;
        }
        setYRot(0); // Flancs (et portes de sas) face à l'est et à l'ouest : la porte ouest donne sur le tunnel.
        for (int dx = TUNNEL_FROM; dx <= to; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    if (Math.abs(dz) != 2 && dy != 4) {
                        continue; // Passage 3 × 3 ; le plancher est celui du quai.
                    }
                    Block block = dy == 4 ? (dz == 0 && Math.floorMod(dx, 2) == 0 ? ShipBlocks.TUNNEL_LIGHT : ShipBlocks.TUNNEL_HULL)
                            : dy == 2 ? ShipBlocks.TUNNEL_WINDOW : ShipBlocks.TUNNEL_HULL;
                    BlockPos pos = beacon.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
                        tunnel.add(pos);
                    }
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1f, 0.6f);
        GeoMod.LOGGER.info("[VAISSEAU] amarré en {} : tunnel-sas de {} blocs vers la balise {}", blockPosition(), tunnel.size(), beacon);
        if (getFirstPassenger() instanceof ServerPlayer pilot) {
            pilot.sendSystemMessage(Component.literal("✦ Amarrage : le tunnel-sas relie le vaisseau à la station.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    protected void onLaunch(ServerLevel level) {
        retractTunnel(level);
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        retractTunnel(level);
        super.destroy(level, source);
    }

    private void retractTunnel(ServerLevel level) {
        if (tunnel.isEmpty()) {
            return;
        }
        int removed = 0;
        for (BlockPos pos : tunnel) {
            var state = level.getBlockState(pos);
            if (state.is(ShipBlocks.TUNNEL_HULL) || state.is(ShipBlocks.TUNNEL_WINDOW) || state.is(ShipBlocks.TUNNEL_LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed++;
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1f, 0.6f);
        GeoMod.LOGGER.info("[VAISSEAU] désamarrage en {} : tunnel-sas replié ({} blocs)", blockPosition(), removed);
        tunnel.clear();
    }

    @Override
    protected boolean dropsParts() {
        return false;
    }

    @Override
    protected Item getDropItem() {
        return ModContent.SPACESHIP_KIT;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModContent.SPACESHIP_KIT);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, 0.7, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        var list = output.list("Tunnel", BlockPos.CODEC);
        tunnel.forEach(list::add);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        tunnel.clear();
        input.listOrEmpty("Tunnel", BlockPos.CODEC).forEach(tunnel::add);
    }
}
