package com.terracraft.geo;

import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.world.MarsChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Blocks;

/** Sites limités à leur chunk de génération : aucune réécriture des constructions existantes. */
public final class OrionSites {
    private OrionSites() { }
    public static BlockPos nearest(int x, int z, boolean temple) {
        int cx = Math.floorDiv(x - 136 + 256,512)*512+136;
        int cz = Math.floorDiv(z - 136 + 256,512)*512+136;
        int y = 128;
        if (!temple) {
            y = Integer.MIN_VALUE;
            for (int dx=-6;dx<=6;dx++) for (int dz=-6;dz<=6;dz++)
                y=Math.max(y,MarsChunkGenerator.height(cx+dx,cz+dz)+1);
        }
        return new BlockPos(cx,y,cz);
    }
    public static void generate(WorldGenLevel level, ChunkAccess chunk, boolean temple) {
        var cp = chunk.getPos();
        if (Math.floorMod(cp.x(),32)!=8 || Math.floorMod(cp.z(),32)!=8) return;
        BlockPos center=nearest(cp.getMinBlockX()+8,cp.getMinBlockZ()+8,temple);
        for (int x=-6;x<=6;x++) for (int z=-6;z<=6;z++) {
            BlockPos base=center.offset(x,0,z);
            if (!temple) for(int y=MarsChunkGenerator.height(base.getX(),base.getZ())+1;y<center.getY();y++)
                level.setBlock(new BlockPos(base.getX(),y,base.getZ()),ModBlocks.ALIEN_STONE.defaultBlockState(),2);
            level.setBlock(base,(temple && Math.max(Math.abs(x),Math.abs(z))==5 ? Blocks.MAGMA_BLOCK : ModBlocks.ALIEN_STONE).defaultBlockState(),2);
            for(int y=1;y<=7;y++) {
                boolean wall=(Math.abs(x)==6 || Math.abs(z)==6) && (Math.abs(x)>1 && Math.abs(z)>1);
                level.setBlock(base.above(y), (wall ? ModBlocks.ALIEN_STONE : Blocks.AIR).defaultBlockState(),2);
            }
        }
        level.setBlock(center,Blocks.REINFORCED_DEEPSLATE.defaultBlockState(),2);
        for(int dx:new int[]{-4,4}) for(int dz:new int[]{-4,4}) {
            for(int y=1;y<=3;y++) level.setBlock(center.offset(dx,y,dz),ModBlocks.ALIEN_STONE.defaultBlockState(),2);
            level.setBlock(center.offset(dx,4,dz),ModBlocks.ALIEN_GLYPH.defaultBlockState(),2);
        }
    }
    static boolean isInside(ServerLevel level,BlockPos player,boolean temple) {
        BlockPos center=nearest(player.getX(),player.getZ(),temple);
        if (Math.abs(player.getX()-center.getX())>6 || Math.abs(player.getZ()-center.getZ())>6
                || player.getY()<center.getY()+1 || player.getY()>center.getY()+7
                || !level.getBlockState(center).is(Blocks.REINFORCED_DEEPSLATE)) return false;
        for(int dx:new int[]{-4,4}) for(int dz:new int[]{-4,4})
            if (!level.getBlockState(center.offset(dx,4,dz)).is(ModBlocks.ALIEN_GLYPH)) return false;
        return true;
    }
}
