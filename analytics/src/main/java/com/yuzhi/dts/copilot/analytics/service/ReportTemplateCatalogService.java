package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsReportTemplateRepository;
import com.yuzhi.dts.copilot.analytics.service.report.FixedReportPageAnchorService;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportTemplateCatalogService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AnalyticsReportTemplateRepository templateRepository;
    private final FixedReportPageAnchorService pageAnchorService;

    public ReportTemplateCatalogService(
            AnalyticsReportTemplateRepository templateRepository,
            FixedReportPageAnchorService pageAnchorService) {
        this.templateRepository = templateRepository;
        this.pageAnchorService = pageAnchorService;
    }

    public List<Map<String, Object>> listTemplates(String domain, String category, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String normalizedDomain = normalize(domain);
        String normalizedCategory = normalize(category);

        return templateRepository.findCatalogTemplates(normalizedDomain, normalizedCategory, PageRequest.of(0, safeLimit)).stream()
                .map(this::toCatalogRow)
                .toList();
    }

    public Optional<Map<String, Object>> getTemplate(String templateCode) {
        String normalizedTemplateCode = normalize(templateCode);
        if (normalizedTemplateCode == null) {
            return Optional.empty();
        }
        return templateRepository.findLatestRunnableTemplateByTemplateCode(normalizedTemplateCode)
                .map(this::toCatalogRow);
    }

    private Map<String, Object> toCatalogRow(AnalyticsReportTemplate template) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", template.getId());
        row.put("name", template.getName());
        row.put("description", template.getDescription());
        row.put("templateCode", template.getTemplateCode());
        row.put("domain", template.getDomain());
        row.put("category", template.getCategory());
        row.put("dataSourceType", template.getDataSourceType());
        row.put("targetObject", template.getTargetObject());
        row.put("refreshPolicy", template.getRefreshPolicy());
        row.put("certificationStatus", template.getCertificationStatus());
        row.put("published", template.isPublished());
        row.put("parameterSchemaJson", template.getParameterSchemaJson());
        row.put("placeholderReviewRequired", isPlaceholderReviewRequired(template.getSpecJson()));
        appendAssetMetadata(row, template);
        row.put("updatedAt", template.getUpdatedAt());
        pageAnchorService.resolve(template.getTemplateCode()).ifPresent(anchor -> {
            row.put("legacyPageTitle", anchor.title());
            row.put("legacyPagePath", anchor.path());
        });
        return row;
    }

    private static void appendAssetMetadata(Map<String, Object> row, AnalyticsReportTemplate template) {
        JsonNode spec = parseJson(template.getSpecJson());
        JsonNode metricDefinition = parseJson(template.getMetricDefinitionJson());
        JsonNode queryContract = spec == null ? null : spec.path("queryContract");
        JsonNode assetGroup = spec == null ? null : spec.path("assetGroup");

        String primaryDbtModel = firstNonBlank(
                stringValue(queryContract, "primaryDbtModel"),
                firstText(queryContract == null ? null : queryContract.get("dbtModels")),
                stringValue(metricDefinition, "primaryDbtModel"));
        List<String> outputColumns = stringArray(queryContract == null ? null : queryContract.get("outputColumns"));
        List<String> sourceRefs = sourceRefs(queryContract, primaryDbtModel);

        row.put("assetKind", firstNonBlank(
                stringValue(spec, "assetKind"),
                stringValue(spec, "reportType"),
                template.getDataSourceType()));
        row.put("assetGroupCode", firstNonBlank(
                stringValue(assetGroup, "code"),
                stringValue(assetGroup, "parentTemplateCode"),
                template.getTemplateCode()));
        row.put("assetGroupName", firstNonBlank(
                stringValue(assetGroup, "name"),
                template.getName()));
        row.put("parentTemplateCode", stringValue(assetGroup, "parentTemplateCode"));
        row.put("primaryDbtModel", primaryDbtModel);
        row.put("outputColumnCount", outputColumns.isEmpty() ? null : outputColumns.size());
        row.put("sourceRefs", sourceRefs);
    }

    private static boolean isPlaceholderReviewRequired(String specJson) {
        JsonNode spec = parseJson(specJson);
        return spec != null && spec.path("placeholderReviewRequired").asBoolean(false);
    }

    private static List<String> sourceRefs(JsonNode queryContract, String primaryDbtModel) {
        Set<String> refs = new LinkedHashSet<>();
        if (primaryDbtModel != null) {
            refs.add("dbt-model:" + primaryDbtModel);
        }
        if (queryContract != null && queryContract.isObject()) {
            for (String dbtModel : stringArray(queryContract.get("dbtModels"))) {
                refs.add("dbt-model:" + dbtModel);
            }
            String targetObject = stringValue(queryContract, "targetObject");
            if (targetObject != null && targetObject.startsWith("screen.")) {
                refs.add(targetObject);
            }
        }
        return List.copyOf(refs);
    }

    private static List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isValueNode()) {
                String value = normalizeValue(item.asText(null));
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static String firstText(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        for (JsonNode item : node) {
            if (item != null && item.isValueNode()) {
                String value = normalizeValue(item.asText(null));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String stringValue(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isValueNode()) {
            return null;
        }
        return normalizeValue(value.asText(null));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeValue(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
