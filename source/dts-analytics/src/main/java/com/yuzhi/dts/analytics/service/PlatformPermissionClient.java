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
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties outboundProps;

    private final Cache<String, PermissionResult> checkCache;
    private final Cache<String, AccessibleAssetsResult> listCache;

    public PlatformPermissionClient(
        RestTemplateBuilder builder,
        ObjectMapper objectMapper,
        com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties outboundProps,
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
        this.outboundProps = outboundProps;

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
        return check(username, roles, deptCode, assetType, assetId, null, null);
    }

    public PermissionResult check(
        String username,
        String roles,
        String deptCode,
        String assetType,
        String assetId,
        String userClassification,
        String assetClassification
    ) {
        return check(username, roles, deptCode, assetType, assetId, userClassification, assetClassification, "READ");
    }

    public PermissionResult check(
        String username,
        String roles,
        String deptCode,
        String assetType,
        String assetId,
        String userClassification,
        String assetClassification,
        String action
    ) {
        String cacheKey = checkCacheKey(username, roles, deptCode, assetType, assetId, userClassification, assetClassification, action);
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
            body.put("userClassification", userClassification);
            body.put("assetClassification", assetClassification);
            body.put("action", StringUtils.hasText(action) ? action.trim().toUpperCase() : "READ");
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
            return PermissionResult.ERROR;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, PermissionResult> batchCheck(String username, String roles, String deptCode,
                                                     List<AssetRef> assets) {
        return batchCheck(username, roles, deptCode, assets, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, PermissionResult> batchCheck(String username, String roles, String deptCode,
                                                     List<AssetRef> assets, String userClassification) {
        try {
            URI uri = buildUri("/api/internal/asset-permission/batch-check");
            List<Map<String, String>> assetDtos = assets.stream()
                .map(a -> Map.of("type", a.type(), "id", a.id()))
                .toList();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", username);
            body.put("userRoles", parseRoles(roles));
            body.put("userDeptCode", deptCode);
            body.put("userClassification", userClassification);
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
                    checkCache.put(checkCacheKey(username, roles, deptCode, parts[0], parts[1], userClassification, null, "READ"), pr);
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
        return listAccessibleAssetIds(username, roles, deptCode, assetType, page, size, null);
    }

    public AccessibleAssetsResult listAccessibleAssetIds(
        String username,
        String roles,
        String deptCode,
        String assetType,
        int page,
        int size,
        String userClassification
    ) {
        String cacheKey = listCacheKey(username, roles, deptCode, assetType, page, size, userClassification);
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
            body.put("userClassification", userClassification);
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
            return AccessibleAssetsResult.ERROR;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listGrants(String assetType, String assetId) {
        try {
            URI uri = UriComponentsBuilder.fromUri(buildUri("/api/internal/asset-permission/grants"))
                .queryParam("assetType", assetType)
                .queryParam("assetId", assetId)
                .build(true)
                .toUri();
            ResponseEntity<List> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                List.class
            );
            Object body = response.getBody();
            if (!(body instanceof List<?> rows)) {
                return List.of();
            }
            return rows.stream()
                .map(row -> objectMapper.convertValue(row, new TypeReference<Map<String, Object>>() {}))
                .toList();
        } catch (Exception ex) {
            LOG.warn("List platform grants failed type={} id={}: {}", assetType, assetId, ex.getMessage());
            throw new PlatformPermissionException("list platform grants failed", ex);
        }
    }

    public Map<String, Object> upsertGrant(
        String assetType,
        String assetId,
        String granteeType,
        String granteeId,
        String permission,
        boolean levelOverride,
        String grantedBy,
        String grantReason
    ) {
        try {
            URI uri = buildUri("/api/internal/asset-permission/grants");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("assetType", assetType);
            body.put("assetId", assetId);
            body.put("granteeType", granteeType);
            body.put("granteeId", granteeId);
            body.put("permission", permission);
            body.put("levelOverride", levelOverride);
            body.put("grantedBy", grantedBy);
            body.put("grantReason", grantReason);
            ResponseEntity<Map> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                new HttpEntity<>(body, buildHeaders()),
                Map.class
            );
            clearAllPermissionCaches();
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                return Map.of();
            }
            return objectMapper.convertValue(responseBody, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            LOG.warn("Upsert platform grant failed type={} id={} grantee={}: {}", assetType, assetId, granteeId, ex.getMessage());
            throw new PlatformPermissionException("upsert platform grant failed", ex);
        }
    }

    public boolean revokeGrant(String assetType, String assetId, Long grantId) {
        try {
            URI uri = UriComponentsBuilder.fromUri(buildUri("/api/internal/asset-permission/grants/" + grantId))
                .queryParam("assetType", assetType)
                .queryParam("assetId", assetId)
                .build(true)
                .toUri();
            restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(buildHeaders()), Map.class);
            clearAllPermissionCaches();
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            return false;
        } catch (Exception ex) {
            LOG.warn("Revoke platform grant failed type={} id={} grantId={}: {}", assetType, assetId, grantId, ex.getMessage());
            throw new PlatformPermissionException("revoke platform grant failed", ex);
        }
    }

    public int revokeAllGrants(String assetType, String assetId) {
        try {
            URI uri = UriComponentsBuilder.fromUri(buildUri("/api/internal/asset-permission/grants/by-asset"))
                .queryParam("assetType", assetType)
                .queryParam("assetId", assetId)
                .build(true)
                .toUri();
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(buildHeaders()), Map.class);
            clearAllPermissionCaches();
            Map<?, ?> responseBody = response.getBody();
            Object deleted = responseBody == null ? null : responseBody.get("deleted");
            return deleted instanceof Number n ? n.intValue() : 0;
        } catch (Exception ex) {
            LOG.warn("Revoke all platform grants failed type={} id={}: {}", assetType, assetId, ex.getMessage());
            throw new PlatformPermissionException("revoke all platform grants failed", ex);
        }
    }

    public void invalidateCache(String username) {
        String prefix = safeKey(username) + "|";
        checkCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        listCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clearAllPermissionCaches() {
        checkCache.invalidateAll();
        listCache.invalidateAll();
    }

    private URI buildUri(String path) {
        String baseUrl = StringUtils.hasText(outboundProps.getBaseUrl()) ? outboundProps.getBaseUrl().trim() : "http://dts-platform:8081";
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path(path)
            .build(true)
            .toUri();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        String serviceName = StringUtils.hasText(outboundProps.getServiceName()) ? outboundProps.getServiceName().trim() : "dts-analytics";
        headers.set(SERVICE_HEADER, serviceName);
        if (StringUtils.hasText(outboundProps.getServiceToken())) {
            headers.set(SERVICE_TOKEN_HEADER, outboundProps.getServiceToken().trim());
        }
        return headers;
    }

    private String checkCacheKey(
        String username,
        String roles,
        String deptCode,
        String assetType,
        String assetId,
        String userClassification,
        String assetClassification,
        String action
    ) {
        return String.join(
            "|",
            safeKey(username),
            rolesCachePart(roles),
            safeKey(deptCode),
            safeKey(assetType),
            safeKey(assetId),
            safeKey(userClassification),
            safeKey(assetClassification),
            safeKey(action)
        );
    }

    private String listCacheKey(String username, String roles, String deptCode, String assetType, int page, int size, String userClassification) {
        return String.join(
            "|",
            safeKey(username),
            rolesCachePart(roles),
            safeKey(deptCode),
            safeKey(assetType),
            String.valueOf(page),
            String.valueOf(size),
            safeKey(userClassification)
        );
    }

    private String rolesCachePart(String rolesCsv) {
        return String.join(",", parseRoles(rolesCsv).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private String safeKey(String value) {
        return value == null ? "" : value.trim();
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
        public static final PermissionResult ERROR = new PermissionResult(false, null, "client_error");

        public boolean clientError() {
            return "client_error".equals(reason);
        }
    }

    public record AssetRef(String type, String id) {}

    public record AccessibleAssetsResult(List<String> assetIds, long total, String scope) {
        public static final AccessibleAssetsResult EMPTY = new AccessibleAssetsResult(List.of(), 0, "FILTERED");
        public static final AccessibleAssetsResult ERROR = new AccessibleAssetsResult(List.of(), 0, "ERROR");

        public boolean isAll() {
            return "ALL".equals(scope);
        }

        public boolean isError() {
            return "ERROR".equals(scope);
        }
    }

    public static class PlatformPermissionException extends RuntimeException {
        public PlatformPermissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
