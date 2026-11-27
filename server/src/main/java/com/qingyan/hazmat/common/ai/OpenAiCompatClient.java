package com.qingyan.hazmat.common.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议实现（DeepSeek 等 /chat/completions 端点），零新依赖（java.net.http）。
 * baseUrl/model/key 环境变量名/超时均可配（application.yml app.ai.*）。
 * API Key 实时读取（每次调用 System.getenv，不缓存、不落盘、不打日志）；
 * 读不到 = 无 key = 返回 null 降级，不是错误（铁律 2）。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenAiCompatClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatClient.class);

    private final String baseUrl;
    private final String model;
    private final String keyEnv;
    private final int maxTokens;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatClient(@Value("${app.ai.base-url:https://api.deepseek.com}") String baseUrl,
                              @Value("${app.ai.model:deepseek-flash}") String model,
                              @Value("${app.ai.key-env:DEEPSEEK_API_KEY}") String keyEnv,
                              @Value("${app.ai.max-tokens:1024}") int maxTokens,
                              @Value("${app.ai.timeout-seconds:30}") int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.keyEnv = keyEnv;
        this.maxTokens = maxTokens;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
    }

    @Override
    public String chat(String systemPrompt, String userMsg) {
        String key = System.getenv(keyEnv); // 实时读取：不在启动时缓存
        if (key == null || key.isBlank()) {
            return null; // 无 key = 降级，不是错误
        }
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMsg)),
                    "max_tokens", maxTokens,
                    "stream", false));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.debug("AI 端点非 200：{}", resp.statusCode());
                return null;
            }
            JsonNode content = mapper.readTree(resp.body())
                    .path("choices").path(0).path("message").path("content");
            String text = content.isMissingNode() ? null : content.asText();
            return (text == null || text.isBlank()) ? null : text;
        } catch (Exception e) {
            log.debug("AI 调用失败，走降级：{}", e.getClass().getSimpleName());
            return null;
        }
    }
}
