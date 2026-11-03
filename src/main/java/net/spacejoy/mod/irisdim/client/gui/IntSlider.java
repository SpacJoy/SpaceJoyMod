package net.spacejoy.mod.irisdim.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * An integer slider over an arbitrary range.
 *
 * <p>Vanilla's {@code OptionInstance} sliders are bound to option objects this mod has no reason to
 * own, and {@link ShadowDistanceSlider} bakes in its own 0-64 range plus the "leave it alone"
 * sentinel. This is the plain version: any range, any label, always active.
 */
public final class IntSlider extends AbstractSliderButton {
	private final int min;
	private final int max;
	private final IntFunction<Component> label;
	private final IntConsumer onChange;

	public IntSlider(int x, int y, int width, int height, int min, int max, int initialValue,
					 IntFunction<Component> label, IntConsumer onChange) {
		super(x, y, width, height, Component.empty(), fractionOf(initialValue, min, max));
		this.min = min;
		this.max = max;
		this.label = label;
		this.onChange = onChange;
		updateMessage();
	}

	private static double fractionOf(int value, int min, int max) {
		if (max <= min) {
			return 0;
		}

		return (Math.max(min, Math.min(value, max)) - min) / (double) (max - min);
	}

	private int currentValue() {
		return min + (int) Math.round(this.value * (max - min));
	}

	@Override
	protected void updateMessage() {
		setMessage(label.apply(currentValue()));
	}

	@Override
	protected void applyValue() {
		onChange.accept(currentValue());
	}
}
