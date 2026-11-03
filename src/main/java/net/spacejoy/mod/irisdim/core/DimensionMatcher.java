package net.spacejoy.mod.irisdim.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Decides whether a {@link DimensionContext} is covered by a rule.
 *
 * <p>Supported pattern syntax (applied to the {@code namespace:path} string of whichever axis is
 * being tested):
 *
 * <table border="1">
 *   <caption>Pattern forms</caption>
 *   <tr><th>Pattern</th><th>Matches</th></tr>
 *   <tr><td>{@code minecraft:the_nether}</td><td>exactly that dimension</td></tr>
 *   <tr><td>{@code twilightforest:*}</td><td>every dimension in one namespace</td></tr>
 *   <tr><td>{@code minecraft:the_*}</td><td>any dimension whose id starts with a prefix</td></tr>
 *   <tr><td>{@code *}</td><td>anything -- the catch-all</td></tr>
 *   <tr><td>{@code regex:^([a-z]+):sky_\\d+$}</td><td>full regular expression, for exotic cases</td></tr>
 * </table>
 *
 * <p>Each axis is optional. An empty list means "no constraint on this axis", which is what lets a
 * user write a one-line rule for a brand-new dimension without filling in anything else.
 */
public final class DimensionMatcher {
	private static final String REGEX_PREFIX = "regex:";
	private static final String WILDCARD = "*";
	private static final Pattern MATCH_EVERYTHING = Pattern.compile(".*");

	private final List<String> dimensionPatterns;
	private final List<String> dimensionTypePatterns;
	private final List<String> worldPatterns;

	private final List<Pattern> compiledDimensions;
	private final List<Pattern> compiledDimensionTypes;
	private final List<Pattern> compiledWorlds;

	private DimensionMatcher(List<String> dimensionPatterns, List<String> dimensionTypePatterns, List<String> worldPatterns) {
		this.dimensionPatterns = List.copyOf(dimensionPatterns);
		this.dimensionTypePatterns = List.copyOf(dimensionTypePatterns);
		this.worldPatterns = List.copyOf(worldPatterns);
		this.compiledDimensions = compileAll(this.dimensionPatterns);
		this.compiledDimensionTypes = compileAll(this.dimensionTypePatterns);
		this.compiledWorlds = compileAll(this.worldPatterns);
	}

	/** A matcher that accepts every dimension: {@code dimensions: ["*"]} with no other axis. */
	public static DimensionMatcher matchAll() {
		return new DimensionMatcher(List.of(WILDCARD), List.of(), List.of());
	}

	public static DimensionMatcher of(List<String> dimensions, List<String> dimensionTypes, List<String> worlds) {
		return new DimensionMatcher(
			dimensions == null ? List.of() : dimensions,
			dimensionTypes == null ? List.of() : dimensionTypes,
			worlds == null ? List.of() : worlds
		);
	}

	public boolean matches(DimensionContext context) {
		Objects.requireNonNull(context, "context");

		if (!matchesAny(compiledDimensions, context.dimension().toString())) {
			return false;
		}

		// A dimension-type constraint cannot be satisfied by an unreadable type id: rule authors
		// who write one are explicitly asking for "dimensions of this kind".
		if (!compiledDimensionTypes.isEmpty()) {
			if (!context.hasDimensionType() || !matchesAny(compiledDimensionTypes, context.dimensionType().toString())) {
				return false;
			}
		}

		if (!compiledWorlds.isEmpty()) {
			if (!context.hasWorldKey() || !matchesAny(compiledWorlds, context.worldKey())) {
				return false;
			}
		}

		return true;
	}

	/** {@code true} when this matcher ignores the dimensions/type/world it is tested against. */
	public boolean isCatchAll() {
		return dimensionTypePatterns.isEmpty()
			&& worldPatterns.isEmpty()
			&& dimensionPatterns.size() == 1
			&& WILDCARD.equals(dimensionPatterns.get(0));
	}

	public List<String> dimensionPatterns() {
		return dimensionPatterns;
	}

	public List<String> dimensionTypePatterns() {
		return dimensionTypePatterns;
	}

	public List<String> worldPatterns() {
		return worldPatterns;
	}

	/** Compact rendering used in logs. */
	public String describe() {
		List<String> parts = new ArrayList<>();

		if (!dimensionPatterns.isEmpty()) {
			parts.add("dimensions=" + dimensionPatterns);
		}

		if (!dimensionTypePatterns.isEmpty()) {
			parts.add("dimensionTypes=" + dimensionTypePatterns);
		}

		if (!worldPatterns.isEmpty()) {
			parts.add("worlds=" + worldPatterns);
		}

		return parts.isEmpty() ? "(matches everything)" : String.join(" ", parts);
	}

	@Override
	public String toString() {
		return "DimensionMatcher" + describe();
	}

	/**
	 * Translates one user-supplied pattern into a compiled regex.
	 *
	 * @throws IllegalArgumentException if the pattern is blank or an invalid {@code regex:} expression
	 */
	public static Pattern compile(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("empty pattern");
		}

		String pattern = raw.trim();

		if (pattern.startsWith(REGEX_PREFIX)) {
			String expression = pattern.substring(REGEX_PREFIX.length());

			try {
				return Pattern.compile(expression);
			} catch (PatternSyntaxException e) {
				throw new IllegalArgumentException("invalid regex pattern '" + raw + "': " + e.getDescription(), e);
			}
		}

		// Dimension and dimension-type ids are always lowercase; accepting mixed case here means a
		// hand-edited config can never fail silently on capitalisation.
		pattern = pattern.toLowerCase(Locale.ROOT);

		if (WILDCARD.equals(pattern)) {
			return MATCH_EVERYTHING;
		}

		if (pattern.endsWith(WILDCARD)) {
			// Covers both "namespace:*" and partial prefixes such as "minecraft:the_*".
			return Pattern.compile("^" + Pattern.quote(pattern.substring(0, pattern.length() - 1)) + ".*$");
		}

		return Pattern.compile("^" + Pattern.quote(pattern) + "$");
	}

	private static List<Pattern> compileAll(List<String> patterns) {
		List<Pattern> compiled = new ArrayList<>(patterns.size());

		for (String pattern : patterns) {
			compiled.add(compile(pattern));
		}

		return List.copyOf(compiled);
	}

	private static boolean matchesAny(List<Pattern> patterns, String value) {
		if (patterns.isEmpty()) {
			return true;
		}

		for (Pattern pattern : patterns) {
			if (pattern.matcher(value).matches()) {
				return true;
			}
		}

		return false;
	}
}
