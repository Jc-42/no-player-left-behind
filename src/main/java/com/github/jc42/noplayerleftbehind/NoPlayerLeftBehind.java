package com.github.jc42.noplayerleftbehind;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoPlayerLeftBehind implements ModInitializer {
	public static final String MOD_ID = "noplayerleftbehind";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		if (!reloadConfig()) {
			LOGGER.warn("Starting with no required players because the config could not be loaded");
		}

		ServerTickEvents.END_SERVER_TICK.register(LockManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LockManager.onDisconnect(handler.player);
			PacketGate.onDisconnect(handler.player);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LockManager.onServerStopping());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("leftbehind")
				.then(Commands.literal("missing").executes(context -> {
					List<String> missing = LockManager.missingPlayers(context.getSource().getServer());
					String text = missing.isEmpty() ? "None" : String.join(", ", missing);
					context.getSource().sendSuccess(() -> Component.literal(text), false);
					return missing.size();
				}))
				.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
					if (reloadConfig()) {
						context.getSource().sendSuccess(() -> Component.literal("No Player Left Behind config reloaded"), false);
						return 1;
					}
					context.getSource().sendFailure(Component.literal("No Player Left Behind config failed to load, see the server log"));
					return 0;
				}))
		));
	}

	private static boolean reloadConfig() {
		try {
			ModConfig config = ModConfig.load();
			LockManager.applyConfig(config);
			LOGGER.info("Loaded config with {} required player(s)", config.requiredNames().size());
			return true;
		} catch (Exception e) {
			LOGGER.error("Failed to load config", e);
			return false;
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
