package net.spacejoy.mod.irisdim.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Owns the {@code config/spacejoy-mod/irisdim/} directory.
 *
 * <pre>
 * config/spacejoy-mod/irisdim/
 *   config.json          rules + settings (hand-editable, comments live in "comment" fields)
 *   config.json.bak      previous contents, written before regenerating a corrupt file
 *   discovered.json      every dimension seen at runtime, with a ready-to-paste rule template
 * </pre>
 *
 * <p>Losing this directory is never fatal: the mod regenerates {@code config.json} from
 * {@link ModConfig#defaults()} and keeps the broken file as {@code config.json.bak} instead of
 * throwing away whatever the user had written.
 */
public final class ConfigStore {
	/** 主目录：config/spacejoy-mod/，与模组自己的 spacejoy-mod.json 放在同一个文件夹里。 */
	public static final String DIRECTORY_NAME = "spacejoy-mod";
	/** 子目录：config/spacejoy-mod/irisdim/。 */
	public static final String SUBDIRECTORY_NAME = "irisdim";
	public static final String CONFIG_FILE_NAME = "config.json";
	public static final String BACKUP_FILE_NAME = "config.json.bak";
	public static final String DISCOVERED_FILE_NAME = "discovered.json";

	private final Logger logger;
	private final Path directory;
	private final Path configPath;
	private final Path backupPath;
	private final Path discoveredPath;

	public ConfigStore(Path configDirectory, Logger logger) {
		this.logger = logger;
		this.directory = configDirectory.resolve(DIRECTORY_NAME).resolve(SUBDIRECTORY_NAME);
		this.configPath = directory.resolve(CONFIG_FILE_NAME);
		this.backupPath = directory.resolve(BACKUP_FILE_NAME);
		this.discoveredPath = directory.resolve(DISCOVERED_FILE_NAME);
	}

	public Path directory() {
		return directory;
	}

	public Path configPath() {
		return configPath;
	}

	public Path backupPath() {
		return backupPath;
	}

	public Path discoveredPath() {
		return discoveredPath;
	}

	/** Loads the config, creating a default one the first time. Never throws. */
	public ModConfig loadOrCreate() {
		if (!Files.isRegularFile(configPath)) {
			ModConfig config = ModConfig.defaults();

			try {
				save(config);
				logger.info("Wrote a default rule set to {} -- every rule starts as NONE, so nothing changes until you edit it.", configPath);
			} catch (RuntimeException e) {
				logger.error("Could not write the default config to {}", configPath, e);
			}

			return config;
		}

		try {
			String json = Files.readString(configPath, StandardCharsets.UTF_8);
			ModConfig config = ModConfig.fromJson(json);

			List<String> problems = config.validate();

			for (String problem : problems) {
				logger.warn("Config: {}", problem);
			}

			return config;
		} catch (IOException | RuntimeException e) {
			logger.error("Could not read {} -- regenerating defaults (the old file is kept as {}).", configPath, BACKUP_FILE_NAME, e);
			backupCorruptFile();

			ModConfig config = ModConfig.defaults();

			try {
				save(config);
			} catch (RuntimeException saveFailure) {
				logger.error("Could not write a replacement config either", saveFailure);
			}

			return config;
		}
	}

	/** Re-reads the file from disk; used by {@code /irisdimensionshaders reload}. */
	public ModConfig reload() {
		return loadOrCreate();
	}

	/**
	 * Writes the config atomically: a temp file is moved over the target, so an interrupted write
	 * can never leave a half-written JSON behind for the next launch to trip over.
	 */
	public void save(ModConfig config) {
		try {
			Files.createDirectories(directory);

			Path temp = configPath.resolveSibling(CONFIG_FILE_NAME + ".tmp");
			Files.writeString(temp, config.toJson(), StandardCharsets.UTF_8);
			moveIntoPlace(temp, configPath);
		} catch (IOException e) {
			throw new IllegalStateException("could not save " + configPath, e);
		}
	}

	private void backupCorruptFile() {
		if (!Files.isRegularFile(configPath)) {
			return;
		}

		try {
			Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			logger.warn("Could not back up the unreadable config to {}", backupPath, e);
		}
	}

	private static void moveIntoPlace(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException atomicUnsupported) {
			// Some filesystems (and some network drives) refuse ATOMIC_MOVE; a plain replace is
			// still strictly better than writing in place.
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
