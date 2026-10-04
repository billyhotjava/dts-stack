package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Client for AI screen generation, delegating to copilot-ai
 * via CopilotAiClient.
 */
@Component
public class PlatformAiNativeClient {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformAiNativeClient.class);

    private final CopilotAiClient copilotAiClient;
    private final ObjectMapper objectMapper;

    public PlatformAiNativeClient(CopilotAiClient copilotAiClient, ObjectMapper objectMapper) {
        this.copilotAiClient = copilotAiClient;
        this.objectMapper = objectMapper;
    }

    public Optional<ObjectNode> generateScreen(String prompt, int width, int height) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", prompt);
        payload.put("width", width);
        payload.put("height", height);
        return postForObjectNode("/api/ai/copilot/screen/generate", payload);
    }

    public Optional<ObjectNode> reviseScreen(String prompt, JsonNode screenSpec, List<String> context, boolean applyChanges) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", prompt);
        payload.put("screenSpec", screenSpec == null ? objectMapper.createObjectNode() : screenSpec);
        payload.put("context", context == null ? List.of() : context);
        payload.put("mode", applyChanges ? "apply" : "suggest");
        return postForObjectNode("/api/ai/copilot/screen/revise", payload);
    }

    private Optional<ObjectNode> postForObjectNode(String path, Map<String, Object> payload) {
        try {
            Optional<Map<String, Object>> response = copilotAiClient.post(path, payload);
            if (response.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> body = response.get();
            Object data = body.get("data");
            if (data == null) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.valueToTree(data);
            if (!node.isObject()) {
                return Optional.empty();
            }
            return Optional.of((ObjectNode) node);
        } catch (Exception ex) {
            LOG.debug("Copilot AI screen request failed path={} error={}", path, ex.getMessage());
            return Optional.empty();
        }
    }
}
