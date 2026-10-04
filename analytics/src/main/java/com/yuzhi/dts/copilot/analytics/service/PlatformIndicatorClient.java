package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlatformIndicatorClient {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformIndicatorClient.class);

    private final PlatformIndicatorProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final PlatformIndicatorMetricsService metricsService;
    private final Map<String, CachedValue> valueCache = new ConcurrentHashMap<>();

    private volatile CachedToken cachedToken;

    @Autowired
    public PlatformIndicatorClient(
            PlatformIndicatorProperties properties,
            ObjectMapper objectMapper,
            PlatformIndicatorMetricsService metricsService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService == null
                ? PlatformIndicatorMetricsService.noop()
                : metricsService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();
    }

    PlatformIndicatorClient(
            PlatformIndicatorProperties properties,
            ObjectMapper objectMapper) {
        this(properties, objectMapper, PlatformIndicatorMetricsService.noop());
    }

    public CatalogResponse listIndicators() {
        if (!StringUtils.hasText(properties.baseUrl())) {
            return degradedCatalog("平台指标服务未配置");
        }
        long start = System.nanoTime();
        try {
            JsonNode payload = sendGet("/api/governance/indicators?status=" + encode("PUBLISHED"));
            JsonNode itemsNode = findItemsNode(unwrapData(payload));
            List<IndicatorItem> items = toIndicatorItems(itemsNode);
            String syncedAt = firstText(payload, "syncedAt", "updatedAt", "timestamp");
            metricsService.recordApiCall("catalog", "success", System.nanoTime() - start);
            return new CatalogResponse(items, syncedAt, false, null);
        } catch (Exception ex) {
            LOG.warn("Failed to list platform indicators: {}", ex.getMessage());
            metricsService.recordApiCall("catalog", "degraded", System.nanoTime() - start);
            return degradedCatalog("平台指标服务暂不可达");
        }
    }

    public ValueResponse getDashboard(Integer days) {
        String path = "/api/governance/indicators/dashboard";
        if (days != null && days > 0) {
            path += "?days=" + encode(String.valueOf(days));
        }
        return fetchValue("_all", "dashboard", path);
    }

    public ValueResponse getDetail(String indicatorId, Integer days) {
        String id = normalizeIndicatorId(indicatorId);
        String path = "/api/governance/indicators/" + encode(id) + "/detail";
        if (days != null && days > 0) {
            path += "?days=" + encode(String.valueOf(days));
        }
        return fetchValue(id, "detail", path);
    }

    public ValueResponse drilldown(String indicatorId, String dimension, String period) {
        String id = normalizeIndicatorId(indicatorId);
        String normalizedDimension = StringUtils.hasText(dimension) ? dimension.trim() : "";
        StringBuilder path = new StringBuilder("/api/governance/indicators/")
                .append(encode(id))
                .append("/drilldown?dimension=")
                .append(encode(normalizedDimension));
        if (StringUtils.hasText(period)) {
            path.append("&period=").append(encode(period.trim()));
        }
        return fetchValue(id, "drilldown", path.toString());
    }

    private ValueResponse fetchValue(String indicatorId, String mode, String path) {
        if (!StringUtils.hasText(properties.baseUrl())) {
            return degradedValue(indicatorId, mode, "平台指标服务未配置");
        }
        String cacheKey = mode + "|" + path;
        CachedValue cached = valueCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            metricsService.recordCache(mode, true);
            return cached.response();
        }
        metricsService.recordCache(mode, false);
        long start = System.nanoTime();
        try {
            JsonNode payload = sendGet(path);
            ValueResponse response = toValueResponse(indicatorId, mode, unwrapData(payload));
            if (!response.degraded() && properties.valueCacheTtlSeconds() > 0) {
                valueCache.put(cacheKey, new CachedValue(
                        response,
                        Instant.now().plusSeconds(properties.valueCacheTtlSeconds())));
            }
            metricsService.recordApiCall(mode, "success", System.nanoTime() - start);
            return response;
        } catch (Exception ex) {
            LOG.warn("Failed to fetch platform indicator value {} {}: {}", mode, indicatorId, ex.getMessage());
            metricsService.recordApiCall(mode, "degraded", System.nanoTime() - start);
            return degradedValue(indicatorId, mode, "平台指标服务暂不可达");
        }
    }

    private JsonNode sendGet(String path) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(resolve(path))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .GET()
                .header("Accept", "application/json");
        if (StringUtils.hasText(properties.serviceName()) && StringUtils.hasText(properties.serviceToken())) {
            builder.header("X-DTS-Service", properties.serviceName());
            builder.header("X-DTS-Service-Token", properties.serviceToken());
        } else {
            String token = resolveAccessToken();
            if (StringUtils.hasText(token)) {
                builder.header("Authorization", "Bearer " + token);
            }
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String resolveAccessToken() throws IOException, InterruptedException {
        if (StringUtils.hasText(properties.authToken())) {
            return properties.authToken();
        }
        if (!StringUtils.hasText(properties.tokenUrl())
                || !StringUtils.hasText(properties.clientId())
                || !StringUtils.hasText(properties.clientSecret())) {
            return "";
        }
        CachedToken token = cachedToken;
        if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return token.value();
        }
        String body = "grant_type=client_credentials"
                + "&client_id=" + encode(properties.clientId())
                + "&client_secret=" + encode(properties.clientSecret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.tokenUrl()))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Token HTTP " + response.statusCode());
        }
        JsonNode payload = objectMapper.readTree(response.body());
        String accessToken = firstText(payload, "access_token", "token");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Token response missing access_token");
        }
        long expiresIn = payload.path("expires_in").asLong(300);
        cachedToken = new CachedToken(accessToken, Instant.now().plusSeconds(Math.max(60, expiresIn)));
        return accessToken;
    }

    private URI resolve(String path) {
        String base = properties.baseUrl().replaceAll("/+$", "");
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + normalizedPath);
    }

    private CatalogResponse degradedCatalog(String reason) {
        return new CatalogResponse(List.of(), null, true, reason);
    }

    private ValueResponse degradedValue(String indicatorId, String mode, String reason) {
        return new ValueResponse(indicatorId, mode, List.of(), List.of(), null, List.of(), true, reason);
    }

    private ValueResponse toValueResponse(String fallbackIndicatorId, String fallbackMode, JsonNode data) {
        JsonNode indicatorNode = data == null ? null : data.path("indicator");
        String indicatorId = firstText(data, "indicatorId", "id", "code");
        if (!StringUtils.hasText(indicatorId)) {
            indicatorId = firstText(indicatorNode, "indicatorId", "id", "code");
        }
        String mode = firstText(data, "mode", "valueMode");
        JsonNode rowsNode = resolveRowsNode(data, fallbackMode);
        List<DatasetColumn> cols = toColumns(firstExisting(data, "cols", "columns"));
        if (cols.isEmpty()) {
            cols = inferColumns(rowsNode);
        }
        List<List<Object>> rows = toRows(rowsNode, cols);
        String timeGrain = firstText(data, "timeGrain", "time_grain", "granularity");
        if (!StringUtils.hasText(timeGrain)) {
            timeGrain = firstText(indicatorNode, "timeGrain", "time_grain", "granularity");
        }
        List<String> dimensionFields = toStringList(firstExisting(data, "dimensionFields", "dimension_fields", "dimensions"));
        if (dimensionFields.isEmpty()) {
            dimensionFields = toStringList(firstExisting(indicatorNode, "dimensionFields", "dimension_fields", "dimensions"));
        }
        return new ValueResponse(
                StringUtils.hasText(indicatorId) ? indicatorId : fallbackIndicatorId,
                StringUtils.hasText(mode) ? mode : fallbackMode,
                cols,
                rows,
                timeGrain,
                dimensionFields,
                data.path("degraded").asBoolean(false),
                firstText(data, "degradedReason", "message"));
    }

    private JsonNode resolveRowsNode(JsonNode data, String fallbackMode) {
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        if (data.isArray()) {
            return data;
        }
        JsonNode rowsNode = firstExisting(data, "rows", "data", "items");
        if (rowsNode != null && rowsNode.isArray()) {
            return rowsNode;
        }
        if ("dashboard".equals(fallbackMode)) {
            JsonNode indicators = data.path("indicators");
            if (indicators.isArray()) {
                return indicators;
            }
        }
        if ("detail".equals(fallbackMode)) {
            JsonNode trend = data.path("trend");
            if (trend.isArray() && !trend.isEmpty()) {
                return trend;
            }
            JsonNode history = data.path("history");
            if (history.isArray()) {
                return history;
            }
        }
        return rowsNode;
    }

    private List<IndicatorItem> toIndicatorItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<IndicatorItem> result = new ArrayList<>();
        for (JsonNode item : node) {
            String id = firstText(item, "id", "indicatorId", "code");
            String name = firstText(item, "name", "displayName");
            if (!StringUtils.hasText(id) || !StringUtils.hasText(name)) {
                continue;
            }
            result.add(new IndicatorItem(
                    id,
                    firstText(item, "code"),
                    name,
                    firstText(item, "category", "domain", "dept"),
                    firstText(item, "definition", "description"),
                    firstText(item, "expressionSql", "expression_sql", "formula"),
                    firstText(item, "status", "classification"),
                    firstText(item, "version", "latestVersion"),
                    toStringList(firstExisting(item, "dimensionFields", "dimension_fields", "dimensions")),
                    firstText(item, "timeGrain", "time_grain", "granularity"),
                    firstText(item, "owner"),
                    firstText(item, "dataLevel", "data_level", "classificationLevel")));
        }
        return result;
    }

    private List<DatasetColumn> toColumns(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DatasetColumn> result = new ArrayList<>();
        int index = 1;
        for (JsonNode column : node) {
            String name = column.isTextual() ? column.asText() : firstText(column, "name", "field", "key");
            if (!StringUtils.hasText(name)) {
                name = "col_" + index;
            }
            result.add(new DatasetColumn(
                    name,
                    column.isTextual() ? name : firstText(column, "display_name", "displayName", "label"),
                    column.isTextual() ? null : firstText(column, "base_type", "baseType", "type")));
            index += 1;
        }
        return result;
    }

    private List<List<Object>> toRows(JsonNode node, List<DatasetColumn> cols) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<List<Object>> result = new ArrayList<>();
        for (JsonNode row : node) {
            if (row.isArray()) {
                result.add(objectMapper.convertValue(row, objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, Object.class)));
                continue;
            }
            if (row.isObject() && !cols.isEmpty()) {
                List<Object> values = new ArrayList<>();
                for (DatasetColumn col : cols) {
                    values.add(toPlainValue(row.get(col.name())));
                }
                result.add(values);
            }
        }
        return result;
    }

    private List<DatasetColumn> inferColumns(JsonNode rowsNode) {
        if (rowsNode == null || !rowsNode.isArray() || rowsNode.isEmpty() || !rowsNode.get(0).isObject()) {
            return List.of();
        }
        List<DatasetColumn> result = new ArrayList<>();
        rowsNode.get(0).fieldNames().forEachRemaining(name -> result.add(new DatasetColumn(name, name, null)));
        return Collections.unmodifiableList(result);
    }

    private JsonNode unwrapData(JsonNode payload) {
        if (payload == null) {
            return objectMapper.createObjectNode();
        }
        JsonNode data = payload.path("data");
        return data.isMissingNode() || data.isNull() ? payload : data;
    }

    private JsonNode findItemsNode(JsonNode data) {
        if (data == null) {
            return null;
        }
        if (data.isArray()) {
            return data;
        }
        for (String key : List.of("items", "content", "records", "list")) {
            JsonNode node = data.path(key);
            if (node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private JsonNode firstExisting(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... keys) {
        JsonNode value = firstExisting(node, keys);
        if (value == null) {
            return null;
        }
        if (value.isTextual()) {
            String text = value.asText().trim();
            return text.isEmpty() ? null : text;
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return null;
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return List.of();
            }
            if (text.startsWith("[") && text.endsWith("]")) {
                try {
                    return toStringList(objectMapper.readTree(text));
                } catch (IOException ignored) {
                    // Fall through to comma splitting for legacy compact strings.
                }
            }
            List<String> values = new ArrayList<>();
            for (String item : text.split("\\s*,\\s*")) {
                if (!item.isBlank()) {
                    values.add(item);
                }
            }
            return Collections.unmodifiableList(values);
        }
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return Collections.unmodifiableList(values);
    }

    private Object toPlainValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private String normalizeIndicatorId(String indicatorId) {
        if (!StringUtils.hasText(indicatorId)) {
            throw new IllegalArgumentException("indicatorId不能为空");
        }
        return indicatorId.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String value, Instant expiresAt) {}

    private record CachedValue(ValueResponse response, Instant expiresAt) {}

    public record CatalogResponse(
            List<IndicatorItem> items,
            String syncedAt,
            boolean degraded,
            String degradedReason) {}

    public record IndicatorItem(
            String id,
            String code,
            String name,
            String category,
            String definition,
            String expressionSql,
            String status,
            String version,
            List<String> dimensionFields,
            String timeGrain,
            String owner,
            String dataLevel) {}

    public record DatasetColumn(
            String name,
            String display_name,
            String base_type) {}

    public record ValueResponse(
            String indicatorId,
            String mode,
            List<DatasetColumn> cols,
            List<List<Object>> rows,
            String timeGrain,
            List<String> dimensionFields,
            boolean degraded,
            String degradedReason) {}
}
