package cn.blockforge.generated.buffinsight;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EffectTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuffInsight/Tracker");
    private static final String HANDLER_NAME = "buff_insight_effect_listener";
    private static final long FADE_MS = 300L;
    private static final Map<MobEffect, Integer> INITIAL_DURATIONS = new HashMap<>();
    private static volatile boolean packetDirty;
    private static volatile int localPlayerId = Integer.MIN_VALUE;
    private static final List<DisplayEntry> DISPLAY = new ArrayList<>();
    private static Set<MobEffect> previousEffects = Set.of();
    private static int ticks;
    private static boolean initialized;

    /** 渲染显示条目：存活条目 deathAt=0，消失中的条目保留至淡出结束 */
    public record DisplayEntry(EffectSnapshot snapshot, long bornAt, long deathAt) {
        public boolean alive() {
            return deathAt == 0L;
        }
    }

    /** 并存模式的临时通知卡片：获得效果时弹出，到时淡出 */
    public record Notice(EffectSnapshot snapshot, long showAt, long hideAt) {
    }

    private static final List<Notice> NOTICES = new ArrayList<>();

    private EffectTracker() {
    }

    /** 完整显示列表（含淡出中的条目），供渲染器做动画 */
    public static List<DisplayEntry> display() {
        return List.copyOf(DISPLAY);
    }

    /** 并存模式的活跃通知 */
    public static List<Notice> notices() {
        long now = Util.getMillis();
        NOTICES.removeIf(notice -> now >= notice.hideAt() + 350L);
        return List.copyOf(NOTICES);
    }

    /** 仅存活的快照 */
    public static List<EffectSnapshot> snapshots() {
        List<EffectSnapshot> alive = new ArrayList<>();
        for (DisplayEntry entry : DISPLAY) {
            if (entry.alive()) alive.add(entry.snapshot());
        }
        return alive;
    }

    public static void attachPacketListener(Connection connection) {
        try {
            ChannelPipeline pipeline = connection.channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME);
            pipeline.addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    if (message instanceof ClientboundUpdateMobEffectPacket packet
                            && packet.getEntityId() == localPlayerId) {
                        packetDirty = true;
                    } else if (message instanceof ClientboundRemoveMobEffectPacket) {
                        // 1.20.1 该包不暴露实体 ID，只能整体标记，误触发只会多一次廉价刷新
                        packetDirty = true;
                    }
                    super.channelRead(context, message);
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("无法挂接效果更新包监听，将继续使用实时同步读取", exception);
        }
    }

    public static void detachPacketListener(Connection connection) {
        try {
            ChannelPipeline pipeline = connection.channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME);
        } catch (RuntimeException ignored) {
        }
        reset();
    }

    public static void markDirty() {
        packetDirty = true;
    }

    public static void reset() {
        DISPLAY.clear();
        NOTICES.clear();
        previousEffects = Set.of();
        INITIAL_DURATIONS.clear();
        packetDirty = false;
        initialized = false;
        localPlayerId = Integer.MIN_VALUE;
        ticks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            if (initialized) reset();
            return;
        }

        localPlayerId = player.getId();
        BuffInsightConfig config = BuffInsightConfig.get();
        // 关闭模式也保持采集：背包原版图标的悬停详细说明依赖快照数据
        ticks++;
        int interval = config.readMode == BuffInsightConfig.ReadMode.PACKET_PRIORITY
                ? 20 : config.realtimeIntervalTicks;
        boolean shouldRefresh = packetDirty || ticks % Math.max(1, interval) == 0 || !initialized;
        if (config.readMode == BuffInsightConfig.ReadMode.REALTIME) {
            shouldRefresh = ticks % Math.max(1, config.realtimeIntervalTicks) == 0 || !initialized;
        }
        if (shouldRefresh) refresh(player, initialized);
    }

    private static void refresh(LocalPlayer player, boolean allowFeedback) {
        Collection<MobEffectInstance> active = player.getActiveEffects();
        Set<MobEffect> currentEffects = new HashSet<>();
        List<EffectSnapshot> updated = new ArrayList<>();

        for (MobEffectInstance instance : active) {
            MobEffect effect = instance.getEffect();
            currentEffects.add(effect);
            int duration = instance.getDuration();
            int initial = INITIAL_DURATIONS.getOrDefault(effect, duration);
            if (!previousEffects.contains(effect) || duration > initial) initial = duration;
            INITIAL_DURATIONS.put(effect, Math.max(1, initial));
            updated.add(EffectSnapshot.of(instance, Math.max(1, initial)));
        }
        INITIAL_DURATIONS.keySet().removeIf(effect -> !currentEffects.contains(effect));
        updated.sort((left, right) -> {
            int category = Boolean.compare(right.effect().isBeneficial(), left.effect().isBeneficial());
            if (category != 0) return category;
            return left.effect().getDisplayName().getString().compareToIgnoreCase(right.effect().getDisplayName().getString());
        });

        if (allowFeedback && BuffInsightConfig.get().displayMode != BuffInsightConfig.DisplayMode.OFF) {
            boolean gainedGood = false, gainedBad = false, lostGood = false, lostBad = false;
            for (MobEffect effect : currentEffects) {
                if (previousEffects.contains(effect)) continue;
                if (effect.getCategory() == MobEffectCategory.HARMFUL) gainedBad = true;
                else gainedGood = true;
            }
            for (MobEffect effect : previousEffects) {
                if (currentEffects.contains(effect)) continue;
                if (effect.getCategory() == MobEffectCategory.HARMFUL) lostBad = true;
                else lostGood = true;
            }
            if (gainedGood) playFeedback(player, true, false);
            if (gainedBad) playFeedback(player, true, true);
            if (lostGood) playFeedback(player, false, false);
            if (lostBad) playFeedback(player, false, true);
        }

        // 更新显示列表：新效果记录出生时间（淡入用），消失的标记死亡时间（淡出用）
        // 注意：与渲染器统一使用 Util.getMillis()（GLFW 相对时间），不能用纪元时间
        long now = Util.getMillis();
        BuffInsightConfig config = BuffInsightConfig.get();
        for (EffectSnapshot snapshot : updated) {
            boolean found = false;
            for (int i = 0; i < DISPLAY.size(); i++) {
                DisplayEntry entry = DISPLAY.get(i);
                if (entry.alive() && entry.snapshot().effect() == snapshot.effect()) {
                    DISPLAY.set(i, new DisplayEntry(snapshot, entry.bornAt(), 0L));
                    found = true;
                    break;
                }
            }
            if (!found) {
                DISPLAY.add(new DisplayEntry(snapshot, now, 0L));
                // 新获得的效果预取 AI 分析，等玩家悬停时结果多半已就绪
                BuffAnalysisService.requestAsync(snapshot, config);
                // 并存模式：弹出临时通知卡片（不遮挡原版图标位置）
                if (allowFeedback && config.displayMode == BuffInsightConfig.DisplayMode.ALONGSIDE) {
                    NOTICES.add(new Notice(snapshot, now,
                            now + (long) (config.noticeSeconds * 1000.0D)));
                }
            }
        }
        for (int i = 0; i < DISPLAY.size(); i++) {
            DisplayEntry entry = DISPLAY.get(i);
            if (entry.alive() && !currentEffects.contains(entry.snapshot().effect())) {
                DISPLAY.set(i, new DisplayEntry(entry.snapshot(), entry.bornAt(), now + FADE_MS));
            }
        }
        DISPLAY.removeIf(entry -> !entry.alive() && now >= entry.deathAt());
        DISPLAY.sort((left, right) -> {
            int category = Boolean.compare(right.snapshot().effect().isBeneficial(),
                    left.snapshot().effect().isBeneficial());
            if (category != 0) return category;
            return left.snapshot().effect().getDisplayName().getString()
                    .compareToIgnoreCase(right.snapshot().effect().getDisplayName().getString());
        });
        previousEffects = Set.copyOf(currentEffects);
        packetDirty = false;
        initialized = true;
    }

    /** 有益效果走清脆系（Pling/气泡），有害效果走低沉系（吉他/水晶） */
    private static void playFeedback(LocalPlayer player, boolean gained, boolean harmful) {
        BuffInsightConfig config = BuffInsightConfig.get();
        boolean playSound = gained ? config.gainSound : config.removeFeedback;
        if (playSound) {
            SoundEvent sound;
            float volume;
            float pitch;
            if (gained && !harmful) {
                sound = SoundEvents.NOTE_BLOCK_PLING.value();
                volume = 0.7F;
                pitch = 1.7F;
            } else if (gained) {
                sound = SoundEvents.NOTE_BLOCK_GUITAR.value();
                volume = 0.65F;
                pitch = 0.65F;
            } else if (!harmful) {
                sound = SoundEvents.BUBBLE_COLUMN_BUBBLE_POP;
                volume = 0.45F;
                pitch = 0.8F;
            } else {
                sound = SoundEvents.AMETHYST_BLOCK_CHIME;
                volume = 0.55F;
                pitch = 1.2F;
            }
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    sound, SoundSource.PLAYERS, volume, pitch, false);
        }
        if (config.worldParticles && (gained || config.removeFeedback)) {
            SimpleParticleType particle = gained
                    ? (harmful ? ParticleTypes.SMOKE : ParticleTypes.GLOW)
                    : ParticleTypes.POOF;
            for (int i = 0; i < 7; i++) {
                double angle = (Math.PI * 2.0D * i) / 7.0D;
                player.level().addParticle(particle,
                        player.getX() + Math.cos(angle) * 0.45D,
                        player.getY() + 1.0D + (i % 3) * 0.18D,
                        player.getZ() + Math.sin(angle) * 0.45D,
                        Math.cos(angle) * 0.015D, 0.025D, Math.sin(angle) * 0.015D);
            }
        }
    }
}
