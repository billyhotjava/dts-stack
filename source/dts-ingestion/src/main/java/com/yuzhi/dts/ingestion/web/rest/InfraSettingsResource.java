package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.security.SecurityUtils;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/infra/settings")
public class InfraSettingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(InfraSettingsResource.class);
    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final String MASKED_SECRET = "******";

    private static final SettingsDefinition ADDAX_DEF = new SettingsDefinition(
        Set.of(
            "enabled",
            "jobDir",
            "image"
        ),
        Set.of()
    );
    private static final SettingsDefinition AIRFLOW_DEF = new SettingsDefinition(
        Set.of("enabled", "baseUrl", "apiPath", "username", "password", "dagId", "dagsDir"),
        Set.of("password")
    );
    private static final SettingsDefinition OPEN_METADATA_DEF = new SettingsDefinition(
        Set.of(
            "enabled",
            "baseUrl",
            "apiPath",
            "authToken",
            "tableFields",
            "sourceServiceName",
            "sourceServiceType",
            "destinationServiceName",
            "destinationServiceType",
            "destinationDatabase",
            "destinationSchema",
            "sourceDatabase",
            "sourceSchema",
            "ingestionEnabled",
            "ingestionPrefix",
            "ingestionSchedule"
        ),
        Set.of("authToken")
    );
    private static final SettingsDefinition DBT_DEF = new SettingsDefinition(
        Set.of("enabled", "baseUrl", "apiPath", "username", "password", "token"),
        Set.of("password", "token")
    );

    private final IngestionSettingsService settingsService;
    private final RestTemplate restTemplate;
    private final AuditService auditService;
    public InfraSettingsResource(RestTemplateBuilder builder, IngestionSettingsService settingsService, AuditService auditService) {
        this.settingsService = settingsService;
        this.auditService = auditService;
        RestTemplateBuilder baseBuilder = builder.setConnectTimeout(Duration.ofSeconds(5));
        this.restTemplate = baseBuilder.setReadTimeout(Duration.ofSeconds(10)).build();
    }

    public record SettingsPayload(String service, Map<String, Object> settings) {}

    @GetMapping("/{service}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<SettingsPayload> getSettings(@PathVariable("service") String service) {
        String normalized = normalizeService(service);
        SettingsDefinition definition = resolveDefinition(normalized);
        if (definition == null) {
            return ApiResponses.error(400, "暂不支持该服务");
        }
        IngestionSettingsService.SettingsSnapshot snapshot = settingsService.getSettings(normalized);
        Map<String, Object> filtered = filterAllowed(snapshot.raw(), definition);
        return ApiResponses.ok(new SettingsPayload(normalized, maskSecrets(filtered)));
    }

    @PostMapping("/{service}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<SettingsPayload> upsertSettings(
        @PathVariable("service") String service,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        String normalized = normalizeService(service);
        SettingsDefinition definition = resolveDefinition(normalized);
        if (definition == null) {
            return ApiResponses.error(400, "暂不支持该服务");
        }
        Map<String, Object> incoming = extractSettings(request);
        IngestionSettingsService.SettingsSnapshot snapshot = settingsService.getSettings(normalized);
        Map<String, Object> merged = mergeSettings(snapshot.raw(), incoming, definition);
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        settingsService.upsertSettings(normalized, merged, operator);
        auditService.auditAction(
            "INFRA_SETTINGS_UPDATE",
            AuditStage.SUCCESS,
            normalized,
            Map.of("summary", "更新集成配置", "service", normalized, "operator", operator)
        );
        return ApiResponses.ok(new SettingsPayload(normalized, maskSecrets(filterAllowed(merged, definition))));
    }

    @PostMapping("/{service}/test")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> testSettings(
        @PathVariable("service") String service,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        String normalized = normalizeService(service);
        SettingsDefinition definition = resolveDefinition(normalized);
        if (definition == null) {
            return ApiResponses.error(400, "暂不支持该服务");
        }
        Map<String, Object> incoming = extractSettings(request);
        IngestionSettingsService.SettingsSnapshot snapshot = settingsService.getSettings(normalized);
        Map<String, Object> merged = mergeSettings(snapshot.raw(), incoming, definition);
        Map<String, Object> result = runTest(normalized, merged);
        AuditStage stage = Boolean.TRUE.equals(result.get("success")) ? AuditStage.SUCCESS : AuditStage.FAIL;
        auditService.auditAction(
            "INFRA_SETTINGS_TEST",
            stage,
            normalized,
            Map.of("summary", "测试集成配置", "service", normalized, "operator", SecurityUtils.getCurrentUserLogin().orElse("system"))
        );
        return ApiResponses.ok(result);
    }

    private Map<String, Object> runTest(String service, Map<String, Object> settings) {
        return switch (service) {
            case IngestionSettingsService.SERVICE_ADDAX -> testAddax(settings);
            case IngestionSettingsService.SERVICE_AIRFLOW -> testAirflow(settings);
            case IngestionSettingsService.SERVICE_OPENMETADATA -> testOpenMetadata(settings);
            case IngestionSettingsService.SERVICE_DBT -> Map.of("success", false, "message", "DBT 接入尚未配置");
            default -> Map.of("success", false, "message", "暂不支持该服务");
        };
    }

    private Map<String, Object> testAddax(Map<String, Object> settings) {
        boolean enabled = booleanValue(settings.get("enabled"));
        if (!enabled) {
            return Map.of("success", true, "message", "Addax 未启用");
        }
        String jobDir = stringValue(settings.get("jobDir"));
        if (!StringUtils.hasText(jobDir)) {
            return Map.of("success", false, "message", "请先填写 Addax 作业目录");
        }
        java.nio.file.Path path = java.nio.file.Paths.get(jobDir);
        if (!java.nio.file.Files.exists(path)) {
            return Map.of("success", false, "message", "作业目录不存在: " + jobDir);
        }
        return Map.of("success", true, "message", "配置可用", "jobDir", jobDir);
    }

    private Map<String, Object> testAirflow(Map<String, Object> settings) {
        String baseUrl = stringValue(settings.get("baseUrl"));
        if (!StringUtils.hasText(baseUrl)) {
            return Map.of("success", false, "message", "请先填写 Airflow 地址");
        }
        String apiPath = stringValue(settings.getOrDefault("apiPath", "/api/v1"));
        String username = stringValue(settings.get("username"));
        String password = stringValue(settings.get("password"));
        URI uri = buildUri(baseUrl, apiPath, "/dags", Map.of("limit", 1));
        try {
            HttpHeaders headers = basicHeaders(username, password);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Map.of("success", true, "message", "连接成功", "status", response.getStatusCode().value());
        } catch (HttpStatusCodeException ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("message", "连接失败");
            result.put("status", ex.getStatusCode().value());
            String body = trim(ex.getResponseBodyAsString(), 256);
            if (StringUtils.hasText(body)) {
                result.put("body", body);
            }
            return result;
        } catch (Exception ex) {
            return Map.of("success", false, "message", "连接失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> testOpenMetadata(Map<String, Object> settings) {
        String baseUrl = stringValue(settings.get("baseUrl"));
        if (!StringUtils.hasText(baseUrl)) {
            return Map.of("success", false, "message", "请先填写 OpenMetadata 地址");
        }
        String apiPath = stringValue(settings.getOrDefault("apiPath", "/api/v1"));
        String token = stringValue(settings.get("authToken"));
        URI uri = buildUri(baseUrl, apiPath, "/system/version", null);
        try {
            HttpHeaders headers = bearerHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Map.of("success", true, "message", "连接成功", "status", response.getStatusCode().value());
        } catch (HttpStatusCodeException ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("message", "连接失败");
            result.put("status", ex.getStatusCode().value());
            String body = trim(ex.getResponseBodyAsString(), 256);
            if (StringUtils.hasText(body)) {
                result.put("body", body);
            }
            return result;
        } catch (Exception ex) {
            return Map.of("success", false, "message", "连接失败: " + ex.getMessage());
        }
    }

    private String normalizeService(String service) {
        return StringUtils.hasText(service) ? service.trim().toLowerCase() : "";
    }

    private SettingsDefinition resolveDefinition(String service) {
        return switch (service) {
            case IngestionSettingsService.SERVICE_ADDAX -> ADDAX_DEF;
            case IngestionSettingsService.SERVICE_AIRFLOW -> AIRFLOW_DEF;
            case IngestionSettingsService.SERVICE_OPENMETADATA -> OPEN_METADATA_DEF;
            case IngestionSettingsService.SERVICE_DBT -> DBT_DEF;
            default -> null;
        };
    }

    private Map<String, Object> extractSettings(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return Map.of();
        }
        Object nested = request.get("settings");
        if (nested instanceof Map<?, ?> map) {
            return new LinkedHashMap(map);
        }
        return new LinkedHashMap<>(request);
    }

    private Map<String, Object> filterAllowed(Map<String, Object> settings, SettingsDefinition definition) {
        if (definition == null || settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String key : definition.allowedKeys()) {
            if (settings.containsKey(key)) {
                filtered.put(key, settings.get(key));
            }
        }
        return filtered;
    }

    private Map<String, Object> mergeSettings(
        Map<String, Object> existing,
        Map<String, Object> incoming,
        SettingsDefinition definition
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existing != null && !existing.isEmpty()) {
            merged.putAll(existing);
        }
        if (incoming == null || incoming.isEmpty() || definition == null) {
            return merged;
        }
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key = entry.getKey();
            if (!definition.allowedKeys().contains(key)) {
                continue;
            }
            Object value = normalizeValue(key, entry.getValue());
            if (definition.secretKeys().contains(key) && isMasked(value)) {
                continue;
            }
            if (value == null || (value instanceof String text && text.isBlank())) {
                merged.remove(key);
                continue;
            }
            merged.put(key, value);
        }
        return merged;
    }

    private Object normalizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if ("enabled".equalsIgnoreCase(key) || "ingestionEnabled".equalsIgnoreCase(key)) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(value.toString().trim());
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? "" : trimmed;
        }
        return value;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value.toString().trim());
    }

    private boolean isMasked(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        return MASKED_SECRET.equals(text.trim());
    }

    private Map<String, Object> maskSecrets(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        settings.forEach(
            (key, value) -> {
                if (value instanceof Map<?, ?> nested) {
                    masked.put(key, maskSecrets(new LinkedHashMap(nested)));
                    return;
                }
                if (key != null) {
                    String lowered = key.toLowerCase();
                    if (lowered.contains("password") || lowered.contains("secret") || lowered.contains("token") || lowered.equals("key")) {
                        masked.put(key, MASKED_SECRET);
                        return;
                    }
                }
                masked.put(key, value);
            }
        );
        return masked;
    }

    private HttpHeaders basicHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(username)) {
            String token = username + ":" + String.valueOf(password);
            String encoded = java.util.Base64.getEncoder().encodeToString(token.getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(token)) {
            String raw = token.trim();
            headers.set(HttpHeaders.AUTHORIZATION, raw.startsWith("Bearer ") ? raw : "Bearer " + raw);
        }
        return headers;
    }

    private URI buildUri(String baseUrl, String apiPath, String suffix, Map<String, ?> params) {
        String path = normalizePath(apiPath, "/api/v1");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl).path(path).path(suffix);
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }

    private String normalizePath(String value, String fallback) {
        String resolved = StringUtils.hasText(value) ? value.trim() : fallback;
        if (!StringUtils.hasText(resolved)) {
            return "";
        }
        if (!resolved.startsWith("/")) {
            resolved = "/" + resolved;
        }
        if (resolved.length() > 1 && resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        return resolved;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private record SettingsDefinition(Set<String> allowedKeys, Set<String> secretKeys) {}
}
