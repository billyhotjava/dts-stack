package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PlatformInfraClient {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformInfraClient.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String CAPABILITY_ANALYTICS_REGISTERABLE = "ANALYTICS_REGISTERABLE";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties outboundProps;

    public PlatformInfraClient(
        RestTemplateBuilder builder,
        ObjectMapper objectMapper,
        com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties outboundProps
    ) {
        long timeout = Math.max(2, outboundProps.getTimeoutSeconds());
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(timeout))
            .setReadTimeout(Duration.ofSeconds(timeout))
            .build();
        this.objectMapper = objectMapper;
        this.outboundProps = outboundProps;
    }

    public List<DataSourceSummary> listDataSources() {
        try {
            List<DataSourceSummary> selectable = listSelectableDataSources();
            if (selectable.isEmpty()) {
                return selectable;
            }
            try {
                return enrichSelectableDataSources(selectable, listLegacyDataSources());
            } catch (RuntimeException ex) {
                LOG.warn("Platform data source metadata enrichment failed: {}; returning selectable summaries", ex.getMessage());
                return selectable;
            }
        } catch (HttpStatusCodeException ex) {
            LOG.warn(
                "Platform data source selection failed status={} body={}; falling back to legacy list",
                ex.getStatusCode().value(),
                ex.getResponseBodyAsString()
            );
        } catch (RuntimeException ex) {
            LOG.warn("Platform data source selection failed: {}; falling back to legacy list", ex.getMessage());
        }
        return listLegacyDataSources();
    }

    private List<DataSourceSummary> enrichSelectableDataSources(
        List<DataSourceSummary> selectable,
        List<DataSourceSummary> legacy
    ) {
        Map<String, DataSourceSummary> legacyById = new LinkedHashMap<>();
        for (DataSourceSummary item : legacy) {
            if (item != null && StringUtils.hasText(item.id())) {
                legacyById.put(item.id(), item);
            }
        }
        return selectable
            .stream()
            .map(item -> item != null && StringUtils.hasText(item.id()) ? legacyById.getOrDefault(item.id(), item) : item)
            .toList();
    }

    private List<DataSourceSummary> listSelectableDataSources() {
        URI uri = buildDataSourceSelectionUri(CAPABILITY_ANALYTICS_REGISTERABLE);
        HttpHeaders headers = buildHeaders();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody() == null ? Map.of() : new LinkedHashMap<>(response.getBody());
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map) {
                Object items = map.get("items");
                if (items instanceof List<?> list) {
                    return toSummaryList(list);
                }
            }
            return List.of();
        } catch (HttpStatusCodeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("获取平台数据源失败: " + ex.getMessage(), ex);
        }
    }

    private List<DataSourceSummary> listLegacyDataSources() {
        URI uri = buildUri("/infra/data-sources");
        HttpHeaders headers = buildHeaders();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody() == null ? Map.of() : new LinkedHashMap<>(response.getBody());
            Object data = body.get("data");
            if (data instanceof List<?> list) {
                return toSummaryList(list);
            }
            return List.of();
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Platform data source list failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("获取平台数据源失败: " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("获取平台数据源失败: " + ex.getMessage(), ex);
        }
    }

    private URI buildDataSourceSelectionUri(String capability) {
        return UriComponentsBuilder.fromUri(buildUri("/infra/data-source-selections"))
            .queryParam("capability", capability)
            .build(true)
            .toUri();
    }

    public DataSourceDetail fetchDataSourceDetail(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("dataSourceId不能为空");
        }
        // Dataset SQL execution needs runtime secrets to build the JDBC connection pool.
        URI uri = buildUri("/infra/data-sources/" + id + "/runtime-detail");
        HttpHeaders headers = buildHeaders();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody() == null ? Map.of() : new LinkedHashMap<>(response.getBody());
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map) {
                return toDetail(map);
            }
            throw new IllegalStateException("平台未返回数据源详情");
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Platform data source detail failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("获取平台数据源失败: " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("获取平台数据源失败: " + ex.getMessage(), ex);
        }
    }

    private URI buildUri(String path) {
        String baseUrl = StringUtils.hasText(outboundProps.getBaseUrl()) ? outboundProps.getBaseUrl().trim() : "http://dts-platform:8081";
        String apiPath = StringUtils.hasText(outboundProps.getApiPath()) ? outboundProps.getApiPath().trim() : "/api";
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path(apiPath)
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

    private List<DataSourceSummary> toSummaryList(List<?> rawList) {
        List<DataSourceSummary> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                result.add(toSummary(map));
            } else if (item != null) {
                Map<String, Object> map = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {});
                result.add(toSummary(map));
            }
        }
        return result;
    }

    private DataSourceSummary toSummary(Map<?, ?> raw) {
        Map<String, Object> map = castMap(raw);
        return new DataSourceSummary(
            stringVal(map.get("id")),
            stringVal(map.get("name")),
            stringVal(map.get("type")),
            stringVal(map.get("jdbcUrl")),
            stringVal(map.get("description")),
            stringVal(map.get("ownerDept")),
            stringVal(map.get("status")),
            stringVal(map.get("driverVersion")),
            stringVal(map.get("lastUpdatedAt"))
        );
    }

    private DataSourceDetail toDetail(Map<?, ?> raw) {
        Map<String, Object> map = castMap(raw);
        return new DataSourceDetail(
            stringVal(map.get("id")),
            stringVal(map.get("name")),
            stringVal(map.get("type")),
            stringVal(map.get("jdbcUrl")),
            stringVal(map.get("username")),
            stringVal(map.get("description")),
            stringVal(map.get("ownerDept")),
            castMap(map.get("props")),
            castMap(map.get("secrets")),
            stringVal(map.get("status")),
            stringVal(map.get("lastVerifiedAt"))
        );
    }

    private Map<String, Object> castMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
        }
        return Collections.emptyMap();
    }

    private String stringVal(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public record DataSourceSummary(
        String id,
        String name,
        String type,
        String jdbcUrl,
        String description,
        String ownerDept,
        String status,
        String driverVersion,
        String lastUpdatedAt
    ) {}

    public record DataSourceDetail(
        String id,
        String name,
        String type,
        String jdbcUrl,
        String username,
        String description,
        String ownerDept,
        Map<String, Object> props,
        Map<String, Object> secrets,
        String status,
        String lastVerifiedAt
    ) {}
}
