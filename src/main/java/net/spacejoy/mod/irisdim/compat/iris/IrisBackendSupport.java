package net.spacejoy.mod.irisdim.compat.iris;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;

import java.util.Locale;
import java.util.Optional;

/**
 * Whether the graphics backend Minecraft is running on can host Iris at all.
 *
 * <h2>Why this exists</h2>
 * Minecraft 26.x can run on Vulkan. Iris cannot: 1.11.7 still dereferences OpenGL backend classes
 * ({@code com.mojang.renderpearl.backend.opengl.GlDevice} and friends), so on Vulkan it either
 * crashes deep inside {@code SamplerLimits.<init>} with "No context is current" or is disabled
 * wholesale by Iris's own mixin plugin, which reads {@code options.txt}, sees
 * {@code preferredGraphicsBackend: vulkan}, and skips <em>every</em> normal mixin -- leaving only a
 * placeholder screen that offers to close the game and switch back to OpenGL.
 *
 * <p>None of that is this mod's problem to fix, and none of it is this mod's problem to work
 * around. The only sane behaviour is to notice it and stay out of the way: switching shaderpack on
 * a backend where shaderpacks cannot load would produce a failed reload every time the player
 * walks through a portal, and a reload is measured in seconds.
 *
 * <h2>How it is detected</h2>
 * Two independent sources, because they answer slightly different questions:
 * <ul>
 *   <li>{@code Minecraft#options.preferredGraphicsBackend()} is what the player <em>asked</em> for.
 *       It is what Iris itself reads, so a {@code VULKAN} value here means Iris has already given
 *       up, even if the current window happens to still be GL.</li>
 *   <li>{@code RenderSystem.tryGetDevice().getDeviceInfo().backendName()} is what is <em>actually
 *       running</em>. This is the only way to notice that a future Minecraft defaults to Vulkan
 *       without the player touching the setting.</li>
 * </ul>
 *
 * <h2>What this class deliberately does not do</h2>
 * It never changes the backend, never opens a screen, never throws, and never blocks startup.
 * Iris's own placeholder offers to switch to OpenGL and restart; that is Iris's call to make on
 * its own UI, and a rules mod has no business closing the player's game. This class answers one
 * question -- "can shader switching work right now?" -- and the answer is either "yes" or a
 * sentence explaining why not.
 */
public final class IrisBackendSupport {
	/** The only backend name Iris's OpenGL code path can run on, lowercased. */
	private static final String OPENGL_BACKEND_NAME = "opengl";

	private IrisBackendSupport() {
	}

	/**
	 * Empty when shader switching can work, otherwise a one-sentence reason fit for chat and logs.
	 *
	 * <p>Calling this before {@code RenderSystem} has a device is safe: the preferred setting is
	 * consulted on its own in that case, and an uninitialised renderer is not a reason to complain.
	 */
	public static Optional<String> unavailableReason() {
		String active = activeBackendName();

		if (active != null && !active.toLowerCase(Locale.ROOT).contains(OPENGL_BACKEND_NAME)) {
			return Optional.of("Minecraft is running on the " + active
				+ " backend; Iris needs OpenGL, so per-dimension shader switching is paused."
				+ " Switch back to OpenGL in Video Settings to use it.");
		}

		return preferredBackendProblem();
	}

	/**
	 * {@link #unavailableReason()} as a plain boolean, for the call sites that only branch.
	 */
	public static boolean supported() {
		return unavailableReason().isEmpty();
	}

	/**
	 * A two-or-three-word version of {@link #unavailableReason()}, for the config screen footer.
	 *
	 * <p>The full sentence is the right thing for a log line and the wrong thing for a footer that
	 * shares its row with the Iris state and the unsaved-changes marker; this is the part of it a
	 * player needs to see at a glance, with the rest one hover away in chat and in the log.
	 */
	public static Optional<String> shortReason() {
		if (unavailableReason().isEmpty()) {
			return Optional.empty();
		}

		String active = activeBackendName();

		if (active != null && !active.toLowerCase(Locale.ROOT).contains(OPENGL_BACKEND_NAME)) {
			return Optional.of(active + " backend");
		}

		return Optional.of("Vulkan preferred");
	}

	/**
	 * The backend the player selected, whether or not a device came up yet.
	 *
	 * <p>Separate from {@link #activeBackendName()} because it is available earlier and because a
	 * pending Vulkan selection is already fatal for Iris -- Iris's mixin plugin reads this same
	 * value straight out of {@code options.txt} during class loading, long before a device exists.
	 */
	private static Optional<String> preferredBackendProblem() {
		try {
			Minecraft client = Minecraft.getInstance();

			if (client == null || client.options == null) {
				return Optional.empty();
			}

			if (client.options.preferredGraphicsBackend().get() == PreferredGraphicsApi.VULKAN) {
				return Optional.of("Video Settings is set to prefer Vulkan; Iris cannot run there,"
					+ " so per-dimension shader switching is paused until you switch back to OpenGL.");
			}
		} catch (Throwable ignored) {
			// The option moved or was renamed: fall through to the live device check, which is the
			// one that actually matters.
		}

		return Optional.empty();
	}

	/** {@code DeviceInfo#backendName()}, or {@code null} when no device exists yet. */
	private static String activeBackendName() {
		try {
			GpuDevice device = RenderSystem.tryGetDevice();

			if (device == null) {
				return null;
			}

			String name = device.getDeviceInfo().backendName();

			return name == null || name.isBlank() ? null : name;
		} catch (Throwable ignored) {
			return null;
		}
	}
}
