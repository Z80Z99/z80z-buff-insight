package cn.blockforge.generated.buffinsight;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BuffHudRenderer {
    private static final ResourceLocation VANILLA_ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final long APPEAR_MS = 200L;
    /** 共享主题与布局常量（配置界面复用） */
    public static final int THEME_GREEN = 0xFF55D778;
    public static final int ROW_LINE_HEIGHT = 9;
    public static final int MIN_ROW_HEIGHT = 21;
    public static final long SCROLL_PERIOD_MS = 2600L;

    private BuffHudRenderer() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.POTION_ICONS.id())) return;
        BuffInsightConfig config = BuffInsightConfig.get();
        if (config.displayMode == BuffInsightConfig.DisplayMode.OFF) return;
        if (config.displayMode == BuffInsightConfig.DisplayMode.REPLACE) {
            event.setCanceled(true);
            render(event.getGuiGraphics());
        } else {
            // 并存：原版图标照常，仅弹出临时通知卡片
            drawNotices(event.getGuiGraphics(), Minecraft.getInstance(), config);
        }
    }

    /** 替换模式下取消原版背包效果图标，由本模组面板接管 */
    @SubscribeEvent
    public static void onInventoryMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
        if (BuffInsightConfig.get().displayMode == BuffInsightConfig.DisplayMode.REPLACE) {
            event.setCanceled(true);
        }
    }

    /** 界面打开期间：替换模式画接管面板；关闭/并存模式保留原版图标并提供悬停详细说明 */
    @SubscribeEvent
    public static void onScreenRendered(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        BuffInsightConfig config = BuffInsightConfig.get();
        if (event.getScreen() instanceof BuffInsightConfigScreen) return;
        if (event.getScreen() instanceof EffectRenderingInventoryScreen<?> container) {
            if (config.displayMode == BuffInsightConfig.DisplayMode.REPLACE) {
                List<EffectTracker.DisplayEntry> entries = EffectTracker.display();
                if (entries.isEmpty()) return;
                int panelWidth = panelWidth(config);
                int anchorX = container.getGuiLeft() + container.getXSize() + 6;
                int x;
                int top;
                if (event.getGuiGraphics().guiWidth() - anchorX >= panelWidth + 4) {
                    x = anchorX;
                    top = container.getGuiTop() + 6;
                } else {
                    // 锚点放不下（创造模式物品栏容器过宽等）：退到屏幕右上角，避免遮挡搜索栏和物品网格
                    x = event.getGuiGraphics().guiWidth() - panelWidth - 4;
                    top = 4;
                }
                drawRows(event.getGuiGraphics(), minecraft, entries, config, x, top, panelWidth,
                        event.getMouseX(), event.getMouseY());
            } else {
                drawVanillaHoverTooltip(event.getGuiGraphics(), minecraft, container,
                        event.getMouseX(), event.getMouseY());
            }
            return;
        }
        if (config.displayMode != BuffInsightConfig.DisplayMode.REPLACE) return;
        List<EffectTracker.DisplayEntry> entries = EffectTracker.display();
        if (entries.isEmpty()) return;
        int panelWidth = panelWidth(config);
        boolean left = config.layout == BuffInsightConfig.Layout.LEFT_PANEL;
        int x = left ? 8 : event.getGuiGraphics().guiWidth() - panelWidth - 8;
        int top = left ? 18 : 5;
        drawRows(event.getGuiGraphics(), minecraft, entries, config, x, top, panelWidth,
                event.getMouseX(), event.getMouseY());
    }

    private static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        if (minecraft.screen != null) return;
        List<EffectTracker.DisplayEntry> entries = EffectTracker.display();
        if (entries.isEmpty()) return;
        BuffInsightConfig config = BuffInsightConfig.get();
        int panelWidth = panelWidth(config);
        boolean left = config.layout == BuffInsightConfig.Layout.LEFT_PANEL;
        int x = left ? 8 : graphics.guiWidth() - panelWidth - 8;
        int top = left ? 18 : 5;
        drawRows(graphics, minecraft, entries, config, x, top, panelWidth, -1, -1);
    }

    /** 并存模式：获得效果时在屏幕顶部弹出通知卡片（避开右上角原版图标），到时淡出 */
    private static void drawNotices(GuiGraphics graphics, Minecraft minecraft, BuffInsightConfig config) {
        if (minecraft.player == null || minecraft.options.hideGui) return;
        List<EffectTracker.Notice> notices = EffectTracker.notices();
        if (notices.isEmpty()) return;
        long now = Util.getMillis();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = panelWidth(config);
        int y = 8;
        for (EffectTracker.Notice notice : notices) {
            float appear = smooth((now - notice.showAt()) / (double) APPEAR_MS);
            float fade = Math.max(0.0F, Math.min(1.0F, (notice.hideAt() - now) / (float) EffectTracker.FADE_MS));
            float alpha = appear * fade;
            if (alpha <= 0.02F) continue;
            EffectSnapshot snapshot = notice.snapshot();
            int height = rowHeight(snapshot, config);
            // 小屏幕上堆叠超出可视区域就不再绘制
            if (y + height > guiHeight - 6) break;
            int x = Mth.clamp(guiWidth / 2 - width / 2, 4, Math.max(4, guiWidth - width - 4));
            // 通知弹窗为垂直滑动：从上方滑入，向上滑出
            int slideY = -Math.round((1.0F - appear) * 6.0F + (1.0F - fade) * 6.0F);
            drawEffect(graphics, minecraft, snapshot, x, y + slideY, width, height + 2, config, alpha);
            y += height + 4;
        }
    }

    private static int panelWidth(BuffInsightConfig config) {
        int textOffset = textOffset(config.visualStyle, 28);
        int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        // 固定基准宽度 × 配置系数：宽度不随标题/倒计时文字变化，过长文字走滚动显示
        int width = (int) Math.round((84 + textOffset) * config.cardWidthFactor);
        return Math.max(80, Math.min(guiWidth - 12, width));
    }

    private static void drawRows(GuiGraphics graphics, Minecraft minecraft, List<EffectTracker.DisplayEntry> entries,
                                 BuffInsightConfig config, int x, int top, int panelWidth,
                                 int mouseX, int mouseY) {
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int available = Math.max(42, guiHeight - top - 12);
        int uniformHeight = rowHeight(EffectTracker.snapshots(), config);
        int gap = config.adaptiveLayout
                ? Math.max(MIN_ROW_HEIGHT, Math.min(uniformHeight, available / Math.max(1, entries.size())))
                : uniformHeight;

        long now = Util.getMillis();
        EffectTracker.DisplayEntry hovered = null;
        // 滑动方向跟随布局：右侧面板从右侧滑入/滑出，左侧面板反之
        int slideDir = config.layout == BuffInsightConfig.Layout.LEFT_PANEL ? -1 : 1;

        for (int index = 0; index < entries.size(); index++) {
            EffectTracker.DisplayEntry entry = entries.get(index);
            int y = top + index * gap;
            if (y + MIN_ROW_HEIGHT > guiHeight) break;
            float appear = smooth((now - entry.bornAt()) / (double) APPEAR_MS);
            float fade = entry.alive()
                    ? 1.0F
                    : Math.max(0.0F, Math.min(1.0F, (entry.deathAt() - now) / (float) EffectTracker.FADE_MS));
            float alpha = appear * fade;
            if (alpha <= 0.02F) continue;
            // 滑入 + 滑出：进入时从布局方向滑到位，消失时向布局方向滑回
            int slide = Math.round((1.0F - appear) * 8.0F + (1.0F - fade) * 8.0F) * slideDir;
            drawEffect(graphics, minecraft, entry.snapshot(), x + slide, y, panelWidth, gap, config, alpha);
            if (entry.alive() && mouseX >= x && mouseX <= x + panelWidth
                    && mouseY >= y && mouseY <= y + gap) {
                hovered = entry;
            }
        }

        if (hovered != null) {
            renderRichTooltip(graphics, minecraft, hovered.snapshot(), mouseX, mouseY);
        }
    }

    /** 共享缓动：smoothstep */
    public static float smooth(double progress) {
        float clamped = (float) Math.max(0.0D, Math.min(1.0D, progress));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    /** 共享滚动偏移：超长文字往复滚动的当前像素偏移 */
    public static int scrollOffset(int overflow) {
        if (overflow <= 0) return 0;
        long t = Util.getMillis() % (SCROLL_PERIOD_MS * 2);
        double phase = (double) t / SCROLL_PERIOD_MS;
        if (phase > 1.0D) phase = 2.0D - phase;
        return (int) Math.round(smooth(phase) * overflow);
    }

    private static int rowHeight(List<EffectSnapshot> effects, BuffInsightConfig config) {
        int lines = 0;
        if (config.showAttributes) {
            for (EffectSnapshot snapshot : effects) lines = Math.max(lines, snapshot.hudLines().size());
        }
        return rowHeight(config, lines);
    }

    private static int rowHeight(EffectSnapshot snapshot, BuffInsightConfig config) {
        return rowHeight(config, config.showAttributes ? snapshot.hudLines().size() : 0);
    }

    private static int rowHeight(BuffInsightConfig config, int lines) {
        int base = baseHeight(config.visualStyle) + lines * ROW_LINE_HEIGHT;
        return Math.max(MIN_ROW_HEIGHT, (int) Math.round(base * config.cardHeightFactor));
    }

    static int baseHeight(BuffInsightConfig.VisualStyle style) {
        return switch (style) {
            case MINIMAL -> 22;
            case VANILLA -> 30;
            case CARD -> 28;
        };
    }

    private static int iconSize(BuffInsightConfig.VisualStyle style, int height) {
        int size = style == BuffInsightConfig.VisualStyle.VANILLA ? height - 12 : height - 10;
        return Math.max(18, Math.min(style == BuffInsightConfig.VisualStyle.VANILLA ? 30 : 32, size));
    }

    private static int textOffset(BuffInsightConfig.VisualStyle style, int height) {
        int icon = iconSize(style, height);
        int frame = style == BuffInsightConfig.VisualStyle.VANILLA ? icon + 6 : icon;
        int pad = switch (style) {
            case MINIMAL -> 2;
            case VANILLA -> 5;
            case CARD -> 6;
        };
        return pad + frame + (style == BuffInsightConfig.VisualStyle.MINIMAL ? 3 : 5);
    }

    private static void drawEffect(GuiGraphics graphics, Minecraft minecraft, EffectSnapshot snapshot,
                                   int x, int y, int width, int allocatedHeight,
                                   BuffInsightConfig config, float alpha) {
        BuffInsightConfig.VisualStyle style = config.visualStyle;
        boolean minimal = style == BuffInsightConfig.VisualStyle.MINIMAL;
        boolean vanilla = style == BuffInsightConfig.VisualStyle.VANILLA;
        int visibleAttributeLines = config.showAttributes ? snapshot.hudLines().size() : 0;
        int height = Math.min(rowHeight(config, visibleAttributeLines), allocatedHeight - 2);
        int icon = iconSize(style, height);
        int textOffset = textOffset(style, height);
        int categoryColor = snapshot.categoryColor();
        int alphaI = (int) Math.round(config.opacity * 210.0D * alpha);

        // 即将消失：整卡闪烁
        boolean flashing = config.countdownGlow && !snapshot.infinite() && snapshot.duration() <= 100;
        double pulse = flashing ? (Math.sin(Util.getMillis() * 0.013D) + 1.0D) * 0.5D : 0.0D;
        int bgAlpha = flashing ? (int) Math.round(alphaI * (0.35D + 0.65D * pulse)) : alphaI;

        switch (style) {
            case MINIMAL -> {
                if (flashing) {
                    graphics.fill(x, y, x + width, y + height,
                            withAlpha(categoryColor, (int) (25 + pulse * 80)));
                }
            }
            case VANILLA -> drawVanillaPanel(graphics, x, y, width, height, bgAlpha);
            case CARD -> {
                graphics.fill(x, y, x + width, y + height, (Math.max(0, bgAlpha) << 24) | 0x10141B);
                graphics.fill(x, y, x + 2, y + height,
                        withAlpha(categoryColor, Math.min(255, alphaI + 35)));
                graphics.renderOutline(x, y, width, height,
                        withAlpha(categoryColor, flashing
                                ? (int) (110 + pulse * 145)
                                : Math.min(255, alphaI + 25)));
            }
        }

        int iconX = switch (style) {
            case MINIMAL -> x + 2;
            case VANILLA -> x + 5;
            case CARD -> x + 6;
        };
        int iconY = y + Math.max(2, (height - icon) / 2);
        drawIcon(graphics, minecraft, snapshot, iconX, iconY, icon, config,
                flashing ? (float) (0.55D + 0.45D * pulse) : 1.0F);
        int nameY = minimal ? y + 3 : y + 4;
        String time = snapshot.rightLabel(config.showPercentage);
        int timeWidth = minecraft.font.width(time);
        drawScrollingText(graphics, minecraft.font, snapshot.title(), x + textOffset,
                x + width - timeWidth - 6, nameY, withAlpha(0xF4F4F4, (int) (alpha * 244)));
        graphics.drawString(minecraft.font, time, x + width - timeWidth - 5, nameY,
                withAlpha(snapshot.duration() <= 100 && !snapshot.infinite() ? 0xFFC65C : 0xE1E7ED,
                        (int) (alpha * 255)), true);

        if (config.showProgressBar) {
            int barX = x + textOffset;
            int barY = minimal ? y + 14 : y + 16;
            int barHeight = minimal ? 2 : 4;
            int barWidth = x + width - 5 - barX;
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight,
                    withAlpha(0x05070A, (int) (alpha * 165)));
            int filled = (int) Math.round(barWidth * snapshot.progress());
            if (filled > 0) graphics.fill(barX, barY, barX + filled, barY + barHeight,
                    withAlpha(categoryColor, (int) (alpha * (minimal ? 200 : 235))));
        }

        if (config.showAttributes) {
            int lineY = minimal ? y + 19 : y + 24;
            for (Component line : snapshot.hudLines()) {
                if (lineY + 8 > y + height) break;
                drawScrollingText(graphics, minecraft.font, line, x + textOffset, x + width - 6, lineY,
                        withAlpha(0xB8C5CF, (int) (alpha * 184)));
                lineY += ROW_LINE_HEIGHT;
            }
        }
    }

    /** 超出可用宽度时启用剪刀裁剪并做往复滚动，否则直接绘制 */
    private static void drawScrollingText(GuiGraphics graphics, Font font, Component text,
                                          int x, int right, int y, int color) {
        int available = right - x;
        if (available <= 0) return;
        int textWidth = font.width(text);
        if (textWidth <= available) {
            graphics.drawString(font, text, x, y, color, true);
            return;
        }
        int offset = scrollOffset(textWidth - available);
        graphics.enableScissor(x - 1, y - 2, right + 1, y + 10);
        graphics.drawString(font, text, x - offset, y, color, true);
        graphics.disableScissor();
    }

    /** 原版图标（关闭/并存模式）悬停时显示本模组的详细说明 */
    private static void drawVanillaHoverTooltip(GuiGraphics graphics, Minecraft minecraft,
                                                EffectRenderingInventoryScreen<?> container,
                                                int mouseX, int mouseY) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        List<MobEffectInstance> sorted = EffectTracker.vanillaOrder();
        if (sorted.isEmpty()) return;
        // 精确复刻原版 renderEffects 的布局参数
        int anchorX = container.getGuiLeft() + container.getXSize() + 2;
        int availableWidth = minecraft.getWindow().getGuiScaledWidth() - anchorX;
        if (availableWidth < 32) return;
        boolean full = availableWidth >= 120;
        int spacing = sorted.size() > 5 ? 132 / (sorted.size() - 1) : 33;
        int hitWidth = full ? 120 : 33;
        if (mouseX < anchorX || mouseX > anchorX + hitWidth) return;
        int y = container.getGuiTop();
        for (MobEffectInstance instance : sorted) {
            if (!instance.isVisible()) continue;
            if (mouseY >= y && mouseY <= y + spacing) {
                EffectSnapshot snapshot = findSnapshot(instance.getEffect());
                if (snapshot != null) renderRichTooltip(graphics, minecraft, snapshot, mouseX, mouseY);
                return;
            }
            y += spacing;
        }
    }

    private static EffectSnapshot findSnapshot(MobEffect effect) {
        for (EffectSnapshot snapshot : EffectTracker.snapshots()) {
            if (snapshot.effect() == effect) return snapshot;
        }
        return null;
    }

    /** 悬停 tooltip 中的固定文案（组件不可变，静态复用避免每帧构建） */
    private static final Component AI_TIP_LINE =
            Component.translatable("gui.buff_insight.ai.tip").withStyle(ChatFormatting.DARK_GREEN);
    private static final Component AI_PENDING_LINE =
            Component.translatable("gui.buff_insight.ai.pending")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    /** AI 文本折行结果记忆化：文本与宽度不变时直接复用（同一悬停下每帧命中） */
    private static String wrapMemoText;
    private static int wrapMemoWidth;
    private static List<Component> wrapMemoResult = List.of();

    /** 悬停详细说明：名称+等级、属性数值、描述、剩余时间、AI 分析 */
    private static void renderRichTooltip(GuiGraphics graphics, Minecraft minecraft, EffectSnapshot snapshot,
                                          int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(snapshot.titleBold());
        lines.addAll(snapshot.tooltipLines());
        lines.add(Component.translatable("gui.buff_insight.remaining", snapshot.rightLabel(false))
                .withStyle(ChatFormatting.GRAY));
        BuffInsightConfig config = BuffInsightConfig.get();
        if (config.aiEnabled) {
            BuffAnalysisService.requestAsync(snapshot, config);
            BuffAnalysisService.AiState ai = BuffAnalysisService.get(snapshot);
            if (ai != null) {
                lines.add(AI_TIP_LINE);
                switch (ai.status()) {
                    case READY -> lines.addAll(wrappedText(minecraft.font, ai.text(),
                            Math.min(200, graphics.guiWidth() - 40)));
                    case PENDING -> lines.add(AI_PENDING_LINE);
                    case FAILED -> lines.add(Component.translatable("gui.buff_insight.ai.failed", ai.text())
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        graphics.renderTooltip(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private static List<Component> wrappedText(Font font, String text, int maxWidth) {
        // AI 文本字符串来自缓存 Map，引用稳定，用引用比较即可
        if (text != wrapMemoText || wrapMemoWidth != maxWidth) {
            wrapMemoText = text;
            wrapMemoWidth = maxWidth;
            wrapMemoResult = wrapText(font, text, maxWidth);
        }
        return wrapMemoResult;
    }

    /** AI 返回的纯文本按像素宽度折行（中文按字符断行），行宽增量累计避免逐字符拼接 */
    private static List<Component> wrapText(Font font, String text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(Component.literal(line.toString()));
                line.setLength(0);
                lineWidth = 0;
                continue;
            }
            int charWidth = font.width(String.valueOf(c));
            if (lineWidth > 0 && lineWidth + charWidth > maxWidth) {
                lines.add(Component.literal(line.toString()));
                line.setLength(0);
                lineWidth = 0;
            }
            line.append(c);
            lineWidth += charWidth;
        }
        if (line.length() > 0) lines.add(Component.literal(line.toString()));
        return lines;
    }

    /** 仿原版物品工具提示：黑底 + 紫蓝渐变描边 */
    private static void drawVanillaPanel(GuiGraphics graphics, int x, int y, int width, int height, int alpha) {
        int inner = withAlpha(0x00100010, Math.min(245, Math.max(0, alpha + 35)));
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, inner);
        graphics.fillGradient(x + 2, y, x + width - 2, y + 1, 0x505000FF, 0x505000FF);
        graphics.fillGradient(x + 2, y + height - 1, x + width - 2, y + height, 0x5028007F, 0x5028007F);
        graphics.fillGradient(x, y + 2, x + 1, y + height - 2, 0x5028007F, 0x505000FF);
        graphics.fillGradient(x + width - 1, y + 2, x + width, y + height - 2, 0x5028007F, 0x505000FF);
    }

    private static void drawIcon(GuiGraphics graphics, Minecraft minecraft, EffectSnapshot snapshot,
                                 int x, int y, int size, BuffInsightConfig config, float alpha) {
        int color = snapshot.categoryColor();
        BuffInsightConfig.VisualStyle style = config.visualStyle;
        if (style == BuffInsightConfig.VisualStyle.CARD) {
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, withAlpha(color, 235));
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xD010141A);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
        if (style == BuffInsightConfig.VisualStyle.VANILLA) {
            // 原版药水 HUD 的 24x24 图标底框，随行高同步拉伸
            int frame = size + 6;
            graphics.blit(VANILLA_ICONS, x - 3, y - 3, frame, frame, 141.0F, 166.0F, 24, 24, 256, 256);
        }
        TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(snapshot.effect());
        graphics.blit(x, y, 0, size, size, sprite);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** 供配置界面的实时预览调用 */
    static void drawPreviewRow(GuiGraphics graphics, Minecraft minecraft, EffectSnapshot snapshot,
                               int x, int y, int width, int allocatedHeight, BuffInsightConfig config) {
        drawEffect(graphics, minecraft, snapshot, x, y, width, allocatedHeight, config, 1.0F);
    }
}
