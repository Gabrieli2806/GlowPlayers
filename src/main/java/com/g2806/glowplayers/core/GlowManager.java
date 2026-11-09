package com.g2806.glowplayers.core;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.ColorMode;
import com.g2806.glowplayers.config.ConfigManager;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.GlowPalette;
import com.g2806.glowplayers.config.NotifyMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns every server-side change the mod makes: scoreboard teams (which carry the glow outline
 * color), the glowing effect itself and the Locator Bar waypoint icon.
 */
public final class GlowManager {
	public static final String TEAM_PREFIX = "gp_";

	/** How often (in ticks) team membership is checked for changes made by someone else. */
	private static final int TEAM_CHECK_INTERVAL = 20;

	private final ConfigManager configs;

	/** Color currently shown for each online player. */
	private final Map<UUID, TeamColor> active = new HashMap<>();
	/** SESSION mode slots of the online players. Forgotten on leave and on restart. */
	private final Map<UUID, TeamColor> session = new LinkedHashMap<>();
	/** Foreign team each player was last reported on, so every conflict is announced once. */
	private final Map<UUID, String> conflicts = new HashMap<>();

	private int tickCounter;
	private boolean refreshPending;

	public GlowManager(ConfigManager configs) {
		this.configs = configs;
	}

	public TeamColor colorOf(UUID uuid) {
		return active.get(uuid);
	}

	/** Name of the foreign team the player is on, or null. */
	public String conflictOf(UUID uuid) {
		return conflicts.get(uuid);
	}

	// ---------------------------------------------------------------- lifecycle

	public void onJoin(ServerPlayer player) {
		GlowConfig config = configs.get();
		String key = player.getStringUUID();
		boolean dirty = false;
		// Keep the stored names fresh so offline entries in the GUI show who they are.
		GlowConfig.Member member = config.members.get(key);
		if (member != null && !player.getScoreboardName().equals(member.name)) {
			member.name = player.getScoreboardName();
			dirty = true;
		}
		if (config.excludedPlayers.containsKey(key)
			&& !player.getScoreboardName().equals(config.excludedPlayers.get(key))) {
			config.excludedPlayers.put(key, player.getScoreboardName());
			dirty = true;
		}
		if (dirty) {
			configs.save();
		}
		// JOIN fires before the player is in the player list, which refreshAll walks.
		refreshPending = true;
	}

	public void onLeave(ServerPlayer player) {
		UUID uuid = player.getUUID();
		active.remove(uuid);
		session.remove(uuid);
		conflicts.remove(uuid);
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			removeFromModTeams(server.getScoreboard(), player);
		}
		// Effects are saved with the player: never leave our glow behind in their data.
		removeGlowEffect(player);
		player.waypointIcon().color = Optional.empty();
	}

	public void onRespawn(ServerPlayer newPlayer) {
		// The respawned ServerPlayer is a brand new entity, so nothing we applied carried over.
		active.remove(newPlayer.getUUID());
		refreshAll(newPlayer.level().getServer());
	}

	public void onDimensionChange(ServerPlayer player) {
		refreshAll(player.level().getServer());
	}

	public void tick(MinecraftServer server) {
		tickCounter++;
		if (refreshPending) {
			refreshPending = false;
			refreshAll(server);
		}
		GlowConfig config = configs.get();
		if (!config.enabled) {
			return;
		}
		if (tickCounter % TEAM_CHECK_INTERVAL == 0) {
			checkTeams(server, config);
		}
		int interval = config.reapplyIntervalTicks;
		if (interval > 0 && config.glowEffect && tickCounter % interval == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (active.containsKey(player.getUUID()) && !player.hasEffect(MobEffects.GLOWING)) {
					applyGlowEffect(player);
				}
			}
		}
	}

	public void onServerStopping(MinecraftServer server) {
		// A server that failed to start still fires this, with half of it not built yet.
		if (server.getPlayerList() == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removeGlowEffect(player);
			player.waypointIcon().color = Optional.empty();
		}
		if (!configs.get().cleanupTeamsOnStop) {
			return;
		}
		Scoreboard scoreboard = server.getScoreboard();
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

		// The player list keeps join order, which is what SESSION mode hands colors out by.
		List<ServerPlayer> targets = players.stream().filter(player -> !isSkipped(player, config)).toList();
		Map<UUID, TeamColor> assignment = config.mode() == ColorMode.FIXED
			? assignFixed(targets, config)
			: assignSession(targets, config);

		for (ServerPlayer player : players) {
			TeamColor color = assignment.get(player.getUUID());
			if (color == null) {
				clear(player);
			} else {
				apply(server, player, color, config);
			}
		}
	}

	private Map<UUID, TeamColor> assignFixed(List<ServerPlayer> targets, GlowConfig config) {
		Map<UUID, TeamColor> result = new HashMap<>();
		boolean dirty = false;
		for (ServerPlayer player : targets) {
			GlowConfig.Member member = config.members.get(player.getStringUUID());
			if (member == null) {
				if (!config.autoAddMembers) {
					continue;
				}
				TeamColor color = leastUsed(GlowPalette.resolve(config.palette), memberColors(config));
				member = new GlowConfig.Member(player.getScoreboardName(), color.getSerializedName());
				config.members.put(player.getStringUUID(), member);
				dirty = true;
			}
			result.put(player.getUUID(), GlowPalette.byName(member.color));
		}
		if (dirty) {
			configs.save();
		}
		return result;
	}

	private Map<UUID, TeamColor> assignSession(List<ServerPlayer> targets, GlowConfig config) {
		List<TeamColor> palette = GlowPalette.resolve(config.palette);
		List<UUID> online = targets.stream().map(ServerPlayer::getUUID).toList();
		// Drop the slots of players that left (or got excluded) and colors taken off the palette.
		session.keySet().retainAll(online);
		session.values().removeIf(color -> !palette.contains(color));

		for (ServerPlayer player : targets) {
			session.computeIfAbsent(player.getUUID(), uuid -> leastUsed(palette, new ArrayList<>(session.values())));
		}
		return new HashMap<>(session);
	}

	/** First palette color nobody uses; with more players than colors, the least shared one. */
	private static TeamColor leastUsed(List<TeamColor> palette, List<TeamColor> taken) {
		Map<TeamColor, Integer> uses = new EnumMap<>(TeamColor.class);
		for (TeamColor color : taken) {
			uses.merge(color, 1, Integer::sum);
		}
		TeamColor best = palette.getFirst();
		for (TeamColor color : palette) {
			if (uses.getOrDefault(color, 0) < uses.getOrDefault(best, 0)) {
				best = color;
			}
		}
		return best;
	}

	private static List<TeamColor> memberColors(GlowConfig config) {
		return config.members.values().stream().map(member -> GlowPalette.byName(member.color)).toList();
	}

	/**
	 * Gives a player a specific color. Whoever owned that color takes the player's old one, so
	 * colors stay unique.
	 *
	 * @return the name of the player the color was swapped with, or null
	 */
	public String setColor(MinecraftServer server, UUID uuid, String name, TeamColor color) {
		GlowConfig config = configs.get();
		String swappedWith = null;
		if (config.mode() == ColorMode.FIXED) {
			GlowConfig.Member target = config.members.get(uuid.toString());
			TeamColor previous = target == null ? null : GlowPalette.byName(target.color);
			for (Map.Entry<String, GlowConfig.Member> entry : config.members.entrySet()) {
				GlowConfig.Member other = entry.getValue();
				if (!entry.getKey().equals(uuid.toString()) && color.getSerializedName().equals(other.color)) {
					TeamColor replacement = previous != null ? previous
						: leastUsed(GlowPalette.resolve(config.palette), memberColors(config));
					other.color = replacement.getSerializedName();
					swappedWith = other.name;
					break;
				}
			}
			if (target == null) {
				config.members.put(uuid.toString(), new GlowConfig.Member(name, color.getSerializedName()));
			} else {
				target.color = color.getSerializedName();
			}
		} else {
			TeamColor previous = session.get(uuid);
			for (Map.Entry<UUID, TeamColor> entry : session.entrySet()) {
				if (!entry.getKey().equals(uuid) && entry.getValue() == color) {
					if (previous != null) {
						entry.setValue(previous);
					} else {
						session.remove(entry.getKey());
					}
					ServerPlayer other = server.getPlayerList().getPlayer(entry.getKey());
					swappedWith = other == null ? null : other.getScoreboardName();
					break;
				}
			}
			session.put(uuid, color);
		}
		GlowPlayers.applyAndSave(server);
		return swappedWith;
	}

	/** FIXED mode: puts a player in the group with the next free color. */
	public void addMember(MinecraftServer server, UUID uuid, String name) {
		GlowConfig config = configs.get();
		if (!config.members.containsKey(uuid.toString())) {
			TeamColor color = leastUsed(GlowPalette.resolve(config.palette), memberColors(config));
			config.members.put(uuid.toString(), new GlowConfig.Member(name, color.getSerializedName()));
		}
		GlowPlayers.applyAndSave(server);
	}

	/** Who holds this color besides {@code except}: a group member in FIXED mode, an online player otherwise. */
	public String ownerOf(MinecraftServer server, TeamColor color, UUID except) {
		GlowConfig config = configs.get();
		if (config.mode() == ColorMode.FIXED) {
			for (Map.Entry<String, GlowConfig.Member> entry : config.members.entrySet()) {
				if (!entry.getKey().equals(except.toString()) && color.getSerializedName().equals(entry.getValue().color)) {
					return entry.getValue().name;
				}
			}
			return null;
		}
		for (Map.Entry<UUID, TeamColor> entry : session.entrySet()) {
			if (!entry.getKey().equals(except) && entry.getValue() == color) {
				ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(entry.getKey());
				return owner == null ? "?" : owner.getScoreboardName();
			}
		}
		return null;
	}

	/** FIXED: takes the player out of the group. SESSION: hands them the lowest free slot again. */
	public void resetColor(MinecraftServer server, UUID uuid) {
		if (configs.get().mode() == ColorMode.FIXED) {
			configs.get().members.remove(uuid.toString());
		} else {
			session.remove(uuid);
		}
		GlowPlayers.applyAndSave(server);
	}

	/** Called when the mode changes: the other mode's state no longer applies. */
	public void onModeChanged() {
		session.clear();
	}

	public boolean isSkipped(ServerPlayer player, GlowConfig config) {
		if (config.excludedPlayers.containsKey(player.getStringUUID())) {
			return true;
		}
		String dimension = player.level().dimension().identifier().toString();
		return config.disabledDimensions.contains(dimension);
	}

	// ---------------------------------------------------------------- applying

	private void apply(MinecraftServer server, ServerPlayer player, TeamColor color, GlowConfig config) {
		applyTeam(server, player, color, config);

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

	/**
	 * Puts the player on the team of their color, unless they are on a team that belongs to
	 * someone else: that one is left alone (and reported) unless overriding is turned on.
	 */
	private void applyTeam(MinecraftServer server, ServerPlayer player, TeamColor color, GlowConfig config) {
		Scoreboard scoreboard = server.getScoreboard();
		String name = player.getScoreboardName();
		PlayerTeam current = scoreboard.getPlayersTeam(name);
		boolean foreign = current != null && !current.getName().startsWith(TEAM_PREFIX);

		if (!config.glowEffect && !config.colorPlayerNames) {
			removeFromModTeams(scoreboard, player);
			conflicts.remove(player.getUUID());
			return;
		}
		if (foreign) {
			reportConflict(server, player, current.getName(), config);
			if (!config.overrideForeignTeams) {
				return;
			}
		} else {
			conflicts.remove(player.getUUID());
		}
		PlayerTeam team = teamFor(scoreboard, color, config);
		if (current != team) {
			scoreboard.addPlayerToTeam(name, team);
		}
	}

	/** Catches teams changed behind our back (/team, maps, other mods) and reacts to them. */
	private void checkTeams(MinecraftServer server, GlowConfig config) {
		if (!config.glowEffect && !config.colorPlayerNames) {
			return;
		}
		Scoreboard scoreboard = server.getScoreboard();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			TeamColor color = active.get(player.getUUID());
			if (color == null) {
				continue;
			}
			PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
			boolean onOurs = current != null && current.getName().equals(TEAM_PREFIX + color.getSerializedName());
			boolean knownConflict = current != null && current.getName().equals(conflicts.get(player.getUUID()))
				&& !config.overrideForeignTeams;
			if (!onOurs && !knownConflict) {
				applyTeam(server, player, color, config);
			}
		}
	}

	private void reportConflict(MinecraftServer server, ServerPlayer player, String teamName, GlowConfig config) {
		if (teamName.equals(conflicts.put(player.getUUID(), teamName))) {
			return;
		}
		String text = config.overrideForeignTeams
			? player.getScoreboardName() + " was moved out of team \"" + teamName
				+ "\" (override foreign teams is on)."
			: player.getScoreboardName() + " is on team \"" + teamName
				+ "\", which GlowPlayers does not own. The team is kept, so the glow uses its color;"
				+ " the Locator Bar color still applies.";
		GlowPlayers.LOGGER.warn(text);
		if (!config.warnTeamConflicts) {
			return;
		}
		Component message = Component.empty()
			.append(Component.literal("[GlowPlayers] ").withStyle(ChatFormatting.DARK_AQUA))
			.append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (GlowPlayers.isAdmin(online.permissions())) {
				online.sendSystemMessage(message);
			}
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
		conflicts.remove(player.getUUID());
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

	/**
	 * Our glow is the only endless, ambient one: that pair marks it as ours, so a glow from a
	 * spectral arrow, a command or another mod is never touched.
	 */
	private static void applyGlowEffect(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(
			MobEffects.GLOWING,
			MobEffectInstance.INFINITE_DURATION,
			0,
			true,   // ambient: our marker
			false,  // particles
			false   // icon
		));
	}

	private static void removeGlowEffect(ServerPlayer player) {
		MobEffectInstance instance = player.getEffect(MobEffects.GLOWING);
		if (instance != null && instance.isAmbient() && instance.isInfiniteDuration()) {
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
	private static void setWaypointColor(ServerPlayer player, Integer rgb) {
		Optional<Integer> desired = Optional.ofNullable(rgb);
		if (player.waypointIcon().color.equals(desired)) {
			return;
		}
		player.waypointIcon().color = desired;
		refreshWaypoint(player);
	}

	/** Drops and recreates this player's waypoint connections so the new icon reaches everyone. */
	private static void refreshWaypoint(ServerPlayer player) {
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
