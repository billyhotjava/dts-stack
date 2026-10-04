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
@RequestMapping("/api/ldap")
@Transactional
public class LdapResource {

    private static final String[] KEYS = {
        "ldap-enabled",
        "ldap-host",
        "ldap-port",
        "ldap-security",
        "ldap-bind-dn",
        "ldap-password",
        "ldap-user-base",
        "ldap-user-filter",
        "ldap-attribute-email",
        "ldap-attribute-firstname",
        "ldap-attribute-lastname",
        "ldap-group-base",
        "ldap-group-membership-filter",
        "ldap-group-mappings",
        "ldap-group-sync",
        "ldap-sync-admin-group"
    };

    private final AnalyticsSessionService sessionService;
    private final AnalyticsSettingRepository settingRepository;

    public LdapResource(AnalyticsSessionService sessionService, AnalyticsSettingRepository settingRepository) {
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
        for (String key : KEYS) {
            if ("ldap-enabled".equals(key) || "ldap-group-sync".equals(key) || "ldap-sync-admin-group".equals(key)) {
                response.put(key, getBoolean(key, false));
            } else if ("ldap-port".equals(key)) {
                response.put(key, getInteger(key));
            } else if ("ldap-password".equals(key)) {
                response.put(key, null);
            } else {
                response.put(key, getString(key));
            }
        }
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

        for (String key : KEYS) {
            putIfPresent(body, key);
        }
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

    private boolean getBoolean(String key, boolean defaultValue) {
        String raw = settingRepository.findById(key).map(AnalyticsSetting::getSettingValue).orElse(null);
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
    }
}

