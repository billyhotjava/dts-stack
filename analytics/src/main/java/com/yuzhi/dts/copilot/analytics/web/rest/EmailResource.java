package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
@Transactional
public class EmailResource {

    private static final String KEY_SMTP_HOST = "email-smtp-host";
    private static final String KEY_SMTP_PORT = "email-smtp-port";
    private static final String KEY_SMTP_SECURITY = "email-smtp-security";
    private static final String KEY_SMTP_USERNAME = "email-smtp-username";
    private static final String KEY_SMTP_PASSWORD = "email-smtp-password";
    private static final String KEY_FROM_ADDRESS = "email-from-address";
    private static final String KEY_FROM_NAME = "email-from-name";
    private static final String KEY_REPLY_TO = "email-reply-to";

    private final AnalyticsSessionService sessionService;
    private final AnalyticsSettingRepository settingRepository;

    public EmailResource(AnalyticsSessionService sessionService, AnalyticsSettingRepository settingRepository) {
        this.sessionService = sessionService;
        this.settingRepository = settingRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put(KEY_SMTP_HOST, getString(KEY_SMTP_HOST));
        response.put(KEY_SMTP_PORT, getInteger(KEY_SMTP_PORT));
        response.put(KEY_SMTP_SECURITY, getString(KEY_SMTP_SECURITY));
        response.put(KEY_SMTP_USERNAME, getString(KEY_SMTP_USERNAME));
        response.put(KEY_SMTP_PASSWORD, null);
        response.put(KEY_FROM_ADDRESS, getString(KEY_FROM_ADDRESS));
        response.put(KEY_FROM_NAME, getString(KEY_FROM_NAME));
        response.put(KEY_REPLY_TO, getString(KEY_REPLY_TO));
        return ResponseEntity.ok(response);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> put(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        putIfPresent(body, KEY_SMTP_HOST);
        putIfPresent(body, KEY_SMTP_PORT);
        putIfPresent(body, KEY_SMTP_SECURITY);
        putIfPresent(body, KEY_SMTP_USERNAME);
        putIfPresent(body, KEY_SMTP_PASSWORD);
        putIfPresent(body, KEY_FROM_ADDRESS);
        putIfPresent(body, KEY_FROM_NAME);
        putIfPresent(body, KEY_REPLY_TO);

        return get(request);
    }

    @PostMapping(path = "/test", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> test(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        return ResponseEntity.ok(Map.of());
    }

    private void putIfPresent(JsonNode body, String key) {
        if (body == null || !body.has(key)) {
            return;
        }
        JsonNode value = body.get(key);
        String stored = value == null || value.isNull() ? "null" : value.isTextual() ? value.asText() : value.toString();
        settingRepository.save(newSetting(key, stored));
    }

    private com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting newSetting(String key, String value) {
        return settingRepository.findById(key).map(existing -> {
            existing.setSettingValue(value);
            return existing;
        }).orElseGet(() -> {
            com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting setting = new com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            return setting;
        });
    }

    private String getString(String key) {
        return settingRepository.findById(key)
                .map(com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank() && !"null".equals(v))
                .orElse(null);
    }

    private Integer getInteger(String key) {
        String raw = getString(key);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

