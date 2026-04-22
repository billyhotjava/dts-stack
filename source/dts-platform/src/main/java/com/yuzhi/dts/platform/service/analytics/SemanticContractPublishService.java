package com.yuzhi.dts.platform.service.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelDto;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SemanticContractPublishService {

    private final AnalyticsServiceClient analyticsServiceClient;
    private final ObjectMapper objectMapper;

    public SemanticContractPublishService(AnalyticsServiceClient analyticsServiceClient, ObjectMapper objectMapper) {
        this.analyticsServiceClient = analyticsServiceClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> publish(SqlModelDto model) {
        if (model == null || model.id() == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        String contractText = trimToNull(model.semanticContract());
        if (contractText == null) {
            throw new IllegalArgumentException("语义契约为空，无法发布");
        }
        if (!analyticsServiceClient.isEnabled()) {
            throw new IllegalStateException("analytics service 未启用，无法发布语义契约");
        }

        Map<String, Object> contract = readContract(contractText);
        Map<String, Object> payload = new LinkedHashMap<>(contract);
        payload.put("modelName", model.name());
        payload.putIfAbsent("label", firstNonBlank(model.alias(), model.name()));
        payload.putIfAbsent("displayName", firstNonBlank(model.alias(), model.name()));
        payload.put("tableName", firstNonBlank(model.alias(), model.name()));
        payload.put("schemaName", firstNonBlank(model.schemaName(), "public"));
        payload.put("dataSourceName", trimToNull(model.sourceDataSourceName()));
        payload.put("description", trimToNull(model.description()));
        payload.put("contractVersion", trimToNull(model.contractVersion()));
        payload.put("status", trimToNull(model.status()));
        payload.put("ownerDept", trimToNull(model.ownerDept()));

        Map<String, Object> analyticsResult = analyticsServiceClient.publishSemantic(payload);
        if (analyticsResult.containsKey("error")) {
            throw new IllegalStateException(String.valueOf(analyticsResult.get("error")));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("modelId", model.id());
        response.put("modelName", model.name());
        response.put("contractVersion", model.contractVersion());
        response.put("published", Boolean.TRUE);
        response.put("analytics", analyticsResult);
        return response;
    }

    private Map<String, Object> readContract(String contractText) {
        try {
            Object parsed = objectMapper.readValue(contractText, new TypeReference<Object>() {});
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> response = new LinkedHashMap<>();
                map.forEach((key, value) -> response.put(String.valueOf(key), value));
                return response;
            }
            throw new IllegalArgumentException("语义契约必须是 JSON 对象");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("语义契约不是合法 JSON: " + ex.getMessage(), ex);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
