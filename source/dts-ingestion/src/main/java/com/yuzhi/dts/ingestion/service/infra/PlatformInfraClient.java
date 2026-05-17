package com.yuzhi.dts.ingestion.service.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.config.IngestionOutboundPlatformProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final RestTemplate restTemplate;
    private final IngestionSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final IngestionOutboundPlatformProperties outboundProps;

    public PlatformInfraClient(
        RestTemplateBuilder builder,
        IngestionSettingsService settingsService,
        ObjectMapper objectMapper,
        IngestionOutboundPlatformProperties outboundProps
    ) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.outboundProps = outboundProps;
    }

    public DataSourceDetail fetchDataSourceDetail(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("dataSourceId不能为空");
        }
        URI uri = buildUri("/infra/data-sources/" + id + "/runtime-detail");
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyServiceHeaders(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("平台返回异常状态: " + response.getStatusCode().value());
            }
            Map<String, Object> body = response.getBody() == null ? Map.of() : new LinkedHashMap<>(response.getBody());
            Object data = body.get("data");
            if (data == null) {
                throw new IllegalStateException("平台未返回数据源详情");
            }
            return objectMapper.convertValue(data, DataSourceDetail.class);
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Platform data source fetch failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("获取平台数据源失败: " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("获取平台数据源失败: " + ex.getMessage(), ex);
        }
    }

    public Map<String, Object> triggerDbtRun(String models, String dagSelector) {
        if (!StringUtils.hasText(models)) {
            throw new IllegalArgumentException("models不能为空");
        }
        URI uri = buildUri("/etl/dbt/run");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("models", models.trim());
        if (StringUtils.hasText(dagSelector)) {
            payload.put("dagSelector", dagSelector.trim());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyServiceHeaders(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("平台返回异常状态: " + response.getStatusCode().value());
            }
            Map<String, Object> body = response.getBody() == null ? Map.of() : new LinkedHashMap<>(response.getBody());
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map) {
                return castMap(map);
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Platform dbt trigger failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("触发 dbt 失败: " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("触发 dbt 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * Call dts-platform governance pre-check endpoint to run quality rules
     * against a staging table.
     *
     * @param stagingTableName the staging table name in PostgreSQL
     * @param datasetId        the dataset UUID whose rule bindings to apply
     * @param totalRows        total number of rows in the staging table
     * @return pre-check result map with totalRows, passedRows, failedRows, errorsByRule
     */
    public Map<String, Object> preCheckStagingData(String stagingTableName, UUID datasetId, int totalRows) {
        URI uri = buildUri("/governance/quality/pre-check");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stagingTableName", stagingTableName);
        payload.put("datasetId", datasetId);
        payload.put("totalRows", totalRows);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyServiceHeaders(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                uri, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("平台预检返回异常状态: " + response.getStatusCode().value());
            }
            Map<String, Object> body = response.getBody() == null ? Map.of() : new LinkedHashMap<>(response.getBody());
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map) {
                return castMap(map);
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Platform pre-check failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("质量预检调用失败: " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("质量预检调用失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * Trigger a quality run on the platform for the given dataset.
     * Used as a post-ingestion step to automatically run cleansing + quality checks
     * after data has been successfully loaded.
     *
     * @param datasetId   the dataset UUID to run quality checks against
     * @param triggerType the trigger type (e.g. "INGESTION")
     */
    public void triggerQualityRun(UUID datasetId, String triggerType) {
        if (datasetId == null) {
            LOG.debug("No datasetId provided — skipping quality run trigger");
            return;
        }
        URI uri = buildUri("/governance/quality/runs");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId);
        payload.put("triggerType", StringUtils.hasText(triggerType) ? triggerType.trim() : "INGESTION");

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyServiceHeaders(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                uri, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("平台质量检测触发返回异常状态: " + response.getStatusCode().value());
            }
            LOG.info("Quality run triggered for datasetId={} triggerType={}", datasetId, triggerType);
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Platform quality run trigger failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("质量检测触发失败: " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("质量检测触发失败: " + ex.getMessage(), ex);
        }
    }

    public boolean syncIngestionExecutionLineage(IngestionTask task, IngestionExecution execution) {
        if (task == null || execution == null) {
            return false;
        }
        URI uri = buildUri("/catalog/lineage/ingestion-executions");
        Map<String, Object> taskPayload = new LinkedHashMap<>();
        taskPayload.put("id", task.getId());
        taskPayload.put("name", task.getName());
        taskPayload.put("sourceType", task.getSourceType());
        taskPayload.put("sourceDataSourceId", task.getSourceDataSourceId() == null ? null : task.getSourceDataSourceId().toString());
        taskPayload.put("destinationType", task.getDestinationType());
        taskPayload.put("destinationConfig", task.getDestinationConfig());
        taskPayload.put("tableMapping", task.getTableMapping());

        Map<String, Object> executionPayload = new LinkedHashMap<>();
        executionPayload.put("id", execution.getId());
        executionPayload.put("executionId", execution.getExecutionId());
        executionPayload.put("batchId", execution.getBatchId());
        executionPayload.put("status", execution.getStatus());
        executionPayload.put("startTime", execution.getStartTime() == null ? null : execution.getStartTime().toString());
        executionPayload.put("endTime", execution.getEndTime() == null ? null : execution.getEndTime().toString());
        executionPayload.put("sourceTables", execution.getSourceTables());
        executionPayload.put("targetTables", execution.getTargetTables());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", taskPayload);
        payload.put("execution", executionPayload);
        payload.values().removeIf(value -> value == null);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyServiceHeaders(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.warn("Platform ingestion lineage sync returned status={}", response.getStatusCode().value());
                return false;
            }
            return true;
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Platform ingestion lineage sync failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Platform ingestion lineage sync failed: {}", ex.getMessage());
        }
        return false;
    }

    private void applyServiceHeaders(HttpHeaders headers) {
        headers.set(SERVICE_HEADER, resolveServiceName());
        String token = resolveServiceToken();
        if (StringUtils.hasText(token)) {
            headers.set(SERVICE_TOKEN_HEADER, token);
        }
    }

    private String resolveServiceName() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM);
        String fallback = StringUtils.hasText(outboundProps.getServiceName()) ? outboundProps.getServiceName().trim() : "dts-ingestion";
        return settings.getString("serviceName", fallback);
    }

    private String resolveServiceToken() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM);
        String envFallback = StringUtils.hasText(outboundProps.getServiceToken()) ? outboundProps.getServiceToken().trim() : null;
        return settings.getString("serviceToken", envFallback);
    }

    private URI buildUri(String path) {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM);
        String baseUrl = settings.getString("baseUrl", outboundProps.getBaseUrl());
        String apiPath = settings.getString("apiPath", outboundProps.getApiPath());
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "http://dts-platform:8081";
        }
        if (!StringUtils.hasText(apiPath)) {
            apiPath = "/api";
        }
        return UriComponentsBuilder.fromHttpUrl(baseUrl.trim())
            .path(apiPath)
            .path(path)
            .build(true)
            .toUri();
    }

    private Map<String, Object> castMap(Map<?, ?> raw) {
        return objectMapper.convertValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    public record DataSourceDetail(
        UUID id,
        String name,
        String type,
        String jdbcUrl,
        String username,
        String description,
        String ownerDept,
        Map<String, Object> props,
        Map<String, Object> secrets,
        String status
    ) {}
}
