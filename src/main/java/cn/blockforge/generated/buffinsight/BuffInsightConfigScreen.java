package cn.blockforge.generated.buffinsight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BuffInsightConfigScreen extends Screen {
    private static final int PANEL_BG = 0xF20C1017;
    private static final int PANEL_EDGE = 0xFF37465A;
    private static final int ACCENT = BuffHudRenderer.THEME_GREEN;
    private static final int HEADER_TEXT = 0xFF8FB4D0;
    private static final int HINT_TEXT = 0xFF77879A;
    private static final int ROW_BG = 0x80101418;
    private static final int ROW_EDGE = 0x5037465A;
    private static final int LABEL_TEXT = 0xFFC9D6E2;
    private static final String VERSION = resolveVersion();
    /** 开/关文案按当前语言解析一次，避免每帧翻译查找 */
    private static final String STATE_ON = Component.translatable("gui.buff_insight.state.on").getString();
    private static final String STATE_OFF = Component.translatable("gui.buff_insight.state.off").getString();

    private final Screen parent;
    private final Map<GuiEventListener, Integer> baseY = new IdentityHashMap<>();
    private Button resetButton;
    private Button doneButton;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int scrollY;
    private int maxScroll;
    private boolean previewOn;
    private List<EffectSnapshot> previewSamples = List.of();
    private long previewBuiltAt;

    public BuffInsightConfigScreen(Screen parent) {
        super(Component.translatable("gui.buff_insight.title"));
        this.parent = parent;
    }

    private static String resolveVersion() {
        try {
            return ModList.get().getModContainerById(GeneratedMod.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("dev");
        } catch (Throwable ignored) {
            return "dev";
        }
    }

    @Override
    protected void init() {
        BuffInsightConfig config = BuffInsightConfig.get();
        baseY.clear();
        previewOn = width >= 476 && height >= 260;

        panelW = Math.min(previewOn ? 464 : 344, width - 12);
        int zoneW = previewOn ? panelW - 146 : panelW;
        int colW = (zoneW - 28) / 2;
        int colL = 0;
        int colR = colW + 8;
        int y = 30;

        List<AbstractWidget> built = new ArrayList<>();

        // ── 显示 ──
        y = section(colL, y, "section.display", zoneW - 20, built);
        y = row(enumRow(colL, y, colW, "displayMode",
                        () -> Component.translatable(config.displayMode.key()),
                        () -> config.displayMode = config.displayMode.next()),
                enumRow(colR, y, colW, "style",
                        () -> Component.translatable(config.visualStyle.key()),
                        () -> config.visualStyle = config.visualStyle.next()), y, built);
        y = row(enumRow(colL, y, colW, "layout",
                        () -> Component.translatable(config.layout.key()),
                        () -> config.layout = config.layout.next()),
                enumRow(colR, y, colW, "readMode",
                        () -> Component.translatable(config.readMode.key()),
                        () -> config.readMode = config.readMode.next()), y, built);
        y = row(toggleRow(colL, y, colW, "progressBar", () -> config.showProgressBar,
                        value -> config.showProgressBar = value),
                toggleRow(colR, y, colW, "percentage", () -> config.showPercentage,
                        value -> config.showPercentage = value), y, built);
        y = row(toggleRow(colL, y, colW, "attributes", () -> config.showAttributes,
                        value -> config.showAttributes = value),
                toggleRow(colR, y, colW, "adaptive", () -> config.adaptiveLayout,
                        value -> config.adaptiveLayout = value), y, built);

        built.add(sliderRow(colL, y, colW, "opacity", (config.opacity - 0.2D) / 0.8D,
                value -> config.opacity = 0.2D + value * 0.8D,
                value -> Component.translatable("gui.buff_insight.opacity",
                        Math.round((0.2D + value * 0.8D) * 100.0D) + "%")));
        built.add(sliderRow(colR, y, colW, "cardWidth", (config.cardWidthFactor - 0.7D) / 1.1D,
                value -> config.cardWidthFactor = 0.7D + value * 1.1D,
                value -> Component.translatable("gui.buff_insight.cardWidth",
                        Math.round((0.7D + value * 1.1D) * 100.0D) + "%")));
        y += 22;
        built.add(sliderRow(colL, y, colW, "cardHeight", (config.cardHeightFactor - 0.8D) / 0.8D,
                value -> config.cardHeightFactor = 0.8D + value * 0.8D,
                value -> Component.translatable("gui.buff_insight.cardHeight",
                        Math.round((0.8D + value * 0.8D) * 100.0D) + "%")));
        built.add(sliderRow(colR, y, colW, "noticeSeconds", (config.noticeSeconds - 1.0D) / 9.0D,
                value -> config.noticeSeconds = 1.0D + value * 9.0D,
                value -> Component.translatable("gui.buff_insight.noticeSeconds",
                        String.format(java.util.Locale.ROOT, "%.1fs", 1.0D + value * 9.0D))));
        y += 26;

        // ── 行为与反馈 ──
        y = section(colL, y, "section.behavior", zoneW - 20, built);
        y = row(toggleRow(colL, y, colW, "glow", () -> config.countdownGlow,
                        value -> config.countdownGlow = value),
                toggleRow(colR, y, colW, "gainSound", () -> config.gainSound,
                        value -> config.gainSound = value), y, built);
        y = row(toggleRow(colL, y, colW, "removeFeedback", () -> config.removeFeedback,
                        value -> config.removeFeedback = value),
                toggleRow(colR, y, colW, "particles", () -> config.worldParticles,
                        value -> config.worldParticles = value), y, built);
        y += 2;

        // ── AI 分析 ──
        y = section(colL, y, "section.ai", zoneW - 20, built);
        y = row(toggleRow(colL, y, colW, "ai", () -> config.aiEnabled,
                        value -> config.aiEnabled = value),
                textField(colR, y, colW, "aiModel", config.aiModel, 60,
                        value -> config.aiModel = value), y, built);
        built.add(textField(colL, y, zoneW - 20, "aiEndpoint", config.aiEndpoint, 200,
                value -> config.aiEndpoint = value));
        y += 22;
        built.add(textField(colL, y, zoneW - 20, "aiApiKey", config.aiApiKey, 120,
                value -> config.aiApiKey = value));
        y += 22;

        int contentBottom = y + 20;
        panelH = Math.min(Math.max(150, contentBottom + 32), height - 8);
        panelX = width / 2 - panelW / 2;
        panelY = height / 2 - panelH / 2;

        int absoluteLeft = panelX + 10;
        int absoluteRight = panelX + 10 + colW + 8;
        for (AbstractWidget widget : built) {
            widget.setX(widget.getX() == colR ? absoluteRight : absoluteLeft);
            // 内容控件此前使用面板内相对坐标，这里统一落到绝对坐标
            widget.setY(widget.getY() + panelY);
            baseY.put(widget, widget.getY());
        }
        for (AbstractWidget widget : built) {
            addRenderableWidget(widget);
        }
        repositionScrollables();

        // 底部按钮固定在按钮条内，不参与滚动；渲染在裁剪区之外单独进行
        Button reset = Button.builder(lang("reset"), button -> {
            BuffInsightConfig.reset();
            rebuildWidgets();
        }).bounds(panelX + 10, panelY + panelH - 24, colW, 20).build();
        Button done = Button.builder(lang("done"), button -> onClose())
                .bounds(panelX + panelW - 10 - colW, panelY + panelH - 24, colW, 20).build();
        addWidget(reset);
        addWidget(done);
        this.resetButton = reset;
        this.doneButton = done;

        maxScroll = Math.max(0, contentBottom + 6 - (panelH - 32));
        scrollY = Math.min(scrollY, maxScroll);
    }

    private int row(AbstractWidget left, AbstractWidget right, int y, List<AbstractWidget> built) {
        built.add(left);
        built.add(right);
        return y + 22;
    }

    private int section(int x, int y, String key, int width, List<AbstractWidget> built) {
        built.add(new SectionLabel(x, y, lang(key), width));
        return y + 15;
    }

    private static Component lang(String key) {
        return Component.translatable("gui.buff_insight." + key);
    }

    private static void tooltip(AbstractWidget widget, String key) {
        widget.setTooltip(Tooltip.create(Component.translatable("gui.buff_insight." + key + ".desc")));
    }

    /** 自绘开关行：左侧标签 + 右侧状态胶囊（开关文案静态解析一次） */
    private AbstractWidget toggleRow(int x, int y, int width, String name, BooleanSupplier getter,
                                     Consumer<Boolean> setter) {
        OptionRow row = new OptionRow(x, y, width, 20, lang(name),
                () -> Component.literal(getter.getAsBoolean() ? STATE_ON : STATE_OFF),
                () -> setter.accept(!getter.getAsBoolean()), getter);
        tooltip(row, name);
        return row;
    }

    /** 自绘循环选项行：左侧标签 + 右侧当前值胶囊 */
    private AbstractWidget enumRow(int x, int y, int width, String name,
                                   Supplier<Component> value, Runnable cycle) {
        OptionRow row = new OptionRow(x, y, width, 20, lang(name), value, cycle, null);
        tooltip(row, name);
        return row;
    }

    /** 主题化滑条 */
    private AbstractWidget sliderRow(int x, int y, int width, String name, double initial,
                                     Consumer<Double> setter, java.util.function.DoubleFunction<Component> message) {
        ThemedSlider slider = new ThemedSlider(x, y, width, 20, lang(name), initial) {
            @Override
            protected void applyValue(double value) {
                setter.accept(value);
            }

            @Override
            protected Component display(double value) {
                return message.apply(value);
            }
        };
        tooltip(slider, name);
        return slider;
    }

    private AbstractWidget textField(int x, int y, int width, String name, String initial, int maxLength,
                                     Consumer<String> setter) {
        EditBox box = new EditBox(font, x, y, width, 20, lang(name));
        box.setMaxLength(maxLength);
        box.setValue(initial);
        box.setResponder(setter);
        box.setHint(lang(name));
        tooltip(box, name);
        return box;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            int next = Mth.clamp(scrollY - (int) Math.signum(delta) * 18, 0, maxScroll);
            if (next != scrollY) {
                scrollY = next;
                repositionScrollables();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void repositionScrollables() {
        for (Map.Entry<GuiEventListener, Integer> entry : baseY.entrySet()) {
            if (entry.getKey() instanceof AbstractWidget widget) {
                widget.setY(entry.getValue() - scrollY);
            }
        }
    }

    @Override
    public void onClose() {
        BuffInsightConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        // 面板与头部
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, panelW, panelH, PANEL_EDGE);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, ACCENT);
        graphics.fill(panelX + 1, panelY + 2, panelX + panelW - 1, panelY + 24, 0x40060A10);
        graphics.drawCenteredString(font, title, panelX + panelW / 2, panelY + 7, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui.buff_insight.hint",
                        ClientBootstrap.OPEN_CONFIG.getTranslatedKeyMessage()),
                panelX + panelW / 2, panelY + 18, HINT_TEXT);
        graphics.drawCenteredString(font, "v" + VERSION, panelX + panelW / 2, panelY + panelH - 12, 0xFF4E5D6C);
        // 底部按钮上方的分隔线
        graphics.fill(panelX + 10, panelY + panelH - 28, panelX + panelW - 10, panelY + panelH - 27, 0x4037465A);
        // 预览区分隔与内衬背景
        if (previewOn) {
            int dividerX = panelX + panelW - 138;
            graphics.fill(dividerX, panelY + 28, dividerX + 1, panelY + panelH - 30, 0x4037465A);
            graphics.fill(dividerX + 4, panelY + 30, panelX + panelW - 6, panelY + panelH - 32, 0x3304070C);
        }
        // 内容区裁剪在按钮条上方截止，滚动内容不会压到底部按钮
        graphics.enableScissor(panelX + 1, panelY + 25, panelX + panelW - 1, panelY + panelH - 30);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        // 底部按钮在裁剪区外单独绘制
        if (resetButton != null) resetButton.render(graphics, mouseX, mouseY, partialTick);
        if (doneButton != null) doneButton.render(graphics, mouseX, mouseY, partialTick);
        // 滚动位置指示条
        if (maxScroll > 0) {
            int trackTop = panelY + 28;
            int trackBottom = panelY + panelH - 34;
            int trackHeight = trackBottom - trackTop;
            if (trackHeight > 10) {
                int thumbHeight = Math.max(12, (int) ((long) trackHeight * trackHeight / (trackHeight + maxScroll)));
                int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollY / maxScroll;
                int trackX = panelX + (previewOn ? panelW - 146 : panelW) - 7;
                graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0x30222C38);
                graphics.fill(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, 0xFF4E637A);
            }
        }
        if (previewOn) {
            drawPreview(graphics);
        }
    }

    /** 右侧实时预览：三行示例（增益有属性 / 增益纯描述 / 减益），随当前风格即时变化；样例 500ms 重建一次 */
    private void drawPreview(GuiGraphics graphics) {
        BuffInsightConfig config = BuffInsightConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        int previewX = panelX + panelW - 128;
        int previewW = 118;
        int previewY = panelY + 44;
        graphics.drawCenteredString(font, lang("preview"), previewX + previewW / 2, previewY - 14, HEADER_TEXT);

        long now = net.minecraft.Util.getMillis();
        if (previewSamples.isEmpty() || now - previewBuiltAt > 500L) {
            long ticks = now / 50L;
            List<EffectSnapshot> samples = new ArrayList<>(3);
            samples.add(EffectSnapshot.of(new MobEffectInstance(MobEffects.DIG_SPEED,
                    (int) (600 + ticks % 900), 1), 1500));
            samples.add(EffectSnapshot.of(new MobEffectInstance(MobEffects.REGENERATION,
                    (int) (300 + ticks % 600), 0), 900));
            samples.add(EffectSnapshot.of(new MobEffectInstance(MobEffects.POISON,
                    (int) (200 + ticks % 500), 0), 700));
            previewSamples = samples;
            previewBuiltAt = now;
        }

        int rowH = BuffHudRenderer.baseHeight(config.visualStyle)
                + (config.showAttributes ? BuffHudRenderer.ROW_LINE_HEIGHT : 0);
        for (int i = 0; i < previewSamples.size(); i++) {
            BuffHudRenderer.drawPreviewRow(graphics, minecraft, previewSamples.get(i),
                    previewX, previewY + i * (rowH + 4), previewW, rowH, config);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 分区标题：强调色小方块 + 文字 + 延伸细线 */
    private static final class SectionLabel extends AbstractWidget {
        private final Component text;
        private final int zoneWidth;

        private SectionLabel(int x, int y, Component text, int zoneWidth) {
            super(x, y, zoneWidth, 10, Component.empty());
            this.text = text;
            this.zoneWidth = zoneWidth;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY() + 1, getX() + 2, getY() + 9, ACCENT);
            graphics.drawString(Minecraft.getInstance().font, text, getX() + 6, getY() + 1, HEADER_TEXT);
            int textEnd = getX() + 6 + Minecraft.getInstance().font.width(text) + 6;
            if (textEnd < getX() + zoneWidth) {
                graphics.fill(textEnd, getY() + 5, getX() + zoneWidth, getY() + 6, 0x4037465A);
            }
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        }
    }

    /** 主题行：深色底 + 悬停高亮 + 右侧数值/状态胶囊，点击切换 */
    private static final class OptionRow extends AbstractWidget {
        private final Supplier<Component> value;
        private final Runnable activate;
        private final BooleanSupplier toggleState;
        /** 胶囊文案缓存：仅在构建与点击切换时重建，渲染帧直接复用 */
        private Component valueCache;

        private OptionRow(int x, int y, int width, int height, Component label,
                          Supplier<Component> value, Runnable activate, BooleanSupplier toggleState) {
            super(x, y, width, height, label);
            this.value = value;
            this.activate = activate;
            this.toggleState = toggleState;
            this.valueCache = value.get();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean hovered = isHovered();
            graphics.fill(getX(), getY(), getX() + width, getY() + height, hovered ? 0xA01A2230 : ROW_BG);
            graphics.renderOutline(getX(), getY(), width, height, hovered ? 0x80496178 : ROW_EDGE);

            Component valueText = valueCache;
            int pillWidth = minecraft.font.width(valueText) + 12;
            int pillX = getX() + width - pillWidth - 4;
            int pillY = getY() + 3;
            int pillH = height - 6;
            int pillBg;
            int pillText;
            if (toggleState != null && toggleState.getAsBoolean()) {
                pillBg = 0x662E7D46;
                pillText = 0xFF7FE0A0;
            } else if (toggleState != null) {
                pillBg = 0x66424A54;
                pillText = 0xFF9AA8B5;
            } else {
                pillBg = 0x66224365;
                pillText = 0xFF8FC1FF;
            }
            graphics.fill(pillX, pillY, pillX + pillWidth, pillY + pillH, pillBg);
            graphics.drawCenteredString(minecraft.font, valueText, pillX + pillWidth / 2,
                    pillY + (pillH - 8) / 2, pillText);

            // 标签绘制到胶囊左侧，超长滚动裁剪（滚动偏移复用渲染器共享实现）
            Component label = getMessage();
            int labelRight = pillX - 4;
            int labelX = getX() + 5;
            if (minecraft.font.width(label) > labelRight - labelX) {
                graphics.enableScissor(labelX, getY(), labelRight, getY() + height);
                int overflow = minecraft.font.width(label) - (labelRight - labelX);
                graphics.drawString(minecraft.font, label,
                        labelX - BuffHudRenderer.scrollOffset(overflow),
                        getY() + (height - 8) / 2, hovered ? 0xFFFFFFFF : LABEL_TEXT);
                graphics.disableScissor();
            } else {
                graphics.drawString(minecraft.font, label, labelX, getY() + (height - 8) / 2,
                        hovered ? 0xFFFFFFFF : LABEL_TEXT);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            activate.run();
            valueCache = value.get();
        }

        @Override
        public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /** 主题滑条：深色轨道 + 强调色填充 + 浅色滑块，标签与数值同行（数值文案仅在取值变化时重建） */
    private abstract static class ThemedSlider extends AbstractWidget {
        protected double value;
        private Component messageCache;

        private ThemedSlider(int x, int y, int width, int height, Component label, double initial) {
            super(x, y, width, height, label);
            this.value = Mth.clamp(initial, 0.0D, 1.0D);
            this.messageCache = display(this.value);
        }

        protected abstract void applyValue(double value);

        protected abstract Component display(double value);

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean hovered = isHovered();
            graphics.fill(getX(), getY(), getX() + width, getY() + height, hovered ? 0xA01A2230 : ROW_BG);
            graphics.renderOutline(getX(), getY(), width, height, hovered ? 0x80496178 : ROW_EDGE);
            int trackX = getX() + 5;
            int trackW = width - 10;
            int trackY = getY() + height - 7;
            graphics.fill(trackX, trackY, trackX + trackW, trackY + 3, 0xFF1A222D);
            int filled = (int) Math.round(trackW * value);
            graphics.fill(trackX, trackY, trackX + filled, trackY + 3, 0xFF4E9E74);
            int knobX = trackX + filled - 1;
            graphics.fill(knobX, trackY - 2, knobX + 3, trackY + 5, 0xFFE8F2F6);
            graphics.drawString(minecraft.font, messageCache, getX() + 5, getY() + 3,
                    hovered ? 0xFFFFFFFF : LABEL_TEXT);
        }

        private void setFromMouse(double mouseX) {
            value = Mth.clamp((mouseX - (getX() + 5)) / (width - 10), 0.0D, 1.0D);
            applyValue(value);
            messageCache = display(value);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
            setFromMouse(mouseX);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_LEFT && value > 0.0D) {
                value = Math.max(0.0D, value - 0.05D);
                applyValue(value);
                messageCache = display(value);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT && value < 1.0D) {
                value = Math.min(1.0D, value + 0.05D);
                applyValue(value);
                messageCache = display(value);
                return true;
            }
            return false;
        }

        @Override
        public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        }
    }
}
