package com.chunkfinder.mixin;

import com.chunkfinder.ButterflyKnife;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only used by Butterfly Knife. When that module is on, the sword in your hand is drawn as a knife instead. This changes
 * the picture on your own screen only; it does not touch what the server sees or what your sword does.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void chunkfinder$knife(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                   float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        try {
            if (ButterflyKnife.render(player, hand, swingProgress, item, equipProgress, matrices, vertexConsumers)) ci.cancel();
        } catch (Throwable ignored) { }
    }
}
