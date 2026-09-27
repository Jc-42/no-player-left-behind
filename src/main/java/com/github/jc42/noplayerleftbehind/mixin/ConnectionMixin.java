package com.github.jc42.noplayerleftbehind.mixin;

import com.github.jc42.noplayerleftbehind.PacketGate;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Input gate. Every incoming packet passes through genericsFtw on the network thread. */
@Mixin(Connection.class)
public class ConnectionMixin {
	@Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
	private static void noplayerleftbehind$gatePacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
		if (listener instanceof ServerGamePacketListenerImpl gameListener && PacketGate.shouldDrop(packet, gameListener)) {
			ci.cancel();
		}
	}
}
