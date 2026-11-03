package net.spacejoy.mod.irisdim.client.gui;

import net.spacejoy.mod.irisdim.compat.iris.IrisBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picks a shaderpack for one rule.
 *
 * <p>The list is {@code shaderpacks/} as Iris sees it -- folder packs must contain a
 * {@code shaders/} directory, zip packs must contain a {@code shaders/} entry -- so every entry can
 * actually be loaded, and the pack Iris is using right now is marked. Names are copied verbatim
 * into the rule, which means no guessing about extensions.
 */
public final class PackPickerScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int BOTTOM = 30;

	private final Screen parent;
	private final String current;
	private final Consumer<String> onPicked;

	private final List<String> values = new ArrayList<>();

	public PackPickerScreen(Screen parent, String current, Consumer<String> onPicked) {
		super(Component.translatable("spacejoy_mod.irisdim.screen.packs"));
		this.parent = parent;
		this.current = current;
		this.onPicked = onPicked;
	}

	@Override
	protected void init() {
		values.clear();

		List<SimpleListWidget.Row> rows = new ArrayList<>();
		rows.add(SimpleListWidget.Row.of(Component.translatable("spacejoy_mod.irisdim.pack.none"), 0xFFCCCCCC));
		values.add("");

		String inUse = IrisBridge.currentPackName().orElse(null);
		List<String> packs = IrisBridge.availablePacks();

		for (String pack : packs) {
			boolean selected = pack.equals(current);
			Component label = Component.literal((selected ? "✔ " : "   ") + pack);
			Component detail = pack.equals(inUse)
				? Component.translatable("spacejoy_mod.irisdim.pack.inUse")
				: Component.empty();
			rows.add(new SimpleListWidget.Row(label, detail, selected ? 0xFF55FF55 : 0xFFFFFFFF, true));
			values.add(pack);
		}

		int listBottom = this.height - BOTTOM;

		addRenderableWidget(new SimpleListWidget(8, 26, this.width - 16, listBottom - 26, ROW_HEIGHT, rows,
			Component.translatable("spacejoy_mod.irisdim.screen.packs.empty"), values.indexOf(current),
			index -> {
				onPicked.accept(values.get(index));
				onClose();
			}));

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
			.bounds(this.width / 2 - 60, this.height - 26, 120, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 9, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}
}
