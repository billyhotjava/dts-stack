package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Provides data source management by delegating to CopilotAiClient
 * (which calls copilot-ai REST APIs).
 */
@Component
public class PlatformInfraClient {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformInfraClient.class);

    private final CopilotAiClient copilotAiClient;
    private final ObjectMapper objectMapper;

    public PlatformInfraClient(
            CopilotAiClient copilotAiClient,
            ObjectMapper objectMapper) {
        this.copilotAiClient = copilotAiClient;
        this.objectMapper = objectMapper;
    }

    public List<DataSourceSummary> listDataSources() {
        try {
            List<Map<String, Object>> sources = copilotAiClient.getDataSources("");
            return toSummaryList(sources);
        } catch (Exception ex) {
            LOG.warn("Failed to list data sources from copilot-ai: {}", ex.getMessage());
            throw new IllegalStateException("获取数据源失败: " + ex.getMessage(), ex);
        }
    }

    public DataSourceDetail fetchDataSourceDetail(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("dataSourceId不能为空");
        }
        try {
            return copilotAiClient.getDataSource(id, "")
                    .map(this::toDetail)
                    .orElseThrow(() -> new IllegalStateException("未返回数据源详情"));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("获取数据源失败: " + ex.getMessage(), ex);
        }
    }

    public DataSourceDetail fetchDataSourceDetail(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("dataSourceId不能为空");
        }
        try {
            Optional<Map<String, Object>> remote = copilotAiClient.getDataSource(id, "");
            if (remote.isPresent()) {
                return toDetail(remote.get());
            }
            throw new IllegalStateException("Data source API returned no details; cross-database fallback is forbidden");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("获取数据源失败: " + ex.getMessage(), ex);
        }
    }

    public DataSourceSummary createDataSource(CreateDataSourceRequest request) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("name", request.name());
        payload.put("type", request.type());
        payload.put("jdbcUrl", request.jdbcUrl());
        payload.put("host", request.host());
        payload.put("port", request.port());
        payload.put("database", request.database());
        payload.put("serviceName", request.serviceName());
        payload.put("sid", request.sid());
        payload.put("username", request.username());
        payload.put("password", request.password());
        payload.put("description", request.description());
        Map<String, Object> created = copilotAiClient.createDataSource(payload);
        if (created.isEmpty()) {
            throw new IllegalStateException("创建数据源失败");
        }
        return toSummary(created);
    }

    public DataSourceSummary updateDataSource(Long id, CreateDataSourceRequest request) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("name", request.name());
        payload.put("type", request.type());
        payload.put("jdbcUrl", request.jdbcUrl());
        payload.put("host", request.host());
        payload.put("port", request.port());
        payload.put("database", request.database());
        payload.put("serviceName", request.serviceName());
        payload.put("sid", request.sid());
        payload.put("username", request.username());
        payload.put("password", request.password());
        payload.put("description", request.description());
        Map<String, Object> updated = copilotAiClient.updateDataSource(id, payload);
        if (updated.isEmpty()) {
            throw new IllegalStateException("更新数据源失败");
        }
        return toSummary(updated);
    }

    private List<DataSourceSummary> toSummaryList(List<Map<String, Object>> rawList) {
        List<DataSourceSummary> result = new ArrayList<>();
        for (Map<String, Object> map : rawList) {
            result.add(toSummary(map));
        }
        return result;
    }

    private DataSourceSummary toSummary(Map<String, Object> map) {
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

    @SuppressWarnings("unchecked")
    private DataSourceDetail toDetail(Map<String, Object> map) {
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

    @SuppressWarnings("unchecked")
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

    public record CreateDataSourceRequest(
        String name,
        String type,
        String jdbcUrl,
        String host,
        Integer port,
        String database,
        String serviceName,
        String sid,
        String username,
        String password,
        String description
    ) {}
}
