package net.spacejoy.mod.irisdim.client;

import net.spacejoy.mod.irisdim.compat.iris.IrisApplyResult;
import net.spacejoy.mod.irisdim.compat.iris.IrisBackendSupport;
import net.spacejoy.mod.irisdim.compat.iris.IrisBridge;
import net.spacejoy.mod.irisdim.config.ConfigStore;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.irisdim.config.ModConfig;
import net.spacejoy.mod.irisdim.config.ModSettings;
import net.spacejoy.mod.irisdim.core.DimensionContext;
import net.spacejoy.mod.irisdim.core.DimensionRule;
import net.spacejoy.mod.irisdim.core.RuleSet;
import net.spacejoy.mod.irisdim.core.ShaderAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The state machine that decides <em>when</em> a rule may be applied.
 *
 * <h2>Why a delay at all</h2>
 * A dimension change is not one event. The dimension id flips immediately, then the client sits
 * behind a loading overlay while the server builds spawn chunks and the client rebuilds its chunk
 * cache. Calling {@code Iris#reload()} inside that window is the worst possible moment: Iris tears
 * down the GL pipeline and recompiles every program while the level is still streaming in, which
 * shows up to the player as a frozen screen followed by a long stutter. So the controller waits
 * {@code applyDelayTicks}, then additionally refuses to fire while a {@link LoadingOverlay} is up,
 * for up to {@link #READY_TIMEOUT_TICKS}.
 *
 * <h2>Why a delay is not enough on its own</h2>
 * Rules are compared against the live Iris state before anything happens
 * ({@link IrisBridge#isAlreadyApplied}), because a shaderpack reload is measured in seconds and
 * players walk through Nether portals casually. Re-entering a dimension whose pack is already
 * loaded costs nothing at all.
 */
public final class DimensionSwitchController {
	/** Stop waiting for the loading overlay after 30 seconds and give up on this change. */
	private static final int READY_TIMEOUT_TICKS = 20 * 30;

	private final ConfigStore store;
	private final DiscoveredDimensions discovered;
	private final DimensionWatcher watcher = new DimensionWatcher();
	private final Logger logger;
	private final Consumer<String> announcer;

	private ModConfig config;
	private RuleSet ruleSet;
	private List<String> configWarnings = List.of();

	private DimensionContext pending;
	private String pendingReason = "";
	private int waitTicks;
	private int overdueTicks;
	private String packBeforeWorld;
	/** Keeps the "wrong graphics backend" line to one entry per session instead of one per portal. */
	private boolean backendWarningLogged;

	public DimensionSwitchController(ConfigStore store, ModConfig config, Logger logger, Consumer<String> announcer) {
		this.store = store;
		this.logger = logger;
		this.announcer = announcer;
		this.discovered = new DiscoveredDimensions(store.discoveredPath(), logger);
		install(config);
	}

	// ---------------------------------------------------------------------------------------
	// Wiring
	// ---------------------------------------------------------------------------------------

	public void onEndTick(Minecraft client) {
		ModSettings settings = config.settings;
		DimensionWatcher.Snapshot snapshot = watcher.poll(client);

		switch (snapshot.change()) {
			case EXIT_WORLD -> handleWorldExit(settings);
			case ENTER_WORLD -> handleWorldEnter(settings, snapshot.context());
			case CHANGE_DIMENSION -> {
				observe(settings, snapshot.context());

				if (settings.applyOnDimensionChange) {
					schedule(settings, snapshot.context(), "dimension change");
				}
			}
			case NONE -> {
				// Nothing moved; fall through to the pending-apply check below.
			}
		}

		if (pending == null) {
			return;
		}

		if (waitTicks > 0) {
			waitTicks--;
			return;
		}

		if (!canApplyYet(client)) {
			if (--overdueTicks < -READY_TIMEOUT_TICKS) {
				logger.warn("Level never finished loading; abandoning the shader change for {}", pending.describe());
				pending = null;
				discovered.saveIfDirty();
			}

			return;
		}

		applyPending(client);
		discovered.saveIfDirty();
	}

	/** Re-reads {@code config.json} from disk and recompiles the rules. */
	public void reloadConfig() {
		install(store.reload());
	}

	/**
	 * Writes {@code newConfig} to disk and adopts it immediately.
	 *
	 * <p>This is what the config screen calls on "Save &amp; apply": the file is written first so the
	 * on-disk state and the running state cannot diverge, and adopting it in the same breath means a
	 * new rule takes effect on the next tick instead of on the next launch.
	 */
	public void saveConfig(ModConfig newConfig) {
		store.save(newConfig);
		install(newConfig);
	}

	/** Directory holding {@code config.json} and {@code discovered.json}. */
	public Path configDirectory() {
		return store.directory();
	}

	/** Applies the rule for the current dimension on the next tick, ignoring the delay. */
	public void forceApply(Minecraft client) {
		DimensionContext context = DimensionWatcher.currentContext(client);

		if (context == null) {
			announcer.accept("Not in a world -- there is no dimension to apply a rule for.");
			return;
		}

		observe(config.settings, context);
		schedule(config.settings, context, "manual");
		waitTicks = 0;
	}

	// ---------------------------------------------------------------------------------------
	// Event handling
	// ---------------------------------------------------------------------------------------

	private void handleWorldEnter(ModSettings settings, DimensionContext context) {
		// Remembered before anything is applied, so restoreOnWorldExit can put the menu back the way
		// the player left it. An empty value here means "shaders were off", and it must stay empty:
		// Iris reports that state as the string "(off)", which is not a pack (see IrisBridge).
		packBeforeWorld = IrisBridge.currentPackName().orElse(null);
		observe(settings, context);

		if (settings.applyOnWorldEnter) {
			schedule(settings, context, "world enter");
		}
	}

	private void handleWorldExit(ModSettings settings) {
		pending = null;
		restoreOnExit(settings);
		packBeforeWorld = null;
		discovered.saveIfDirty();
	}

	private void restoreOnExit(ModSettings settings) {
		if (!settings.restoreOnWorldExit || packBeforeWorld == null || !IrisBridge.isAvailable()) {
			return;
		}

		if (!IrisBackendSupport.supported()) {
			// Nothing was applied on this backend (see applyPending), so there is nothing to put
			// back -- and attempting it would only add another failed reload.
			packBeforeWorld = null;
			return;
		}

		ShaderAction restore = ShaderAction.ofPack(packBeforeWorld);
		packBeforeWorld = null;

		if (settings.reloadOnlyWhenChanged && IrisBridge.isAlreadyApplied(restore)) {
			return;
		}

		IrisApplyResult result = IrisBridge.apply(restore);

		if (result.success()) {
			logger.info("Restored shaderpack '{}' on leaving the world", restore.packName());
		} else {
			logger.warn("Could not restore the previous shaderpack: {}", result.message());
		}
	}

	private void schedule(ModSettings settings, DimensionContext context, String reason) {
		if (!settings.enabled || context == null) {
			return;
		}

		pending = context;
		pendingReason = reason;
		waitTicks = settings.applyDelayTicks;
		overdueTicks = 0;

		if (verbose()) {
			logger.debug("Queued a shader change for {} ({}), applying in {} tick(s)", context.describe(), reason, waitTicks);
		}
	}

	private void applyPending(Minecraft client) {
		DimensionContext context = pending;
		String reason = pendingReason;
		pending = null;
		pendingReason = "";

		if (!config.settings.enabled || context == null) {
			return;
		}

		if (!IrisBridge.isAvailable()) {
			logger.warn("Iris is unavailable ({}); leaving shaders alone for {}", IrisBridge.unavailableReason(), context.describe());
			return;
		}

		Optional<String> backendProblem = IrisBackendSupport.unavailableReason();

		if (backendProblem.isPresent()) {
			// Deliberately not an error and deliberately not a fix: Iris cannot run on Vulkan, and
			// switching the player's backend out from under them (which is what Iris's own
			// placeholder offers, at the cost of closing the game) is not this mod's call. Log it
			// once per session so a portal loop does not flood the log, and stay out of the way.
			if (!backendWarningLogged) {
				backendWarningLogged = true;
				logger.warn("{} The rule for {} is being kept, but will only take effect on OpenGL.",
					backendProblem.get(), context.describe());
			} else if (verbose()) {
				logger.debug("Skipped the shader change for {}: running on a backend Iris cannot use", context.describe());
			}

			return;
		}

		Optional<DimensionRule> matched = ruleSet.match(context);

		if (matched.isEmpty()) {
			logger.info("No rule matches {} -- Iris left as it is. A ready-to-paste template is waiting in {}",
				context.describe(), discovered.path());
			return;
		}

		DimensionRule rule = matched.get();
		ShaderAction action = rule.action();

		if (action.isNoop()) {
			if (verbose()) {
				logger.debug("Rule '{}' keeps shaders unchanged for {}", rule.id(), context.describe());
			}

			return;
		}

		DimensionRule effective = rule;

		if (action.mode() == ShaderAction.Mode.SET_PACK && !IrisBridge.isPackInstalled(action.packName())) {
			Optional<DimensionRule> fallback = catchAllFallback(rule);

			if (fallback.isEmpty()) {
				String suggestion = IrisBridge.suggestZipVariant(action.packName())
					.map(candidate -> " Did you mean '" + candidate + "'?")
					.orElse("");
				String message = "Rule '" + rule.id() + "' names shaderpack '" + action.packName()
					+ "', which is not in shaderpacks/." + suggestion;
				logger.warn("{} (dimension {})", message, context.describe());
				announcer.accept(message);
				return;
			}

			effective = fallback.get();
			action = effective.action();
			logger.warn("Rule '{}' names a missing shaderpack; falling back to rule '{}'", rule.id(), effective.id());
		}

		if (action.isNoop()) {
			return;
		}

		if (config.settings.reloadOnlyWhenChanged && IrisBridge.isAlreadyApplied(action)) {
			if (verbose()) {
				logger.debug("Iris is already in the requested state for {} (rule '{}')", context.describe(), effective.id());
			}

			return;
		}

		IrisApplyResult result = IrisBridge.apply(action);

		if (result.success()) {
			String phase = client.gui.overlay() instanceof LoadingOverlay ? ", during loading" : "";
			logger.info("Applied rule '{}' for {} ({}): {}", effective.id(), context.dimension(), reason + phase, result.message());
			announce(config.settings, "Iris: " + result.message() + "  [" + context.dimension() + " / " + effective.id() + "]");
		} else {
			logger.warn("Rule '{}' failed for {}: {}", effective.id(), context.describe(), result.message());
			announce(config.settings, "Iris Dimension Shaders: " + result.message());
		}
	}

	private Optional<DimensionRule> catchAllFallback(DimensionRule rule) {
		if (config.settings.policy() != ModSettings.MissingPackPolicy.USE_CATCH_ALL) {
			return Optional.empty();
		}

		return ruleSet.catchAll()
			.filter(candidate -> !candidate.id().equals(rule.id()))
			.filter(candidate -> !candidate.action().isNoop());
	}

	private void observe(ModSettings settings, DimensionContext context) {
		if (!settings.trackDiscoveredDimensions || context == null) {
			return;
		}

		if (discovered.record(context)) {
			logger.info("New dimension observed: {}. Edit {} to give it its own shader setup (look for '{}').",
				context.describe(), store.configPath(), context.dimension());
		}
	}

	private void announce(ModSettings settings, String message) {
		if (settings.announceInChat) {
			announcer.accept(message);
		}
	}

	/**
	 * The one place that decides "can we touch Iris yet".
	 *
	 * <p>The interesting question is what the shader reload actually needs. It needs a level, because
	 * {@code Iris#getCurrentDimension} reads it; it needs nothing from the player, and it touches only
	 * GL state. So with {@code applyDuringLevelLoading} the test is simply "does the new level exist"
	 * -- which happens a tick or two into the loading overlay, at the point where the player is
	 * already looking at a progress bar. The switch is then finished before the world is shown,
	 * instead of hitting them a second later.
	 *
	 * <p>With the setting off, the old conservative test applies: a loading overlay on screen delays
	 * the change, and so does a missing player reference.
	 */
	private boolean canApplyYet(Minecraft client) {
		if (client.level == null) {
			return false;
		}

		if (config.settings.applyDuringLevelLoading) {
			return true;
		}

		if (client.player == null) {
			return false;
		}

		Overlay overlay = client.gui.overlay();
		return !(overlay instanceof LoadingOverlay);
	}

	// ---------------------------------------------------------------------------------------
	// Introspection for the command and the log
	// ---------------------------------------------------------------------------------------

	public ModConfig config() {
		return config;
	}

	/**
	 * 详细日志开关：跟着模组「通用」页的那一个走，不再单独占一个配置项。
	 *
	 * <p>{@code ModSettings.debugLogging} 字段还留着（老配置文件里可能有），但界面和判断都只认
	 * {@link SpaceJoyConfig#isDebugLog()}，避免同一件事出现两个开关、两个状态还可能不一致。
	 */
	private static boolean verbose() {
		return SpaceJoyConfig.isDebugLog();
	}

	public RuleSet ruleSet() {
		return ruleSet;
	}

	public DiscoveredDimensions discovered() {
		return discovered;
	}

	public List<String> configWarnings() {
		return configWarnings;
	}

	public String statusLine() {
		Optional<String> backendProblem = IrisBackendSupport.unavailableReason();

		return "enabled=" + config.settings.enabled
			+ " iris=" + (IrisBridge.isAvailable() ? "available" : "UNAVAILABLE (" + IrisBridge.unavailableReason() + ")")
			+ (backendProblem.isPresent() ? " backend=UNUSABLE (" + backendProblem.get() + ")" : " backend=ok")
			+ " pack=" + IrisBridge.currentPackName().orElse("<shaders off>")
			+ (IrisBridge.isFallback() ? " fallback=true" : "")
			+ " rules=" + ruleSet.rules().size()
			+ " dimensions=" + discovered.size();
	}

	private void install(ModConfig newConfig) {
		// Config problems are reported once, by the store, at load time; the controller only
		// surfaces them again on demand (status / reload command).
		this.config = newConfig;
		this.ruleSet = newConfig.toRuleSet().ruleSet();
		this.configWarnings = List.copyOf(newConfig.validate());

		warnAboutMissingPacks();
	}

	/**
	 * Reports rules that name a shaderpack Iris cannot load.
	 *
	 * <p>Worth a dedicated pass because this failure is otherwise nearly invisible: the rule looks
	 * fine, Iris logs one error, and the player just sees their shaders turn off. The most common
	 * cause by far is a missing {@code .zip}, which is part of the name Iris resolves -- so when that
	 * is the problem, say so.
	 */
	private void warnAboutMissingPacks() {
		if (!IrisBridge.isAvailable()) {
			return;
		}

		for (DimensionRule rule : ruleSet.rules()) {
			ShaderAction action = rule.action();

			if (action.mode() != ShaderAction.Mode.SET_PACK || IrisBridge.isPackInstalled(action.packName())) {
				continue;
			}

			String suggestion = IrisBridge.suggestZipVariant(action.packName())
				.map(candidate -> " (did you mean '" + candidate + "'?)")
				.orElse("");

			logger.warn("Rule '{}' names shaderpack '{}', which is not in shaderpacks/{}",
				rule.id(), action.packName(), suggestion);
		}
	}
}
