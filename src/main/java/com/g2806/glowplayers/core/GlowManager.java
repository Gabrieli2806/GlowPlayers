package com.g2806.glowplayers.core;

import com.g2806.glowplayers.config.AssignMode;
import com.g2806.glowplayers.config.ConfigManager;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.GlowPalette;
import com.g2806.glowplayers.config.NotifyMode;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every server-side change the mod makes: scoreboard teams (which carry the glow outline
 * color), the glowing effect itself and the Locator Bar waypoint icon.
 */
public final class GlowManager {
	public static final String TEAM_PREFIX = "gp_";

	private final ConfigManager configs;
	private final Random random = new Random();

	/** Color currently shown for each online player. */
	private final Map<UUID, TeamColor> active = new HashMap<>();
	/** Players whose glowing effect was applied by this mod, so we only ever remove our own. */
	private final Set<UUID> glowApplied = new HashSet<>();

	private int tickCounter;

	public GlowManager(ConfigManager configs) {
		this.configs = configs;
	}

	public TeamColor colorOf(UUID uuid) {
		return active.get(uuid);
	}

	// ---------------------------------------------------------------- lifecycle

	public void onJoin(ServerPlayer player) {
		refreshAll(player.level().getServer());
	}

	public void onLeave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		active.remove(player.getUUID());
		glowApplied.remove(player.getUUID());
		if (server != null) {
			removeFromModTeams(server.getScoreboard(), player);
			server.execute(() -> refreshAll(server));
		}
	}

	public void onRespawn(ServerPlayer newPlayer) {
		// The respawned ServerPlayer is a brand new entity, so nothing we applied carried over.
		active.remove(newPlayer.getUUID());
		glowApplied.remove(newPlayer.getUUID());
		refreshAll(newPlayer.level().getServer());
	}

	public void onDimensionChange(ServerPlayer player) {
		refreshAll(player.level().getServer());
	}

	public void tick(MinecraftServer server) {
		GlowConfig config = configs.get();
		int interval = config.reapplyIntervalTicks;
		if (interval <= 0) {
			return;
		}
		if (++tickCounter < interval) {
			return;
		}
		tickCounter = 0;
		if (!config.enabled || !config.glowEffect) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isSkipped(player, config) && !player.hasEffect(MobEffects.GLOWING)) {
				applyGlowEffect(player);
			}
		}
	}

	public void onServerStopping(MinecraftServer server) {
		// A server that failed to start still fires this, with half of it not built yet.
		if (!configs.get().cleanupTeamsOnStop || server.getPlayerList() == null) {
			return;
		}
		Scoreboard scoreboard = server.getScoreboard();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			setWaypointColor(player, null);
		}
		for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
			if (team.getName().startsWith(TEAM_PREFIX)) {
				scoreboard.removePlayerTeam(team);
			}
		}
	}

	// ---------------------------------------------------------------- assignment

	/** Recomputes every online player's color and pushes the result to the world. */
	public void refreshAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		GlowConfig config = configs.get();
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());

		if (!config.enabled) {
			players.forEach(this::clear);
			return;
		}

		List<ServerPlayer> targets = players.stream().filter(player -> !isSkipped(player, config)).toList();
		Map<UUID, TeamColor> assignment = assign(targets, config);

		boolean persistDirty = false;
		for (ServerPlayer player : players) {
			TeamColor color = assignment.get(player.getUUID());
			if (color == null) {
				clear(player);
				continue;
			}
			apply(server, player, color, config);
			if (config.persistColors && !config.playerColors.containsKey(player.getStringUUID())) {
				String stored = config.assignedColors.put(player.getStringUUID(), color.getSerializedName());
				persistDirty |= !color.getSerializedName().equals(stored);
			}
		}
		if (persistDirty) {
			configs.save();
		}
	}

	private Map<UUID, TeamColor> assign(List<ServerPlayer> targets, GlowConfig config) {
		List<TeamColor> palette = GlowPalette.resolve(config.palette);
		Map<UUID, TeamColor> result = new LinkedHashMap<>();
		Set<TeamColor> used = new HashSet<>();
		List<ServerPlayer> pending = new ArrayList<>(targets);

		// 1. Manual overrides always win, even when two players were given the same color.
		pending.removeIf(player -> {
			TeamColor color = GlowPalette.byName(config.playerColors.get(player.getStringUUID()));
			if (color == null) {
				return false;
			}
			result.put(player.getUUID(), color);
			used.add(color);
			return true;
		});

		// 2. Reuse the color a player already had, so nobody's color shifts when someone leaves.
		if (config.persistColors) {
			pending.removeIf(player -> {
				TeamColor color = GlowPalette.byName(config.assignedColors.get(player.getStringUUID()));
				if (color == null || used.contains(color) || !palette.contains(color)) {
					return false;
				}
				result.put(player.getUUID(), color);
				used.add(color);
				return true;
			});
		}

		// 3. Everyone else follows the configured mode.
		AssignMode mode = config.assignMode();
		List<ServerPlayer> ordered = new ArrayList<>(pending);
		if (mode == AssignMode.ALPHABETICAL) {
			ordered.sort(Comparator.comparing(player -> player.getGameProfile().name().toLowerCase()));
		}
		for (int i = 0; i < ordered.size(); i++) {
			ServerPlayer player = ordered.get(i);
			int preferred = switch (mode) {
				case JOIN_ORDER, ALPHABETICAL -> i % palette.size();
				case NAME_HASH -> Math.floorMod(player.getGameProfile().name().hashCode(), palette.size());
				case RANDOM -> random.nextInt(palette.size());
			};
			TeamColor color = firstFreeFrom(palette, preferred, used);
			result.put(player.getUUID(), color);
			used.add(color);
		}
		return result;
	}

	private static TeamColor firstFreeFrom(List<TeamColor> palette, int start, Set<TeamColor> used) {
		for (int offset = 0; offset < palette.size(); offset++) {
			TeamColor candidate = palette.get((start + offset) % palette.size());
			if (!used.contains(candidate)) {
				return candidate;
			}
		}
		// More players than colors: duplicates are unavoidable.
		return palette.get(start % palette.size());
	}

	public boolean isSkipped(ServerPlayer player, GlowConfig config) {
		if (config.excludedPlayers.contains(player.getStringUUID())) {
			return true;
		}
		String dimension = player.level().dimension().identifier().toString();
		return config.disabledDimensions.contains(dimension);
	}

	// ---------------------------------------------------------------- applying

	private void apply(MinecraftServer server, ServerPlayer player, TeamColor color, GlowConfig config) {
		Scoreboard scoreboard = server.getScoreboard();
		boolean wantsTeam = config.glowEffect || config.colorPlayerNames;

		if (wantsTeam) {
			PlayerTeam team = teamFor(scoreboard, color, config);
			if (!team.getPlayers().contains(player.getScoreboardName())) {
				removeFromModTeams(scoreboard, player);
				scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
			}
		} else {
			removeFromModTeams(scoreboard, player);
		}

		if (config.glowEffect) {
			if (!player.hasEffect(MobEffects.GLOWING)) {
				applyGlowEffect(player);
			}
		} else {
			removeGlowEffect(player);
		}

		setWaypointColor(player, config.locatorBarColors ? GlowPalette.rgb(color) : null);

		TeamColor previous = active.put(player.getUUID(), color);
		if (previous != color) {
			notify(player, color, config);
		}
	}

	private void clear(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			removeFromModTeams(server.getScoreboard(), player);
		}
		removeGlowEffect(player);
		setWaypointColor(player, null);
		active.remove(player.getUUID());
	}

	/** Undoes everything for every online player, e.g. when the mod is switched off in the GUI. */
	public void clearAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			clear(player);
		}
	}

	private PlayerTeam teamFor(Scoreboard scoreboard, TeamColor color, GlowConfig config) {
		String name = TEAM_PREFIX + color.getSerializedName();
		PlayerTeam team = scoreboard.getPlayerTeam(name);
		if (team == null) {
			team = scoreboard.addPlayerTeam(name);
		}
		team.setColor(Optional.of(color));
		team.setDisplayName(Component.literal(GlowPalette.displayName(color)));
		team.setSeeFriendlyInvisibles(config.seeFriendlyInvisibles);
		team.setAllowFriendlyFire(true);
		team.setCollisionRule(Team.CollisionRule.ALWAYS);
		return team;
	}

	private void removeFromModTeams(Scoreboard scoreboard, ServerPlayer player) {
		String name = player.getScoreboardName();
		PlayerTeam team = scoreboard.getPlayersTeam(name);
		if (team != null && team.getName().startsWith(TEAM_PREFIX)) {
			scoreboard.removePlayerFromTeam(name, team);
		}
	}

	private void applyGlowEffect(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(
			MobEffects.GLOWING,
			MobEffectInstance.INFINITE_DURATION,
			0,
			false,  // ambient
			false,  // particles
			false   // icon
		));
		glowApplied.add(player.getUUID());
	}

	private void removeGlowEffect(ServerPlayer player) {
		boolean ours = glowApplied.remove(player.getUUID());
		MobEffectInstance instance = player.getEffect(MobEffects.GLOWING);
		// After a restart nothing is tracked yet, so an endless glow is taken as one of ours.
		if (instance != null && (ours || instance.isInfiniteDuration())) {
			player.removeEffect(MobEffects.GLOWING);
		}
	}

	/**
	 * Sets the Locator Bar color of a player and forces the waypoint to be rebuilt.
	 *
	 * <p>A waypoint connection snapshots the icon when it is created, so changing the team (or
	 * the icon) never reaches the players that are already tracking this one — the color only
	 * refreshed when the connection happened to be recreated, which in practice meant waiting
	 * for someone to die and respawn. Vanilla hits the same wall and works around it in
	 * {@code /waypoint modify} by untracking and retracking the waypoint; we do the same.
	 *
	 * @param rgb the color to show, or null to hand the icon back to vanilla
	 */
	private void setWaypointColor(ServerPlayer player, Integer rgb) {
		Optional<Integer> desired = Optional.ofNullable(rgb);
		if (player.waypointIcon().color.equals(desired)) {
			return;
		}
		player.waypointIcon().color = desired;
		refreshWaypoint(player);
	}

	/** Drops and recreates this player's waypoint connections so the new icon reaches everyone. */
	public static void refreshWaypoint(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		level.getWaypointManager().untrackWaypoint(player);
		if (player.isTransmittingWaypoint()) {
			level.getWaypointManager().trackWaypoint(player);
		}
	}

	private void notify(ServerPlayer player, TeamColor color, GlowConfig config) {
		NotifyMode mode = config.notifyMode();
		if (mode == NotifyMode.OFF) {
			return;
		}
		Component message = Component.empty()
			.append(Component.literal("Glow color: ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(GlowPalette.displayName(color)).withColor(color.textColor()));
		player.sendSystemMessage(message, mode == NotifyMode.ACTION_BAR);
	}
}
