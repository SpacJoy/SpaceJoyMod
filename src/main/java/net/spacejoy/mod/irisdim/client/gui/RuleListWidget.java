package net.spacejoy.mod.irisdim.client.gui;

import net.spacejoy.mod.irisdim.config.RuleSpec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rule list on the config screen.
 *
 * <p>A row shows the three things worth seeing at a glance -- whether the rule is on, what it
 * matches, and what it does -- and carries its own toggle zone: clicking the checkbox flips
 * {@code enabled}, clicking anywhere else selects the rule for the detail panel. Toggling a rule
 * that is already selected therefore does not disturb the panel, which is the behaviour you want
 * when you are skimming a list switching things on and off.
 */
public final class RuleListWidget extends ScrollListWidget {
	public static final int ROW_HEIGHT = 28;

	private static final int CHECKBOX = 12;
	private static final int PAD = 4;

	private static final int ROW_SELECTED = 0x604A6FA5;
	private static final int ROW_HOVER = 0x30FFFFFF;
	private static final int ROW_PLAIN = 0x20000000;

	private final ConfigScreen screen;
	private final List<RuleSpec> rules;
	/**
	 * Display order: file positions, sorted by priority, highest first.
	 *
	 * <p>Only the view sorts. The model keeps its file order, because that order is what gets
	 * written back to config.json and silently reshuffling a hand-written file on save would be
	 * rude. Sorted stably on priority alone -- the one field that decides which rule wins -- so
	 * rules sharing a priority stay in file order, exactly the way {@code RuleSet} resolves them.
	 */
	private final int[] order;

	public RuleListWidget(ConfigScreen screen, int x, int y, int width, int height, List<RuleSpec> rules) {
		super(x, y, width, height, ROW_HEIGHT);
		this.screen = screen;
		this.rules = rules;
		this.order = displayOrder(rules);
	}

	private static int[] displayOrder(List<RuleSpec> rules) {
		List<Integer> positions = new ArrayList<>(rules.size());

		for (int index = 0; index < rules.size(); index++) {
			positions.add(index);
		}

		// List#sort is stable, so equal priorities keep their file order.
		positions.sort((left, right) -> Integer.compare(rules.get(right).priority, rules.get(left).priority));

		int[] sorted = new int[positions.size()];

		for (int index = 0; index < sorted.length; index++) {
			sorted[index] = positions.get(index);
		}

		return sorted;
	}

	/** File position of the rule drawn at display row {@code index}; {@code -1} when out of range. */
	private int modelIndex(int index) {
		if (index < 0 || index >= order.length || order[index] >= rules.size()) {
			return -1;
		}

		return order[index];
	}

	@Override
	protected int rowCount() {
		return Math.min(order.length, rules.size());
	}

	@Override
	protected Component emptyMessage() {
		return Component.translatable("spacejoy_mod.irisdim.screen.rules.empty");
	}

	@Override
	protected void renderRow(GuiGraphicsExtractor graphics, int index, int rowX, int rowTop, int rowWidth,
							 int mouseX, int mouseY) {
		int model = modelIndex(index);

		if (model < 0) {
			return;
		}

		RuleSpec rule = rules.get(model);
		boolean selected = model == screen.selectedIndex();
		boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth
			&& mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;

		int background = selected ? ROW_SELECTED : (hovered ? ROW_HOVER : ROW_PLAIN);
		graphics.fill(rowX, rowTop, rowX + rowWidth, rowTop + ROW_HEIGHT - 1, background);

		// Enabled checkbox: filled when on, outlined when off. Drawn manually rather than using a
		// widget so that a row stays a single click target.
		int boxX = rowX + PAD;
		int boxY = rowTop + (ROW_HEIGHT - CHECKBOX) / 2;
		graphics.fill(boxX, boxY, boxX + CHECKBOX, boxY + CHECKBOX, 0xFF101014);
		int border = rule.enabled ? 0xFF55FF55 : 0xFF707070;
		fillOutline(graphics, boxX, boxY, CHECKBOX, border);

		if (rule.enabled) {
			graphics.fill(boxX + 3, boxY + 3, boxX + CHECKBOX - 3, boxY + CHECKBOX - 3, 0xFF55FF55);
		}

		int textX = boxX + CHECKBOX + 6;
		int textWidth = rowX + rowWidth - textX - 4;
		int titleColour = rule.enabled ? 0xFFFFFFFF : 0xFF8A8A8A;
		int detailColour = rule.enabled ? 0xFFB4B4B4 : 0xFF6A6A6A;

		drawClipped(graphics, title(rule, model), textX, rowTop + 4, textWidth, titleColour);
		drawClipped(graphics, describe(rule), textX, rowTop + 15, textWidth, detailColour);
	}

	@Override
	protected void rowClicked(int index, int localX, int localY) {
		int model = modelIndex(index);

		if (model < 0) {
			return;
		}

		if (localX < CHECKBOX + PAD * 2) {
			screen.toggleEnabled(model);
		} else {
			screen.select(model);
		}
	}

	private static void fillOutline(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
		graphics.fill(x, y, x + size, y + 1, colour);
		graphics.fill(x, y + size - 1, x + size, y + size, colour);
		graphics.fill(x, y, x + 1, y + size, colour);
		graphics.fill(x + size - 1, y, x + size, y + size, colour);
	}

	static String displayId(RuleSpec rule, int index) {
		return rule.id == null || rule.id.isBlank() ? "rule-" + index : rule.id;
	}

	/**
	 * A row's title line: the rule id with its priority, e.g. {@code #my-rule   P100}.
	 *
	 * <p>Shipped rules get a translated name instead of the raw id -- {@code #vanilla-overworld}
	 * reads as "Overworld" or "主世界" -- so the list speaks the player's language. Ids stay
	 * machine-readable in config.json either way; only the label changes.
	 */
	static Component title(RuleSpec rule, int index) {
		return Labels.rule(displayId(rule, index))
			.copy()
			.append(Component.literal("   P" + rule.priority));
	}

	/** One-line summary: what it matches, then what it does. */
	static Component describe(RuleSpec rule) {
		return matchSummary(rule)
			.copy()
			.append(Component.literal("  \u2192  "))
			.append(actionSummary(rule));
	}

	/**
	 * What the rule matches, with known dimension ids replaced by their translated names.
	 *
	 * <p>{@link Labels#dimension(String)} falls back to the raw id, so wildcards and modded
	 * dimensions pass through unchanged -- {@code twilightforest:*} still reads as itself, and a
	 * rule that matches nothing recognisable shows exactly the string the user typed.
	 */
	static Component matchSummary(RuleSpec rule) {
		if (rule.match != null) {
			if (rule.match.dimensions != null && !rule.match.dimensions.isEmpty()) {
				return names(rule.match.dimensions);
			}

			if (rule.match.dimensionTypes != null && !rule.match.dimensionTypes.isEmpty()) {
				return names(rule.match.dimensionTypes)
					.append(Component.translatable("spacejoy_mod.irisdim.match.typeSuffix"));
			}

			if (rule.match.worlds != null && !rule.match.worlds.isEmpty()) {
				return names(rule.match.worlds)
					.append(Component.translatable("spacejoy_mod.irisdim.match.worldSuffix"));
			}
		}

		return Component.literal("*");
	}

	private static MutableComponent names(List<String> ids) {
		MutableComponent joined = Component.empty();

		for (int index = 0; index < ids.size(); index++) {
			if (index > 0) {
				joined.append(Component.literal(", "));
			}

			joined.append(Labels.dimension(ids.get(index)));
		}

		return joined;
	}

	/**
	 * What the rule does. Deliberately parses the raw mode token rather than going through
	 * {@code ShaderAction.parseMode}, because this runs while painting: an unfinished rule being
	 * typed must render as text, never raise.
	 */
	static Component actionSummary(RuleSpec rule) {
		if (rule.shader == null) {
			return Component.translatable("spacejoy_mod.irisdim.mode.NONE");
		}

		String mode = rule.shader.mode == null ? "NONE" : rule.shader.mode.trim().toUpperCase(Locale.ROOT);

		if (mode.equals("SET_PACK") || mode.equals("SET") || mode.equals("PACK")) {
			if (rule.shader.pack == null || rule.shader.pack.isBlank()) {
				return Component.translatable("spacejoy_mod.irisdim.action.noPack");
			}

			// The pack name is a file name, not a phrase: built as a literal and appended as its own
			// part, so a name carrying §-formatting codes keeps its colours instead of having the
			// codes printed as glyphs by a translatable argument.
			Component label = Component.literal(rule.shader.pack);
			int options = rule.shader.options == null ? 0 : rule.shader.options.size();

			return options == 0
				? label
				: label.copy().append(Component.translatable("spacejoy_mod.irisdim.action.optionCount", options));
		}

		if (mode.equals("DISABLE_SHADERS") || mode.equals("DISABLE") || mode.equals("OFF")) {
			return Component.translatable("spacejoy_mod.irisdim.action.shadersOff");
		}

		return Component.translatable("spacejoy_mod.irisdim.mode.NONE");
	}
}
