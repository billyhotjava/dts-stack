package com.yuzhi.dts.copilot.analytics.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * REST client to read AI configuration for the analytics module
 * from copilot-ai's /api/ai/config/providers endpoint.
 */
@Component
public class AiConfigClient {

    private static final Logger LOG = LoggerFactory.getLogger(AiConfigClient.class);
    private static final long CACHE_TTL_MS = 60_000;
    private static final String CACHE_KEY = "analytics";

    private final CopilotAiClient copilotAiClient;
    private final ConcurrentHashMap<String, CachedConfig> cache = new ConcurrentHashMap<>();

    public AiConfigClient(CopilotAiClient copilotAiClient) {
        this.copilotAiClient = copilotAiClient;
    }

    /**
     * Fetch AI config for analytics module from copilot-ai.
     */
    @SuppressWarnings("unchecked")
    public Optional<AiProviderConfig> fetchConfig() {
        CachedConfig cached = cache.get(CACHE_KEY);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
            return Optional.of(cached.config);
        }

        try {
            Optional<Map<String, Object>> response = copilotAiClient.get("/api/ai/config/providers");
            if (response.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> body = response.get();
            Map<String, Object> settings = extractSettings(body);
            if (settings != null && !settings.isEmpty()) {
                AiProviderConfig config = AiProviderConfig.from(settings);
                if (config.isConfigured()) {
                    cache.put(CACHE_KEY, new CachedConfig(config, System.currentTimeMillis() + CACHE_TTL_MS));
                    return Optional.of(config);
                }
            }
        } catch (Exception ex) {
            LOG.debug("Failed to fetch AI config from copilot-ai: {}", ex.getMessage());
        }

        return Optional.empty();
    }

    public void invalidateCache() {
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSettings(Map<String, Object> body) {
        if (body == null) return null;
        Object data = body.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object settingsObj = dataMap.get("settings");
            if (settingsObj instanceof Map<?, ?> settingsMap) {
                return (Map<String, Object>) settingsMap;
            }
            return (Map<String, Object>) dataMap;
        }
        Object settings = body.get("settings");
        if (settings instanceof Map<?, ?>) {
            return (Map<String, Object>) settings;
        }
        return body;
    }

    private record CachedConfig(AiProviderConfig config, long expiresAt) {}
}
