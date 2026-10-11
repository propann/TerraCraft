package com.terracraft.geo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.Space;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.List;

/**
 * Ciel du système solaire, monde par monde : la planète survolée emplit le bas du ciel depuis une orbite, la Terre
 * reste immobile au-dessus de la Lune (rotation synchrone), Phobos et Deimos défilent au-dessus de Mars, et le Soleil
 * rapetisse en s'éloignant. Les astres sont des sprites de l'atlas « celestials » (textures/environment/celestial),
 * dessinés après le Soleil et les étoiles avec un mélange translucide : ils sont opaques et cachent les étoiles.
 */
public final class SpaceSky {
    /** Comme le pipeline des astres vanilla, mais en mélange translucide (le vanilla est additif). */
    private static final RenderPipeline PLANET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "pipeline/planet"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false) // Planète survolée vue d'au-dessus, astres vus d'en dessous.
            .build();
    private static final List<String> SPRITES = List.of("earth", "moon_surface", "mars", "jupiter", "phobos", "deimos");
    private static final RenderSystem.AutoStorageIndexBuffer QUAD_INDICES = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

    private static GpuBuffer quads;
    private static TextureAtlas builtFor;
    private static float builtU0 = Float.NaN;

    private SpaceSky() {
    }

    /** Vue depuis ce monde : la Lune vanilla n'y a pas sa place (on est sur elle, ou bien plus loin). */
    public static boolean hidesVanillaMoon() {
        byte id = current();
        return id == Space.MOON_ID || id == Space.MOON_ORBIT_ID || id == Space.MARS_ID || id == Space.MARS_ORBIT_ID
                || id == Space.ASTEROIDS_ID;
    }

    /** Taille apparente du Soleil (30 sur Terre) : ~1/1,5 sur Mars, plus petit encore dans la ceinture. */
    public static float sunScale(float vanilla) {
        byte id = current();
        if (id == Space.MARS_ID || id == Space.MARS_ORBIT_ID) {
            return vanilla * 0.66f;
        }
        return id == Space.ASTEROIDS_ID ? vanilla * 0.4f : vanilla;
    }

    private static byte current() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? Space.EARTH : Space.id(minecraft.level);
    }

    /** Dessine les astres du monde courant (appelé à la fin du rendu du Soleil, de la Lune et des étoiles). */
    public static void render(RenderPass pass, TextureAtlas atlas) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        byte id = Space.id(minecraft.level);
        if (id == Space.EARTH) {
            return;
        }
        ensureQuads(atlas);
        float t = (minecraft.level.getGameTime() % 2_400_000L + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20f;
        switch (id) {
            case Space.ORBIT_ID -> below(pass, atlas, "earth", 170, t * 0.15f);
            case Space.MOON_ORBIT_ID -> {
                below(pass, atlas, "moon_surface", 150, t * 0.05f);
                fixed(pass, atlas, "earth", 40, 25, 9);
            }
            case Space.MOON_ID -> fixed(pass, atlas, "earth", 30, 55, 14); // Synchrone : la Terre ne bouge pas.
            case Space.MARS_ORBIT_ID -> {
                below(pass, atlas, "mars", 160, t * 0.12f);
                orbiting(pass, atlas, "phobos", t * 2.4f, 15, 3);
                orbiting(pass, atlas, "deimos", t * 0.6f + 2, 30, 2);
            }
            case Space.MARS_ID -> {
                orbiting(pass, atlas, "phobos", t * 1.5f, 20, 4);   // Phobos traverse le ciel en quelques minutes.
                orbiting(pass, atlas, "deimos", t * 0.25f + 2, 40, 2);
                fixed(pass, atlas, "earth", 120, 35, 1.5f);         // Point bleu.
            }
            case Space.ASTEROIDS_ID -> {
                fixed(pass, atlas, "mars", 200, 15, 3);
                fixed(pass, atlas, "jupiter", 330, 30, 6);
            }
            default -> {
            }
        }
    }

    /** Planète survolée : immense disque sous l'observateur, qui tourne lentement sur lui-même. */
    private static void below(RenderPass pass, TextureAtlas atlas, String sprite, float scale, float spin) {
        PoseStack pose = new PoseStack();
        pose.rotate(Axis.XP.rotationDegrees(180));
        pose.rotate(Axis.YP.rotationDegrees(spin));
        draw(pass, atlas, pose, sprite, scale);
    }

    /** Astre immobile, à {@code azimuth}° du nord et {@code elevation}° au-dessus de l'horizon. */
    private static void fixed(RenderPass pass, TextureAtlas atlas, String sprite, float azimuth, float elevation, float scale) {
        PoseStack pose = new PoseStack();
        pose.rotate(Axis.YP.rotationDegrees(-azimuth));
        pose.rotate(Axis.XP.rotationDegrees(90 - elevation));
        draw(pass, atlas, pose, sprite, scale);
    }

    /** Lune qui traverse le ciel d'est en ouest, sur une orbite inclinée de {@code tilt}°. */
    private static void orbiting(RenderPass pass, TextureAtlas atlas, String sprite, float angle, float tilt, float scale) {
        PoseStack pose = new PoseStack();
        pose.rotate(Axis.YP.rotationDegrees(-90));
        pose.rotate(Axis.ZP.rotationDegrees(tilt));
        pose.rotate(Axis.XP.rotationDegrees(angle % 360));
        draw(pass, atlas, pose, sprite, scale);
    }

    private static void draw(RenderPass pass, TextureAtlas atlas, PoseStack pose, String sprite, float scale) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(pose.last().pose());
        modelView.translate(0, 100, 0);
        modelView.scale(scale, 1, scale);
        Matrix4f matrix = new Matrix4f(modelView);
        modelView.popMatrix();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(matrix, new Vector4f(1, 1, 1, 1));
        pass.pushDebugGroup(() -> "TerraCraft " + sprite);
        pass.setPipeline(RenderSystem.getCompiledPipeline(PLANET));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("Sampler0", atlas.getTextureView(), atlas.getSampler());
        pass.setVertexBuffer(0, quads.slice());
        pass.setIndexBuffer(QUAD_INDICES.getBuffer(6), QUAD_INDICES.type());
        pass.drawIndexed(6, 1, 0, SPRITES.indexOf(sprite) * 4, 0);
        pass.popDebugGroup();
    }

    /** Un quad par sprite, refait si l'atlas est rechargé (pack de ressources, F3+T). */
    private static void ensureQuads(TextureAtlas atlas) {
        TextureAtlasSprite first = atlas.getSprite(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, SPRITES.getFirst()));
        if (quads != null && builtFor == atlas && builtU0 == first.getU0()) {
            return;
        }
        if (quads != null) {
            quads.close();
        }
        var format = DefaultVertexFormat.POSITION_TEX;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(SPRITES.size() * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
            for (String name : SPRITES) {
                TextureAtlasSprite s = atlas.getSprite(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name));
                builder.addVertex(-1, 0, -1).setUv(s.getU0(), s.getV0());
                builder.addVertex(1, 0, -1).setUv(s.getU1(), s.getV0());
                builder.addVertex(1, 0, 1).setUv(s.getU1(), s.getV1());
                builder.addVertex(-1, 0, 1).setUv(s.getU0(), s.getV1());
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                quads = RenderSystem.getDevice().createBuffer(() -> "TerraCraft planets", 32, mesh.vertexBuffer());
            }
        }
        builtFor = atlas;
        builtU0 = first.getU0();
    }
}
