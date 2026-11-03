package net.spacejoy.mod.irisdim.client.gui;

import com.google.gson.Gson;
import net.spacejoy.mod.irisdim.client.DimensionSwitchController;
import net.spacejoy.mod.irisdim.compat.iris.IrisBackendSupport;
import net.spacejoy.mod.irisdim.compat.iris.IrisBridge;
import net.spacejoy.mod.irisdim.config.ActionSpec;
import net.spacejoy.mod.irisdim.config.MatchSpec;
import net.spacejoy.mod.irisdim.config.ModConfig;
import net.spacejoy.mod.irisdim.config.RuleSpec;
import net.spacejoy.mod.irisdim.core.ShaderAction;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The rule editor: the config file, rendered.
 *
 * <p>It edits a <em>copy</em> of the loaded config and only writes on "Save &amp; apply". That is
 * deliberate -- the running mod must not be reconfigured from under itself mid-edit, and the file
 * stays the single source of truth rather than becoming a cache of whatever the last screen state
 * happened to be.
 *
 * <p>Only the fields worth a widget get one. The match block's {@code worlds} axis, arbitrary
 * comment text and the full option-override map stay file-only; the screen shows the rule summary
 * that results from them, so nothing is invisible, and hand-edited files survive a trip through
 * the GUI untouched.
 */
public final class ConfigScreen extends Screen {
	private static final Gson GSON = new Gson();

	private static final int MARGIN = 8;
	private static final int TOP = 30;
	private static final int FIELD_HEIGHT = 20;
	private static final int SECTION = 34;
	/** Two 20px button rows plus gaps; see {@link #buildBottomBar()}. */
	private static final int BOTTOM_BAR = 50;
	/** 底栏按钮的最宽值：不让三四个按钮被拉得铺满整屏。 */
	private static final int MAX_BUTTON_WIDTH = 150;

	private static final int PANEL_BACKGROUND = 0x50000000;
	private static final int LABEL_COLOUR = 0xFF9BA3B5;
	private static final int STATUS_COLOUR = 0xFF7FD67F;
	private static final int WARN_COLOUR = 0xFFFF8A5C;

	private static final List<String> MODE_CYCLE = List.of("NONE", "SET_PACK", "DISABLE_SHADERS");

	private record Label(Component text, int x, int y) {
	}

	private final DimensionSwitchController controller;
	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();

	private ModConfig working;
	private int selectedIndex;
	private boolean dirty;

	private RuleListWidget ruleList;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentX;
	private int contentWidth;
	private int contentY;

	private EditBox dimensionsBox;
	private EditBox typesBox;
	private ShadowDistanceSlider shadowSlider;
	private Button enabledButton;
	private Button modeButton;

	private String status = "";
	private int statusTicks;

	public ConfigScreen(DimensionSwitchController controller, Screen parent) {
		super(Component.translatable("spacejoy_mod.irisdim.screen.title"));
		this.controller = controller;
		this.parent = parent;
		this.working = copyOf(controller.config());
		this.selectedIndex = working.rules.isEmpty() ? -1 : 0;
	}

	/** The screen edits a snapshot; a copy is also how the duplicate button clones a rule. */
	static ModConfig copyOf(ModConfig config) {
		return ModConfig.fromJson(config.toJson());
	}

	// -------------------------------------------------------------------------------------
	// Layout
	// -------------------------------------------------------------------------------------

	@Override
	protected void init() {
		double previousScroll = ruleList == null ? 0 : ruleList.scrollAmount();

		if (working.rules == null) {
			working.rules = new ArrayList<>();
		}

		if (selectedIndex >= working.rules.size()) {
			selectedIndex = working.rules.size() - 1;
		}

		labels.clear();

		// The list gives up width before the detail panel does: the panel is where the editing
		// happens and a 6px-wide "choose shaderpack" button is worse than a narrower list.
		int listWidth = Mth.clamp((int) (this.width * 0.44), 120, Math.max(120, this.width - 232));
		int listBottom = this.height - BOTTOM_BAR - 18;

		ruleList = new RuleListWidget(this, MARGIN, TOP, listWidth, listBottom - TOP, working.rules);
		ruleList.setScrollAmount(previousScroll);
		addRenderableWidget(ruleList);

		panelX = MARGIN + listWidth + 8;
		panelY = TOP;
		panelWidth = this.width - panelX - MARGIN;
		panelHeight = listBottom - TOP;

		contentX = panelX + 6;
		contentWidth = panelWidth - 12;
		contentY = panelY + 6;

		labels.add(new Label(Component.translatable("spacejoy_mod.irisdim.label.rules"), MARGIN + 2, TOP - 12));
		labels.add(new Label(Component.translatable("spacejoy_mod.irisdim.label.details"), contentX, TOP - 12));

		buildDetailPanel();
		buildBottomBar();
	}

	private void buildDetailPanel() {
		RuleSpec rule = selectedRule();

		if (rule == null) {
			labels.add(new Label(Component.translatable("spacejoy_mod.irisdim.details.none"), contentX, contentY + 4));
			return;
		}

		int y = contentY;

		// --- identity: priority + enabled -------------------------------------------------
		int stepWidth = 32;
		int enabledX = contentX + 74 + stepWidth * 2 + 6;

		labels.add(new Label(Component.translatable("spacejoy_mod.irisdim.label.priority", rule.priority), contentX, y + 6));
		addRenderableWidget(Button.builder(Component.literal("−5"), b -> changePriority(-5))
			.bounds(contentX + 74, y, stepWidth, FIELD_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("+5"), b -> changePriority(5))
			.bounds(contentX + 74 + stepWidth + 2, y, stepWidth, FIELD_HEIGHT).build());
		enabledButton = addRenderableWidget(Button.builder(enabledLabel(rule), b -> toggleSelected())
			.bounds(enabledX, y, Math.max(40, contentX + contentWidth - enabledX), FIELD_HEIGHT).build());
		y += FIELD_HEIGHT + 12;

		// --- match ------------------------------------------------------------------------
		labels.add(new Label(Component.translatable("spacejoy_mod.irisdim.label.dimension"), contentX, y));
		dimensionsBox = new EditBox(this.font, contentX, y + 10, contentWidth, FIELD_HEIGHT, Component.empty());
		dimensionsBox.setMaxLength(1024);
		dimensionsBox.setValue(join(rule.match.dimensions));
		dimensionsBox.setResponder(value -> rule.match.dimensions = split(value));
		addRenderableWidget(dimensionsBox);
		y += SECTION;

		labels.add(new Label(Component.translatable("spacejoy_mod.irisdim.label.dimensionType"), contentX, y));
		typesBox = new EditBox(this.font, contentX, y + 10, contentWidth, FIELD_HEIGHT, Component.empty());
		typesBox.setMaxLength(1024);
		typesBox.setValue(join(rule.match.dimensionTypes));
		typesBox.setResponder(value -> rule.match.dimensionTypes = split(value));
		addRenderableWidget(typesBox);
		y += SECTION;

		// --- action -----------------------------------------------------------------------
		labels.add(new Label(Component.translatable("spacejoy_mod.irisdim.label.action"), contentX, y));
		modeButton = addRenderableWidget(Button.builder(modeLabel(rule), b -> cycleMode())
			.bounds(contentX, y + 10, 116, FIELD_HEIGHT).build());
		int packX = contentX + 122;
		addRenderableWidget(Button.builder(packLabel(rule), b -> openPackPicker())
			.bounds(packX, y + 10, Math.max(40, contentX + contentWidth - packX), FIELD_HEIGHT).build());
		y += SECTION;

		// --- shadow distance --------------------------------------------------------------
		// No separate label: the toggle button already reads "Default" / "Override", which is the
		// whole question this row asks, and the vertical budget is tight on a 240px-tall GUI.
		addRenderableWidget(Button.builder(shadowLabel(rule), b -> toggleShadowDistance())
			.bounds(contentX, y, 92, FIELD_HEIGHT).build());
		shadowSlider = addRenderableWidget(new ShadowDistanceSlider(contentX + 98, y, contentWidth - 98, FIELD_HEIGHT,
			rule.shader.shadowDistance == null ? ShadowDistanceSlider.DISABLED : rule.shader.shadowDistance,
			value -> rule.shader.shadowDistance = value));
	}

	private void buildBottomBar() {
		int firstRow = this.height - BOTTOM_BAR + 2;
		int secondRow = firstRow + FIELD_HEIGHT + 2;

		// Row 1 edits the rule that is selected; row 2 deals with files. The split keeps the
		// destructive buttons away from "Save & apply" -- they are 22px apart, not 6.
		row(firstRow,
			new Entry("spacejoy_mod.irisdim.button.add", b -> openDimensionPicker()),
			new Entry("spacejoy_mod.irisdim.button.duplicate", b -> duplicateSelected()),
			new Entry("spacejoy_mod.irisdim.button.delete", b -> deleteSelected()),
			new Entry("spacejoy_mod.irisdim.button.defaults", b -> restoreDefaults()));

		// Row 2 ends on "Done": the same write as "Save & apply", plus the exit. Escape still
		// leaves the file alone, so the two ways out of this screen stay "keep" and "discard".
		// There used to be a "Settings" button here -- those ten toggles now live in the Cloth
		// config screen, so this screen only deals with rules.
		row(secondRow,
			new Entry("spacejoy_mod.irisdim.button.reload", b -> reloadFromDisk()),
			new Entry("spacejoy_mod.irisdim.button.folder", b -> openConfigFolder()),
			new Entry("spacejoy_mod.irisdim.button.save", b -> saveAndApply()),
			new Entry("spacejoy_mod.irisdim.button.done", b -> saveAndClose()));
	}

	private record Entry(String translationKey, Button.OnPress onPress) {
	}

	/**
	 * Lays a bar of buttons out centred, each at most {@link #MAX_BUTTON_WIDTH} wide.
	 *
	 * <p>Stretching three buttons across the whole window made every button a different size
	 * from row to row and looked wrong; a fixed cap keeps rows visually consistent and leaves
	 * breathing room at both edges instead of a wall of grey.
	 */
	private void row(int y, Entry... entries) {
		int gap = 6;
		int usable = this.width - MARGIN * 2;
		int width = Math.min(MAX_BUTTON_WIDTH, (usable - gap * (entries.length - 1)) / entries.length);
		int total = width * entries.length + gap * (entries.length - 1);
		int x = Math.max(MARGIN, (this.width - total) / 2);

		for (Entry entry : entries) {
			addRenderableWidget(Button.builder(Component.translatable(entry.translationKey()), entry.onPress())
				.bounds(x, y, width, FIELD_HEIGHT).build());
			x += width + gap;
		}
	}

	// -------------------------------------------------------------------------------------
	// Rendering
	// -------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// The extractor pipeline calls this before the widgets, which is exactly where a panel
		// backdrop belongs -- drawing it from extractRenderState would paint over the buttons.
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BACKGROUND);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(this.font, this.title, this.width / 2, 9, 0xFFFFFFFF);

		for (Label label : labels) {
			graphics.text(this.font, label.text(), label.x(), label.y(), LABEL_COLOUR, false);
		}

		renderFooter(graphics);
	}

	private void renderFooter(GuiGraphicsExtractor graphics) {
		int y = this.height - BOTTOM_BAR - 12;
		String state = IrisBridge.isAvailable()
			? "Iris: " + IrisBridge.currentPackName().orElse(Component.translatable("spacejoy_mod.irisdim.status.shadersOff").getString())
			: "Iris: " + Component.translatable("spacejoy_mod.irisdim.status.unavailable").getString();

		// Iris gives up on a pack it cannot compile and then never retries on its own; saying so
		// here is what turns "my shaders stopped working" into something the player can act on.
		if (IrisBridge.isFallback()) {
			state = state + " " + Component.translatable("spacejoy_mod.irisdim.status.fallback").getString();
		}

		graphics.text(this.font, state, MARGIN + 2, y, LABEL_COLOUR, false);

		// The rules are still perfectly editable on a backend Iris cannot use -- they just cannot
		// be applied. Saying so here is the cheapest place to catch it, because this screen is
		// where a player goes after noticing that nothing happens when they walk through a portal.
		Optional<String> backendProblem = IrisBackendSupport.shortReason();

		if (backendProblem.isPresent() && statusTicks <= 0 && !dirty) {
			Component paused = Component.translatable("spacejoy_mod.irisdim.status.backendUnusable",
				Component.literal(backendProblem.get()));
			int pausedWidth = this.font.width(paused);

			if (pausedWidth < this.width - 2 * MARGIN - 2) {
				graphics.text(this.font, paused, this.width - MARGIN - 2 - pausedWidth, y, WARN_COLOUR, false);
			}
		}

		if (statusTicks > 0) {
			int width = this.font.width(status);
			graphics.text(this.font, status, (this.width - width) / 2, y, STATUS_COLOUR, false);
		} else if (dirty) {
			Component unsaved = Component.translatable("spacejoy_mod.irisdim.status.unsaved");
			graphics.text(this.font, unsaved, this.width - MARGIN - 2 - this.font.width(unsaved), y, WARN_COLOUR, false);
		}
	}

	@Override
	public void tick() {
		if (statusTicks > 0) {
			statusTicks--;
		}

		// Recomputed on the tick rather than per frame: toJson() on every frame would be wasted work
		// for a signal that nobody perceives at 20 Hz, let alone 200.
		dirty = hasUnsavedChanges();
	}

	// -------------------------------------------------------------------------------------
	// Model helpers
	// -------------------------------------------------------------------------------------

	RuleSpec selectedRule() {
		if (selectedIndex < 0 || working.rules == null || selectedIndex >= working.rules.size()) {
			return null;
		}

		return working.rules.get(selectedIndex);
	}

	int selectedIndex() {
		return selectedIndex;
	}

	private static String join(List<String> values) {
		return values == null ? "" : String.join(", ", values);
	}

	private static List<String> split(String value) {
		if (value == null || value.isBlank()) {
			return new ArrayList<>();
		}

		return Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(part -> !part.isEmpty())
			.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	private Component enabledLabel(RuleSpec rule) {
		return Component.translatable(rule.enabled
			? "spacejoy_mod.irisdim.button.enabled"
			: "spacejoy_mod.irisdim.button.disabled");
	}

	private Component modeLabel(RuleSpec rule) {
		return Component.translatable("spacejoy_mod.irisdim.mode." + normalisedMode(rule));
	}

	private Component packLabel(RuleSpec rule) {
		String pack = rule.shader.pack;

		if (pack == null || pack.isBlank()) {
			return Component.translatable("spacejoy_mod.irisdim.button.pickPack");
		}

		// The name is built as a literal and the warning is appended as a separate part, so that a
		// pack whose file name carries §-formatting codes keeps its colours instead of having the
		// codes printed as glyphs by a translatable argument.
		Component label = Component.literal(pack);

		if (normalisedMode(rule).equals("SET_PACK") && !IrisBridge.isPackInstalled(pack)) {
			label = label.copy().append(Component.translatable("spacejoy_mod.irisdim.pack.missingSuffix"));
		}

		return label;
	}

	private Component shadowLabel(RuleSpec rule) {
		return Component.translatable(rule.shader.shadowDistance == null
			? "spacejoy_mod.irisdim.shadowDistance.keep"
			: "spacejoy_mod.irisdim.shadowDistance.override");
	}

	private static String normalisedMode(RuleSpec rule) {
		if (rule.shader == null || rule.shader.mode == null) {
			return "NONE";
		}

		String mode = rule.shader.mode.trim().toUpperCase(Locale.ROOT);

		if (mode.equals("DISABLE_SHADERS") || mode.equals("DISABLE") || mode.equals("OFF")) {
			return "DISABLE_SHADERS";
		}

		if (mode.equals("SET_PACK") || mode.equals("SET") || mode.equals("PACK")) {
			return "SET_PACK";
		}

		return "NONE";
	}

	// -------------------------------------------------------------------------------------
	// Actions
	// -------------------------------------------------------------------------------------

	void select(int index) {
		if (index == selectedIndex) {
			return;
		}

		selectedIndex = index;
		rebuildWidgets();
	}

	void toggleEnabled(int index) {
		RuleSpec rule = index >= 0 && index < working.rules.size() ? working.rules.get(index) : null;

		if (rule == null) {
			return;
		}

		rule.enabled = !rule.enabled;

		// The list paints straight from the model, so only the detail panel needs rebuilding.
		if (index == selectedIndex) {
			rebuildWidgets();
		}
	}

	private void toggleSelected() {
		RuleSpec rule = selectedRule();

		if (rule != null) {
			rule.enabled = !rule.enabled;
			rebuildWidgets();
		}
	}

	private void changePriority(int delta) {
		RuleSpec rule = selectedRule();

		if (rule != null) {
			rule.priority += delta;
			rebuildWidgets();
		}
	}

	private void cycleMode() {
		RuleSpec rule = selectedRule();

		if (rule == null) {
			return;
		}

		String current = normalisedMode(rule);
		int index = MODE_CYCLE.indexOf(current);
		String next = MODE_CYCLE.get((index + 1) % MODE_CYCLE.size());

		rule.shader.mode = next;

		if (next.equals("SET_PACK") && rule.shader.pack.isBlank()) {
			List<String> packs = IrisBridge.availablePacks();

			if (!packs.isEmpty()) {
				rule.shader.pack = packs.get(0);
			}
		}

		rebuildWidgets();
	}

	private void toggleShadowDistance() {
		RuleSpec rule = selectedRule();

		if (rule == null) {
			return;
		}

		rule.shader.shadowDistance = rule.shader.shadowDistance == null ? 16 : null;
		rebuildWidgets();
	}

	private void openPackPicker() {
		RuleSpec rule = selectedRule();

		if (rule == null) {
			return;
		}

		this.minecraft.setScreenAndShow(new PackPickerScreen(this, rule.shader.pack, picked -> {
			rule.shader.pack = picked;
			rebuildWidgets();
		}));
	}

	private void deleteSelected() {
		if (selectedRule() == null) {
			return;
		}

		working.rules.remove(selectedIndex);

		if (selectedIndex >= working.rules.size()) {
			selectedIndex = working.rules.size() - 1;
		}

		rebuildWidgets();
	}

	private void duplicateSelected() {
		RuleSpec rule = selectedRule();

		if (rule == null) {
			return;
		}

		RuleSpec copy = GSON.fromJson(GSON.toJson(rule), RuleSpec.class);
		copy.id = rule.id + "-copy";
		copy.priority = rule.priority - 1;
		working.rules.add(selectedIndex + 1, copy);
		selectedIndex = selectedIndex + 1;
		rebuildWidgets();
	}

	private void openDimensionPicker() {
		this.minecraft.setScreenAndShow(new DimensionPickerScreen(this, controller, (dimensionId, dimensionType) -> {
			RuleSpec rule = new RuleSpec();
			rule.id = dimensionId.path().replace('/', '-');
			rule.priority = 50;
			rule.enabled = true;
			rule.comment = "Created from the GUI for " + dimensionId + ".";
			rule.match = new MatchSpec();
			rule.shader = new ActionSpec();

			if (dimensionType == null) {
				rule.match.dimensions.add(dimensionId.toString());
			} else {
				// Matching on the type is the better default when the mod that added this dimension
				// looks like a vanilla one -- it keeps working for siblings that do not exist yet.
				rule.match.dimensionTypes.add(dimensionType.toString());
			}

			working.rules.add(rule);
			selectedIndex = working.rules.size() - 1;
			rebuildWidgets();
		}));
	}

	/**
	 * Hands the {@code settings} block to its own screen.
	 *
	 * <p>That screen edits this screen's working copy in place instead of taking a copy of its own,
	 * because the write still happens here, on "Save &amp; apply". The transaction boundary stays in
	 * one place, so the settings screen cannot leave the file and the running mod disagreeing.
	 *
	 * <p>Kept so the settings page is still reachable from code; the bottom bar no longer has a
	 * button for it because the same ten toggles live in the Cloth config screen.
	 */
	@SuppressWarnings("unused")
	private void openSettings() {
		this.minecraft.setScreenAndShow(new SettingsScreen(this, working));
	}

	private void reloadFromDisk() {
		controller.reloadConfig();
		working = copyOf(controller.config());
		selectedIndex = working.rules.isEmpty() ? -1 : Math.min(Math.max(selectedIndex, 0), working.rules.size() - 1);
		rebuildWidgets();
		report("spacejoy_mod.irisdim.status.reloaded");
	}

	private void restoreDefaults() {
		working = ModConfig.defaults();
		selectedIndex = 0;
		rebuildWidgets();
		report("spacejoy_mod.irisdim.status.defaultsLoaded");
	}

	private void openConfigFolder() {
		try {
			Blaze3D.openUri(controller.configDirectory().toUri());
		} catch (Throwable t) {
			report("spacejoy_mod.irisdim.status.openFolderFailed");
		}
	}

	private void saveAndApply() {
		List<String> missing = missingPacksIn(working);

		if (missing.isEmpty()) {
			commitAndApply();
			return;
		}

		askAboutMissingPacks(missing, () -> {
			this.minecraft.setScreenAndShow(this);
			commitAndApply();
		});
	}

	private void commitAndApply() {
		controller.saveConfig(working);
		controller.forceApply(this.minecraft);
		dirty = false;
		report("spacejoy_mod.irisdim.status.saved");
	}

	/**
	 * Names the packs a rule asks for that are not in {@code shaderpacks/}.
	 *
	 * <p>Exists because a stale pack name is the one config mistake that looks like a mod failure:
	 * Iris logs an error and falls back to no shaders, the rule still fires on every dimension
	 * change, and the player sees "the mod switched nothing" rather than "the file name is wrong".
	 * Reported here, before the write, where it can still be fixed.
	 *
	 * <p>{@code NONE} rules and {@code DISABLE_SHADERS} rules are skipped: only a rule that names a
	 * pack can name a missing one. {@code isPackInstalled} answers {@code true} when Iris's
	 * directory cannot be read at all, so an unavailable Iris never blocks a save.
	 */
	private static List<String> missingPacksIn(ModConfig config) {
		List<String> missing = new ArrayList<>();

		if (config.rules == null || !IrisBridge.isAvailable()) {
			return missing;
		}

		for (RuleSpec rule : config.rules) {
			if (rule == null || rule.shader == null) {
				continue;
			}

			ShaderAction.Mode mode;

			try {
				mode = ShaderAction.parseMode(rule.shader.mode);
			} catch (IllegalArgumentException e) {
				// An unparseable mode is the editor's own problem to report; it is not a pack.
				continue;
			}

			if (mode != ShaderAction.Mode.SET_PACK) {
				continue;
			}

			String pack = rule.shader.pack;

			if (pack == null || pack.isBlank() || IrisBridge.isPackInstalled(pack)) {
				continue;
			}

			if (!missing.contains(pack)) {
				missing.add(pack);
			}
		}

		return missing;
	}

	/**
	 * Asks whether to save despite the packs named in {@code missing}, running {@code onConfirm}
	 * only when the player says yes.
	 *
	 * <p>A "no" always returns to the editor. A "yes" leaves the screen entirely to {@code
	 * onConfirm}, because the two callers want different things there: "Save &amp; apply" stays in
	 * the editor to show the saved status, while "Done" moves on to the parent screen. Restoring
	 * the editor first would make "Done" flash it for one frame.
	 */
	private void askAboutMissingPacks(List<String> missing, Runnable onConfirm) {
		StringBuilder detail = new StringBuilder();

		for (String pack : missing) {
			if (!detail.isEmpty()) {
				detail.append('\n');
			}

			detail.append(pack);
		}

		// ConfirmScreen rather than a checkbox: the point is that the player may not know the file
		// moved, so the question has to be asked once and answered deliberately. Esc does nothing on
		// this screen, and the delay stops a double-click on "Save" from answering it for them.
		this.minecraft.setScreenAndShow(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					onConfirm.run();
				} else {
					this.minecraft.setScreenAndShow(this);
				}
			},
			Component.translatable("spacejoy_mod.irisdim.confirm.missingPacks.title"),
			Component.translatable("spacejoy_mod.irisdim.confirm.missingPacks.message",
				Component.literal(detail.toString())),
			Component.translatable("spacejoy_mod.irisdim.confirm.missingPacks.save"),
			Component.translatable("spacejoy_mod.irisdim.confirm.missingPacks.cancel")));
	}

	/**
	 * "Done": commit the edits if there are any, then leave the screen.
	 *
	 * <p>The comparison is redone here instead of trusting {@link #dirty}, because that flag is only
	 * refreshed once per tick -- a click that lands in the same tick as the last keystroke would see
	 * a stale value and drop the edit. Writing unconditionally would be worse the other way round:
	 * every open-and-close would re-apply the rule for the current dimension, and a shaderpack
	 * reload is measured in seconds.
	 */
	private void saveAndClose() {
		if (!hasUnsavedChanges()) {
			onClose();
			return;
		}

		List<String> missing = missingPacksIn(working);

		if (missing.isEmpty()) {
			controller.saveConfig(working);
			controller.forceApply(this.minecraft);
			onClose();
			return;
		}

		askAboutMissingPacks(missing, () -> {
			controller.saveConfig(working);
			controller.forceApply(this.minecraft);
			onClose();
		});
	}

	private boolean hasUnsavedChanges() {
		return !working.toJson().equals(controller.config().toJson());
	}

	private void report(String translationKey) {
		status = Component.translatable(translationKey).getString();
		statusTicks = 80;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}
}
