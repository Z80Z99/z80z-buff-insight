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
    /** 渲染器淡出动画时长，与死亡时间的差值计算共用 */
    public static final long FADE_MS = 300L;
    private static final Map<MobEffect, Integer> INITIAL_DURATIONS = new HashMap<>();
    private static volatile boolean packetDirty;
    private static volatile int localPlayerId = Integer.MIN_VALUE;
    private static final List<DisplayEntry> DISPLAY = new ArrayList<>();
    /** 渲染热路径直接读取的不可变缓存，refresh 时整体重建，访问零分配 */
    private static volatile List<DisplayEntry> displayCache = List.of();
    private static volatile List<EffectSnapshot> snapshotCache = List.of();
    private static volatile List<Notice> noticeCache = List.of();
    /** 原版自然顺序的效果列表（供背包悬停复刻原版布局），refresh 时重建 */
    private static volatile List<MobEffectInstance> vanillaOrderCache = List.of();
    private static Set<MobEffect> previousEffects = Set.of();
    private static int ticks;
    private static boolean initialized;

    /** 显示条目排序：先有益后其他，再按快照预计算的排序键（不触发翻译解析） */
    private static final java.util.Comparator<DisplayEntry> DISPLAY_ORDER = (left, right) -> {
        int category = Boolean.compare(right.snapshot().effect().isBeneficial(),
                left.snapshot().effect().isBeneficial());
        if (category != 0) return category;
        return left.snapshot().sortName().compareTo(right.snapshot().sortName());
    };

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
        return displayCache;
    }

    /** 并存模式的活跃通知 */
    public static List<Notice> notices() {
        return noticeCache;
    }

    /** 仅存活的快照 */
    public static List<EffectSnapshot> snapshots() {
        return snapshotCache;
    }

    /** 原版自然顺序效果列表（背包悬停用） */
    public static List<MobEffectInstance> vanillaOrder() {
        return vanillaOrderCache;
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
        displayCache = List.of();
        snapshotCache = List.of();
        noticeCache = List.of();
        vanillaOrderCache = List.of();
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
        boolean periodic;
        if (config.readMode == BuffInsightConfig.ReadMode.REALTIME) {
            // 实时模式只按固定间隔轮询，不响应包事件
            periodic = ticks % Math.max(1, config.realtimeIntervalTicks) == 0;
        } else {
            int interval = config.readMode == BuffInsightConfig.ReadMode.PACKET_PRIORITY
                    ? 20 : config.realtimeIntervalTicks;
            periodic = ticks % Math.max(1, interval) == 0;
        }
        if (periodic || packetDirty || !initialized) refresh(player, initialized);
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
        NOTICES.removeIf(notice -> now >= notice.hideAt() + 50L);
        DISPLAY.sort(DISPLAY_ORDER);
        rebuildCaches(active);
        previousEffects = Set.copyOf(currentEffects);
        packetDirty = false;
        initialized = true;
    }

    /** refresh 末尾整体重建渲染热路径读取的不可变缓存（tick 与渲染同线程，volatile 仅作可见性保险） */
    private static void rebuildCaches(Collection<MobEffectInstance> active) {
        displayCache = List.copyOf(DISPLAY);
        List<EffectSnapshot> alive = new ArrayList<>(DISPLAY.size());
        for (DisplayEntry entry : DISPLAY) {
            if (entry.alive()) alive.add(entry.snapshot());
        }
        snapshotCache = List.copyOf(alive);
        noticeCache = List.copyOf(NOTICES);
        List<MobEffectInstance> vanillaOrder = new ArrayList<>(active);
        java.util.Collections.sort(vanillaOrder);
        vanillaOrderCache = List.copyOf(vanillaOrder);
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
