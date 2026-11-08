package com.chunkfinder.mixin;

import com.chunkfinder.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the game camera to the freecam position while Freecam is on. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void chunkfinder$freecam(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                     boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        Vec3d pos = Freecam.getPos(tickDelta);
        setPos(pos.x, pos.y, pos.z);
        setRotation(Freecam.yaw, Freecam.pitch);
    }
}
