package net.spacejoy.mod.irisdim.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * A minimal scrolling list, written by hand instead of built on {@code ObjectSelectionList}.
 *
 * <p>The reason is row behaviour, not taste: these lists need click zones <em>inside</em> a row (the
 * enabled checkbox, for instance) and they need their scroll position to survive a
 * {@code Screen#rebuildWidgets()} pass, which is how the config screen swaps the detail panel when
 * the selection changes. Both are awkward to express through the vanilla selection-list API and
 * trivial here.
 *
 * <p>Subclasses render rows and react to clicks; scrolling, clipping, the scrollbar and the empty
 * state are handled once, here.
 */
public abstract class ScrollListWidget extends AbstractWidget {
	private static final int SCROLLBAR_WIDTH = 5;
	private static final int BACKGROUND = 0x50000000;
	private static final int TRACK = 0x30000000;
	private static final int KNOB = 0x90FFFFFF;
	private static final int EMPTY_TEXT = 0xFF909090;

	private final int rowHeight;
	private double scroll;

	protected ScrollListWidget(int x, int y, int width, int height, int rowHeight) {
		super(x, y, width, height, Component.empty());
		this.rowHeight = rowHeight;
	}

	/** Number of rows to draw. Called every frame, so keep it cheap. */
	protected abstract int rowCount();

	/**
	 * Draws one row. The coordinates are absolute and the scissor is already applied, so a row may
	 * draw outside its own bounds without leaking into the rest of the screen.
	 */
	protected abstract void renderRow(GuiGraphicsExtractor graphics, int index, int rowX, int rowTop, int rowWidth,
									  int mouseX, int mouseY);

	/** A click landed inside row {@code index}; {@code localX}/{@code localY} are row-relative. */
	protected abstract void rowClicked(int index, int localX, int localY);

	/** Shown centred when the list is empty; {@code null} for nothing. */
	protected Component emptyMessage() {
		return null;
	}

	protected Font font() {
		return Minecraft.getInstance().font;
	}

	protected int totalWidth() {
		return getWidth() - SCROLLBAR_WIDTH - 3;
	}

	protected int rowHeight() {
		return rowHeight;
	}

	/**
	 * Draws text clipped to {@code maxWidth}, going through the component pipeline.
	 *
	 * <p>The component pipeline matters: some shaderpack file names begin with {@code §}-style
	 * formatting codes (purely so they look fancy in Iris's own list). Rendering
	 * {@code Component#getString()} would draw those codes as literal glyphs -- which is what Iris
	 * does not do, and why this list used to disagree with Iris about what a pack is called.
	 */
	protected void drawClipped(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int colour) {
		if (maxWidth <= 0) {
			return;
		}

		Font font = font();

		if (font.width(text) <= maxWidth) {
			graphics.text(font, text, x, y, colour, false);
			return;
		}

		List<FormattedCharSequence> firstLine = font.split(text, maxWidth);

		if (!firstLine.isEmpty()) {
			graphics.text(font, firstLine.get(0), x, y, colour, false);
		}
	}

	/** @see #drawClipped(GuiGraphicsExtractor, Component, int, int, int, int) */
	protected void drawClipped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int colour) {
		drawClipped(graphics, Component.literal(text), x, y, maxWidth, colour);
	}

	public double scrollAmount() {
		return scroll;
	}

	public void setScrollAmount(double amount) {
		scroll = clampScroll(amount);
	}

	public void resetScroll() {
		scroll = 0;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(getX(), getY(), getRight(), getBottom(), BACKGROUND);

		scroll = clampScroll(scroll);

		int rows = rowCount();
		int first = (int) Math.floor(scroll);
		int offset = (int) Math.round((scroll - first) * rowHeight);
		int rowWidth = totalWidth();

		graphics.enableScissor(getX(), getY(), getRight(), getBottom());

		int limit = Math.min(rows, first + visibleRows() + 1);

		for (int index = first; index < limit; index++) {
			int rowTop = getY() - offset + (index - first) * rowHeight;
			renderRow(graphics, index, getX() + 2, rowTop, rowWidth, mouseX, mouseY);
		}

		graphics.disableScissor();

		if (rows == 0) {
			Component message = emptyMessage();

			if (message != null) {
				graphics.centeredText(font(), message, getX() + getWidth() / 2, getY() + 10, EMPTY_TEXT);
			}
		}

		renderScrollbar(graphics, rows);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();

		if (!isMouseOver(mouseX, mouseY)) {
			return;
		}

		int index = rowIndexAt(mouseY);

		if (index < 0) {
			return;
		}

		rowClicked(index, (int) (mouseX - (getX() + 2)), (int) (mouseY - rowTopOf(index)));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}

		setScrollAmount(scroll - scrollY);
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		// Rows render their own text and are announced individually by their owning screen; the
		// container itself has nothing meaningful to add.
	}

	private void renderScrollbar(GuiGraphicsExtractor graphics, int rows) {
		int trackX = getRight() - SCROLLBAR_WIDTH;
		graphics.fill(trackX, getY(), getRight(), getBottom(), TRACK);

		double max = maxScroll();

		if (max <= 0 || rows == 0) {
			return;
		}

		int trackHeight = getHeight();
		int knobHeight = Math.max(14, (int) (trackHeight * (visibleRows() / (double) rows)));
		int knobTop = getY() + (int) ((trackHeight - knobHeight) * (scroll / max));
		graphics.fill(trackX, knobTop, getRight(), knobTop + knobHeight, KNOB);
	}

	private int visibleRows() {
		return Math.max(1, getHeight() / rowHeight);
	}

	private double maxScroll() {
		return Math.max(0, rowCount() - visibleRows());
	}

	private double clampScroll(double amount) {
		return Mth.clamp(amount, 0, maxScroll());
	}

	private int rowIndexAt(double mouseY) {
		if (mouseY < getY() || mouseY >= getBottom()) {
			return -1;
		}

		int first = (int) Math.floor(scroll);
		int offset = (int) Math.round((scroll - first) * rowHeight);
		int index = first + (int) ((mouseY - getY() + offset) / rowHeight);

		return index >= 0 && index < rowCount() ? index : -1;
	}

	private int rowTopOf(int index) {
		int first = (int) Math.floor(scroll);
		int offset = (int) Math.round((scroll - first) * rowHeight);
		return getY() - offset + (index - first) * rowHeight;
	}
}
