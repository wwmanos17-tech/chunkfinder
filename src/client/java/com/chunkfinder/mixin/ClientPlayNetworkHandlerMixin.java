package com.chunkfinder.mixin;

import com.chunkfinder.ReachTest;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only used by the Reach Test panel and the Sound Radar. It looks at block change, sound and particle packets that the server has already
 * sent to your game and adds one to a counter. It does not change them, store them or send anything anywhere.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onBlockUpdate", at = @At("HEAD"), require = 0)
    private void chunkfinder$block(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        ReachTest.block(packet.getPos());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"), require = 0)
    private void chunkfinder$delta(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        ReachTest.delta(packet);
    }

    @Inject(method = "onPlaySound", at = @At("HEAD"), require = 0)
    private void chunkfinder$sound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        ReachTest.sound(packet.getX(), packet.getY(), packet.getZ());
        try {
            com.chunkfinder.SoundRadar.sound(packet.getX(), packet.getY(), packet.getZ(), packet.getSound().value().id().getPath());
        } catch (Throwable ignored) { }
    }

    @Inject(method = "onParticle", at = @At("HEAD"), require = 0)
    private void chunkfinder$particle(ParticleS2CPacket packet, CallbackInfo ci) {
        ReachTest.particle(packet.getX(), packet.getY(), packet.getZ());
    }
}
