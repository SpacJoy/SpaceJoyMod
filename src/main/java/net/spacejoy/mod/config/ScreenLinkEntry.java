package net.spacejoy.mod.config;

import java.util.List;
import java.util.Optional;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config 里的一条「按钮条目」，点开另一个界面。
 *
 * <p>Cloth 自带的条目全是改值的（开关、输入框、滑条），没有「跳到子界面」这种。规则编辑器是
 * 一个自带列表和详情面板的自绘界面，用 Cloth 的条目拼不出来，所以这里只做一个入口：
 * 左边是条目标题，右边一个按钮，点了就 {@code setScreenAndShow} 到目标界面。
 *
 * <p>泛型参数用 {@link Void} 是因为它不持有任何配置值。Cloth 会调用 {@code getValue()} 和
 * {@code isEdited()}，这里分别返回 {@code null} 和 {@code false}，保存时不会写任何东西。
 *
 * <p>继承 {@link TooltipListEntry} 而不是 {@code AbstractConfigListEntry}：后者没有 tooltip 支持，
 * 悬停说明是 {@code TooltipListEntry} 在 {@code extractRenderState} 里画的。复用它就不用自己
 * 处理鼠标悬停判定和文本框的换行。
 */
@Environment(EnvType.CLIENT)
public final class ScreenLinkEntry extends TooltipListEntry<Void> {
    private final Button button;

    public ScreenLinkEntry(Component fieldName, Component buttonLabel, Runnable onPress) {
        // 没有说明时给一个恒空的 supplier，父类照样能正常工作。
        this(fieldName, buttonLabel, onPress, null);
    }

    public ScreenLinkEntry(Component fieldName, Component buttonLabel, Runnable onPress,
                           Component tooltip) {
        super(fieldName, tooltip == null
                ? () -> Optional.<Component[]>empty()
                : () -> Optional.<Component[]>of(new Component[]{tooltip}));
        this.button = Button.builder(buttonLabel, b -> onPress.run()).build();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth,
                                   int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        // 必须先让父类跑一遍：悬停 tooltip 是它在 extractRenderState 末尾画的，
        // 自己整个盖掉就再也看不到说明了。
        super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY,
                isHovered, delta);

        // 按钮贴右边，宽度最多 160：左边留出标题的位置，窄窗口下按一半宽度收。
        int buttonWidth = Math.min(160, Math.max(60, entryWidth / 2));
        button.setX(x + entryWidth - buttonWidth);
        button.setY(y);
        button.setWidth(buttonWidth);
        button.setHeight(entryHeight);
        button.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(Minecraft.getInstance().font, getFieldName(), x, y + (entryHeight - 8) / 2,
                getPreferredTextColor(), false);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public boolean isEdited() {
        return false;
    }
}
