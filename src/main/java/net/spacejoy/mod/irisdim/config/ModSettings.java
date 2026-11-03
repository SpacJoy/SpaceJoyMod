package net.spacejoy.mod.irisdim.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global behaviour switches. Plain public fields with initialisers: Gson deserialises through the
 * implicit no-arg constructor, so any key missing from an older config file keeps the default
 * written here. That is what makes the config forward- and backward-compatible without a migration
 * step.
 */
public final class ModSettings {
	/** Minimum ticks to wait after a dimension change before touching Iris. */
	public static final int MIN_DELAY_TICKS = 0;
	/** Maximum ticks to wait; 600 ticks = 30s, comfortably past any loading screen. */
	public static final int MAX_DELAY_TICKS = 600;

	/** Shortest action-bar note worth showing: 20 ticks = 1s. */
	public static final int MIN_ANNOUNCE_TICKS = 20;
	/** Longest action-bar note worth showing: 400 ticks = 20s, past the point where it is noise. */
	public static final int MAX_ANNOUNCE_TICKS = 400;
	/** Vanilla's own action-bar duration, and therefore the value the mod uses when nothing is set. */
	public static final int DEFAULT_ANNOUNCE_TICKS = 60;

	/** Master switch. When false the mod loads but never touches Iris. */
	public boolean enabled = true;

	/** Apply the rule that matches a dimension when a world is first entered. */
	public boolean applyOnWorldEnter = true;

	/** Apply the rule when the player moves to a different dimension inside a world. */
	public boolean applyOnDimensionChange = true;

	/**
	 * When leaving a world, restore the shaderpack that was active before entering it.
	 * Off by default: leaving a world shows the main menu, and reloading a shaderpack there costs a
	 * full shader recompile for something the player is about to navigate away from.
	 */
	public boolean restoreOnWorldExit = false;

	/**
	 * Apply the rule as soon as the new level exists, even while the "Loading terrain" overlay is up.
	 *
	 * <p>On by default, because the alternative is strictly worse in every way that is observable:
	 * a shaderpack reload re-parses the pack and rebuilds its pipeline, and its GL programs compile
	 * lazily on first use. Doing that behind the loading overlay puts both costs where the player is
	 * already staring at a loading bar and only sees the result once the world is visible; doing it
	 * afterwards puts the same costs in front of them, on a world they can already see.
	 */
	public boolean applyDuringLevelLoading = true;

	/**
	 * Ticks to wait after a dimension change before applying.
	 *
	 * <p>Zero by default: with {@link #applyDuringLevelLoading} the switch happens on the first tick
	 * where the new level exists, which is also the earliest moment Iris's own pipeline rebuild for
	 * that dimension can be superseded rather than followed. Raise it only to debug a racing mod.
	 */
	public int applyDelayTicks = 0;

	/** Skip the Iris reload entirely when the target state is already in effect. */
	public boolean reloadOnlyWhenChanged = true;

	/**
	 * What to do when a rule names a shaderpack that is not present in {@code shaderpacks/}.
	 * One of {@code SKIP_AND_WARN} or {@code USE_CATCH_ALL}.
	 */
	public String missingPackPolicy = "SKIP_AND_WARN";

	/** Record every dimension seen at runtime into {@code discovered.json}. */
	public boolean trackDiscoveredDimensions = true;

	/** Show a short action-bar note when the mod changes the Iris state. */
	public boolean announceInChat = true;

	/**
	 * How long the action-bar note stays up, in ticks.
	 *
	 * <p>Exists because vanilla hardcodes 60 ticks inside {@code Hud#setOverlayMessage}: there is no
	 * overload that takes a duration, so the only way to show a message longer is to overwrite the
	 * field the message just set. 60 ticks (3s) is fine for "shaders changed" but short for the
	 * longer messages -- a missing pack and the suggested fix together need a beat more.
	 *
	 * <p>Clamped to {@link #MIN_ANNOUNCE_TICKS}-{@link #MAX_ANNOUNCE_TICKS}; the upper bound exists
	 * because a note that outlives the moment it describes is just clutter on the HUD.
	 */
	public int announceDurationTicks = DEFAULT_ANNOUNCE_TICKS;

	/** Verbose per-tick logging. Only useful while debugging rules. */
	public boolean debugLogging = false;

	public enum MissingPackPolicy {
		/** Log a warning and leave Iris untouched. */
		SKIP_AND_WARN,
		/** Fall through to the catch-all rule, if one exists. */
		USE_CATCH_ALL
	}

	public MissingPackPolicy policy() {
		if (missingPackPolicy == null) {
			return MissingPackPolicy.SKIP_AND_WARN;
		}

		return switch (missingPackPolicy.trim().toUpperCase(Locale.ROOT)) {
			case "USE_CATCH_ALL", "CATCH_ALL", "FALLBACK" -> MissingPackPolicy.USE_CATCH_ALL;
			default -> MissingPackPolicy.SKIP_AND_WARN;
		};
	}

	/**
	 * Clamps and repairs out-of-range values.
	 *
	 * @return a description of every repair made, for the startup log
	 */
	public List<String> validate() {
		List<String> repairs = new ArrayList<>();

		if (applyDelayTicks < MIN_DELAY_TICKS || applyDelayTicks > MAX_DELAY_TICKS) {
			int clamped = Math.max(MIN_DELAY_TICKS, Math.min(applyDelayTicks, MAX_DELAY_TICKS));
			repairs.add("applyDelayTicks " + applyDelayTicks + " -> " + clamped);
			applyDelayTicks = clamped;
		}

		if (announceDurationTicks < MIN_ANNOUNCE_TICKS || announceDurationTicks > MAX_ANNOUNCE_TICKS) {
			int clamped = Math.max(MIN_ANNOUNCE_TICKS, Math.min(announceDurationTicks, MAX_ANNOUNCE_TICKS));
			repairs.add("announceDurationTicks " + announceDurationTicks + " -> " + clamped);
			announceDurationTicks = clamped;
		}

		if (missingPackPolicy == null || missingPackPolicy.isBlank()) {
			repairs.add("missingPackPolicy was empty -> SKIP_AND_WARN");
			missingPackPolicy = "SKIP_AND_WARN";
		}

		return repairs;
	}
}
