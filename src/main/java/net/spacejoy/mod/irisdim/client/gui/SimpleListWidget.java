package net.spacejoy.mod.irisdim.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A {@link ScrollListWidget} whose rows are supplied up front: a label, an optional detail line, a
 * text colour and whether the row can be clicked. Used by both picker screens, which differ only in
 * where their rows come from.
 */
public final class SimpleListWidget extends ScrollListWidget {
	private static final int HOVER = 0x40FFFFFF;
	private static final int SELECTED = 0x604A6FA5;

	/** @param selectable {@code false} renders the row as a section header that ignores clicks */
	public record Row(Component label, Component detail, int colour, boolean selectable) {
		public static Row header(Component label) {
			return new Row(label, Component.empty(), 0xFF808080, false);
		}

		public static Row of(Component label, int colour) {
			return new Row(label, Component.empty(), colour, true);
		}
	}

	private final List<Row> rows;
	private final Component emptyMessage;
	private final IntConsumer onSelected;
	private final int highlighted;

	public SimpleListWidget(int x, int y, int width, int height, int rowHeight, List<Row> rows,
							Component emptyMessage, int highlighted, IntConsumer onSelected) {
		super(x, y, width, height, rowHeight);
		this.rows = List.copyOf(rows);
		this.emptyMessage = emptyMessage;
		this.highlighted = highlighted;
		this.onSelected = onSelected;
	}

	@Override
	protected int rowCount() {
		return rows.size();
	}

	@Override
	protected Component emptyMessage() {
		return emptyMessage;
	}

	@Override
	protected void renderRow(GuiGraphicsExtractor graphics, int index, int rowX, int rowTop, int rowWidth,
							 int mouseX, int mouseY) {
		Row row = rows.get(index);
		boolean hovered = row.selectable() && isRowHovered(rowX, rowTop, rowWidth, mouseX, mouseY);

		if (index == highlighted) {
			graphics.fill(rowX, rowTop, rowX + rowWidth, rowTop + rowHeight() - 1, SELECTED);
		} else if (hovered) {
			graphics.fill(rowX, rowTop, rowX + rowWidth, rowTop + rowHeight() - 1, HOVER);
		}

		drawClipped(graphics, row.label(), rowX + 4, rowTop + 3, rowWidth - 8, row.colour());

		if (row.detail().getString().isEmpty()) {
			return;
		}

		int detailColour = row.selectable() ? 0xFFA0A0A0 : 0xFF707070;
		drawClipped(graphics, row.detail(), rowX + 4, rowTop + 13, rowWidth - 8, detailColour);
	}

	@Override
	protected void rowClicked(int index, int localX, int localY) {
		if (rows.get(index).selectable()) {
			onSelected.accept(index);
		}
	}

	private boolean isRowHovered(int rowX, int rowTop, int rowWidth, int mouseX, int mouseY) {
		return mouseX >= rowX && mouseX < rowX + rowWidth
			&& mouseY >= rowTop && mouseY < rowTop + rowHeight();
	}
}
