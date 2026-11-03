package net.spacejoy.mod.irisdim.client.gui;

import net.spacejoy.mod.irisdim.client.DimensionSwitchController;
import net.spacejoy.mod.irisdim.client.DiscoveredDimensions;
import net.spacejoy.mod.irisdim.core.DimensionContext;
import net.spacejoy.mod.irisdim.core.DimensionId;
import net.spacejoy.mod.irisdim.core.DimensionRule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creates a rule for a dimension, either one this mod has already observed or one typed by hand.
 *
 * <p>Bringing the discovery log into the GUI is the point of this screen. Without it the workflow
 * is "open a JSON file, find an entry, copy a template"; with it, the newly-created rule is already
 * half-filled -- matching on the dimension <em>type</em> when the observed type is a vanilla one,
 * so it covers siblings that do not exist yet. The one thing the mod cannot decide for the user is
 * which shaderpack they want, so the new rule keeps its action at {@code NONE} and says so.
 */
public final class DimensionPickerScreen extends Screen {
	/** Callback for a chosen dimension; {@code dimensionType} may be {@code null}. */
	public interface Picker {
		void pick(DimensionId dimension, DimensionId dimensionType);
	}

	private static final int ROW_HEIGHT = 22;
	/** Room below the list for the hint line, the id field and the button row. */
	private static final int BOTTOM = 80;

	private final Screen parent;
	private final DimensionSwitchController controller;
	private final Picker picker;

	private final List<SimpleListWidget.Row> rows = new ArrayList<>();
	private final List<DimensionId> rowDimension = new ArrayList<>();
	private final List<DimensionId> rowType = new ArrayList<>();

	private EditBox customBox;
	private String error = "";

	public DimensionPickerScreen(Screen parent, DimensionSwitchController controller, Picker picker) {
		super(Component.translatable("spacejoy_mod.irisdim.screen.dimensions"));
		this.parent = parent;
		this.controller = controller;
		this.picker = picker;
	}

	@Override
	protected void init() {
		rows.clear();
		rowDimension.clear();
		rowType.clear();

		addHeader(Component.translatable("spacejoy_mod.irisdim.dimensions.observed"));

		for (DiscoveredDimensions.Entry entry : controller.discovered().entries()) {
			DimensionId dimension = DimensionId.tryParse(entry.dimension);

			if (dimension == null) {
				continue;
			}

			DimensionId type = entry.dimensionType == null ? null : DimensionId.tryParse(entry.dimensionType);
			DimensionContext context = new DimensionContext(dimension, type, entry.worldKey);
			Optional<DimensionRule> matched = controller.ruleSet().match(context);

			Component coverage;

			if (matched.isPresent()) {
				DimensionRule rule = matched.get();
				coverage = Component.literal(rule.action().isNoop() ? "\u00b7 " : "\u2192 ").append(Labels.rule(rule.id()));
			} else {
				coverage = Component.literal("! ").append(Component.translatable("spacejoy_mod.irisdim.coverage.none"));
			}

			// The raw id stays on screen whenever the name above it is a translation: this screen
			// exists to hand the user the exact string to write into a rule, so a friendly name must
			// never be the only thing visible.
			MutableComponent detail = Component.empty();

			if (Labels.hasDimensionName(dimension.toString())) {
				detail.append(Component.literal(dimension + "   "));
			}

			detail.append(coverage)
				.append(Component.literal("   [" + (type == null ? "?" : type) + "]   "))
				.append(Component.translatable("spacejoy_mod.irisdim.dimensions.seen", entry.seen));

			rows.add(new SimpleListWidget.Row(Labels.dimension(dimension), detail,
				matched.isPresent() && !matched.get().action().isNoop() ? 0xFF55FF55 : 0xFFFFFFFF, true));
			rowDimension.add(dimension);
			rowType.add(type);
		}

		if (rowDimension.isEmpty()) {
			addHeader(Component.translatable("spacejoy_mod.irisdim.dimensions.empty"));
		}

		int listBottom = this.height - BOTTOM;

		addRenderableWidget(new SimpleListWidget(8, 26, this.width - 16, listBottom - 26, ROW_HEIGHT, rows,
			Component.translatable("spacejoy_mod.irisdim.dimensions.empty"), -1, this::picked));

		int fieldY = this.height - BOTTOM + 18;
		customBox = addRenderableWidget(new EditBox(this.font, 8, fieldY, this.width - 130, 20,
			Component.translatable("spacejoy_mod.irisdim.dimensions.custom")));
		customBox.setMaxLength(256);
		customBox.setHint(Component.translatable("spacejoy_mod.irisdim.dimensions.customHint"));
		customBox.setResponder(value -> error = "");

		addRenderableWidget(Button.builder(Component.translatable("spacejoy_mod.irisdim.button.create"), button -> createFromField())
			.bounds(this.width - 118, fieldY, 110, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
			.bounds(this.width / 2 - 60, this.height - 26, 120, 20).build());
	}

	private void addHeader(Component text) {
		rows.add(SimpleListWidget.Row.header(text));
		rowDimension.add(null);
		rowType.add(null);
	}

	private void picked(int index) {
		DimensionId dimension = rowDimension.get(index);

		if (dimension == null) {
			return;
		}

		picker.pick(dimension, rowType.get(index));
		onClose();
	}

	private void createFromField() {
		DimensionId dimension = DimensionId.tryParse(customBox.getValue());

		if (dimension == null) {
			error = Component.translatable("spacejoy_mod.irisdim.dimensions.invalid").getString();
			return;
		}

		picker.pick(dimension, null);
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(this.font, this.title, this.width / 2, 9, 0xFFFFFFFF);
		graphics.text(this.font, Component.translatable("spacejoy_mod.irisdim.dimensions.hint"),
			8, this.height - BOTTOM + 6, 0xFF9BA3B5, false);

		if (!error.isEmpty()) {
			graphics.text(this.font, error, 8, this.height - BOTTOM + 42, 0xFFFF8A5C, false);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}
}
