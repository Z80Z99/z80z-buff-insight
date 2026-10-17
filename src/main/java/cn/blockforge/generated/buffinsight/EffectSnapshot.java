package cn.blockforge.generated.buffinsight;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 不可变效果快照：标题、罗马数字、类别色、排序键、AI 缓存键等逐帧不变量
 * 都在构建时一次性算好，渲染热路径直接取用、零额外分配。
 */
public final class EffectSnapshot {
    private final MobEffectInstance instance;
    private final int initialDuration;
    private final List<Component> hudLines;
    private final List<Component> tooltipLines;
    private final Component title;
    private final Component titleBold;
    private final String roman;
    private final int categoryColor;
    private final String sortName;
    private final String aiKey;

    private EffectSnapshot(MobEffectInstance instance, int initialDuration, List<Component> hudLines,
                           List<Component> tooltipLines, Component title, Component titleBold, String roman,
                           int categoryColor, String sortName, String aiKey) {
        this.instance = instance;
        this.initialDuration = initialDuration;
        this.hudLines = hudLines;
        this.tooltipLines = tooltipLines;
        this.title = title;
        this.titleBold = titleBold;
        this.roman = roman;
        this.categoryColor = categoryColor;
        this.sortName = sortName;
        this.aiKey = aiKey;
    }

    public MobEffectInstance instance() {
        return instance;
    }

    public MobEffect effect() {
        return instance.getEffect();
    }

    public int duration() {
        return instance.getDuration();
    }

    public boolean infinite() {
        return instance.isInfiniteDuration();
    }

    public double progress() {
        if (infinite()) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D, duration() / (double) Math.max(1, initialDuration)));
    }

    public List<Component> hudLines() {
        return hudLines;
    }

    public List<Component> tooltipLines() {
        return tooltipLines;
    }

    /** 预构建的「名称 等级」标题 */
    public Component title() {
        return title;
    }

    public Component titleBold() {
        return titleBold;
    }

    public String roman() {
        return roman;
    }

    public int categoryColor() {
        return categoryColor;
    }

    public String sortName() {
        return sortName;
    }

    public String aiKey() {
        return aiKey;
    }

    /** 统一的快照构建入口：HUD 行与悬停行都在这里决定 */
    public static EffectSnapshot of(MobEffectInstance instance, int initialDuration) {
        List<Component> attributeLines = buildAttributes(instance);
        Component description = descriptionOf(instance.getEffect());
        List<Component> hud;
        if (!attributeLines.isEmpty()) {
            hud = attributeLines;
        } else if (description != null) {
            hud = List.of(description);
        } else {
            hud = List.of(Component.translatable("gui.buff_insight.strength",
                    roman(instance.getAmplifier() + 1)));
        }
        List<Component> tooltip = new ArrayList<>(attributeLines);
        if (description != null) tooltip.add(description);

        String roman = roman(instance.getAmplifier() + 1);
        Component title = instance.getEffect().getDisplayName().copy().append(" " + roman);
        return new EffectSnapshot(instance, Math.max(1, initialDuration),
                List.copyOf(hud), List.copyOf(tooltip),
                title, title.copy().withStyle(ChatFormatting.BOLD), roman,
                categoryColorOf(instance.getEffect()),
                instance.getEffect().getDisplayName().getString().toLowerCase(Locale.ROOT),
                aiKeyOf(instance.getEffect(), instance.getAmplifier()));
    }

    /** 属性数值行（可能为空，无属性的效果由调用方决定用描述或强度兜底） */
    private static List<Component> buildAttributes(MobEffectInstance instance) {
        List<Component> lines = new ArrayList<>();
        int amplifier = instance.getAmplifier();
        for (Map.Entry<Attribute, AttributeModifier> entry : instance.getEffect().getAttributeModifiers().entrySet()) {
            Attribute attribute = entry.getKey();
            AttributeModifier template = entry.getValue();
            double amount = instance.getEffect().getAttributeModifierValue(amplifier, template);
            String value;
            if (template.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE
                    || template.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                value = signed(amount * 100.0D) + "%";
            } else {
                value = signed(amount);
            }
            lines.add(Component.translatable(attribute.getDescriptionId()).append(" " + value));
        }
        return List.copyOf(lines);
    }

    /** 内置效果说明（来自语言文件 effect.buff_insight.desc.<命名空间>.<路径>），没有词条则返回 null */
    private static Component descriptionOf(MobEffect effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (id == null) return null;
        String key = "effect.buff_insight.desc." + id.getNamespace() + "." + id.getPath();
        if (!I18n.exists(key)) return null;
        return Component.translatable(key).withStyle(ChatFormatting.ITALIC);
    }

    private static int categoryColorOf(MobEffect effect) {
        return switch (effect.getCategory()) {
            case BENEFICIAL -> BuffHudRenderer.THEME_GREEN;
            case HARMFUL -> 0xFFFF6262;
            default -> 0xFF65A9FF;
        };
    }

    private static String aiKeyOf(MobEffect effect, int amplifier) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        return (id == null ? effect.getCategory().toString() : id.toString()) + "#" + amplifier;
    }

    private static String signed(double value) {
        String number = Math.abs(value - Math.rint(value)) < 0.001D
                ? Long.toString(Math.round(Math.abs(value)))
                : String.format(Locale.ROOT, "%.1f", Math.abs(value));
        return (value >= 0.0D ? "+" : "-") + number;
    }

    private static String roman(int value) {
        if (value <= 0 || value > 20) return Integer.toString(value);
        int[] numbers = {10, 9, 5, 4, 1};
        String[] symbols = {"X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                result.append(symbols[i]);
                remaining -= numbers[i];
            }
        }
        return result.toString();
    }
}
