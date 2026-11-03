package net.spacejoy.mod.irisdim.compat.iris;

/**
 * Outcome of an {@link IrisBridge} call.
 *
 * @param success whether Iris accepted the change (or was already in the requested state)
 * @param message short human-readable explanation, safe to show in chat and to log
 */
public record IrisApplyResult(boolean success, String message) {
	public static IrisApplyResult success(String message) {
		return new IrisApplyResult(true, message);
	}

	public static IrisApplyResult failure(String message) {
		return new IrisApplyResult(false, message);
	}
}
