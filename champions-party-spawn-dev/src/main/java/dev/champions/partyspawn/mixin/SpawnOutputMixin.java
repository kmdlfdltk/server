package dev.champions.partyspawn.mixin;

import dev.champions.partyspawn.SpawnCapture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cobblemon's /checkspawn calls ServerPlayer.sendSystemMessage directly,
 * bypassing the command source. Suppress only those messages while our
 * one-player, one-thread scoped capture is active.
 */
@Mixin(ServerPlayer.class)
public abstract class SpawnOutputMixin {
    @Inject(
        method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void champions$captureCheckspawn(Component message, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if (SpawnCapture.intercept(self.getUUID(), message)) ci.cancel();
    }
}
