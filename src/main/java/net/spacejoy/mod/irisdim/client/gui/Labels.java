package net.spacejoy.mod.irisdim.client.gui;

import net.spacejoy.mod.irisdim.core.DimensionId;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * Display names for the ids this mod puts on screen.
 *
 * <p>Ids stay machine-readable everywhere -- in {@code config.json}, in the editable text fields,
 * in the command output -- and are only <em>labelled</em> here. A shipped rule or a vanilla
 * dimension gets a translated name; anything else falls back to the raw id, so a screen can never
 * show a bare translation key or an empty cell because a modded id had no translation.
 *
 * <p>The fallback is not a nicety. Every dimension in this mod is an opaque namespaced string, and
 * a list that hides the string behind a missing translation is worse than one that never
 * translated anything: the whole point of the picker is telling the user which id to write.
 */
public final class Labels {
	private static final String RULE_PREFIX = "spacejoy_mod.irisdim.rule.";
	private static final String DIMENSION_PREFIX = "spacejoy_mod.irisdim.dimensionName.";

	private Labels() {
	}

	/**
	 * Rule label: {@code spacejoy_mod.irisdim.rule.<id>} when it exists, else {@code #<id>}.
	 *
	 * <p>A rule created from the dimension picker is named after the dimension's <em>path</em> --
	 * picking {@code minecraft:overworld} writes a rule called {@code overworld}. That id has no
	 * rule translation of its own, so the dimension table is consulted next: otherwise the
	 * picker's own rows would read "主世界" while the rule they create read "#overworld", which is
	 * exactly the row the user is looking at when they wonder whether the name took effect.
	 *
	 * <p>The {@code #} marks "this is an id, not a name" -- it survives on exactly the rules that
	 * have no translation, which is the honest signal that the name came from the file.
	 */
	public static Component rule(String id) {
		if (id == null || id.isBlank()) {
			return Component.literal("#?");
		}

		String explicit = RULE_PREFIX + id;

		if (Language.getInstance().has(explicit)) {
			return Component.translatable(explicit);
		}

		// Only for ids that carry no namespace of their own, so a modded "mymod:overworld" is
		// never silently renamed to the vanilla Overworld.
		if (id.indexOf(':') < 0) {
			String viaDimension = dimensionKey(DimensionId.VANILLA_NAMESPACE + ":" + id);

			if (Language.getInstance().has(viaDimension)) {
				return Component.translatable(viaDimension);
			}
		}

		return Component.literal("#" + id);
	}

	/** Dimension (or dimension type) label, falling back to the raw id. */
	public static Component dimension(DimensionId id) {
		return id == null ? Component.literal("?") : dimension(id.toString());
	}

	/** Dimension (or dimension type) label, falling back to the raw id. */
	public static Component dimension(String id) {
		if (id == null || id.isBlank()) {
			return Component.literal("?");
		}

		return lookup(dimensionKey(id), id);
	}

	/**
	 * Whether {@link #dimension(String)} has a name for this id -- used by screens that want to show
	 * the raw id alongside the name, and must not print it twice when there is no name.
	 */
	public static boolean hasDimensionName(String id) {
		return id != null && !id.isBlank() && Language.getInstance().has(dimensionKey(id));
	}

	/**
	 * Translation key for a dimension id.
	 *
	 * <p>The {@code :} between namespace and path becomes a {@code .}: a colon in a key is legal but
	 * indistinguishable from a typo when it is read back, and the fallback keeps the real id anyway.
	 */
	private static String dimensionKey(String id) {
		return DIMENSION_PREFIX + id.replace(':', '.');
	}

	private static Component lookup(String key, String fallback) {
		return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(fallback);
	}
}
