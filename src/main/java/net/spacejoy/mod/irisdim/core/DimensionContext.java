package net.spacejoy.mod.irisdim.core;

/**
 * An immutable description of "where the player currently is", handed to the rule matcher.
 *
 * <p>Three independent axes are exposed on purpose. Dimension <em>id</em> is the precise thing a
 * player usually wants to configure, but it is also the thing that mods and datapacks invent
 * endlessly. Dimension <em>type</em> id is the more stable axis: every dimension must reference a
 * registered dimension type, so a rule written against {@code minecraft:the_end} keeps applying to
 * any future end-style dimension a mod adds -- including dimensions whose ids did not exist when
 * the rule was written. The world key disambiguates identical dimension ids across servers/saves.
 *
 * @param dimension     the current dimension id, never {@code null}
 * @param dimensionType the id of the dimension's type, or {@code null} when it could not be read
 * @param worldKey      an opaque identifier of the loaded world ("singleplayer:Name",
 *                      "multiplayer:host:port"), or {@code null} when it could not be read
 */
public record DimensionContext(DimensionId dimension, DimensionId dimensionType, String worldKey) {
	public DimensionContext(DimensionId dimension) {
		this(dimension, null, null);
	}

	public DimensionContext {
		if (dimension == null) {
			dimension = DimensionId.unknown();
		}
	}

	public boolean hasDimensionType() {
		return dimensionType != null;
	}

	public boolean hasWorldKey() {
		return worldKey != null && !worldKey.isBlank();
	}

	/** Human-readable one-liner used in logs and in {@code /irisdimshaders status}. */
	public String describe() {
		StringBuilder builder = new StringBuilder(dimension.toString());

		if (dimensionType != null) {
			builder.append(" [type=").append(dimensionType).append(']');
		}

		if (hasWorldKey()) {
			builder.append(" [world=").append(worldKey).append(']');
		}

		return builder.toString();
	}
}
