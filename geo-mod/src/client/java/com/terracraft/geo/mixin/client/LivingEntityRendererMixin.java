package com.terracraft.geo.mixin.client;

import com.terracraft.geo.HealthBars;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Barres de vie : posées dans la ligne affichée sous le nom (voir {@link HealthBars}). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void terracraft$healthBar(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        Component bar = HealthBars.bar(entity, state.distanceToCameraSq);
        if (bar == null) {
            return;
        }
        state.scoreText = bar;
        if (state.nameTagAttachment == null) {
            state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
        }
    }
}
