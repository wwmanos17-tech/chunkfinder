package com.chunkfinder.mixin;

import com.chunkfinder.Freecam;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** While Freecam is on, the mouse turns the camera and leaves your real player facing the same way. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Redirect(
        method = "updateMouse",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"),
        require = 0)
    private void chunkfinder$look(ClientPlayerEntity player, double dx, double dy) {
        if (Freecam.isActive()) {
            Freecam.look(dx, dy);
        } else {
            player.changeLookDirection(dx, dy);
        }
    }
}
