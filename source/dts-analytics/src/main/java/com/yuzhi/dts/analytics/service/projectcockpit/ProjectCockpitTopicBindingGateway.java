package com.yuzhi.dts.analytics.service.projectcockpit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
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
public class ProjectCockpitTopicBindingGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectCockpitTopicBindingGateway.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String PROJECT_MANAGEMENT_SELECTOR = "tag:project-management";
    private static final String PROJECT_MANAGEMENT_ENTITY = "project-management.project_subject_domain";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties outboundProps;

    public ProjectCockpitTopicBindingGateway(
            RestTemplateBuilder builder,
            ObjectMapper objectMapper,
            com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties outboundProps) {
        long timeout = Math.max(2, outboundProps.getTimeoutSeconds());
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(timeout))
                .setReadTimeout(Duration.ofSeconds(timeout))
                .build();
        this.objectMapper = objectMapper;
        this.outboundProps = outboundProps;
    }

    public TopicBindingState currentProjectManagementState() {
        try {
            String baseUrl = StringUtils.hasText(outboundProps.getBaseUrl()) ? outboundProps.getBaseUrl().trim() : "http://dts-platform:8081";
            String apiPath = StringUtils.hasText(outboundProps.getApiPath()) ? outboundProps.getApiPath().trim() : "/api";
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path(apiPath)
                    .path("/topic-bindings/status")
                    .queryParam("selector", PROJECT_MANAGEMENT_SELECTOR)
                    .build(true)
                    .toUri();
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(buildHeaders()), Map.class);
            Map<String, Object> body = response.getBody() == null ? Map.of() : new LinkedHashMap<>(response.getBody());
            Map<String, Object> data = castMap(body.get("data"));
            List<String> missingRequired = objectMapper.convertValue(data.get("missingRequired"), new TypeReference<List<String>>() {});
            List<Map<String, Object>> rows = objectMapper.convertValue(data.get("rows"), new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Object> row = rows.stream()
                    .filter(item -> PROJECT_MANAGEMENT_ENTITY.equals(text(item.get("templateCode")) + "." + text(item.get("entityCode"))))
                    .findFirst()
                    .orElse(Map.of());
            boolean bound = Boolean.TRUE.equals(row.get("bound"));
            String schemaName = text(row.get("boundSchemaName"));
            String tableName = text(row.get("boundTableName"));
            String sourceName = text(row.get("sourceName"));
            String logicalTableName = text(row.get("logicalTableName"));
            if (bound) {
                return new TopicBindingState(true, "已绑定", sourceName, logicalTableName, schemaName, tableName,
                        "项目管理专题当前绑定到 %s.%s。".formatted(blankToDash(schemaName), blankToDash(tableName)));
            }
            if (missingRequired.contains(PROJECT_MANAGEMENT_ENTITY)) {
                return new TopicBindingState(false, "待绑定", sourceName, logicalTableName, schemaName, tableName,
                        "项目管理专题的必填逻辑实体尚未绑定到现场 ODS 表。");
            }
            return new TopicBindingState(false, "未命中", sourceName, logicalTableName, schemaName, tableName,
                    "当前专题绑定中心未返回项目管理逻辑实体状态。");
        } catch (Exception ex) {
            LOG.warn("Project cockpit topic binding status unavailable: {}", ex.getMessage());
            return new TopicBindingState(false, "不可达", "pm_ods", "project_subject_domain", "", "",
                    "无法读取平台侧专题绑定状态，口径支撑仅展示数仓与主数据占位信息。");
        }
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

    private Map<String, Object> castMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
        }
        return Map.of();
    }

    private String text(Object raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.toString().trim();
        return text.isEmpty() ? "" : text;
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    public record TopicBindingState(
            boolean bound,
            String status,
            String sourceName,
            String logicalTableName,
            String schemaName,
            String tableName,
            String message) {}
}
