package net.spacejoy.mod.irisdim.client.gui;

import net.spacejoy.mod.irisdim.config.ModConfig;
import net.spacejoy.mod.irisdim.config.ModSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Editor for the {@code settings} block of {@code config.json}.
 *
 * <p>Every switch here is one field of {@link ModSettings} -- the half of the config that used to be
 * file-only: a rule has a panel full of widgets, but "apply during loading" or "announce in the
 * action bar" had none. This screen makes the whole file reachable without a text editor, and puts
 * the reason behind each flag one hover away, which a comment in JSON can never do.
 *
 * <p>Like the rule list, it edits the <em>working copy</em> it is handed and writes nothing itself:
 * the rule editor owns the transaction and this screen is a page inside it. That is why both ways
 * out of it -- the button and Escape -- mean "back to the rules" rather than "save", and why the
 * hint line says so. A screen that quietly dropped edits would be worse than no screen.
 */
public final class SettingsScreen extends Screen {
	private static final String PREFIX = "spacejoy_mod.irisdim.settings.";

	private static final int MARGIN = 8;
	private static final int TOP = 34;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 6;
	private static final int COLUMN_GAP = 8;
	private static final int BOTTOM = 26;
	/** Toggle rows per column; both columns are built to this height so they stay aligned. */
	private static final int TOGGLE_ROWS = 5;

	private static final int TITLE_COLOUR = 0xFFFFFFFF;
	private static final int HINT_COLOUR = 0xFF9BA3B5;
	private static final int DESC_COLOUR = 0xFFB8C0D0;

	/** A widget plus the translation key that names it; {@code key + ".desc"} explains it. */
	private record Slot(String nameKey, AbstractWidget widget) {
	}

	private final Screen parent;
	private final ModConfig config;
	private final List<Slot> slots = new ArrayList<>();

	private int descriptionY;

	public SettingsScreen(Screen parent, ModConfig config) {
		super(Component.translatable("spacejoy_mod.irisdim.screen.settings"));
		this.parent = parent;
		this.config = config;
	}

	private ModSettings settings() {
		if (config.settings == null) {
			// Reachable only from a hand-written file that set "settings": null. ModConfig repairs
			// that at load time; this keeps the screen total regardless.
			config.settings = new ModSettings();
		}

		return config.settings;
	}

	// -------------------------------------------------------------------------------------
	// Layout
	// -------------------------------------------------------------------------------------

	@Override
	protected void init() {
		slots.clear();

		ModSettings settings = settings();

		int buttonsY = this.height - BOTTOM;
		descriptionY = buttonsY - 14;

		// The two sliders share one strip above the description line, side by side rather than
		// stacked: stacking them would cost a second full row, and on the smallest GUI scales that
		// row is the difference between the toggle block fitting and overlapping the sliders.
		int sliderY = descriptionY - 30;
		int step = ROW_HEIGHT + ROW_GAP;

		int columnWidth = (this.width - MARGIN * 2 - COLUMN_GAP) / 2;
		int rightX = MARGIN + columnWidth + COLUMN_GAP;

		// The toggle block is centred in whatever room is left between the hint line and the
		// sliders, so a tall window does not leave two columns stranded at the top of the screen.
		int rowsHeight = TOGGLE_ROWS * ROW_HEIGHT + (TOGGLE_ROWS - 1) * ROW_GAP;
		int rowTop = TOP + Math.max(0, (sliderY - 10 - TOP - rowsHeight) / 2);

		// Left column: the master switch and the events that trigger an apply.
		int y = rowTop;
		toggle(MARGIN, y, columnWidth, "enabled", settings.enabled,
			() -> settings.enabled = !settings.enabled);
		toggle(MARGIN, y += step, columnWidth, "applyOnWorldEnter", settings.applyOnWorldEnter,
			() -> settings.applyOnWorldEnter = !settings.applyOnWorldEnter);
		toggle(MARGIN, y += step, columnWidth, "applyOnDimensionChange", settings.applyOnDimensionChange,
			() -> settings.applyOnDimensionChange = !settings.applyOnDimensionChange);
		toggle(MARGIN, y += step, columnWidth, "applyDuringLevelLoading", settings.applyDuringLevelLoading,
			() -> settings.applyDuringLevelLoading = !settings.applyDuringLevelLoading);
		toggle(MARGIN, y += step, columnWidth, "restoreOnWorldExit", settings.restoreOnWorldExit,
			() -> settings.restoreOnWorldExit = !settings.restoreOnWorldExit);

		// Right column: how the apply itself behaves.
		y = rowTop;
		toggle(rightX, y, columnWidth, "reloadOnlyWhenChanged", settings.reloadOnlyWhenChanged,
			() -> settings.reloadOnlyWhenChanged = !settings.reloadOnlyWhenChanged);
		toggle(rightX, y += step, columnWidth, "trackDiscoveredDimensions", settings.trackDiscoveredDimensions,
			() -> settings.trackDiscoveredDimensions = !settings.trackDiscoveredDimensions);
		toggle(rightX, y += step, columnWidth, "announceInChat", settings.announceInChat,
			() -> settings.announceInChat = !settings.announceInChat);
		toggle(rightX, y += step, columnWidth, "debugLogging", settings.debugLogging,
			() -> settings.debugLogging = !settings.debugLogging);
		policy(rightX, y += step, columnWidth);

		slider(MARGIN, sliderY, columnWidth, "announceDurationTicks",
			ModSettings.MIN_ANNOUNCE_TICKS, ModSettings.MAX_ANNOUNCE_TICKS, settings.announceDurationTicks,
			value -> settings.announceDurationTicks = value);

		slider(rightX, sliderY, this.width - MARGIN - rightX, "applyDelayTicks",
			ModSettings.MIN_DELAY_TICKS, ModSettings.MAX_DELAY_TICKS, settings.applyDelayTicks,
			value -> settings.applyDelayTicks = value);

		bottomBar(buttonsY);
	}

	private void bottomBar(int y) {
		int gap = 6;
		int width = (this.width - MARGIN * 2 - gap) / 2;
		int secondX = MARGIN + width + gap;

		addRenderableWidget(Button.builder(Component.translatable("spacejoy_mod.irisdim.button.settingsDefaults"),
			b -> restoreDefaults()).bounds(MARGIN, y, width, ROW_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.translatable("spacejoy_mod.irisdim.button.backToRules"),
			b -> onClose()).bounds(secondX, y, this.width - MARGIN - secondX, ROW_HEIGHT).build());
	}

	// -------------------------------------------------------------------------------------
	// Widgets
	// -------------------------------------------------------------------------------------

	/** One on/off switch: the flip lands on the model, then the labels are redrawn from it. */
	private void toggle(int x, int y, int width, String name, boolean value, Runnable flip) {
		Button button = addRenderableWidget(Button.builder(toggleLabel(name, value), b -> {
			flip.run();
			rebuildWidgets();
		}).bounds(x, y, width, ROW_HEIGHT).build());

		slots.add(new Slot(PREFIX + name, button));
	}

	/** The one setting with more than two states. */
	private void policy(int x, int y, int width) {
		Button button = addRenderableWidget(Button.builder(policyLabel(), b -> {
			ModSettings settings = settings();
			settings.missingPackPolicy = settings.policy() == ModSettings.MissingPackPolicy.USE_CATCH_ALL
				? ModSettings.MissingPackPolicy.SKIP_AND_WARN.name()
				: ModSettings.MissingPackPolicy.USE_CATCH_ALL.name();
			rebuildWidgets();
		}).bounds(x, y, width, ROW_HEIGHT).build());

		slots.add(new Slot(PREFIX + "missingPackPolicy", button));
	}

	private void slider(int x, int y, int width, String name, int min, int max, int initialValue,
		IntConsumer onChange) {
		IntSlider slider = addRenderableWidget(new IntSlider(x, y, width, ROW_HEIGHT,
			min, max, initialValue,
			value -> Component.translatable(PREFIX + name + ".value", value), onChange));

		slots.add(new Slot(PREFIX + name, slider));
	}

	private void restoreDefaults() {
		// Replaced rather than copied field by field: every handler above closed over the old
		// instance, and rebuilding the widgets re-reads the new one.
		config.settings = new ModSettings();
		rebuildWidgets();
	}

	// -------------------------------------------------------------------------------------
	// Labels
	// -------------------------------------------------------------------------------------

	private Component toggleLabel(String name, boolean value) {
		return Component.empty()
			.append(Component.translatable(PREFIX + name))
			.append(Component.literal(" · "))
			.append(Component.translatable(PREFIX + (value ? "on" : "off")));
	}

	private Component policyLabel() {
		return Component.empty()
			.append(Component.translatable(PREFIX + "missingPackPolicy"))
			.append(Component.literal(" · "))
			.append(Component.translatable(PREFIX + "policy." + settings().policy().name()));
	}

	// -------------------------------------------------------------------------------------
	// Rendering
	// -------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(this.font, this.title, this.width / 2, 9, TITLE_COLOUR);
		graphics.text(this.font, Component.translatable(PREFIX + "hint"), MARGIN + 2, 22, HINT_COLOUR, false);

		// An explanation of whatever the cursor is over, drawn in the one line of the screen that is
		// always free. A flag whose reason is not obvious -- "skip reload when unchanged"? -- is
		// worse than no flag, and this is cheaper than a tooltip on every widget.
		String description = descriptionAt(mouseX, mouseY);

		if (!description.isEmpty()) {
			graphics.text(this.font, description, MARGIN + 2, descriptionY, DESC_COLOUR, false);
		}
	}

	private String descriptionAt(int mouseX, int mouseY) {
		for (Slot slot : slots) {
			if (slot.widget().isMouseOver(mouseX, mouseY)) {
				return Component.translatable(slot.nameKey() + ".desc").getString();
			}
		}

		return "";
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}
}
