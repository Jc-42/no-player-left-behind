package com.github.jc42.noplayerleftbehind.mixin;

import com.github.jc42.noplayerleftbehind.LockManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Player gate. tickPlayer() runs the player's own tick (hunger, effects, damage, stats).
 * Returning false makes tick() fall through to its keep-alive branch, so the connection stays up.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
	@Inject(method = "tickPlayer", at = @At("HEAD"), cancellable = true)
	private void noplayerleftbehind$skipPlayerTick(CallbackInfoReturnable<Boolean> cir) {
		if (LockManager.isFrozen()) {
			cir.setReturnValue(false);
		}
	}
}
