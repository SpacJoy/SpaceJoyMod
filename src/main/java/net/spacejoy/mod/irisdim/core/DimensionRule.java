package net.spacejoy.mod.irisdim.core;

import java.util.Objects;

/**
 * One "when the player is in X, make Iris look like Y" entry.
 *
 * @param id       stable, human-readable identifier used in logs and in
 *                 {@code /irisdimensionshaders status}; free-form, but must be unique-ish
 * @param priority higher wins; rules are evaluated priority-descending and the first match is
 *                 applied, so a specific rule simply outranks the catch-all
 * @param enabled  lets a rule be kept in the file but switched off without deleting it
 * @param matcher  which dimensions this rule covers
 * @param action   what to do with Iris there
 * @param comment  free-form note carried through load/save, so the file stays readable
 */
public record DimensionRule(
	String id,
	int priority,
	boolean enabled,
	DimensionMatcher matcher,
	ShaderAction action,
	String comment
) {
	public DimensionRule {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("rule id must not be blank");
		}

		id = id.trim();
		Objects.requireNonNull(matcher, "matcher");
		Objects.requireNonNull(action, "action");

		if (comment == null) {
			comment = "";
		}
	}

	public String describe() {
		return "#" + id + " (priority " + priority + ") " + matcher.describe() + " -> " + action.describe();
	}
}
