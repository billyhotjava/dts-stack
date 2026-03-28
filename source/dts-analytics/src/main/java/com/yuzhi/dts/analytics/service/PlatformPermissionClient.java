package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PlatformPermissionClient {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformPermissionClient.class);
    private static final String DEFAULT_BASE_URL = "http://dts-platform:8081";
    private static final String SERVICE_HEADER = "X-DTS-Service";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceName;

    private final Cache<String, PermissionResult> checkCache;
    private final Cache<String, AccessibleAssetsResult> listCache;

    public PlatformPermissionClient(
        RestTemplateBuilder builder,
        ObjectMapper objectMapper,
        @Value("${dts.analytics.platform.base-url:}") String baseUrl,
        @Value("${dts.analytics.platform.service-name:dts-analytics}") String serviceName,
        @Value("${dts.analytics.platform-permission.connect-timeout-ms:2000}") long connectTimeoutMs,
        @Value("${dts.analytics.platform-permission.read-timeout-ms:5000}") long readTimeoutMs,
        @Value("${dts.analytics.platform-permission.check-cache-ttl-seconds:30}") long checkCacheTtl,
        @Value("${dts.analytics.platform-permission.list-cache-ttl-seconds:60}") long listCacheTtl,
        @Value("${dts.analytics.platform-permission.check-cache-max-size:10000}") long checkCacheMaxSize
    ) {
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            .setReadTimeout(Duration.ofMillis(readTimeoutMs))
            .build();
        this.objectMapper = objectMapper;
        this.baseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim() : DEFAULT_BASE_URL;
        this.serviceName = StringUtils.hasText(serviceName) ? serviceName.trim() : "dts-analytics";

        this.checkCache = Caffeine.newBuilder()
            .maximumSize(checkCacheMaxSize)
            .expireAfterWrite(Duration.ofSeconds(checkCacheTtl))
            .build();

        this.listCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofSeconds(listCacheTtl))
            .build();
    }

    public PermissionResult check(String username, String roles, String deptCode, String assetType, String assetId) {
        String cacheKey = username + ":" + assetType + ":" + assetId;
        PermissionResult cached = checkCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            URI uri = buildUri("/api/internal/asset-permission/check");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", username);
            body.put("userRoles", parseRoles(roles));
            body.put("userDeptCode", deptCode);
            body.put("asset", Map.of("type", assetType, "id", assetId));

            ResponseEntity<Map> response = restTemplate.exchange(
                uri, HttpMethod.POST,
                new HttpEntity<>(body, buildHeaders()),
                Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return PermissionResult.DENIED;
            }

            PermissionResult result = new PermissionResult(
                Boolean.TRUE.equals(responseBody.get("allowed")),
                stringVal(responseBody.get("permission")),
                stringVal(responseBody.get("reason"))
            );
            checkCache.put(cacheKey, result);
            return result;
        } catch (Exception ex) {
            LOG.warn("Permission check failed for {}:{} user={}: {}", assetType, assetId, username, ex.getMessage());
            return PermissionResult.DENIED;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, PermissionResult> batchCheck(String username, String roles, String deptCode,
                                                     List<AssetRef> assets) {
        try {
            URI uri = buildUri("/api/internal/asset-permission/batch-check");
            List<Map<String, String>> assetDtos = assets.stream()
                .map(a -> Map.of("type", a.type(), "id", a.id()))
                .toList();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", username);
            body.put("userRoles", parseRoles(roles));
            body.put("userDeptCode", deptCode);
            body.put("assets", assetDtos);

            ResponseEntity<Map> response = restTemplate.exchange(
                uri, HttpMethod.POST,
                new HttpEntity<>(body, buildHeaders()),
                Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return Collections.emptyMap();
            }

            Object resultsObj = responseBody.get("results");
            if (!(resultsObj instanceof Map<?, ?> resultsMap)) {
                return Collections.emptyMap();
            }

            Map<String, PermissionResult> results = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : resultsMap.entrySet()) {
                String key = entry.getKey().toString();
                Map<String, Object> value = objectMapper.convertValue(entry.getValue(),
                    new TypeReference<Map<String, Object>>() {});
                PermissionResult pr = new PermissionResult(
                    Boolean.TRUE.equals(value.get("allowed")),
                    stringVal(value.get("permission")),
                    stringVal(value.get("reason"))
                );
                results.put(key, pr);
                // Populate single-check cache as well
                String[] parts = key.split(":", 2);
                if (parts.length == 2) {
                    checkCache.put(username + ":" + key, pr);
                }
            }
            return results;
        } catch (Exception ex) {
            LOG.warn("Batch permission check failed user={}: {}", username, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    public AccessibleAssetsResult listAccessibleAssetIds(String username, String roles, String deptCode,
                                                          String assetType, int page, int size) {
        String cacheKey = username + ":" + assetType + ":" + page + ":" + size;
        AccessibleAssetsResult cached = listCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            URI uri = buildUri("/api/internal/asset-permission/accessible-ids");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", username);
            body.put("userRoles", parseRoles(roles));
            body.put("userDeptCode", deptCode);
            body.put("assetType", assetType);
            body.put("page", page);
            body.put("size", size);

            ResponseEntity<Map> response = restTemplate.exchange(
                uri, HttpMethod.POST,
                new HttpEntity<>(body, buildHeaders()),
                Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return AccessibleAssetsResult.EMPTY;
            }

            @SuppressWarnings("unchecked")
            List<String> assetIds = responseBody.get("assetIds") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();
            long total = responseBody.get("total") instanceof Number n ? n.longValue() : 0;
            String scope = stringVal(responseBody.get("scope"));

            AccessibleAssetsResult result = new AccessibleAssetsResult(assetIds, total,
                scope != null ? scope : "FILTERED");
            listCache.put(cacheKey, result);
            return result;
        } catch (Exception ex) {
            LOG.warn("Accessible assets query failed user={} type={}: {}", username, assetType, ex.getMessage());
            return AccessibleAssetsResult.EMPTY;
        }
    }

    public void invalidateCache(String username) {
        checkCache.asMap().keySet().removeIf(k -> k.startsWith(username + ":"));
        listCache.asMap().keySet().removeIf(k -> k.startsWith(username + ":"));
    }

    private URI buildUri(String path) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path(path)
            .build(true)
            .toUri();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SERVICE_HEADER, serviceName);
        return headers;
    }

    private List<String> parseRoles(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return List.of();
        }
        return List.of(rolesCsv.split(",")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private String stringVal(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    // --- Records ---

    public record PermissionResult(boolean allowed, String permission, String reason) {
        public static final PermissionResult DENIED = new PermissionResult(false, null, "denied");
    }

    public record AssetRef(String type, String id) {}

    public record AccessibleAssetsResult(List<String> assetIds, long total, String scope) {
        public static final AccessibleAssetsResult EMPTY = new AccessibleAssetsResult(List.of(), 0, "FILTERED");

        public boolean isAll() {
            return "ALL".equals(scope);
        }
    }
}
