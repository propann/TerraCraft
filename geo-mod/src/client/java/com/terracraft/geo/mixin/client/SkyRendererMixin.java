package com.terracraft.geo.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.terracraft.geo.client.SpaceSky;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ciel des mondes spatiaux : planètes et lunes du système solaire (voir {@link SpaceSky}). */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow
    @Final
    private TextureAtlas celestialsAtlas;

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void terracraft$hideMoon(RenderPass renderPass, MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        if (SpaceSky.hidesVanillaMoon()) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"),
            index = 2)
    private float terracraft$sunScale(float scale) {
        return SpaceSky.sunScale(scale);
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void terracraft$planets(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
                                    MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        SpaceSky.render(renderPass, celestialsAtlas);
    }
}
