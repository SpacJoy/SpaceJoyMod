package net.spacejoy.mod.irisdim.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An ordered, immutable collection of {@link DimensionRule}s plus the resolution algorithm.
 *
 * <p>Resolution is deliberately the dullest possible thing: filter out disabled rules, sort by
 * priority descending, and take the first rule whose matcher accepts the context. Sorting is
 * stable, so rules sharing a priority keep the order they appear in the config file. There is no
 * scoring, no "most specific wins" heuristic and no hidden fallback chain -- when a user asks why a
 * pack was chosen, the answer is always "the first rule in the file that matched", which they can
 * verify by eye.
 */
public final class RuleSet {
	private final List<DimensionRule> ordered;
	private final Optional<DimensionRule> catchAll;

	private RuleSet(List<DimensionRule> ordered) {
		this.ordered = List.copyOf(ordered);
		this.catchAll = this.ordered.stream().filter(rule -> rule.matcher().isCatchAll()).findFirst();
	}

	public static RuleSet of(List<DimensionRule> rules) {
		List<DimensionRule> active = new ArrayList<>();

		if (rules != null) {
			for (DimensionRule rule : rules) {
				if (rule != null && rule.enabled()) {
					active.add(rule);
				}
			}
		}

		// Stable sort: equal priorities keep their declaration order.
		active.sort(Comparator.comparingInt(DimensionRule::priority).reversed());

		return new RuleSet(active);
	}

	public static RuleSet empty() {
		return new RuleSet(List.of());
	}

	/** The active rules, in evaluation order. */
	public List<DimensionRule> rules() {
		return ordered;
	}

	public boolean isEmpty() {
		return ordered.isEmpty();
	}

	/** The first active rule that covers the context, if any. */
	public Optional<DimensionRule> match(DimensionContext context) {
		for (DimensionRule rule : ordered) {
			if (rule.matcher().matches(context)) {
				return Optional.of(rule);
			}
		}

		return Optional.empty();
	}

	/**
	 * The catch-all rule ({@code dimensions: ["*"]}), used as the second chance when a rule matched
	 * but named a shaderpack that is not installed.
	 */
	public Optional<DimensionRule> catchAll() {
		return catchAll;
	}
}
