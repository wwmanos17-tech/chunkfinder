package com.chunkfinder.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the Market Helper know where a container window sits on the screen, so it can mark the right slots. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int chunkfinder$getX();

    @Accessor("y")
    int chunkfinder$getY();

    @Accessor("backgroundWidth")
    int chunkfinder$getBackgroundWidth();
}
