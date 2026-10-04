package com.yuzhi.dts.copilot.analytics.service;

import java.util.Map;

/**
 * AI provider configuration record for analytics module.
 * Mirrors the platform-side AiProviderConfig but is self-contained.
 */
public record AiProviderConfig(
    String provider,
    String baseUrl,
    String model,
    String apiKey,
    int maxTokens,
    double temperature,
    int timeout
) {

    public static AiProviderConfig from(Map<String, Object> raw) {
        if (raw == null) {
            return defaultConfig();
        }
        return new AiProviderConfig(
            stringVal(raw.get("provider"), "openai"),
            stringVal(raw.get("baseUrl"), "https://api.openai.com/v1"),
            stringVal(raw.get("model"), "gpt-4o"),
            stringVal(raw.get("apiKey"), ""),
            intVal(raw.get("maxTokens"), 4096),
            doubleVal(raw.get("temperature"), 0.3),
            intVal(raw.get("timeout"), 60)
        );
    }

    public static AiProviderConfig defaultConfig() {
        return new AiProviderConfig("openai", "https://api.openai.com/v1", "gpt-4o", "", 4096, 0.3, 60);
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }

    private static String stringVal(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private static int intVal(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number num) return num.intValue();
        try { return Integer.parseInt(value.toString().trim()); } catch (Exception e) { return fallback; }
    }

    private static double doubleVal(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number num) return num.doubleValue();
        try { return Double.parseDouble(value.toString().trim()); } catch (Exception e) { return fallback; }
    }
}
