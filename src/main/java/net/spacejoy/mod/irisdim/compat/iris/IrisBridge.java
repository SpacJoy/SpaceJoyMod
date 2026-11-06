package net.spacejoy.mod.irisdim.compat.iris;

import net.spacejoy.mod.irisdim.core.ShaderAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The only class in this mod that knows anything about Iris.
 *
 * <h2>Why reflection</h2>
 * Iris publishes an API ({@code net.irisshaders.iris.api.v0.IrisApi}) and it is deliberately small:
 * it can tell you whether a pack is in use, open the selection screen, and enable or disable shaders
 * -- but it cannot select a <em>different</em> pack. Selecting a pack is done inside Iris's own GUI
 * ({@code ShaderPackScreen#applyChanges}) and is not exposed. A mod that wants the same effect
 * therefore has two options: link against Iris internals and break whenever Iris reshuffles them, or
 * reach the internals reflectively and degrade gracefully when a member moves. This class does the
 * latter, and the symbol table below is the whole contract.
 *
 * <h2>Symbol table (verified against Iris 1.11.7+mc26.3 with javap)</h2>
 * <pre>
 * net.irisshaders.iris.Iris
 *   getIrisConfig()                        -> IrisConfig        (required)
 *   getCurrentPackName()                   -> String            (soft: no-op detection)
 *   isFallback()                           -> boolean           (soft: "Iris gave up" detection)
 *   getShaderpacksDirectory()              -> Path              (soft: pack validation/listing)
 *   getShaderPackOptionQueue()             -> Map&lt;String,String&gt; (soft: per-dimension options)
 *   clearShaderPackOptionQueue()           -> void              (soft)
 *   reload()                               -> void (throws IOException)  (fallback apply path)
 * net.irisshaders.iris.config.IrisConfig
 *   setShaderPackName(String)              -> void              (required for SET_PACK)
 *   setShadersEnabled(boolean)             -> void              (fallback apply path)
 *   areShadersEnabled()                    -> boolean           (soft)
 *   save()                                 -> void (throws IOException)  (required: Iris#reload
 *                                                                re-reads the file from disk)
 * net.irisshaders.iris.api.v0.IrisApi       (preferred apply path)
 *   getInstance() / getConfig() / IrisApiConfig#setShadersEnabledAndApply(boolean)
 * net.irisshaders.iris.gui.option.IrisVideoSettings
 *   static int shadowDistance                                  (soft: per-dimension shadow distance)
 * </pre>
 *
 * <p>Every member is resolved independently. A missing <em>soft</em> member disables only the feature
 * that needs it: no {@code getShaderpacksDirectory()} means the mod cannot pre-validate pack names,
 * not that it stops working.
 *
 * <h2>Threading</h2>
 * {@link #apply(ShaderAction)} must be called on the client thread. It ends up inside
 * {@code Iris#reload()}, which destroys and rebuilds the whole shader pipeline against the current
 * GL context.
 */
public final class IrisBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("SpaceJoyMod/Iris");

	private static final String IRIS_CLASS = "net.irisshaders.iris.Iris";
	private static final String IRIS_CONFIG_CLASS = "net.irisshaders.iris.config.IrisConfig";
	private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
	private static final String IRIS_VIDEO_SETTINGS_CLASS = "net.irisshaders.iris.gui.option.IrisVideoSettings";

	private static final String INTERNAL_PACK_NAME = "(internal)";

	/**
	 * What {@code Iris#setShadersDisabled()} writes into {@code currentPackName}.
	 *
	 * <p>This is not a pack. It is Iris's marker for "no pack is loaded", and it is a plain string
	 * rather than {@code null} only because the field has always been written that way. Filtering it
	 * is load-bearing twice over: {@link #currentPackName()} would otherwise report a pack called
	 * "(off)" that no rule can match, and {@link #isAlreadyApplied} would think every rule had
	 * changed state. Worse, {@code IrisConfig#setShaderPackName} does <em>not</em> recognise this
	 * value -- it only normalises {@code null}, {@code "(internal)"} and the empty string -- so
	 * restoring a remembered "(off)" would write a genuine shaderpack named {@code (off)} and fail
	 * to load it.
	 */
	private static final String SHADERS_OFF_PACK_NAME = "(off)";

	private static final String SHADERPACK_SUBDIRECTORY = "shaders";

	private static boolean resolved;
	private static String unavailableReason = "not resolved yet";

	private static Class<?> irisClass;
	private static Method getIrisConfig;
	private static Method getCurrentPackName;
	private static Method irisIsFallback;
	private static Method getShaderpacksDirectory;
	private static Method getShaderPackOptionQueue;
	private static Method clearShaderPackOptionQueue;
	private static Method reload;

	private static Method configSetShaderPackName;
	private static Method configSetShadersEnabled;
	private static Method configAreShadersEnabled;
	private static Method configSave;

	private static Method apiGetInstance;
	private static Method apiGetConfig;
	private static Method apiAreShadersEnabled;
	private static Method apiSetShadersEnabledAndApply;

	private static Field shadowDistanceField;

	private IrisBridge() {
	}

	// ---------------------------------------------------------------------------------------
	// Discovery
	// ---------------------------------------------------------------------------------------

	/** {@code true} when the members needed to change Iris state were all found. */
	public static synchronized boolean isAvailable() {
		resolve();
		return irisClass != null && getIrisConfig != null && configSetShaderPackName != null
			&& (apiSetShadersEnabledAndApply != null || (configSave != null && reload != null));
	}

	/** Why {@link #isAvailable()} is false; empty when it is true. */
	public static synchronized String unavailableReason() {
		resolve();
		return isAvailable() ? "" : unavailableReason;
	}

	private static synchronized void resolve() {
		if (resolved) {
			return;
		}

		resolved = true;

		try {
			irisClass = Class.forName(IRIS_CLASS);
		} catch (Throwable t) {
			unavailableReason = "Iris is not present on the classpath";
			return;
		}

		getIrisConfig = findMethod(irisClass, "getIrisConfig");

		if (getIrisConfig == null) {
			unavailableReason = IRIS_CLASS + "#getIrisConfig() not found -- Iris internals changed";
			return;
		}

		getCurrentPackName = findMethod(irisClass, "getCurrentPackName");
		irisIsFallback = findMethod(irisClass, "isFallback");
		getShaderpacksDirectory = findMethod(irisClass, "getShaderpacksDirectory");
		getShaderPackOptionQueue = findMethod(irisClass, "getShaderPackOptionQueue");
		clearShaderPackOptionQueue = findMethod(irisClass, "clearShaderPackOptionQueue");
		reload = findMethod(irisClass, "reload");

		Class<?> configClass = resolveConfigClass();

		if (configClass != null) {
			configSetShaderPackName = findMethod(configClass, "setShaderPackName", String.class);
			configSetShadersEnabled = findMethod(configClass, "setShadersEnabled", boolean.class);
			configAreShadersEnabled = findMethod(configClass, "areShadersEnabled");
			configSave = findMethod(configClass, "save");
		}

		resolvePublicApi();
		resolveVideoSettings();

		if (getCurrentPackName == null) {
			LOGGER.warn("Iris#getCurrentPackName() not found; redundant shader reloads can no longer be skipped");
		}

		if (getShaderpacksDirectory == null) {
			LOGGER.warn("Iris#getShaderpacksDirectory() not found; shaderpack names will not be pre-validated");
		}

		if (apiSetShadersEnabledAndApply == null && (configSave == null || reload == null)) {
			LOGGER.warn("Neither IrisApi#setShadersEnabledAndApply nor the IrisConfig+reload fallback is available");
		}
	}

	private static Class<?> resolveConfigClass() {
		try {
			return Class.forName(IRIS_CONFIG_CLASS);
		} catch (Throwable ignored) {
			// Older/newer Iris may relocate the class; the accessor's return type is the next best
			// guess and costs nothing.
			Class<?> returnType = getIrisConfig.getReturnType();
			return returnType == Object.class ? null : returnType;
		}
	}

	private static void resolvePublicApi() {
		Class<?> api;

		try {
			api = Class.forName(IRIS_API_CLASS);
		} catch (Throwable t) {
			return;
		}

		apiGetInstance = findMethod(api, "getInstance");
		apiGetConfig = findMethod(api, "getConfig");

		if (apiGetInstance == null || apiGetConfig == null) {
			apiGetInstance = null;
			apiGetConfig = null;
			return;
		}

		apiSetShadersEnabledAndApply = findMethod(apiGetConfig.getReturnType(), "setShadersEnabledAndApply", boolean.class);
		apiAreShadersEnabled = findMethod(apiGetConfig.getReturnType(), "areShadersEnabled");
	}

	private static void resolveVideoSettings() {
		try {
			Class<?> videoSettings = Class.forName(IRIS_VIDEO_SETTINGS_CLASS);
			Field field = videoSettings.getField("shadowDistance");

			if (field.getType() == int.class) {
				shadowDistanceField = field;
			}
		} catch (Throwable ignored) {
			// Optional feature.
		}
	}

	private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
		try {
			Method method = owner.getMethod(name, parameterTypes);
			method.setAccessible(true);
			return method;
		} catch (Throwable t) {
			return null;
		}
	}

	// ---------------------------------------------------------------------------------------
	// Read-only queries
	// ---------------------------------------------------------------------------------------

	/**
	 * The pack Iris currently has loaded, or empty when shaders are off or the pack is internal.
	 *
	 * <p>Note what "off" looks like from the inside: {@code Iris#setShadersDisabled()} leaves the
	 * name as the literal {@code "(off)"} rather than clearing it, so this method has to treat that
	 * marker as "no pack" -- see {@link #SHADERS_OFF_PACK_NAME}.
	 */
	public static Optional<String> currentPackName() {
		if (getCurrentPackName == null || !isAvailable()) {
			return Optional.empty();
		}

		try {
			Object value = getCurrentPackName.invoke(null);

			if (!(value instanceof String name) || name.isBlank()
				|| INTERNAL_PACK_NAME.equals(name) || SHADERS_OFF_PACK_NAME.equals(name)) {
				return Optional.empty();
			}

			return Optional.of(name);
		} catch (Throwable t) {
			return Optional.empty();
		}
	}

	/**
	 * Whether Iris has fallen back to unshaded rendering after failing to load the selected pack.
	 *
	 * <p>Worth distinguishing from "shaders disabled": in the fallback state Iris has already
	 * logged the real error and given up, so a rule that asks for a shaderpack cannot be satisfied
	 * by any code path here. Reporting that honestly is more useful than a reload that silently
	 * lands on the same broken state.
	 */
	public static boolean isFallback() {
		if (irisIsFallback == null || !isAvailable()) {
			return false;
		}

		try {
			return Boolean.TRUE.equals(irisIsFallback.invoke(null));
		} catch (Throwable t) {
			return false;
		}
	}

	/** Whether Iris has shaders switched on. */
	public static boolean shadersEnabled() {
		if (!isAvailable()) {
			return false;
		}

		try {
			if (apiGetInstance != null && apiGetConfig != null && apiAreShadersEnabled != null) {
				Object api = apiGetInstance.invoke(null);
				Object apiConfig = apiGetConfig.invoke(api);

				if (apiConfig != null) {
					return Boolean.TRUE.equals(apiAreShadersEnabled.invoke(apiConfig));
				}
			}

			Object config = irisConfig();

			if (config != null && configAreShadersEnabled != null) {
				return Boolean.TRUE.equals(configAreShadersEnabled.invoke(config));
			}
		} catch (Throwable t) {
			LOGGER.warn("Could not read the Iris shader state", t);
		}

		return false;
	}

	/** {@code shaderpacks/}, or empty when Iris has not initialised it yet. */
	public static Optional<Path> shaderpacksDirectory() {
		if (getShaderpacksDirectory == null || !isAvailable()) {
			return Optional.empty();
		}

		try {
			Object value = getShaderpacksDirectory.invoke(null);
			return value instanceof Path path ? Optional.of(path) : Optional.empty();
		} catch (Throwable t) {
			return Optional.empty();
		}
	}

	/**
	 * The shaderpacks Iris would list, in Iris's own order.
	 *
	 * <p>Names are the raw file names including the {@code .zip} extension, because that is the exact
	 * string {@code Iris#loadExternalShaderpack} resolves against {@code shaderpacks/}, and therefore
	 * the string a rule has to contain. Mirrors {@code ShaderpackDirectoryManager#enumerate}: keep
	 * anything that is a directory or ends in {@code .zip}, then sort case-insensitively with
	 * formatting codes stripped, then naturally -- so a pack whose file name starts with {@code §r§l}
	 * sorts by the letters that follow, exactly as it does in Iris's list.
	 */
	public static List<String> availablePacks() {
		Path directory = shaderpacksDirectory().orElse(null);

		if (directory == null || !Files.isDirectory(directory)) {
			return List.of();
		}

		return listPacksIn(directory);
	}

	/**
	 * The directory-in, names-out half of {@link #availablePacks()}, split out so the scan can be
	 * exercised against a real {@code shaderpacks/} folder without a running Iris.
	 */
	static List<String> listPacksIn(Path directory) {
		try (Stream<Path> entries = Files.list(directory)) {
			return entries
				.filter(IrisBridge::isListablePack)
				.sorted(PACK_ORDER)
				.map(path -> path.getFileName().toString())
				.collect(Collectors.toList());
		} catch (IOException e) {
			LOGGER.warn("Could not list the shaderpacks directory", e);
			return List.of();
		}
	}

	/**
	 * Name of the pack that would load if {@code name} failed with "not a valid pack", or empty.
	 *
	 * <p>Exists because the {@code .zip} extension is part of the name Iris resolves, which is easy
	 * to leave off when writing a rule by hand -- and the failure mode is silent: Iris logs an error
	 * and quietly falls back to no shaders at all.
	 */
	public static Optional<String> suggestZipVariant(String name) {
		if (name == null || name.isBlank() || name.endsWith(".zip")) {
			return Optional.empty();
		}

		return suggestZipVariantIn(shaderpacksDirectory().orElse(null), name);
	}

	static Optional<String> suggestZipVariantIn(Path directory, String name) {
		if (directory == null || name == null || name.isBlank() || name.endsWith(".zip")) {
			return Optional.empty();
		}

		String candidate = name + ".zip";
		return isPackInstalledIn(directory, candidate) ? Optional.of(candidate) : Optional.empty();
	}

	/**
	 * Whether {@code name} resolves to a shaderpack Iris would actually load.
	 *
	 * <p>Mirrors {@code Iris#isValidShaderpack}: a folder pack must contain a {@code shaders}
	 * directory, and a zip is accepted when a directory named {@code shaders} exists <em>anywhere</em>
	 * in the archive -- not just at the root. That distinction is not pedantry: several widely used
	 * packs (Bliss, Eclipse) wrap everything in one top-level folder, so requiring a root-level
	 * {@code shaders/} would report a perfectly good pack as missing.
	 */
	public static boolean isPackInstalled(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}

		Path directory = shaderpacksDirectory().orElse(null);

		if (directory == null) {
			// Cannot verify -- assume the user knows what they wrote, and let Iris report failure.
			return true;
		}

		return isPackInstalledIn(directory, name);
	}

	/** The directory-in, verdict-out half of {@link #isPackInstalled(String)}; see {@link #listPacksIn}. */
	static boolean isPackInstalledIn(Path directory, String name) {
		try {
			return isValidShaderpack(directory.resolve(name), directory);
		} catch (InvalidPathException e) {
			return false;
		}
	}

	/**
	 * Iris's ordering: case-insensitive first (so {@code bsl} and {@code BSL} do not alternate), then
	 * natural (so the more sensible of the two wins where case-insensitive compares equal).
	 */
	private static final Comparator<String> NAME_ORDER =
		String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

	private static final Comparator<Path> PACK_ORDER = (a, b) -> NAME_ORDER.compare(
		stripFormatting(a.getFileName().toString()),
		stripFormatting(b.getFileName().toString()));

	/** {@code Iris#isValidToShowPack}: a directory, or anything ending in {@code .zip}. */
	private static boolean isListablePack(Path path) {
		return Files.isDirectory(path) || path.getFileName().toString().endsWith(".zip");
	}

	/**
	 * {@code ShaderpackDirectoryManager#removeFormatting}: drops section-sign formatting codes so
	 * that the sort order is driven by the readable name. Some packs put {@code §r§l} at the front
	 * of their file name purely to look fancy in Iris's list.
	 */
	private static String stripFormatting(String formatted) {
		StringBuilder cleaned = new StringBuilder(formatted.length());

		for (int index = 0; index < formatted.length(); index++) {
			if (formatted.charAt(index) == '\u00a7') {
				index++;
			} else {
				cleaned.append(formatted.charAt(index));
			}
		}

		return cleaned.toString();
	}

	private static boolean isValidShaderpack(Path pack, Path shaderpacksDirectory) {
		if (Files.isDirectory(pack)) {
			return !pack.equals(shaderpacksDirectory) && Files.exists(pack.resolve(SHADERPACK_SUBDIRECTORY));
		}

		if (!Files.isRegularFile(pack) || !pack.getFileName().toString().endsWith(".zip")) {
			return false;
		}

		try (ZipFile file = new ZipFile(pack.toFile())) {
			Enumeration<? extends ZipEntry> entries = file.entries();

			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String entryName = entry.getName();

				// Directories may be explicit entries or may be absent entirely, depending on the
				// archiver; a file inside shaders/ is proof either way. The boundary checks keep
				// "myshaders/" from being mistaken for "shaders/".
				if (entryName.startsWith(SHADERPACK_SUBDIRECTORY + "/") || entryName.contains("/" + SHADERPACK_SUBDIRECTORY + "/")) {
					return true;
				}

				String trimmed = entryName.endsWith("/") ? entryName.substring(0, entryName.length() - 1) : entryName;

				if (trimmed.equals(SHADERPACK_SUBDIRECTORY) || trimmed.endsWith("/" + SHADERPACK_SUBDIRECTORY)) {
					return true;
				}
			}
		} catch (IOException e) {
			return false;
		}

		return false;
	}

	/**
	 * Whether Iris is already in the state this action asks for. Used to avoid reloading a 300 MB
	 * shaderpack because the player walked through a Nether portal twice.
	 *
	 * <p>The fallback state is deliberately <em>not</em> treated as applied. Iris raises its
	 * fallback flag when a shaderpack fails to compile and then keeps it: its own source carries
	 * a TODO saying the flag should be cleared once a dimension change makes the shaders compile
	 * again, and that never happens. Reporting "already applied" there would make the one state
	 * that needs a retry the one state that can never get one.
	 */
	public static boolean isAlreadyApplied(ShaderAction action) {
		if (!isAvailable() || action == null) {
			return false;
		}

		// NONE is exempt: it asks for nothing to change, so the fallback state is irrelevant to it.
		if (action.mode() != ShaderAction.Mode.NONE && isFallback()) {
			return false;
		}

		return switch (action.mode()) {
			case NONE -> true;
			case DISABLE_SHADERS -> !shadersEnabled();
			case SET_PACK -> {
				if (!action.packName().equals(currentPackName().orElse(null))) {
					yield false;
				}

				// Option overrides are only guaranteed to take effect through a reload, so a rule
				// that carries them is never treated as "already applied" unless they are absent.
				if (!action.options().isEmpty()) {
					yield false;
				}

				yield action.shadowDistance() == null || action.shadowDistance() == currentShadowDistance();
			}
		};
	}

	private static int currentShadowDistance() {
		if (shadowDistanceField == null) {
			return Integer.MIN_VALUE;
		}

		try {
			return shadowDistanceField.getInt(null);
		} catch (Throwable t) {
			return Integer.MIN_VALUE;
		}
	}

	/**
	 * Whether {@code action} is already satisfied except for its shadow distance -- same pack, no
	 * option overrides, only that one integer differs.
	 *
	 * <p>Such an action does not deserve a reload. Iris reads {@code IrisVideoSettings.shadowDistance}
	 * every frame (it sizes the shadow frustum and decides whether shadows run at all from it), and
	 * its own video-settings slider changes it without rebuilding anything. Reloading a shaderpack
	 * to change one field means re-parsing every shader file and recompiling the whole pipeline --
	 * minutes of work in the worst case -- for a value that takes effect on the next frame either way.
	 */
	public static boolean differsOnlyInShadowDistance(ShaderAction action) {
		if (!isAvailable() || action == null || shadowDistanceField == null) {
			return false;
		}

		if (action.mode() != ShaderAction.Mode.SET_PACK || isFallback()) {
			return false;
		}

		if (!action.packName().equals(currentPackName().orElse(null)) || !action.options().isEmpty()) {
			return false;
		}

		return action.shadowDistance() != null && action.shadowDistance() != currentShadowDistance();
	}

	/**
	 * Writes the shadow distance in place, without touching the pipeline.
	 *
	 * <p>The config file is saved best-effort afterwards, because {@code Iris#reload()} re-reads
	 * {@code iris.properties} from disk: an unsaved value would be silently reverted by the next
	 * pack change, and this rule would then "differ" again on every single dimension switch.
	 */
	public static boolean applyShadowDistanceInPlace(ShaderAction action) {
		if (!differsOnlyInShadowDistance(action)) {
			return false;
		}

		try {
			shadowDistanceField.setInt(null, action.shadowDistance());

			Object config = irisConfig();

			if (configSave != null && config != null) {
				configSave.invoke(config);
			}

			return true;
		} catch (Throwable t) {
			LOGGER.warn("Could not set the shadow distance in place: {}", t.toString());
			return false;
		}
	}

	/** The live shadow distance, or {@code null} when Iris does not expose it. */
	public static Integer shadowDistance() {
		int value = currentShadowDistance();
		return value == Integer.MIN_VALUE ? null : value;
	}

	// ---------------------------------------------------------------------------------------
	// Mutation
	// ---------------------------------------------------------------------------------------

	/**
	 * Brings Iris into the state described by {@code action}.
	 *
	 * <p>Mirrors the sequence Iris's own shader selection screen performs on "Apply", because that
	 * sequence is the only one Iris actually supports:
	 * <ol>
	 *   <li>drop queued options if the pack is about to change (otherwise one pack's option values
	 *       leak into another pack's option ids),</li>
	 *   <li>queue this rule's option overrides,</li>
	 *   <li>write the new pack name into {@code IrisConfig},</li>
	 *   <li>call {@code setShadersEnabledAndApply(true)} -- which saves the config and calls
	 *       {@code Iris#reload()}. The save is not optional: {@code reload()} re-reads the file from
	 *       disk first, so an unsaved pack name would be silently discarded.</li>
	 * </ol>
	 */
	public static IrisApplyResult apply(ShaderAction action) {
		if (action == null || action.isNoop()) {
			return IrisApplyResult.success("no change requested");
		}

		if (!isAvailable()) {
			return IrisApplyResult.failure("Iris internals unavailable: " + unavailableReason());
		}

		Object config = irisConfig();

		if (config == null) {
			return IrisApplyResult.failure("Iris is not ready yet (no IrisConfig instance)");
		}

		try {
			applyShadowDistance(action);

			if (action.mode() == ShaderAction.Mode.DISABLE_SHADERS) {
				setShadersEnabledAndApply(false);
				return IrisApplyResult.success("shaders disabled");
			}

			String target = action.packName();
			String current = currentPackName().orElse(null);
			boolean packChanged = !target.equals(current);

			if (packChanged) {
				invokeStatic(clearShaderPackOptionQueue);
			}

			if (!action.options().isEmpty()) {
				Map<String, String> queue = optionQueue();

				if (queue == null) {
					return IrisApplyResult.failure("Iris option queue unavailable; cannot apply option overrides");
				}

				if (packChanged) {
					queue.clear();
				}

				queue.putAll(action.options());
			}

			configSetShaderPackName.invoke(config, target);
			setShadersEnabledAndApply(true);

			String suffix = action.options().isEmpty() ? "" : " with " + action.options().size() + " option override(s)";
			return IrisApplyResult.success("shaderpack -> " + target + suffix);
		} catch (Throwable t) {
			LOGGER.error("Failed to apply shader action: {}", action.describe(), t);
			Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
			return IrisApplyResult.failure("Iris rejected the change: " + cause);
		}
	}

	private static void applyShadowDistance(ShaderAction action) throws IllegalAccessException {
		if (action.shadowDistance() == null || shadowDistanceField == null) {
			return;
		}

		int target = action.shadowDistance();

		if (shadowDistanceField.getInt(null) != target) {
			shadowDistanceField.setInt(null, target);
		}
	}

	/**
	 * Prefers the public API path so that Iris owns its own save+reload sequence, and falls back to
	 * replaying that sequence by hand when the API method is absent.
	 */
	private static void setShadersEnabledAndApply(boolean enabled) throws Exception {
		if (apiSetShadersEnabledAndApply != null && apiGetConfig != null && apiGetInstance != null) {
			Object api = apiGetInstance.invoke(null);
			Object apiConfig = apiGetConfig.invoke(api);

			if (apiConfig != null) {
				apiSetShadersEnabledAndApply.invoke(apiConfig, enabled);
				return;
			}
		}

		Object config = irisConfig();

		if (configSetShadersEnabled != null && config != null) {
			configSetShadersEnabled.invoke(config, enabled);
		}

		if (configSave != null && config != null) {
			configSave.invoke(config);
		}

		invokeStatic(reload);
	}

	private static Map<String, String> optionQueue() {
		if (getShaderPackOptionQueue == null) {
			return null;
		}

		try {
			Object value = getShaderPackOptionQueue.invoke(null);

			if (value instanceof Map<?, ?> map) {
				@SuppressWarnings("unchecked")
				Map<String, String> typed = (Map<String, String>) map;
				return typed;
			}
		} catch (Throwable t) {
			LOGGER.warn("Could not reach the Iris shader option queue", t);
		}

		return null;
	}

	private static Object irisConfig() {
		try {
			return getIrisConfig.invoke(null);
		} catch (Throwable t) {
			return null;
		}
	}

	private static void invokeStatic(Method method) throws Exception {
		if (method != null) {
			method.invoke(null);
		}
	}
}
