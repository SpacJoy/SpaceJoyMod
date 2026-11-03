package net.spacejoy.mod.irisdim.core;

import java.util.Locale;
import java.util.Objects;

/**
 * A dimension identifier in the vanilla {@code namespace:path} form.
 *
 * <p>This type is the reason the mod keeps working on dimensions that did not exist when it was
 * written. Nothing in the rule engine keys off a fixed set of dimensions: a dimension is just an
 * opaque namespaced string, so {@code minecraft:the_nether}, a datapack dimension
 * ({@code mypack:skylands}) and a modded dimension ({@code twilightforest:twilight_forest}) are all
 * the same shape and all matchable by the same patterns.
 *
 * <p>Deliberately free of any Minecraft type -- see the note in {@code build.gradle.kts}.
 */
public record DimensionId(String namespace, String path) implements Comparable<DimensionId> {
	/** Namespace assumed when a bare path such as {@code the_nether} is written without one. */
	public static final String VANILLA_NAMESPACE = "minecraft";

	/** Placeholder used when a dimension id could not be resolved at all. */
	private static final DimensionId UNKNOWN = new DimensionId("irisdimensionshaders", "unknown");

	public DimensionId {
		Objects.requireNonNull(namespace, "namespace");
		Objects.requireNonNull(path, "path");
	}

	public static DimensionId of(String namespace, String path) {
		return new DimensionId(namespace, path);
	}

	/**
	 * Parses a {@code namespace:path} string, defaulting the namespace to {@code minecraft} when the
	 * colon is absent.
	 *
	 * @throws IllegalArgumentException if the input is null, blank or malformed
	 */
	public static DimensionId parse(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("dimension id must not be null");
		}

		String trimmed = raw.trim().toLowerCase(Locale.ROOT);

		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("dimension id must not be empty");
		}

		int colon = trimmed.indexOf(':');

		if (colon < 0) {
			return new DimensionId(VANILLA_NAMESPACE, trimmed);
		}

		if (colon == 0 || colon == trimmed.length() - 1) {
			throw new IllegalArgumentException("malformed dimension id: '" + raw + "'");
		}

		return new DimensionId(trimmed.substring(0, colon), trimmed.substring(colon + 1));
	}

	/** Lenient variant of {@link #parse(String)} that yields {@code null} instead of throwing. */
	public static DimensionId tryParse(String raw) {
		try {
			return parse(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A stable stand-in for "the dimension could not be determined". */
	public static DimensionId unknown() {
		return UNKNOWN;
	}

	/** {@code true} for the synthetic id returned by {@link #unknown()}. */
	public boolean isUnknown() {
		return UNKNOWN.equals(this);
	}

	@Override
	public String toString() {
		return namespace + ":" + path;
	}

	@Override
	public int compareTo(DimensionId other) {
		return toString().compareTo(other.toString());
	}
}
