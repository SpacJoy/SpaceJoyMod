package net.spacejoy.mod.irisdim.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What should happen to the Iris state when the player enters a dimension.
 *
 * <p>{@link Mode#NONE} is the default for every shipped rule, and it is important that it exists:
 * it lets the config describe a dimension ("yes, I know about this one, and I want Iris left
 * alone") without the mod having to guess that silence means "do nothing". Silence is ambiguous --
 * a dimension with no rule at all is reported as an unknown dimension, which is actionable
 * information; a dimension with a {@code NONE} rule is deliberate and stays quiet.
 *
 * @param packName        shaderpack name (file or folder name inside {@code shaderpacks/}), or
 *                        {@code null} for the other two modes
 * @param options         Iris shaderpack option overrides applied on top of the pack defaults,
 *                        keyed by the option id used in {@code shaders.properties}
 * @param shadowDistance  optional Iris shadow render distance (0-64) applied together with the
 *                        switch, or {@code null} to leave the current value alone
 */
public record ShaderAction(Mode mode, String packName, Map<String, String> options, Integer shadowDistance) {
	public enum Mode {
		/** Leave Iris exactly as it is. */
		NONE,
		/** Load {@link #packName()} and enable shaders. */
		SET_PACK,
		/** Turn shaders off entirely for this dimension. */
		DISABLE_SHADERS
	}

	/** Largest shadow distance Iris will honour; values above this are clamped, never rejected. */
	public static final int MAX_SHADOW_DISTANCE = 64;

	private static final ShaderAction NOOP = new ShaderAction(Mode.NONE, null, Map.of(), null);

	public ShaderAction {
		Objects.requireNonNull(mode, "mode");

		Map<String, String> sanitised = new LinkedHashMap<>();

		if (options != null) {
			options.forEach((key, value) -> {
				if (key != null && !key.isBlank() && value != null) {
					sanitised.put(key, value);
				}
			});
		}

		options = Map.copyOf(sanitised);

		if (mode == Mode.SET_PACK) {
			if (packName == null || packName.isBlank()) {
				throw new IllegalArgumentException("SET_PACK requires a non-blank shaderpack name");
			}

			packName = packName.trim();

			if (shadowDistance != null) {
				shadowDistance = Math.max(0, Math.min(shadowDistance, MAX_SHADOW_DISTANCE));
			}
		} else {
			packName = null;
			shadowDistance = null;
		}
	}

	/** Do nothing. */
	public static ShaderAction none() {
		return NOOP;
	}

	/** Load the named shaderpack, optionally with option overrides and a shadow distance. */
	public static ShaderAction ofPack(String packName, Map<String, String> options, Integer shadowDistance) {
		return new ShaderAction(Mode.SET_PACK, packName, options, shadowDistance);
	}

	/** Load the named shaderpack with no overrides. */
	public static ShaderAction ofPack(String packName) {
		return ofPack(packName, Map.of(), null);
	}

	/** Disable shaders for the matched dimension. */
	public static ShaderAction disableShaders() {
		return new ShaderAction(Mode.DISABLE_SHADERS, null, Map.of(), null);
	}

	public boolean isNoop() {
		return mode == Mode.NONE;
	}

	/** {@code true} when this action would need Iris to reload the shader pipeline. */
	public boolean requiresReload() {
		return mode == Mode.SET_PACK || mode == Mode.DISABLE_SHADERS;
	}

	public String describe() {
		return switch (mode) {
			case NONE -> "keep current shaders";
			case DISABLE_SHADERS -> "disable shaders";
			case SET_PACK -> {
				StringBuilder builder = new StringBuilder("shaderpack '").append(packName).append('\'');

				if (!options.isEmpty()) {
					builder.append(" +").append(options.size()).append(" option(s)");
				}

				if (shadowDistance != null) {
					builder.append(" shadowDistance=").append(shadowDistance);
				}

				yield builder.toString();
			}
		};
	}

	/** Parses the config-facing mode token; tolerant about case and surrounding whitespace. */
	public static Mode parseMode(String raw) {
		if (raw == null || raw.isBlank()) {
			return Mode.NONE;
		}

		return switch (raw.trim().toUpperCase(Locale.ROOT)) {
			case "NONE", "KEEP", "NOOP" -> Mode.NONE;
			case "SET_PACK", "SET", "PACK" -> Mode.SET_PACK;
			case "DISABLE_SHADERS", "DISABLE", "OFF" -> Mode.DISABLE_SHADERS;
			default -> throw new IllegalArgumentException("unknown shader mode: '" + raw + "'");
		};
	}
}
