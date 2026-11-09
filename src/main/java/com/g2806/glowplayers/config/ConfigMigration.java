package com.g2806.glowplayers.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** Upgrades a config file written by an older version before Gson reads it. */
final class ConfigMigration {
	private ConfigMigration() {
	}

	/** @return true when the file was changed and should be written back */
	static boolean migrate(JsonObject json) {
		int version = json.has("configVersion") ? json.get("configVersion").getAsInt() : 2;
		if (version >= GlowConfig.CURRENT_VERSION) {
			return false;
		}

		// v2 had four automatic modes plus "persistColors". Persisted colors are a fixed group.
		boolean persist = !json.has("persistColors") || json.get("persistColors").getAsBoolean();
		json.addProperty("mode", (persist ? ColorMode.FIXED : ColorMode.SESSION).name());

		// Remembered colors first, so a color an admin forced wins over them.
		JsonObject members = new JsonObject();
		copyColors(json, "assignedColors", members);
		copyColors(json, "playerColors", members);
		json.add("members", members);

		// The exclusion list became a map so excluded players keep a name while offline.
		if (json.get("excludedPlayers") instanceof JsonArray excluded) {
			JsonObject map = new JsonObject();
			for (JsonElement uuid : excluded) {
				map.addProperty(uuid.getAsString(), "");
			}
			json.add("excludedPlayers", map);
		}

		json.remove("assignMode");
		json.remove("persistColors");
		json.remove("assignedColors");
		json.remove("playerColors");
		json.addProperty("configVersion", GlowConfig.CURRENT_VERSION);
		return true;
	}

	private static void copyColors(JsonObject json, String key, JsonObject members) {
		if (!(json.get(key) instanceof JsonObject colors)) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : colors.entrySet()) {
			JsonObject member = new JsonObject();
			member.addProperty("name", "");
			member.addProperty("color", entry.getValue().getAsString());
			members.add(entry.getKey(), member);
		}
	}
}
