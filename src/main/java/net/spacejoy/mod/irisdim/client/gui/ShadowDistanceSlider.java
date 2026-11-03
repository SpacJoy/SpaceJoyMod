package net.spacejoy.mod.irisdim.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Iris shadow distance, 0-64, as a slider.
 *
 * <p>{@link #DISABLED} is passed when the rule should leave the shadow distance alone rather than
 * pin it; the slider is then rendered inactive and reports that state in its own label, so the
 * difference between "leave it" and "set it to 0" is never ambiguous.
 */
public final class ShadowDistanceSlider extends AbstractSliderButton {
	/** Value meaning "do not override"; also the label shown for that state. */
	public static final int DISABLED = -1;

	public static final int MAX = 64;

	private final IntConsumer onChange;

	public ShadowDistanceSlider(int x, int y, int width, int height, int initialValue, IntConsumer onChange) {
		super(x, y, width, height, Component.empty(), normalise(initialValue));
		this.onChange = onChange;
		this.active = initialValue != DISABLED;
		updateMessage();
	}

	private static double normalise(int value) {
		int clamped = value == DISABLED ? 16 : Math.max(0, Math.min(value, MAX));
		return clamped / (double) MAX;
	}

	@Override
	protected void updateMessage() {
		int value = (int) Math.round(this.value * MAX);

		if (!this.active) {
			setMessage(Component.translatable("spacejoy_mod.irisdim.shadowDistance.keep"));
		} else {
			setMessage(Component.translatable("spacejoy_mod.irisdim.shadowDistance.value", value));
		}
	}

	@Override
	protected void applyValue() {
		if (this.active) {
			onChange.accept((int) Math.round(this.value * MAX));
		}
	}

	/** Re-points the slider at a new value without recreating the widget. */
	public void setValue(int value) {
		this.value = normalise(value);
		this.active = value != DISABLED;
		updateMessage();
	}
}
