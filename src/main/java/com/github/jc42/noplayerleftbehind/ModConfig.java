package com.github.jc42.noplayerleftbehind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModConfig {
	// Lenient so the // comments in the default file are allowed.
	private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.LENIENT).create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(NoPlayerLeftBehind.MOD_ID + ".json");

	private static final String DEFAULT_FILE = """
		{
		  // Minecraft usernames of every player who must be online before the world unfreezes.
		  // Capitalization does not matter. Leave the list empty to turn the mod off.
		  // Example: "requiredPlayers": ["Alex", "Steve"],
		  "requiredPlayers": [],

		  // Number of seconds everyone gets to rejoin after a required player leaves, before the world freezes.
		  "gracePeriodSeconds": 30,

		  // Length of the starting countdown.
		  "countdownSeconds": 3
		}
		""";

	public List<String> requiredPlayers = new ArrayList<>();
	public int gracePeriodSeconds = 30;
	public int countdownSeconds = 3;

	/** Required usernames keyed by their lowercased form for case-insensitive matching, in config order. */
	public Map<String, String> requiredNames() {
		Map<String, String> names = new LinkedHashMap<>();
		for (String name : requiredPlayers) {
			String trimmed = name.trim();
			if (!trimmed.isEmpty()) {
				names.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
			}
		}
		return names;
	}

	/** Reads the config file, writing a default one first if it doesn't exist. */
	public static ModConfig load() throws IOException, JsonParseException {
		if (!Files.exists(PATH)) {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, DEFAULT_FILE);
			return new ModConfig();
		}

		ModConfig config = GSON.fromJson(Files.readString(PATH), ModConfig.class);
		if (config == null) {
			throw new JsonParseException("Config file is empty");
		}
		if (config.requiredPlayers == null) {
			config.requiredPlayers = new ArrayList<>();
		}
		config.gracePeriodSeconds = Math.max(0, config.gracePeriodSeconds);
		config.countdownSeconds = Math.max(0, config.countdownSeconds);
		return config;
	}
}
