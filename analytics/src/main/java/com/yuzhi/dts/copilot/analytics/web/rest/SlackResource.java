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
@RequestMapping("/api/slack")
@Transactional
public class SlackResource {

    private static final String KEY_SLACK_TOKEN = "slack-token";
    private static final String KEY_SLACK_APP_TOKEN = "slack-app-token";
    private static final String KEY_SLACK_FILES_CHANNEL = "slack-files-channel";

    private final AnalyticsSessionService sessionService;
    private final AnalyticsSettingRepository settingRepository;

    public SlackResource(AnalyticsSessionService sessionService, AnalyticsSettingRepository settingRepository) {
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
        response.put(KEY_SLACK_TOKEN, getString(KEY_SLACK_TOKEN));
        response.put(KEY_SLACK_APP_TOKEN, getString(KEY_SLACK_APP_TOKEN));
        response.put(KEY_SLACK_FILES_CHANNEL, getString(KEY_SLACK_FILES_CHANNEL));
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

        putIfPresent(body, KEY_SLACK_TOKEN);
        putIfPresent(body, KEY_SLACK_APP_TOKEN);
        putIfPresent(body, KEY_SLACK_FILES_CHANNEL);
        return settings(request);
    }

    @GetMapping(path = "/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> manifest(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        return ResponseEntity.ok(Map.of(
                "display_information", Map.of("name", "DTS Analytics"),
                "oauth_config", Map.of(),
                "features", Map.of()));
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
}

