package net.spacejoy.mod.irisdim.client;

import net.spacejoy.mod.irisdim.core.DimensionContext;
import net.spacejoy.mod.irisdim.core.DimensionId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Optional;

/**
 * Turns "what is the client doing this tick" into a stream of level events.
 *
 * <p>Polling instead of mixin-hooking is a considered choice. A mixin on {@code Minecraft#setLevel}
 * would fire earlier, but it would also be a mapping-sensitive, version-sensitive injection point -
 * exactly the kind of thing that breaks on someone else's modpack. Comparing the dimension id once
 * per tick costs two reference comparisons and, in exchange, works identically for vanilla
 * dimensions, datapack dimensions, dimensions added by mods that do not even exist yet, and
 * dimension changes performed by proxies such as Immersive Portals. Nothing here enumerates
 * dimensions; a dimension only has to exist for it to be observed.
 */
final class DimensionWatcher {
	enum Change {
		/** Nothing moved. */
		NONE,
		/** A level became available (world join, respawn into a new world, server transfer). */
		ENTER_WORLD,
		/** The current level's dimension id changed. */
		CHANGE_DIMENSION,
		/** The level went away (disconnect, quit to title). */
		EXIT_WORLD
	}

	/**
	 * @param change what happened
	 * @param context where the player is now; {@code null} for {@link Change#EXIT_WORLD}
	 */
	record Snapshot(Change change, DimensionContext context) {
	}

	private static final Snapshot NOTHING = new Snapshot(Change.NONE, null);

	private boolean inLevel;
	private DimensionId lastDimension;

	Snapshot poll(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null) {
			if (!inLevel) {
				return NOTHING;
			}

			inLevel = false;
			lastDimension = null;
			return new Snapshot(Change.EXIT_WORLD, null);
		}

		DimensionId dimension = toDimensionId(level.dimension().identifier());

		if (!inLevel) {
			inLevel = true;
			lastDimension = dimension;
			return new Snapshot(Change.ENTER_WORLD, contextFor(level, client, dimension));
		}

		if (!dimension.equals(lastDimension)) {
			lastDimension = dimension;
			return new Snapshot(Change.CHANGE_DIMENSION, contextFor(level, client, dimension));
		}

		return NOTHING;
	}

	/** Forgets the current level, so the next tick re-reports it as {@link Change#ENTER_WORLD}. */
	void reset() {
		inLevel = false;
		lastDimension = null;
	}

	/**
	 * Whether the watcher already knows about a level. {@code false} means the next poll will be an
	 * {@link Change#ENTER_WORLD} -- which is how the early-switch path tells a world join apart from
	 * a dimension change, since both arrive as a {@code Minecraft#updateLevelInEngines} call.
	 */
	boolean hasLevel() {
		return inLevel;
	}

	/**
	 * Marks the client's current level as already seen, so the next poll reports nothing.
	 *
	 * <p>Used by the early-switch path: it acts while {@code updateLevelInEngines} is running, a
	 * tick before the polling path would have noticed. Without this the tick path would see the
	 * same change again and queue a second application -- harmless with {@code reloadOnlyWhenChanged},
	 * but a duplicate reload of a 47 MB pack when that setting is off.
	 */
	void adopt(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null) {
			inLevel = false;
			lastDimension = null;
			return;
		}

		inLevel = true;
		lastDimension = toDimensionId(level.dimension().identifier());
	}

	/** Current context without disturbing the change tracking, or {@code null} when not in a level. */
	static DimensionContext currentContext(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null) {
			return null;
		}

		return contextFor(level, client, toDimensionId(level.dimension().identifier()));
	}

	private static DimensionContext contextFor(ClientLevel level, Minecraft client, DimensionId dimension) {
		return new DimensionContext(dimension, dimensionTypeId(level), worldKey(client));
	}

	private static DimensionId toDimensionId(Identifier location) {
		return DimensionId.of(location.getNamespace(), location.getPath());
	}

	/**
	 * The id of the dimension's registered <em>type</em>. This is the axis that keeps rules working
	 * for dimensions nobody has written yet: every dimension, modded or vanilla, must reference a
	 * dimension type, and the vanilla end/nether types are the ones mods reuse.
	 *
	 * <p>Failure is survivable -- a null type id simply disables rules that match on
	 * {@code dimensionTypes}.
	 */
	private static DimensionId dimensionTypeId(ClientLevel level) {
		try {
			Holder<DimensionType> holder = level.dimensionTypeRegistration();
			Optional<ResourceKey<DimensionType>> key = holder.unwrapKey();
			return key.map(resourceKey -> toDimensionId(resourceKey.identifier())).orElse(null);
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * A coarse identifier of the loaded world, so a rule can be scoped to one server. Rendered as
	 * {@code singleplayer:<level name>} or {@code multiplayer:<host>}. Absent on clients that expose
	 * neither (e.g. a LAN world being joined), which is why {@code worlds} matching is optional.
	 */
	private static String worldKey(Minecraft client) {
		try {
			if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
				return "singleplayer:" + client.getSingleplayerServer().getWorldData().getLevelName();
			}
		} catch (Throwable ignored) {
			// Fall through to the multiplayer probe.
		}

		try {
			if (client.getConnection() != null) {
				ServerData server = client.getConnection().getServerData();

				if (server != null && server.ip != null) {
					return "multiplayer:" + server.ip;
				}
			}
		} catch (Throwable ignored) {
			// Optional information.
		}

		return null;
	}
}
