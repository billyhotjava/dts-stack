package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorTemplateRepository;
import com.yuzhi.dts.platform.service.governance.request.TemplateApplyRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class IndicatorTemplateService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorTemplateService.class);

    private final GovIndicatorTemplateRepository templateRepo;
    private final GovIndicatorDefinitionRepository indicatorRepo;
    private final ObjectMapper objectMapper;

    public IndicatorTemplateService(
        GovIndicatorTemplateRepository templateRepo,
        GovIndicatorDefinitionRepository indicatorRepo,
        ObjectMapper objectMapper
    ) {
        this.templateRepo = templateRepo;
        this.indicatorRepo = indicatorRepo;
        this.objectMapper = objectMapper;
    }

    // ---- CRUD ----

    @Transactional(readOnly = true)
    public List<GovIndicatorTemplate> list(String domain) {
        if (domain != null && !domain.isBlank()) {
            return templateRepo.findByDomainAndEnabledTrue(domain);
        }
        return templateRepo.findByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public GovIndicatorTemplate getById(UUID id) {
        return templateRepo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
    }

    public GovIndicatorTemplate create(GovIndicatorTemplate template) {
        return templateRepo.save(template);
    }

    public GovIndicatorTemplate update(UUID id, GovIndicatorTemplate update) {
        GovIndicatorTemplate existing = getById(id);
        existing.setName(update.getName());
        existing.setDescription(update.getDescription());
        existing.setDomain(update.getDomain());
        existing.setIndicatorBlueprints(update.getIndicatorBlueprints());
        existing.setRequiredSourceFields(update.getRequiredSourceFields());
        existing.setSeedTables(update.getSeedTables());
        existing.setRecommendedSnapshot(update.getRecommendedSnapshot());
        existing.setDisplayOrder(update.getDisplayOrder());
        existing.setEnabled(update.getEnabled());
        return templateRepo.save(existing);
    }

    public void delete(UUID id) {
        GovIndicatorTemplate tpl = getById(id);
        if (Boolean.TRUE.equals(tpl.getBuiltin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "内置模板不可删除");
        }
        templateRepo.deleteById(id);
    }

    // ---- T04: 模板展开 ----

    public List<GovIndicatorDefinition> applyTemplate(UUID templateId, TemplateApplyRequest request) {
        GovIndicatorTemplate template = getById(templateId);

        List<Map<String, Object>> blueprints;
        try {
            blueprints = objectMapper.readValue(
                template.getIndicatorBlueprints(),
                new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "模板蓝图 JSON 解析失败: " + e.getMessage());
        }

        // Filter by selectedBlueprints if specified
        List<String> selected = request.selectedBlueprints();
        if (selected != null && !selected.isEmpty()) {
            blueprints = blueprints.stream()
                .filter(bp -> selected.contains(String.valueOf(bp.get("code"))))
                .toList();
        }

        if (blueprints.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无匹配的蓝图指标");
        }

        Map<String, String> fieldMapping = request.fieldMapping() != null ? request.fieldMapping() : Collections.emptyMap();
        Map<String, Map<String, Object>> overrides = request.overrides() != null ? request.overrides() : Collections.emptyMap();

        List<GovIndicatorDefinition> created = new ArrayList<>();

        for (Map<String, Object> bp : blueprints) {
            String code = getString(bp, "code");
            GovIndicatorDefinition def = new GovIndicatorDefinition();

            def.setCode(code);
            def.setName(getString(bp, "name"));
            def.setTemplateId(templateId);
            def.setDomain(template.getDomain());
            def.setStatus("DRAFT");

            // Data binding
            def.setSourceTable(request.sourceTable());
            def.setSourceLayer(request.sourceLayer());
            def.setTargetLayer("ADS");
            def.setTargetModelName("ind_" + code);
            if (request.datasetId() != null && !request.datasetId().isBlank()) {
                def.setDatasetId(request.datasetId());
            }

            // Aggregation type
            def.setAggregationType(getString(bp, "aggregationType"));

            // Map placeholder fields using fieldMapping
            String numeratorPlaceholder = getString(bp, "numeratorPlaceholder");
            if (numeratorPlaceholder != null) {
                def.setNumeratorExpression(mapField(numeratorPlaceholder, fieldMapping));
            }
            String denominatorPlaceholder = getString(bp, "denominatorPlaceholder");
            if (denominatorPlaceholder != null) {
                def.setDenominatorExpression(mapField(denominatorPlaceholder, fieldMapping));
            }
            String measurePlaceholder = getString(bp, "measurePlaceholder");
            if (measurePlaceholder != null) {
                def.setMeasureField(mapField(measurePlaceholder, fieldMapping));
            }

            // Expression skeleton: replace {{placeholder}} with mapped field names
            String expressionSkeleton = getString(bp, "expressionSkeleton");
            if (expressionSkeleton != null) {
                String resolved = resolveExpression(expressionSkeleton, fieldMapping);
                def.setExpressionSql(resolved);
            }

            // Static filter skeleton
            String staticFilterSkeleton = getString(bp, "staticFilterSkeleton");
            if (staticFilterSkeleton != null) {
                def.setStaticFilter(staticFilterSkeleton);
            }

            // Dimensions
            Object dimPlaceholders = bp.get("dimensionPlaceholders");
            if (dimPlaceholders instanceof List<?> dimList) {
                List<String> mappedDims = new ArrayList<>();
                for (Object d : dimList) {
                    String placeholder = String.valueOf(d);
                    mappedDims.add(mapField(placeholder, fieldMapping));
                }
                try {
                    def.setDimensionFields(objectMapper.writeValueAsString(mappedDims));
                } catch (Exception e) {
                    log.warn("Failed to serialize dimension fields", e);
                }
            }

            // Date column
            String datePlaceholder = getString(bp, "datePlaceholder");
            if (datePlaceholder != null) {
                def.setDateColumn(mapField(datePlaceholder, fieldMapping));
            }

            // Window function
            def.setWindowFunction(getString(bp, "windowFunction"));

            // Business attributes
            def.setUnit(getString(bp, "unit"));
            def.setPrecisionScale(getInteger(bp, "precisionScale"));
            def.setDirection(getString(bp, "direction"));
            def.setThresholdMin(getBigDecimal(bp, "thresholdMin"));
            def.setThresholdMax(getBigDecimal(bp, "thresholdMax"));

            // Derived indicator
            Object isDerived = bp.get("isDerived");
            def.setIsDerived(isDerived instanceof Boolean b ? b : false);

            // Dependency codes
            Object depCodes = bp.get("dependencyCodes");
            if (depCodes instanceof List<?> depList && !depList.isEmpty()) {
                try {
                    def.setDependencyIndicators(objectMapper.writeValueAsString(depList));
                } catch (Exception e) {
                    log.warn("Failed to serialize dependency codes", e);
                }
            }

            // Apply overrides
            Map<String, Object> overrideMap = overrides.get(code);
            if (overrideMap != null) {
                if (overrideMap.containsKey("thresholdMin")) {
                    def.setThresholdMin(toBigDecimal(overrideMap.get("thresholdMin")));
                }
                if (overrideMap.containsKey("thresholdMax")) {
                    def.setThresholdMax(toBigDecimal(overrideMap.get("thresholdMax")));
                }
                if (overrideMap.containsKey("staticFilter")) {
                    def.setStaticFilter(String.valueOf(overrideMap.get("staticFilter")));
                }
                if (overrideMap.containsKey("unit")) {
                    def.setUnit(String.valueOf(overrideMap.get("unit")));
                }
                if (overrideMap.containsKey("direction")) {
                    def.setDirection(String.valueOf(overrideMap.get("direction")));
                }
            }

            created.add(def);
        }

        return indicatorRepo.saveAll(created);
    }

    // ---- helpers ----

    private String mapField(String placeholder, Map<String, String> fieldMapping) {
        return fieldMapping.getOrDefault(placeholder, placeholder);
    }

    private String resolveExpression(String skeleton, Map<String, String> fieldMapping) {
        String result = skeleton;
        for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private static Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return toBigDecimal(val);
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (val instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
