package com.yuzhi.dts.copilot.analytics.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class CopilotAgentChatClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient sseHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final RestClient restClient;
    private final String baseUrl;
    private final String adminSecret;

    public CopilotAgentChatClient(
            @Value("${dts.copilot.ai.base-url:http://localhost:8091}") String baseUrl,
            @Value("${copilot.admin-secret:}") String adminSecret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.adminSecret = adminSecret;
    }

    public Map<String, Object> sendMessage(
            String userId,
            String sessionId,
            String message,
            Long datasourceId,
            Map<String, Boolean> martHealth,
            Map<String, String> assumptionOverrides,
            Map<String, String> clarificationAnswers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("message", message);
        if (sessionId != null && !sessionId.isBlank()) {
            payload.put("sessionId", sessionId);
        }
        if (datasourceId != null) {
            payload.put("datasourceId", datasourceId);
        }
        if (martHealth != null && !martHealth.isEmpty()) {
            payload.put("martHealth", martHealth);
        }
        if (assumptionOverrides != null && !assumptionOverrides.isEmpty()) {
            payload.put("assumptionOverrides", assumptionOverrides);
        }
        if (clarificationAnswers != null && !clarificationAnswers.isEmpty()) {
            payload.put("clarificationAnswers", clarificationAnswers);
        }
        return restClient.post()
                .uri("/internal/agent/chat/send")
                .header("X-Admin-Secret", adminSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> listSessions(String userId, int limit) {
        List<Map<String, Object>> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/agent/chat/sessions")
                        .queryParam("userId", userId)
                        .queryParam("limit", limit)
                        .build())
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return body != null ? body : Collections.emptyList();
    }

    public Map<String, Object> getSession(String userId, String sessionId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/agent/chat/{sessionId}")
                        .queryParam("userId", userId)
                        .build(sessionId))
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> getRouteTelemetry(int days, int limit) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/agent/chat/route-telemetry")
                        .queryParam("days", days)
                        .queryParam("limit", limit)
                        .build())
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public void sendMessageStream(String userId, String sessionId, String message,
                                   Long datasourceId, Map<String, Boolean> martHealth,
                                   Map<String, String> assumptionOverrides,
                                   Map<String, String> clarificationAnswers,
                                   OutputStream output) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("message", message);
        if (sessionId != null && !sessionId.isBlank()) {
            payload.put("sessionId", sessionId);
        }
        if (datasourceId != null) {
            payload.put("datasourceId", datasourceId);
        }
        if (martHealth != null && !martHealth.isEmpty()) {
            payload.put("martHealth", martHealth);
        }
        if (assumptionOverrides != null && !assumptionOverrides.isEmpty()) {
            payload.put("assumptionOverrides", assumptionOverrides);
        }
        if (clarificationAnswers != null && !clarificationAnswers.isEmpty()) {
            payload.put("clarificationAnswers", clarificationAnswers);
        }

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/internal/agent/chat/send-stream"))
                    .header("Content-Type", "application/json")
                    .header("X-Admin-Secret", adminSecret)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<java.io.InputStream> response = sseHttpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("Streaming failed: " + response.statusCode() + " " + error);
            }

            try (var in = response.body()) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new org.springframework.web.client.RestClientException(
                    "Streaming chat interrupted", e);
        } catch (IOException e) {
            throw new org.springframework.web.client.RestClientException(
                    "Streaming chat failed: " + describeStreamTransportFailure(e), e);
        }
    }

    static String describeStreamTransportFailure(IOException exception) {
        if (exception == null) {
            return "stream transport error";
        }
        String message = exception.getMessage();
        if (StringUtils.hasText(message)) {
            return message.trim();
        }
        return "stream transport error";
    }

    public void deleteSession(String userId, String sessionId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/agent/chat/{sessionId}")
                        .queryParam("userId", userId)
                        .build(sessionId))
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .toBodilessEntity();
    }
}
