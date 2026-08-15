package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.StringUtils;

final class IndicatorValidationSignature {

    private static final int MAX_DEPENDENCIES = 32;
    private static final int MAX_DEPENDENCY_JSON_LENGTH = 16_384;
    private static final int MAX_CANONICAL_JSON_LENGTH = 65_536;
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private IndicatorValidationSignature() {}

    static String compute(
        GovIndicatorDefinition entity,
        ObjectMapper objectMapper,
        GovIndicatorDefinitionRepository repository
    ) {
        if (entity == null) {
            return null;
        }
        DependencyInput dependencies = parseDependencies(entity.getDependencyIndicators(), objectMapper);
        StringBuilder input = new StringBuilder();
        append(input, "derived", String.valueOf(IndicatorDefinitionSemantics.isDerivedLike(entity)));
        append(input, "datasetId", normalize(entity.getDatasetId()));
        append(input, "name", normalize(entity.getName()));
        append(input, "code", normalize(entity.getCode()));
        append(input, "domain", normalizeLower(entity.getDomain()));
        append(input, "businessCategoryId", uuid(entity.getBusinessCategoryId()));
        append(input, "dataDomainId", uuid(entity.getDataDomainId()));
        append(input, "businessProcessId", uuid(entity.getBusinessProcessId()));
        append(input, "metricType", normalizeUpper(entity.getMetricType()));
        append(input, "metricGroupCode", normalize(entity.getMetricGroupCode()));
        append(input, "sourceRefs", canonicalJson(entity.getSourceRefs(), objectMapper, "sourceRefs"));
        append(input, "aggregation", normalizeUpper(entity.getAggregationType()));
        append(input, "measure", normalize(entity.getMeasureField()));
        append(input, "date", normalize(entity.getDateColumn()));
        append(input, "sourceTable", normalize(entity.getSourceTable()));
        append(input, "sourceLayer", normalizeUpper(entity.getSourceLayer()));
        append(input, "targetLayer", normalizeUpper(entity.getTargetLayer()));
        append(input, "numerator", normalize(entity.getNumeratorExpression()));
        append(input, "denominator", normalize(entity.getDenominatorExpression()));
        append(input, "precision", normalizeNumber(entity.getPrecisionScale()));
        append(input, "staticFilter", normalize(entity.getStaticFilter()));
        append(input, "joinConfig", canonicalJson(entity.getJoinConfig(), objectMapper, "joinConfig"));
        append(input, "expressionSql", normalize(entity.getExpressionSql()));
        append(
            input,
            "dimensionFields",
            canonicalJson(entity.getDimensionFields(), objectMapper, "dimensionFields")
        );
        append(input, "timeGrain", normalizeUpper(entity.getTimeGrain()));
        append(input, "windowFunction", normalizeUpper(entity.getWindowFunction()));
        append(input, "unit", normalize(entity.getUnit()));
        append(input, "thresholdMin", normalizeNumber(entity.getThresholdMin()));
        append(input, "thresholdMax", normalizeNumber(entity.getThresholdMax()));
        append(input, "dependencies", dependencies.canonicalJson());
        append(
            input,
            "dependencyState",
            dependencyState(dependencies.codes(), objectMapper, repository)
        );
        return DigestUtils.sha256Hex(input.toString());
    }

    private static String dependencyState(
        List<String> dependencyCodes,
        ObjectMapper objectMapper,
        GovIndicatorDefinitionRepository repository
    ) {
        if (dependencyCodes.isEmpty()) {
            return "";
        }
        StringBuilder state = new StringBuilder();
        for (String code : dependencyCodes) {
            Optional<GovIndicatorDefinition> resolved;
            try {
                resolved = repository.findFirstByCodeIgnoreCase(code);
            } catch (RuntimeException repositoryFailure) {
                throw new IllegalStateException("读取依赖指标状态失败: " + code, repositoryFailure);
            }
            GovIndicatorDefinition dependency = resolved != null ? resolved.orElse(null) : null;
            append(state, "code", code);
            append(state, "exists", String.valueOf(dependency != null));
            append(state, "id", dependency != null && dependency.getId() != null
                ? dependency.getId().toString()
                : "");
            append(state, "status", dependency != null ? normalizeUpper(dependency.getStatus()) : "MISSING");
            append(state, "version", dependency != null ? normalize(dependency.getVersion()) : "");
            append(state, "dataLevel", dependency != null ? normalizeUpper(dependency.getDataLevel()) : "");
            append(state, "ownerDept", dependency != null ? normalizeUpper(dependency.getOwnerDept()) : "");
            append(
                state,
                "derived",
                dependency != null ? String.valueOf(IndicatorDefinitionSemantics.isDerivedLike(dependency)) : ""
            );
            append(state, "name", dependency != null ? normalize(dependency.getName()) : "");
            append(state, "domain", dependency != null ? normalizeLower(dependency.getDomain()) : "");
            append(state, "businessCategoryId", dependency != null ? uuid(dependency.getBusinessCategoryId()) : "");
            append(state, "dataDomainId", dependency != null ? uuid(dependency.getDataDomainId()) : "");
            append(state, "businessProcessId", dependency != null ? uuid(dependency.getBusinessProcessId()) : "");
            append(state, "metricType", dependency != null ? normalizeUpper(dependency.getMetricType()) : "");
            append(state, "sourceRefs", dependency != null
                ? canonicalJson(dependency.getSourceRefs(), objectMapper, "dependency.sourceRefs")
                : "");
            append(
                state,
                "aggregation",
                dependency != null ? normalizeUpper(dependency.getAggregationType()) : ""
            );
            append(state, "measure", dependency != null ? normalize(dependency.getMeasureField()) : "");
            append(state, "date", dependency != null ? normalize(dependency.getDateColumn()) : "");
            append(state, "sourceTable", dependency != null ? normalize(dependency.getSourceTable()) : "");
            append(
                state,
                "sourceLayer",
                dependency != null ? normalizeUpper(dependency.getSourceLayer()) : ""
            );
            append(
                state,
                "targetLayer",
                dependency != null ? normalizeUpper(dependency.getTargetLayer()) : ""
            );
            append(
                state,
                "numerator",
                dependency != null ? normalize(dependency.getNumeratorExpression()) : ""
            );
            append(
                state,
                "denominator",
                dependency != null ? normalize(dependency.getDenominatorExpression()) : ""
            );
            append(
                state,
                "precision",
                dependency != null ? normalizeNumber(dependency.getPrecisionScale()) : ""
            );
            append(
                state,
                "staticFilter",
                dependency != null ? normalize(dependency.getStaticFilter()) : ""
            );
            append(
                state,
                "joinConfig",
                dependency != null
                    ? canonicalJson(dependency.getJoinConfig(), objectMapper, "依赖指标 joinConfig")
                    : ""
            );
            append(
                state,
                "expressionSql",
                dependency != null ? normalize(dependency.getExpressionSql()) : ""
            );
            append(
                state,
                "dimensionFields",
                dependency != null
                    ? canonicalJson(dependency.getDimensionFields(), objectMapper, "依赖指标 dimensionFields")
                    : ""
            );
            append(
                state,
                "timeGrain",
                dependency != null ? normalizeUpper(dependency.getTimeGrain()) : ""
            );
            append(
                state,
                "windowFunction",
                dependency != null ? normalizeUpper(dependency.getWindowFunction()) : ""
            );
            append(state, "unit", dependency != null ? normalize(dependency.getUnit()) : "");
            append(
                state,
                "thresholdMin",
                dependency != null ? normalizeNumber(dependency.getThresholdMin()) : ""
            );
            append(
                state,
                "thresholdMax",
                dependency != null ? normalizeNumber(dependency.getThresholdMax()) : ""
            );
            append(
                state,
                "dependencies",
                dependency != null
                    ? canonicalJson(
                        dependency.getDependencyIndicators(),
                        objectMapper,
                        "依赖指标 dependencyIndicators"
                    )
                    : ""
            );
        }
        return state.toString();
    }

    private static DependencyInput parseDependencies(String value, ObjectMapper objectMapper) {
        if (value != null && value.length() > MAX_DEPENDENCY_JSON_LENGTH) {
            throw new IllegalArgumentException(
                "dependencyIndicators 长度不能超过 " + MAX_DEPENDENCY_JSON_LENGTH
            );
        }
        String raw = normalize(value);
        if (!StringUtils.hasText(raw)) {
            return new DependencyInput("", List.of());
        }
        List<String> codes = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        try (JsonParser parser = objectMapper.getFactory().createParser(raw)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException("dependencyIndicators 必须是指标编码数组");
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.VALUE_STRING) {
                    throw new IllegalArgumentException("dependencyIndicators 必须是非空指标编码数组");
                }
                if (codes.size() >= MAX_DEPENDENCIES) {
                    throw new IllegalArgumentException(
                        "dependencyIndicators 最多允许 " + MAX_DEPENDENCIES + " 个依赖指标"
                    );
                }
                String code = normalize(parser.getValueAsString());
                if (!SQL_IDENTIFIER.matcher(code).matches()) {
                    throw new IllegalArgumentException("dependencyIndicators 包含非法指标编码: " + code);
                }
                String normalized = code.toUpperCase(Locale.ROOT);
                if (!unique.add(normalized)) {
                    throw new IllegalArgumentException("dependencyIndicators 包含重复指标: " + code);
                }
                codes.add(normalized);
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("dependencyIndicators 包含尾随 JSON 内容");
            }
            return new DependencyInput(objectMapper.writeValueAsString(codes), List.copyOf(codes));
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception ex) {
            throw new IllegalArgumentException("dependencyIndicators 不是合法的指标编码数组", ex);
        }
    }

    private static String canonicalJson(String value, ObjectMapper objectMapper, String label) {
        String raw = normalize(value);
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (raw.length() > MAX_CANONICAL_JSON_LENGTH) {
            throw new IllegalArgumentException(label + "长度超过限制");
        }
        try {
            JsonNode parsed = objectMapper
                .reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(raw);
            return objectMapper.writeValueAsString(canonicalNode(parsed, objectMapper));
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + "不是合法 JSON", ex);
        }
    }

    private static JsonNode canonicalNode(JsonNode value, ObjectMapper objectMapper) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                result.set(name, canonicalNode(value.get(name), objectMapper));
            }
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(canonicalNode(item, objectMapper)));
            return result;
        }
        return value.deepCopy();
    }

    private static void append(StringBuilder target, String name, String value) {
        String safe = value == null ? "" : value;
        target.append(name).append(':').append(safe.length()).append(':').append(safe).append('\n');
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String uuid(java.util.UUID value) {
        return value == null ? "" : value.toString();
    }

    private static String normalizeUpper(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizeLower(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeNumber(Number value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }

    private record DependencyInput(String canonicalJson, List<String> codes) {}
}
