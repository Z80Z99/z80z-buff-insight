package cn.blockforge.generated.buffinsight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 接入任意 OpenAI 兼容接口（智谱/DeepSeek/OpenAI/本地 Ollama 等）分析效果详情。
 * 结果按「效果id#等级」持久缓存，同一个效果只请求一次；失败后 5 分钟内不重试。
 */
public final class BuffAnalysisService {
    public enum Status {PENDING, READY, FAILED}

    public record AiState(Status status, String text) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("BuffInsight/AI");
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get().resolve("buff_insight_ai_cache.json");
    private static final long FAIL_COOLDOWN_MS = 300_000L;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, AiState> STATES = new ConcurrentHashMap<>();
    private static final Set<String> INFLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BuffInsight-AI");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private BuffAnalysisService() {
    }

    public static void loadCache() {
        if (!Files.exists(CACHE_PATH)) return;
        try (Reader reader = Files.newBufferedReader(CACHE_PATH, StandardCharsets.UTF_8)) {
            Map<String, String> loaded = PRETTY.fromJson(reader,
                    new TypeToken<Map<String, String>>() {
                    }.getType());
            if (loaded != null) CACHE.putAll(loaded);
        } catch (Exception exception) {
            LOGGER.warn("AI 分析缓存读取失败，忽略", exception);
        }
    }

    private static void saveCache() {
        try (Writer writer = Files.newBufferedWriter(CACHE_PATH, StandardCharsets.UTF_8)) {
            PRETTY.toJson(CACHE, writer);
        } catch (Exception exception) {
            LOGGER.warn("AI 分析缓存写入失败", exception);
        }
    }

    /** 当前某效果的 AI 状态；未请求过返回 null */
    public static AiState get(EffectSnapshot snapshot) {
        return STATES.get(snapshot.aiKey());
    }

    /** 需要时触发异步分析；已缓存/进行中/冷却中则直接返回 */
    public static void requestAsync(EffectSnapshot snapshot, BuffInsightConfig config) {
        if (!config.aiEnabled || config.aiApiKey.isBlank()) return;
        String key = snapshot.aiKey();
        if (CACHE.containsKey(key) || INFLIGHT.contains(key)) return;
        Long failedAt = FAILED_AT.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAIL_COOLDOWN_MS) return;

        INFLIGHT.add(key);
        STATES.put(key, new AiState(Status.PENDING, ""));
        String endpoint = config.aiEndpoint;
        String apiKey = config.aiApiKey;
        String model = config.aiModel;
        String prompt = buildPrompt(snapshot);
        boolean chinese = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("zh");
        EXEC.submit(() -> {
            try {
                String answer = callApi(endpoint, apiKey, model, prompt, chinese);
                CACHE.put(key, answer);
                STATES.put(key, new AiState(Status.READY, answer));
                // 成功后清除失败冷却记录，避免陈旧时间戳累积
                FAILED_AT.remove(key);
                saveCache();
            } catch (Exception exception) {
                LOGGER.warn("AI 分析请求失败: {}", exception.toString());
                String message = exception.getMessage();
                FAILED_AT.put(key, System.currentTimeMillis());
                STATES.put(key, new AiState(Status.FAILED,
                        message == null ? exception.getClass().getSimpleName() : message));
            } finally {
                INFLIGHT.remove(key);
            }
        });
    }

    private static String buildPrompt(EffectSnapshot snapshot) {
        String category = switch (snapshot.effect().getCategory()) {
            case BENEFICIAL -> "增益";
            case HARMFUL -> "减益";
            default -> "中立";
        };
        StringBuilder prompt = new StringBuilder();
        prompt.append("效果: ").append(snapshot.effect().getDisplayName().getString()).append(" ")
                .append(snapshot.roman())
                .append("（").append(category).append("）\n");
        prompt.append(snapshot.infinite() ? "剩余: 无限" : "剩余: " + snapshot.duration() / 20 + "秒").append('\n');
        if (!snapshot.tooltipLines().isEmpty()) {
            prompt.append("已知说明: ");
            for (int i = 0; i < snapshot.tooltipLines().size(); i++) {
                if (i > 0) prompt.append("；");
                prompt.append(snapshot.tooltipLines().get(i).getString());
            }
        }
        return prompt.toString();
    }

    private static String callApi(String endpoint, String apiKey, String model, String userContent,
                                  boolean chinese) throws Exception {
        String systemPrompt = chinese
                ? "你是Minecraft游戏专家。根据玩家提供的状态效果信息，用不超过两句话的简体中文纯文本说明该效果的实际作用和一条实用建议。不要使用markdown、列表、emoji或引号。"
                : "You are a Minecraft expert. In at most two short plain-text sentences, explain what the given status effect does and one practical tip. No markdown, lists, emoji or quotes.";

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 160);
        body.addProperty("temperature", 0.6D);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userContent));
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = extractError(response.body());
            throw new IllegalStateException("HTTP " + response.statusCode() + (detail.isEmpty() ? "" : " " + detail));
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) throw new IllegalStateException("empty response");
        String content = choices.get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
        if (content == null || content.isBlank()) throw new IllegalStateException("empty content");
        return content.trim();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String extractError(String responseBody) {
        try {
            JsonObject error = JsonParser.parseString(responseBody).getAsJsonObject().getAsJsonObject("error");
            if (error != null && error.get("message") != null) {
                String text = error.get("message").getAsString();
                return text.length() > 80 ? text.substring(0, 80) : text;
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
