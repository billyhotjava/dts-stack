package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSettingRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/google")
@Transactional
public class GoogleResource {

    private static final String KEY_GOOGLE_AUTH_ENABLED = "google-auth-enabled";
    private static final String KEY_GOOGLE_AUTH_CONFIGURED = "google-auth-configured";
    private static final String KEY_GOOGLE_AUTH_CLIENT_ID = "google-auth-client-id";
    private static final String KEY_GOOGLE_AUTH_AUTO_CREATE_DOMAIN = "google-auth-auto-create-accounts-domain";

    private final AnalyticsSessionService sessionService;
    private final AnalyticsSettingRepository settingRepository;

    public GoogleResource(AnalyticsSessionService sessionService, AnalyticsSettingRepository settingRepository) {
        this.sessionService = sessionService;
        this.settingRepository = settingRepository;
    }

    @GetMapping(path = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> settings(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put(KEY_GOOGLE_AUTH_ENABLED, getBoolean(KEY_GOOGLE_AUTH_ENABLED, false));
        response.put(KEY_GOOGLE_AUTH_CONFIGURED, getBoolean(KEY_GOOGLE_AUTH_CONFIGURED, false));
        response.put(KEY_GOOGLE_AUTH_CLIENT_ID, getString(KEY_GOOGLE_AUTH_CLIENT_ID));
        response.put(KEY_GOOGLE_AUTH_AUTO_CREATE_DOMAIN, getString(KEY_GOOGLE_AUTH_AUTO_CREATE_DOMAIN));
        return ResponseEntity.ok(response);
    }

    @PutMapping(path = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putSettings(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        putIfPresent(body, KEY_GOOGLE_AUTH_ENABLED);
        putIfPresent(body, KEY_GOOGLE_AUTH_CONFIGURED);
        putIfPresent(body, KEY_GOOGLE_AUTH_CLIENT_ID);
        putIfPresent(body, KEY_GOOGLE_AUTH_AUTO_CREATE_DOMAIN);
        return settings(request);
    }

    private void putIfPresent(JsonNode body, String key) {
        if (body == null || !body.has(key)) {
            return;
        }
        JsonNode value = body.get(key);
        String stored = value == null || value.isNull() ? "null" : value.isTextual() ? value.asText() : value.toString();
        settingRepository.save(upsert(key, stored));
    }

    private AnalyticsSetting upsert(String key, String value) {
        return settingRepository.findById(key).map(existing -> {
            existing.setSettingValue(value);
            return existing;
        }).orElseGet(() -> {
            AnalyticsSetting setting = new AnalyticsSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            return setting;
        });
    }

    private String getString(String key) {
        return settingRepository.findById(key)
                .map(AnalyticsSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank() && !"null".equals(v))
                .orElse(null);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String raw = settingRepository.findById(key).map(AnalyticsSetting::getSettingValue).orElse(null);
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
    }
}

