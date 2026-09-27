package com.github.jc42.noplayerleftbehind;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks whether all required players are online and drives the freeze.
 *
 * IDLE -> (anyone joins) -> LOCKED, and any state -> (nobody online) -> IDLE
 * LOCKED -> (all present) -> COUNTDOWN -> (countdown ends) -> RUNNING
 * RUNNING -> (someone missing) -> GRACE -> (grace ends) -> LOCKED
 * COUNTDOWN -> (someone missing) -> LOCKED, GRACE -> (all present again) -> RUNNING
 */
public class LockManager {
	private enum State { IDLE, LOCKED, COUNTDOWN, RUNNING, GRACE }

	/** Client movement attributes that get zeroed on the client while frozen. */
	private static final List<Holder<Attribute>> MOVEMENT_ATTRIBUTES = List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH, Attributes.GRAVITY);
	// Titles stay slightly longer than a second so they don't blink out before the next update.
	private static final int TITLE_STAY_TICKS = 30;

	/** Read from the network thread by the packet gate, so it must be volatile. */
	private static volatile boolean frozen = false;

	private static State state = State.IDLE;
	/** Lowercased name to the name as written in the config. */
	private static Map<String, String> requiredNames = Map.of();
	private static int gracePeriodSeconds = 30;
	private static int countdownSeconds = 3;
	private static boolean graceUsesActionBar = true;
	private static boolean countdownUsesActionBar = false;

	private static int secondsLeft;
	private static int ticksIntoSecond;

	private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

	public static boolean isFrozen() {
		return frozen;
	}

	public static void applyConfig(ModConfig config) {
		requiredNames = config.requiredNames();
		gracePeriodSeconds = config.gracePeriodSeconds;
		countdownSeconds = config.countdownSeconds;
		graceUsesActionBar = config.gracePeriodDisplay.equals("actionbar");
		countdownUsesActionBar = config.countdownDisplay.equals("actionbar");
	}

	public static void onDisconnect(ServerPlayer player) {
		BOSS_BAR.removePlayer(player);
	}

	public static void onServerStopping() {
		BOSS_BAR.removeAllPlayers();
		state = State.IDLE;
		frozen = false;
	}

	public static void tick(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			// Nobody online: stay out of the way. Vanilla's pause-when-empty handles an empty server.
			state = State.IDLE;
			frozen = false;
			return;
		}

		int present = countPresent(players);
		int required = requiredNames.size();
		boolean allPresent = present >= required;

		if (state == State.IDLE) {
			// First player in. Freeze right away, the switch below starts the countdown if everyone is here.
			if (required == 0) {
				state = State.RUNNING;
			} else {
				freeze(players);
				state = State.LOCKED;
			}
		}

		if (required == 0) {
			// Nobody is required, so the mod stays out of the way entirely.
			if (state != State.RUNNING) {
				clearMessages(players);
				unfreeze(server, players);
				state = State.RUNNING;
			}
		} else {
			switch (state) {
				case LOCKED -> {
					if (allPresent) {
						startTimer(State.COUNTDOWN, countdownSeconds);
						tickCountdown(server, players);
					}
				}
				case COUNTDOWN -> {
					if (allPresent) {
						tickCountdown(server, players);
					} else {
						clearMessages(players);
						state = State.LOCKED;
					}
				}
				case RUNNING -> {
					if (!allPresent) {
						startTimer(State.GRACE, gracePeriodSeconds);
						tickGrace(players);
					}
				}
				case GRACE -> {
					if (allPresent) {
						clearMessages(players);
						state = State.RUNNING;
					} else {
						tickGrace(players);
					}
				}
			}
		}

		updateBossBar(players, present, required);

		if (frozen) {
			// Every tick so players who join, respawn or change dimension while frozen get it right away.
			for (ServerPlayer player : players) {
				sendFrozenClientState(player);
			}
		}
	}

	private static void tickCountdown(MinecraftServer server, List<ServerPlayer> players) {
		if (ticksIntoSecond == 0) {
			if (secondsLeft == 0) {
				clearMessages(players);
				unfreeze(server, players);
				state = State.RUNNING;
				return;
			}
			showMessage(players, Component.literal("Starting in " + secondsLeft), Component.empty(), countdownUsesActionBar);
		}
		advanceTimer();
	}

	private static void tickGrace(List<ServerPlayer> players) {
		if (ticksIntoSecond == 0) {
			if (secondsLeft == 0) {
				clearMessages(players);
				freeze(players);
				state = State.LOCKED;
				return;
			}
			showMessage(players, Component.literal("Freezing in " + secondsLeft + "s"), Component.literal("Required player(s) not present"), graceUsesActionBar);
		}
		advanceTimer();
	}

	private static void advanceTimer() {
		if (++ticksIntoSecond >= 20) {
			ticksIntoSecond = 0;
			secondsLeft--;
		}
	}

	/** Required players who aren't online, as written in the config. */
	public static List<String> missingPlayers(MinecraftServer server) {
		Set<String> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getGameProfile().name().toLowerCase(Locale.ROOT));
		}

		List<String> missing = new ArrayList<>();
		requiredNames.forEach((lower, name) -> {
			if (!online.contains(lower)) {
				missing.add(name);
			}
		});
		return missing;
	}

	private static int countPresent(List<ServerPlayer> players) {
		int present = 0;
		for (ServerPlayer player : players) {
			if (requiredNames.containsKey(player.getGameProfile().name().toLowerCase(Locale.ROOT))) {
				present++;
			}
		}
		return present;
	}

	private static void startTimer(State timerState, int seconds) {
		state = timerState;
		secondsLeft = seconds;
		ticksIntoSecond = 0;
	}

	private static void updateBossBar(List<ServerPlayer> players, int present, int required) {
		boolean show = frozen && required > 0;
		BOSS_BAR.setVisible(show);
		if (!show) {
			return;
		}

		BOSS_BAR.setName(Component.literal("Waiting for players " + present + "/" + required));
		BOSS_BAR.setProgress(Math.min(1.0F, (float) present / required));
		for (ServerPlayer player : players) {
			BOSS_BAR.addPlayer(player);
		}
	}

	/**
	 * Shows the message as a center title, or on the action bar above the hotbar if configured.
	 * Both update in place, unlike chat. The action bar puts the subtitle first, on one line.
	 */
	private static void showMessage(List<ServerPlayer> players, Component title, Component subtitle, boolean useActionBar) {
		Component actionBar = subtitle.getString().isEmpty() ? title : Component.empty().append(subtitle).append(". ").append(title);
		for (ServerPlayer player : players) {
			if (useActionBar) {
				player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
			} else {
				player.connection.send(new ClientboundSetTitlesAnimationPacket(0, TITLE_STAY_TICKS, 0));
				player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
				player.connection.send(new ClientboundSetTitleTextPacket(title));
			}
		}
	}

	private static void clearMessages(List<ServerPlayer> players) {
		// Clears both, in case the display mode was switched by a reload while a message was showing.
		for (ServerPlayer player : players) {
			player.connection.send(new ClientboundClearTitlesPacket(true));
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
		}
	}

	private static void freeze(List<ServerPlayer> players) {
		frozen = true;
		for (ServerPlayer player : players) {
			sendFrozenClientState(player);
		}
	}

	private static void unfreeze(MinecraftServer server, List<ServerPlayer> players) {
		frozen = false;
		for (ServerPlayer player : players) {
			List<AttributeInstance> real = new ArrayList<>();
			for (Holder<Attribute> attribute : MOVEMENT_ATTRIBUTES) {
				AttributeInstance instance = player.getAttribute(attribute);
				if (instance != null) {
					real.add(instance);
				}
			}
			player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), real));
			player.onUpdateAbilities();
			server.tickRateManager().updateJoiningPlayer(player);
		}
	}

	/**
	 * Tells the client it can't move and that the world is frozen. These are only packets,
	 * nothing is changed on the server's copy of the player, so nothing gets saved.
	 */
	private static void sendFrozenClientState(ServerPlayer player) {
		List<AttributeInstance> zeroed = new ArrayList<>();
		for (Holder<Attribute> attribute : MOVEMENT_ATTRIBUTES) {
			AttributeInstance instance = new AttributeInstance(attribute, i -> {});
			instance.setBaseValue(0.0);
			zeroed.add(instance);
		}
		player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), zeroed));

		Abilities abilities = new Abilities();
		abilities.apply(player.getAbilities().pack());
		abilities.setFlyingSpeed(0.0F);
		// The client skips its speed based FOV zoom when walking speed is 0, so the view doesn't narrow.
		abilities.setWalkingSpeed(0.0F);
		player.connection.send(new ClientboundPlayerAbilitiesPacket(abilities));

		player.connection.send(new ClientboundTickingStatePacket(player.level().tickRateManager().tickrate(), true));
	}
}
