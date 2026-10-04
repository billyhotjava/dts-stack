package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSettingRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.SetupStateService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setting")
@Transactional
public class SettingsResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsSettingRepository settingRepository;
    private final AnalyticsUserRepository userRepository;
    private final SetupStateService setupStateService;
    private final ObjectMapper objectMapper;

    public SettingsResource(
            AnalyticsSessionService sessionService,
            AnalyticsSettingRepository settingRepository,
            AnalyticsUserRepository userRepository,
            SetupStateService setupStateService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
        this.setupStateService = setupStateService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        List<Map<String, Object>> response = new ArrayList<>();
        response.add(setting("admin-email", adminEmail().orElse(null), false, "MB_ADMIN_EMAIL", "The email address users should be referred to if they encounter a problem.", null));
        response.add(setting("site-name", setupStateService.getSiteName().orElse(null), false, "MB_SITE_NAME", "Name used in the UI and emails.", null));
        response.add(setting("analytics-uuid", getOrCreateUuid("analytics-uuid"), false, "MB_ANALYTICS_UUID", "Unique identifier for this instance.", null));
        response.add(setting("anon-tracking-enabled", null, true, "MB_ANON_TRACKING_ENABLED", "Enable anonymous usage tracking.", "Using value of env var $MB_ANON_TRACKING_ENABLED"));
        return ResponseEntity.ok(response);
    }

    @PutMapping(path = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> put(@PathVariable("key") String key, @RequestBody JsonNode value, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        String storedValue = encodeSettingValue(value);
        AnalyticsSetting setting = settingRepository.findById(key).orElseGet(AnalyticsSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(storedValue);
        settingRepository.save(setting);

        if ("site-name".equals(key) && value != null && value.isTextual()) {
            setupStateService.setSiteName(value.asText());
        }

        return ResponseEntity.noContent().build();
    }

    private Optional<String> adminEmail() {
        return userRepository.findFirstBySuperuserTrueOrderByIdAsc().map(AnalyticsUser::getUsername);
    }

    private String getOrCreateUuid(String key) {
        return settingRepository.findById(key).map(AnalyticsSetting::getSettingValue).filter(v -> !v.isBlank()).orElseGet(() -> {
            String uuid = UUID.randomUUID().toString();
            AnalyticsSetting setting = settingRepository.findById(key).orElseGet(AnalyticsSetting::new);
            setting.setSettingKey(key);
            setting.setSettingValue(uuid);
            settingRepository.save(setting);
            return uuid;
        });
    }

    private static Map<String, Object> setting(
            String key, Object value, boolean isEnvSetting, String envName, String description, Object defaultValue) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("value", value);
        item.put("is_env_setting", isEnvSetting);
        item.put("env_name", envName);
        item.put("description", description);
        item.put("default", defaultValue);
        return item;
    }

    private String encodeSettingValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return value.toString();
    }

    @SuppressWarnings("unused")
    private JsonNode decodeSettingValue(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException ignored) {
            return TextNode.valueOf(raw);
        }
    }
}
