package com.yuzhi.dts.copilot.analytics.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class CopilotAdminClient {

    private final RestClient restClient;
    private final String adminSecret;

    public CopilotAdminClient(
            @Value("${dts.copilot.ai.base-url:http://localhost:8091}") String baseUrl,
            @Value("${copilot.admin-secret:}") String adminSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.adminSecret = adminSecret;
    }

    public List<Map<String, Object>> listProviders() {
        Map<String, Object> body = restClient.get()
                .uri("/api/ai/config/providers")
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return extractDataList(body);
    }

    public List<Map<String, Object>> listProviderTemplates() {
        Map<String, Object> body = restClient.get()
                .uri("/api/ai/config/providers/templates")
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return extractDataList(body);
    }

    public Map<String, Object> createProvider(Map<String, Object> payload) {
        Map<String, Object> body = restClient.post()
                .uri("/api/ai/config/providers")
                .header("X-Admin-Secret", adminSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return extractDataMap(body);
    }

    public Map<String, Object> updateProvider(Long id, Map<String, Object> payload) {
        Map<String, Object> body = restClient.put()
                .uri("/api/ai/config/providers/{id}", id)
                .header("X-Admin-Secret", adminSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return extractDataMap(body);
    }

    public void deleteProvider(Long id) {
        restClient.delete()
                .uri("/api/ai/config/providers/{id}", id)
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .toBodilessEntity();
    }

    public Map<String, Object> testProvider(Long id) {
        Map<String, Object> body = restClient.post()
                .uri("/api/ai/config/providers/{id}/test", id)
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return extractDataMap(body);
    }

    public List<Map<String, Object>> listApiKeys() {
        List<Map<String, Object>> body = restClient.get()
                .uri("/api/auth/keys")
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return body != null ? body : Collections.emptyList();
    }

    public Map<String, Object> createApiKey(Map<String, Object> payload) {
        Map<String, Object> body = restClient.post()
                .uri("/api/auth/keys")
                .header("X-Admin-Secret", adminSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return body != null ? body : Collections.emptyMap();
    }

    public Map<String, Object> rotateApiKey(Long id) {
        Map<String, Object> body = restClient.put()
                .uri("/api/auth/keys/{id}/rotate", id)
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return body != null ? body : Collections.emptyMap();
    }

    public void revokeApiKey(Long id) {
        restClient.delete()
                .uri("/api/auth/keys/{id}", id)
                .header("X-Admin-Secret", adminSecret)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDataList(Map<String, Object> body) {
        if (body == null) {
            return Collections.emptyList();
        }
        Object data = body.get("data");
        if (data instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDataMap(Map<String, Object> body) {
        if (body == null) {
            return Collections.emptyMap();
        }
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }
}
