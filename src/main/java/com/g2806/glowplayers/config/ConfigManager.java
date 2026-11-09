package com.g2806.glowplayers.config;

import com.g2806.glowplayers.GlowPlayers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves {@link GlowConfig}. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path path;
	private GlowConfig config = new GlowConfig();

	public ConfigManager() {
		this.path = FabricLoader.getInstance().getConfigDir().resolve(GlowPlayers.MOD_ID + ".json");
	}

	public GlowConfig get() {
		return config;
	}

	public void load() {
		if (!Files.exists(path)) {
			config = new GlowConfig();
			save();
			return;
		}
		boolean migrated = false;
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement json = JsonParser.parseReader(reader);
			if (json instanceof JsonObject object) {
				migrated = ConfigMigration.migrate(object);
			}
			GlowConfig loaded = GSON.fromJson(json, GlowConfig.class);
			config = loaded == null ? new GlowConfig() : loaded;
		} catch (Exception e) {
			GlowPlayers.LOGGER.error("Could not read {} — falling back to defaults", path, e);
			config = new GlowConfig();
		}
		config.sanitize();
		if (migrated) {
			GlowPlayers.LOGGER.info("Upgraded {} to config version {}", path, GlowConfig.CURRENT_VERSION);
			save();
		}
	}

	public void save() {
		config.sanitize();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			GlowPlayers.LOGGER.error("Could not write {}", path, e);
		}
	}
}
