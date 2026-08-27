package com.g2806.glowplayers;

import com.g2806.glowplayers.command.GlowCommand;
import com.g2806.glowplayers.config.ConfigManager;
import com.g2806.glowplayers.core.GlowManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side mod that makes every player glow in a color of their own and paints the matching
 * color on their Locator Bar waypoint. Vanilla clients need nothing installed.
 */
public class GlowPlayers implements ModInitializer {
	public static final String MOD_ID = "glowplayers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ConfigManager CONFIGS = new ConfigManager();
	private static final GlowManager MANAGER = new GlowManager(CONFIGS);

	public static ConfigManager configs() {
		return CONFIGS;
	}

	public static GlowManager manager() {
		return MANAGER;
	}

	/** Writes the config back to disk and pushes the new state to every online player. */
	public static void applyAndSave(MinecraftServer server) {
		CONFIGS.save();
		if (server != null) {
			MANAGER.refreshAll(server);
		}
	}

	@Override
	public void onInitialize() {
		CONFIGS.load();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			GlowCommand.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(MANAGER::refreshAll);
		ServerLifecycleEvents.SERVER_STOPPING.register(MANAGER::onServerStopping);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> MANAGER.onJoin(handler.getPlayer())));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			MANAGER.onLeave(handler.getPlayer()));

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
			MANAGER.onRespawn(newPlayer));

		// A dimension change moves the player to another waypoint manager, so it has to be redone.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
			MANAGER.onDimensionChange(player));

		ServerTickEvents.END_SERVER_TICK.register(MANAGER::tick);

		LOGGER.info("GlowPlayers ready — /glowplayers opens the admin menu.");
	}
}
