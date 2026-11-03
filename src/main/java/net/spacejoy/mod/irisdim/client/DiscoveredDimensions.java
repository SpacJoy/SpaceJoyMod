package net.spacejoy.mod.irisdim.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.spacejoy.mod.irisdim.config.RuleSpec;
import net.spacejoy.mod.irisdim.core.DimensionContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime census of every dimension the player has ever entered, written to
 * {@code config/spacejoy-mod/irisdim/discovered.json}.
 *
 * <p>This is the answer to "how do I configure a dimension I have never heard of?" -- the mod cannot
 * invent a shaderpack choice for an unknown dimension, but it can hand the user the exact strings
 * needed to write one. Each entry carries the dimension id, its dimension <em>type</em> id and a
 * complete rule template that can be pasted straight into {@code config.json}.
 *
 * <p>Writes are deferred to {@link #saveIfDirty()} and happen off the hot path, so entering a
 * dimension never blocks on disk I/O.
 */
public final class DiscoveredDimensions {
	private static final int SCHEMA_VERSION = 1;
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create();

	/** On-disk shape: a versioned wrapper so the file can gain fields without breaking readers. */
	private static final class FileModel {
		int schemaVersion = SCHEMA_VERSION;
		Map<String, Entry> dimensions = new LinkedHashMap<>();
	}

	/** Readymade strings for one dimension, including a paste-ready rule. */
	public static final class Entry {
		public String dimension;
		public String dimensionType;
		public String worldKey;
		public String firstSeen;
		public String lastSeen;
		public int seen;
		public String suggestedRule;
	}

	private final Path path;
	private final Logger logger;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	private boolean dirty;

	public DiscoveredDimensions(Path path, Logger logger) {
		this.path = path;
		this.logger = logger;
		load();
	}

	public Path path() {
		return path;
	}

	public Collection<Entry> entries() {
		return entries.values();
	}

	public int size() {
		return entries.size();
	}

	/** One-line census for the {@code /spacejoydim dims} command. */
	public String summary() {
		if (entries.isEmpty()) {
			return "No dimensions seen yet.";
		}

		StringBuilder builder = new StringBuilder(entries.size() + " dimension(s) seen:");

		for (Entry entry : entries.values()) {
			builder.append("\n  ").append(entry.dimension)
				.append(" x").append(entry.seen)
				.append(entry.dimensionType == null ? "" : " (type " + entry.dimensionType + ")");
		}

		return builder.toString();
	}

	/**
	 * Records an observation.
	 *
	 * @return {@code true} when this dimension had not been seen before, which the caller turns into
	 *         a one-line log message telling the user where to look
	 */
	public boolean record(DimensionContext context) {
		if (context == null || context.dimension().isUnknown()) {
			return false;
		}

		String key = context.dimension().toString();
		String now = ZonedDateTime.now().format(TIMESTAMP);
		Entry entry = entries.get(key);

		if (entry != null) {
			entry.seen++;
			entry.lastSeen = now;

			if (entry.dimensionType == null && context.hasDimensionType()) {
				entry.dimensionType = context.dimensionType().toString();
				entry.suggestedRule = template(context);
			}

			if (entry.worldKey == null && context.hasWorldKey()) {
				entry.worldKey = context.worldKey();
			}

			dirty = true;
			return false;
		}

		Entry created = new Entry();
		created.dimension = key;
		created.dimensionType = context.hasDimensionType() ? context.dimensionType().toString() : null;
		created.worldKey = context.hasWorldKey() ? context.worldKey() : null;
		created.firstSeen = now;
		created.lastSeen = now;
		created.seen = 1;
		created.suggestedRule = template(context);

		entries.put(key, created);
		dirty = true;
		return true;
	}

	/** Rule template for a dimension: disabled by default, so pasting it changes nothing yet. */
	private static String template(DimensionContext context) {
		RuleSpec spec = new RuleSpec();
		spec.id = context.dimension().namespace() + "-" + context.dimension().path().replace('/', '-');
		spec.priority = 50;
		spec.enabled = false;
		spec.comment = "Discovered " + ZonedDateTime.now().format(TIMESTAMP)
			+ ". Set shader.pack to a name from shaderpacks/ and flip enabled to true.";
		spec.match.dimensions = List.of(context.dimension().toString());
		spec.shader.mode = "SET_PACK";
		spec.shader.pack = "";
		return GSON.toJson(spec);
	}

	public void saveIfDirty() {
		if (!dirty) {
			return;
		}

		dirty = false;

		try {
			Files.createDirectories(path.getParent());

			FileModel model = new FileModel();
			model.schemaVersion = SCHEMA_VERSION;
			model.dimensions = entries;

			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(model), StandardCharsets.UTF_8);

			try {
				Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException atomicUnsupported) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			logger.warn("Could not write {} -- discovered dimensions will not survive this session", path, e);
		}
	}

	private void load() {
		if (!Files.isRegularFile(path)) {
			return;
		}

		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			FileModel model = GSON.fromJson(json, FileModel.class);

			if (model != null && model.dimensions != null) {
				entries.putAll(model.dimensions);
			}
		} catch (IOException | RuntimeException e) {
			// Purely informational file: a broken one is rewritten on the next dimension change.
			logger.warn("Could not read {} -- it will be recreated", path, e);
		}
	}
}
