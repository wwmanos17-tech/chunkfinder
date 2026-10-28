package com.chunkfinder.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only used by the Sound Radar and New Chunks. It looks at block change and sound packets that the server has already
 * sent to your game and adds one to a counter (New Chunks only checks whether a changed block holds flowing water or lava). It does not change them, store them or send anything anywhere.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onBlockUpdate", at = @At("HEAD"), require = 0)
    private void chunkfinder$block(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        try { com.chunkfinder.NewChunks.fluid(packet.getPos(), packet.getState()); } catch (Throwable ignored) { }
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"), require = 0)
    private void chunkfinder$delta(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        try { packet.visitUpdates(com.chunkfinder.NewChunks::fluid); } catch (Throwable ignored) { }
    }

    @Inject(method = "onPlaySound", at = @At("HEAD"), require = 0)
    private void chunkfinder$sound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        try {
            com.chunkfinder.SoundRadar.sound(packet.getX(), packet.getY(), packet.getZ(), packet.getSound().value().id().getPath());
        } catch (Throwable ignored) { }
    }
}
