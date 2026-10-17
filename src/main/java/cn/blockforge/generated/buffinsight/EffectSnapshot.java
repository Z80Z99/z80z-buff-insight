package cn.blockforge.generated.buffinsight;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record EffectSnapshot(MobEffectInstance instance, int initialDuration,
                             List<Component> hudLines, List<Component> tooltipLines) {
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

    /** 属性数值行（可能为空，无属性的效果由调用方决定用描述或强度兜底） */
    public static List<Component> buildAttributes(MobEffectInstance instance) {
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
        return new EffectSnapshot(instance, initialDuration, List.copyOf(hud), List.copyOf(tooltip));
    }

    /** 内置效果说明（来自语言文件 effect.buff_insight.desc.<命名空间>.<路径>），没有词条则返回 null */
    public static Component descriptionOf(MobEffect effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (id == null) return null;
        String key = "effect.buff_insight.desc." + id.getNamespace() + "." + id.getPath();
        if (!I18n.exists(key)) return null;
        return Component.translatable(key).withStyle(ChatFormatting.ITALIC);
    }

    private static String signed(double value) {
        String number = Math.abs(value - Math.rint(value)) < 0.001D
                ? Long.toString(Math.round(Math.abs(value)))
                : String.format(java.util.Locale.ROOT, "%.1f", Math.abs(value));
        return (value >= 0.0D ? "+" : "-") + number;
    }

    public static String roman(int value) {
        if (value <= 0) return Integer.toString(value);
        if (value > 20) return Integer.toString(value);
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
