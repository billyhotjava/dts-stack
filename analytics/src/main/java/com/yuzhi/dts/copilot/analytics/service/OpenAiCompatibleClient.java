package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI-compatible LLM client for the analytics module.
 * Synchronous chat completion only (analytics does not need streaming).
 */
@Component
public class OpenAiCompatibleClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Synchronous chat completion — returns the full response text.
     */
    public String chatCompletion(AiProviderConfig config, List<ChatMessage> messages) {
        try {
            String body = buildRequestBody(config, messages);
            HttpRequest request = buildHttpRequest(config, body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warn("AI API returned status {}: {}", response.statusCode(), truncate(response.body(), 500));
                throw new RuntimeException("AI API error: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText("");
            }
            return "";
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("AI chat completion failed: {}", e.getMessage());
            throw new RuntimeException("AI chat completion failed: " + e.getMessage(), e);
        }
    }

    private HttpRequest buildHttpRequest(AiProviderConfig config, String body) {
        String baseUrl = config.baseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/chat/completions";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(config.timeout()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        if (StringUtils.hasText(config.apiKey())) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }

        return builder.build();
    }

    private String buildRequestBody(AiProviderConfig config, List<ChatMessage> messages) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", config.model());
            root.put("max_tokens", config.maxTokens());
            root.put("temperature", config.temperature());
            root.put("stream", false);

            ArrayNode messagesNode = root.putArray("messages");
            for (ChatMessage msg : messages) {
                ObjectNode msgNode = messagesNode.addObject();
                msgNode.put("role", msg.role());
                msgNode.put("content", msg.content());
            }

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    public record ChatMessage(String role, String content) {}
}
