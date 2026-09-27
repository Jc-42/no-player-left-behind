package com.github.jc42.noplayerleftbehind.mixin;

import com.github.jc42.noplayerleftbehind.LockManager;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World gate. The server checks runsNormally() before ticking weather, time, block and fluid ticks,
 * raids, random ticks, spawning, block entities, functions and clocks, and isEntityFrozen() before
 * ticking each entity. While frozen we report the game as stopped and every entity (players
 * included, unlike vanilla /tick freeze) as frozen. Chunk loading and sending keep running.
 */
@Mixin(TickRateManager.class)
public class TickRateManagerMixin {
	@Inject(method = "runsNormally", at = @At("HEAD"), cancellable = true)
	private void noplayerleftbehind$freezeGame(CallbackInfoReturnable<Boolean> cir) {
		if (LockManager.isFrozen()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
	private void noplayerleftbehind$freezeAllEntities(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (LockManager.isFrozen()) {
			cir.setReturnValue(true);
		}
	}
}
