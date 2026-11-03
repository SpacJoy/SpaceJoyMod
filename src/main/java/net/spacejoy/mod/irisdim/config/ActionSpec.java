package net.spacejoy.mod.irisdim.config;

import net.spacejoy.mod.irisdim.core.ShaderAction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON shape of a rule's {@code shader} block.
 *
 * <pre>{@code
 * "shader": {
 *   "mode": "SET_PACK",
 *   "pack": "ComplementaryReimagined_r5.5.1",
 *   "options": { "SHADOW_QUALITY": "1", "GENERATED_NORMALS": "true" },
 *   "shadowDistance": 24
 * }
 * }</pre>
 */
public final class ActionSpec {
	/** One of {@code NONE}, {@code SET_PACK}, {@code DISABLE_SHADERS}; parsed case-insensitively. */
	public String mode = "NONE";

	/** Shaderpack folder or zip name inside {@code shaderpacks/}; ignored unless mode is SET_PACK. */
	public String pack = "";

	/** Iris shaderpack option overrides, applied on top of the pack's own defaults. */
	public Map<String, String> options = new LinkedHashMap<>();

	/** Optional Iris shadow render distance for this dimension (0-64). */
	public Integer shadowDistance = null;

	/** Replaces any explicitly-null field with its default; see {@link RuleSpec#normalise()}. */
	public void normalise() {
		if (mode == null) {
			mode = "NONE";
		}

		if (pack == null) {
			pack = "";
		}

		if (options == null) {
			options = new LinkedHashMap<>();
		}
	}

	/**
	 * @throws IllegalArgumentException if the block cannot be turned into a valid action; the caller
	 *                                  skips the rule and reports it instead of failing the reload
	 */
	public ShaderAction toAction() {
		normalise();

		return switch (ShaderAction.parseMode(mode)) {
			case NONE -> ShaderAction.none();
			case DISABLE_SHADERS -> ShaderAction.disableShaders();
			case SET_PACK -> ShaderAction.ofPack(pack, options, shadowDistance);
		};
	}
}
