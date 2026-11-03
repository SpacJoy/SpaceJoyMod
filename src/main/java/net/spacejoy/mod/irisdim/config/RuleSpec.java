package net.spacejoy.mod.irisdim.config;

import net.spacejoy.mod.irisdim.core.DimensionRule;

/**
 * JSON shape of one entry in the {@code rules} array.
 *
 * <pre>{@code
 * {
 *   "id": "nether",
 *   "priority": 100,
 *   "enabled": true,
 *   "comment": "Nether: no shadows worth rendering, so a cheap pack.",
 *   "match": { "dimensions": ["minecraft:the_nether"] },
 *   "shader": { "mode": "SET_PACK", "pack": "ComplementaryReimagined_r5.5.1" }
 * }
 * }</pre>
 */
public final class RuleSpec {
	/** Stable identifier; defaults to {@code rule-<index>} when omitted. */
	public String id = "";

	/** Higher runs first. Use a large negative value for catch-alls. */
	public int priority = 0;

	/** Keep a rule in the file but out of the match set. */
	public boolean enabled = true;

	/** Carried through load/save untouched; purely for the human reading the file. */
	public String comment = "";

	public MatchSpec match = new MatchSpec();

	public ActionSpec shader = new ActionSpec();

	/**
	 * Fills in any sub-block a hand-written file omitted or explicitly set to {@code null}.
	 *
	 * <p>Without this, {@code "match": null} in a config file would turn a rule into a
	 * {@code NullPointerException} at startup -- and a config file is exactly the kind of thing
	 * people edit by hand at 2am. Gson already defaults missing keys through the no-arg constructor;
	 * this covers the explicit-null case.
	 */
	public void normalise() {
		if (match == null) {
			match = new MatchSpec();
		}

		match.normalise();

		if (shader == null) {
			shader = new ActionSpec();
		}

		shader.normalise();

		if (id == null) {
			id = "";
		}

		if (comment == null) {
			comment = "";
		}
	}

	/**
	 * @param index position in the file, used to synthesise an id when the user omitted one
	 * @throws IllegalArgumentException when this entry cannot produce a valid rule
	 */
	public DimensionRule toRule(int index) {
		normalise();

		String resolvedId = (id == null || id.isBlank()) ? "rule-" + index : id;

		return new DimensionRule(
			resolvedId,
			priority,
			enabled,
			match.toMatcher(),
			shader.toAction(),
			comment
		);
	}
}
