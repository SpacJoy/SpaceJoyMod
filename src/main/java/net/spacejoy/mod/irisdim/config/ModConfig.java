package net.spacejoy.mod.irisdim.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.spacejoy.mod.irisdim.core.DimensionRule;
import net.spacejoy.mod.irisdim.core.RuleSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Root of {@code config/spacejoy-mod/irisdim/config.json}.
 *
 * <p>The shipped default is intentionally inert: every active rule uses {@code NONE}, so a fresh
 * install changes nothing until the user opts in. That is the only honest default for a mod whose
 * whole job is to make a visible, several-second change to the player's screen.
 */
public final class ModConfig {
	/** Bumped only when the on-disk shape changes incompatibly. */
	public static final int SCHEMA_VERSION = 1;

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create();

	public int schemaVersion = SCHEMA_VERSION;

	public ModSettings settings = new ModSettings();

	public List<RuleSpec> rules = new ArrayList<>();

	/** Result of {@link #toRuleSet()}: the compiled rules plus whatever had to be skipped. */
	public record CompileResult(RuleSet ruleSet, List<String> warnings) {
	}

	public static ModConfig defaults() {
		ModConfig config = new ModConfig();
		config.schemaVersion = SCHEMA_VERSION;
		config.settings = new ModSettings();
		config.rules = defaultRules();
		return config;
	}

	private static List<RuleSpec> defaultRules() {
		List<RuleSpec> rules = new ArrayList<>();

		// --- Vanilla dimensions -----------------------------------------------------------
		// Shipped as NONE: the mod is installed because the user wants per-dimension control,
		// but which pack belongs in the Nether is their call, not ours.
		rules.add(rule("vanilla-overworld", 100, true,
			"Overworld: the place most packs are tuned for. Set \"mode\": \"SET_PACK\" and fill in "
				+ "\"pack\" with the exact file name from shaderpacks/ -- .zip included, or the folder "
				+ "name for an unzipped pack.",
			List.of("minecraft:overworld"), List.of(), "NONE", "", null));

		rules.add(rule("vanilla-nether", 100, true,
			"Nether: no sky, permanent fog. A pack with a cheap shadow path is usually the right pick.",
			List.of("minecraft:the_nether"), List.of(), "NONE", "", null));

		rules.add(rule("vanilla-end", 100, true,
			"End: unusual lighting, often needs its own pack to avoid the washed-out look.",
			List.of("minecraft:the_end"), List.of(), "NONE", "", null));

		// --- Worked examples for dimensions that do not exist in vanilla --------------------
		// Disabled so they never fire on their own. To enable one, put the EXACT file name from
		// shaderpacks/ into "pack" -- the .zip extension is part of it, because that is the name
		// Iris resolves -- then flip "enabled" to true. This is the entire procedure for supporting
		// a new dimension: no registry, no enum, no code change.
		rules.add(rule("example-modded-namespace", 50, false,
			"Example: every dimension added by one mod, matched by namespace wildcard. "
				+ "Set shader.pack to a full file name from shaderpacks/ (including .zip).",
			List.of("twilightforest:*"), List.of(), "SET_PACK", "", null));

		rules.add(rule("example-by-dimension-type", 40, false,
			"Example: match by dimension TYPE instead of id. This keeps working for dimensions that "
				+ "do not exist yet, as long as they register the vanilla end type.",
			List.of(), List.of("minecraft:the_end"), "SET_PACK", "", null));

		// --- Catch-all ---------------------------------------------------------------------
		// Lowest priority, matched only when nothing above did. Delete it and unknown dimensions
		// will simply be reported in discovered.json instead of silently ignored.
		rules.add(rule("catch-all", -1000, true,
			"Fallback for every dimension not matched above, including modded and future ones. "
				+ "NONE keeps Iris as it is; switch to SET_PACK to force a default pack everywhere.",
			List.of("*"), List.of(), "NONE", "", null));

		return rules;
	}

	private static RuleSpec rule(String id, int priority, boolean enabled, String comment,
								 List<String> dimensions, List<String> dimensionTypes,
								 String mode, String pack, Integer shadowDistance) {
		RuleSpec spec = new RuleSpec();
		spec.id = id;
		spec.priority = priority;
		spec.enabled = enabled;
		spec.comment = comment;
		spec.match.dimensions = new ArrayList<>(dimensions);
		spec.match.dimensionTypes = new ArrayList<>(dimensionTypes);
		spec.shader.mode = mode;
		spec.shader.pack = pack;
		spec.shader.shadowDistance = shadowDistance;
		return spec;
	}

	/**
	 * Compiles the JSON entries into the runtime rule set.
	 *
	 * <p>Disabled rules are skipped before validation, so an unfinished example entry can sit in the
	 * file without producing errors. A malformed <em>enabled</em> rule is skipped individually and
	 * reported; one bad rule never takes out the rest of the file.
	 */
	public CompileResult toRuleSet() {
		List<String> warnings = new ArrayList<>();
		List<DimensionRule> compiled = new ArrayList<>();

		if (rules != null) {
			for (int index = 0; index < rules.size(); index++) {
				RuleSpec spec = rules.get(index);

				if (spec == null || !spec.enabled) {
					continue;
				}

				try {
					compiled.add(spec.toRule(index));
				} catch (RuntimeException e) {
					// A rule that cannot be compiled is skipped, never fatal: one bad entry must not
					// take out the whole config, and it must not stop the game from loading either.
					String label = (spec.id == null || spec.id.isBlank()) ? "rules[" + index + "]" : spec.id;
					warnings.add(label + " skipped: " + e);
				}
			}
		}

		return new CompileResult(RuleSet.of(compiled), warnings);
	}

	/** Structural problems worth surfacing at startup. Never throws. */
	public List<String> validate() {
		List<String> warnings = new ArrayList<>();

		if (settings == null) {
			warnings.add("settings block missing; defaults restored");
			settings = new ModSettings();
		}

		warnings.addAll(settings.validate());

		if (schemaVersion > SCHEMA_VERSION) {
			warnings.add("config schemaVersion " + schemaVersion + " is newer than this build understands ("
				+ SCHEMA_VERSION + "); unknown keys were ignored");
		}

		CompileResult result = toRuleSet();
		warnings.addAll(result.warnings());

		if (result.ruleSet().isEmpty()) {
			warnings.add("no usable rules -- the mod will never change the Iris state");
		} else if (result.ruleSet().catchAll().isEmpty()) {
			warnings.add("no catch-all rule (dimensions: [\"*\"]) -- unmatched dimensions will be reported, not handled");
		}

		return warnings;
	}

	public String toJson() {
		if (schemaVersion <= 0) {
			schemaVersion = SCHEMA_VERSION;
		}

		return GSON.toJson(this);
	}

	/**
	 * @throws JsonSyntaxException if the text is not valid JSON
	 * @throws IllegalArgumentException if the text parses to something that is not an object
	 */
	public static ModConfig fromJson(String json) {
		ModConfig config = GSON.fromJson(json, ModConfig.class);

		if (config == null) {
			throw new IllegalArgumentException("config file is empty");
		}

		if (config.settings == null) {
			config.settings = new ModSettings();
		}

		if (config.rules == null) {
			config.rules = new ArrayList<>();
		}

		for (RuleSpec rule : config.rules) {
			if (rule != null) {
				rule.normalise();
			}
		}

		return config;
	}
}
