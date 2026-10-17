package cn.blockforge.generated.buffinsight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BuffInsightConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuffInsight/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("buff_insight.json");
    private static BuffInsightConfig instance = defaults();

    public DisplayMode displayMode = DisplayMode.REPLACE;
    public Layout layout = Layout.RIGHT_STREAM;
    public ReadMode readMode = ReadMode.HYBRID;
    public VisualStyle visualStyle = VisualStyle.CARD;
    public boolean showProgressBar = true;
    public boolean showPercentage = true;
    public boolean showAttributes = true;
    public boolean adaptiveLayout = true;
    public boolean countdownGlow = true;
    public boolean gainSound = true;
    public boolean removeFeedback = true;
    public boolean worldParticles = true;
    public double opacity = 0.82D;
    public double cardWidthFactor = 1.0D;
    public double cardHeightFactor = 1.0D;
    public double noticeSeconds = 4.0D;
    public int realtimeIntervalTicks = 10;
    public boolean aiEnabled = false;
    public String aiEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    public String aiApiKey = "";
    public String aiModel = "glm-4-flash";

    public static BuffInsightConfig get() {
        return instance;
    }

    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    BuffInsightConfig loaded = GSON.fromJson(reader, BuffInsightConfig.class);
                    instance = loaded == null ? defaults() : loaded;
                }
            } else {
                instance = defaults();
            }
            instance.sanitize();
            save();
        } catch (Exception exception) {
            LOGGER.error("无法读取配置文件 {}，将使用默认值", PATH, exception);
            instance = defaults();
        }
    }

    public static void save() {
        instance.sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception exception) {
            LOGGER.error("无法保存配置文件 {}", PATH, exception);
        }
    }

    public static void reset() {
        instance = defaults();
        save();
    }

    private void sanitize() {
        if (displayMode == null) displayMode = DisplayMode.REPLACE;
        if (layout == null) layout = Layout.RIGHT_STREAM;
        if (visualStyle == null) visualStyle = VisualStyle.CARD;
        opacity = Math.max(0.2D, Math.min(1.0D, opacity));
        cardWidthFactor = Math.max(0.7D, Math.min(1.8D, cardWidthFactor));
        cardHeightFactor = Math.max(0.8D, Math.min(1.6D, cardHeightFactor));
        noticeSeconds = Math.max(1.0D, Math.min(10.0D, noticeSeconds));
        realtimeIntervalTicks = Math.max(1, Math.min(40, realtimeIntervalTicks));
        if (aiEndpoint == null || aiEndpoint.isBlank()) {
            aiEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
        }
        if (aiModel == null || aiModel.isBlank()) aiModel = "glm-4-flash";
        if (aiApiKey == null) aiApiKey = "";
    }

    private static BuffInsightConfig defaults() {
        return new BuffInsightConfig();
    }

    /** 循环到下一个枚举值（用于配置界面的点击切换） */
    private static <T extends Enum<T>> T nextOf(T current) {
        T[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }

    public enum DisplayMode {
        OFF("gui.buff_insight.displayMode.off"),
        REPLACE("gui.buff_insight.displayMode.replace"),
        ALONGSIDE("gui.buff_insight.displayMode.alongside");
        private final String key;
        DisplayMode(String key) { this.key = key; }
        public String key() { return key; }
        public DisplayMode next() { return BuffInsightConfig.nextOf(this); }
    }

    public enum ReadMode {
        HYBRID("gui.buff_insight.readMode.hybrid"),
        PACKET_PRIORITY("gui.buff_insight.readMode.packet"),
        REALTIME("gui.buff_insight.readMode.realtime");
        private final String key;
        ReadMode(String key) { this.key = key; }
        public String key() { return key; }
        public ReadMode next() { return BuffInsightConfig.nextOf(this); }
    }

    public enum Layout {
        RIGHT_STREAM("gui.buff_insight.layout.right"),
        LEFT_PANEL("gui.buff_insight.layout.left");
        private final String key;
        Layout(String key) { this.key = key; }
        public String key() { return key; }
        public Layout next() { return BuffInsightConfig.nextOf(this); }
    }

    public enum VisualStyle {
        MINIMAL("gui.buff_insight.style.minimal"),
        VANILLA("gui.buff_insight.style.vanilla"),
        CARD("gui.buff_insight.style.card");
        private final String key;
        VisualStyle(String key) { this.key = key; }
        public String key() { return key; }
        public VisualStyle next() { return BuffInsightConfig.nextOf(this); }
    }
}
