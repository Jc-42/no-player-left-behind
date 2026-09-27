package com.github.jc42.noplayerleftbehind;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which incoming game packets are processed while the server is frozen.
 * Anything from the game protocol not on the allow list is dropped, and whatever the
 * client predicted locally (moved, broke a block, moved an item) is sent back to the real state.
 */
public class PacketGate {
	private static final String GAME_PACKAGE = "net.minecraft.network.protocol.game";

	private static final Set<Class<?>> ALLOWED = Set.of(
		ServerboundAcceptTeleportationPacket.class,
		ServerboundBlockEntityTagQueryPacket.class,
		ServerboundChatAckPacket.class,
		ServerboundChatCommandPacket.class,
		ServerboundChatCommandSignedPacket.class,
		ServerboundChatPacket.class,
		ServerboundChatSessionUpdatePacket.class,
		ServerboundChunkBatchReceivedPacket.class,
		ServerboundClientCommandPacket.class,
		ServerboundClientTickEndPacket.class,
		ServerboundCommandSuggestionPacket.class,
		ServerboundConfigurationAcknowledgedPacket.class,
		ServerboundContainerClosePacket.class,
		ServerboundDebugSubscriptionRequestPacket.class,
		ServerboundEntityTagQueryPacket.class,
		ServerboundPlayerInputPacket.class,
		ServerboundPlayerLoadedPacket.class,
		ServerboundRecipeBookChangeSettingsPacket.class,
		ServerboundRecipeBookSeenRecipePacket.class,
		ServerboundSeenAdvancementsPacket.class,
		ServerboundSetCarriedItemPacket.class
	);

	/** Minimum ticks between position corrections for one player, at most one per tick. */
	private static final int POSITION_RESYNC_COOLDOWN = 1;
	private static final double POSITION_TOLERANCE_SQR = 0.01 * 0.01;
	/** Server tick of each player's last position correction. Only touched on the server thread. */
	private static final Map<UUID, Integer> LAST_POSITION_RESYNC = new HashMap<>();

	/**
	 * Called on the network thread for every incoming packet.
	 * Returns true if the packet should be dropped.
	 */
	public static boolean shouldDrop(Packet<?> packet, ServerGamePacketListenerImpl listener) {
		if (!LockManager.isFrozen()) {
			return false;
		}
		// Common packets (keep alive, client settings, mod channels, resource packs) always pass.
		if (!packet.getClass().getPackageName().equals(GAME_PACKAGE) || ALLOWED.contains(packet.getClass())) {
			return false;
		}

		ServerPlayer player = listener.player;
		player.level().getServer().execute(() -> undoClientPrediction(packet, listener));
		return true;
	}

	private static void undoClientPrediction(Packet<?> packet, ServerGamePacketListenerImpl listener) {
		ServerPlayer player = listener.player;
		if (player.isRemoved()) {
			return;
		}

		switch (packet) {
			case ServerboundMovePlayerPacket move -> {
				if (move.hasPosition()) {
					Vec3 claimed = new Vec3(move.getX(player.getX()), move.getY(player.getY()), move.getZ(player.getZ()));
					if (claimed.distanceToSqr(player.position()) > POSITION_TOLERANCE_SQR && resyncCooldownOver(player)) {
						// Relative rotation with zero change keeps wherever the player is looking.
						listener.teleport(new PositionMoveRotation(player.position(), Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
					}
				}
			}
			case ServerboundMoveVehiclePacket move -> {
				Entity vehicle = player.getRootVehicle();
				if (vehicle != player && move.movingTo().position().distanceToSqr(vehicle.position()) > POSITION_TOLERANCE_SQR && resyncCooldownOver(player)) {
					listener.send(ClientboundMoveVehiclePacket.fromEntity(vehicle));
				}
			}
			case ServerboundPaddleBoatPacket ignored -> {
			}
			case ServerboundPlayerActionPacket action -> {
				listener.ackBlockChangesUpTo(action.getSequence());
				player.containerMenu.sendAllDataToRemote();
			}
			case ServerboundUseItemOnPacket use -> {
				listener.ackBlockChangesUpTo(use.sequence());
				player.containerMenu.sendAllDataToRemote();
			}
			case ServerboundUseItemPacket use -> {
				listener.ackBlockChangesUpTo(use.sequence());
				player.containerMenu.sendAllDataToRemote();
			}
			default -> player.containerMenu.sendAllDataToRemote();
		}
	}

	private static boolean resyncCooldownOver(ServerPlayer player) {
		// The player's own tick count is frozen too, so use the server's.
		int now = player.level().getServer().getTickCount();
		Integer last = LAST_POSITION_RESYNC.get(player.getUUID());
		if (last != null && now - last < POSITION_RESYNC_COOLDOWN) {
			return false;
		}
		LAST_POSITION_RESYNC.put(player.getUUID(), now);
		return true;
	}

	public static void onDisconnect(ServerPlayer player) {
		LAST_POSITION_RESYNC.remove(player.getUUID());
	}
}
