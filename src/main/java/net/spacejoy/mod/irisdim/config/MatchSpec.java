package net.spacejoy.mod.irisdim.config;

import net.spacejoy.mod.irisdim.core.DimensionMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON shape of a rule's {@code match} block. Kept separate from {@link DimensionMatcher} so that
 * the on-disk format can gain keys without the matching logic ever seeing a Gson type.
 */
public final class MatchSpec {
	/** Patterns tested against the dimension id, e.g. {@code ["minecraft:the_nether"]}. */
	public List<String> dimensions = new ArrayList<>();

	/** Patterns tested against the dimension <em>type</em> id, e.g. {@code ["minecraft:the_end"]}. */
	public List<String> dimensionTypes = new ArrayList<>();

	/** Patterns tested against the world key, e.g. {@code ["multiplayer:play.example.com:25565"]}. */
	public List<String> worlds = new ArrayList<>();

	public boolean isEmpty() {
		return dimensions.isEmpty() && dimensionTypes.isEmpty() && worlds.isEmpty();
	}

	/** Replaces any explicitly-null list with an empty one; see {@link RuleSpec#normalise()}. */
	public void normalise() {
		if (dimensions == null) {
			dimensions = new ArrayList<>();
		}

		if (dimensionTypes == null) {
			dimensionTypes = new ArrayList<>();
		}

		if (worlds == null) {
			worlds = new ArrayList<>();
		}
	}

	public DimensionMatcher toMatcher() {
		if (isEmpty()) {
			// An empty match block is the catch-all, not an error: "apply this everywhere".
			return DimensionMatcher.matchAll();
		}

		return DimensionMatcher.of(dimensions, dimensionTypes, worlds);
	}
}
