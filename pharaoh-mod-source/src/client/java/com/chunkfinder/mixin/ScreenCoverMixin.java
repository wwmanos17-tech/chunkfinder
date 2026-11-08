package com.chunkfinder.mixin;

import com.chunkfinder.CoverBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the background of the multiplayer and join / leave screens for the Pharaoh desert cover (pure drawing). */
@Mixin(Screen.class)
public abstract class ScreenCoverMixin {
    @Inject(
        method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void chunkfinder$cover(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Object self = this;
        if (CoverBackground.applies((Screen) self)) {
            CoverBackground.draw(context);
            ci.cancel();
        }
    }
}
