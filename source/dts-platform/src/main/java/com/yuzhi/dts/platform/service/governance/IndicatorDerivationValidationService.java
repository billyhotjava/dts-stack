package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.governance.IndicatorDerivationValidationResult.Issue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class IndicatorDerivationValidationService {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final GovIndicatorDefinitionRepository repository;
    private final ControlledIndicatorDerivationCompiler compiler;
    private final ObjectMapper objectMapper;

    public IndicatorDerivationValidationService(
        GovIndicatorDefinitionRepository repository,
        ControlledIndicatorDerivationCompiler compiler,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.compiler = compiler;
        this.objectMapper = objectMapper;
    }

    public IndicatorDerivationValidationResult validate(UUID indicatorId) {
        GovIndicatorDefinition target = repository
            .findById(indicatorId)
            .orElseThrow(() -> new IllegalArgumentException("指标不存在: " + indicatorId));
        List<Issue> issues = new ArrayList<>();
        List<String> dependencyCodes = parseDependencies(target.getDependencyIndicators(), issues);

        if (!Boolean.TRUE.equals(target.getIsDerived())) {
            addIssue(issues, "DERIVATION_NOT_DERIVED", "当前指标不是派生指标");
        }
        if (dependencyCodes.isEmpty()) {
            addIssue(issues, "DERIVATION_DEPENDENCIES_MISSING", "派生指标至少需要一个依赖指标");
        }
        if (!StringUtils.hasText(target.getExpressionSql())) {
            addIssue(issues, "DERIVATION_EXPRESSION_MISSING", "派生指标表达式不能为空");
        }

        String targetCode = normalizeCode(target.getCode());
        if (!SQL_IDENTIFIER.matcher(target.getCode() == null ? "" : target.getCode().trim()).matches()) {
            addIssue(issues, "DERIVATION_CODE_INVALID", "目标指标编码不能安全映射为 dbt 字段");
        }
        Set<String> targetDimensions = dimensionFields(target.getDimensionFields(), issues, "目标指标");
        String targetTimeGrain = normalizeTimeGrain(target.getTimeGrain());

        for (String dependencyCode : dependencyCodes) {
            String normalizedDependency = normalizeCode(dependencyCode);
            if (normalizedDependency.equals(targetCode)) {
                addIssue(issues, "DERIVATION_SELF_REFERENCE", "派生指标不能依赖自身: " + dependencyCode);
                continue;
            }
            GovIndicatorDefinition dependency = repository.findFirstByCodeIgnoreCase(dependencyCode).orElse(null);
            if (dependency == null) {
                addIssue(issues, "DERIVATION_DEPENDENCY_MISSING", "依赖指标不存在: " + dependencyCode);
                continue;
            }
            if (target.getId() != null && target.getId().equals(dependency.getId())) {
                addIssue(issues, "DERIVATION_SELF_REFERENCE", "派生指标不能依赖自身: " + dependencyCode);
                continue;
            }
            if (!"PUBLISHED".equalsIgnoreCase(dependency.getStatus())) {
                addIssue(issues, "DERIVATION_DEPENDENCY_NOT_PUBLISHED", "依赖指标尚未发布: " + dependencyCode);
            }
            if (dataLevel(target.getDataLevel()).rank() < dataLevel(dependency.getDataLevel()).rank()) {
                addIssue(
                    issues,
                    "DERIVATION_CLASSIFICATION_DOWNGRADE",
                    "目标指标密级不能低于依赖指标: " + dependencyCode
                );
            }
            if (
                hasPathToTarget(
                    dependency,
                    targetCode,
                    new LinkedHashSet<>(),
                    new HashSet<>()
                )
            ) {
                addIssue(issues, "DERIVATION_CYCLE", "依赖链形成循环: " + dependencyCode + " -> " + target.getCode());
            }
            Set<String> dependencyDimensions = dimensionFields(
                dependency.getDimensionFields(),
                issues,
                "依赖指标 " + dependencyCode
            );
            if (
                !targetDimensions.equals(dependencyDimensions) ||
                !targetTimeGrain.equals(normalizeTimeGrain(dependency.getTimeGrain()))
            ) {
                addIssue(
                    issues,
                    "DERIVATION_GRAIN_INCOMPATIBLE",
                    "依赖指标 " + dependencyCode + " 与目标指标的时间或维度粒度不一致"
                );
            }
        }

        String compiledExpression = null;
        if (StringUtils.hasText(target.getExpressionSql())) {
            List<String> referencedCodes = compiler.referencedMetricCodes(target.getExpressionSql());
            if (!sameCodes(dependencyCodes, referencedCodes)) {
                addIssue(
                    issues,
                    "DERIVATION_EXPRESSION_DEPENDENCY_MISMATCH",
                    "表达式引用的指标必须与 dependencyIndicators 完全一致"
                );
            }
            try {
                compiledExpression = compiler.compile(target.getExpressionSql(), dependencyCodes);
            } catch (IllegalArgumentException ex) {
                addIssue(issues, "DERIVATION_COMPILE_FAILED", ex.getMessage());
            }
        }

        return new IndicatorDerivationValidationResult(
            issues.isEmpty(),
            issues.isEmpty() ? compiledExpression : null,
            issues,
            dependencyCodes
        );
    }

    private boolean hasPathToTarget(
        GovIndicatorDefinition current,
        String targetCode,
        Set<String> visiting,
        Set<String> visited
    ) {
        String currentCode = normalizeCode(current.getCode());
        if (currentCode.equals(targetCode)) {
            return true;
        }
        if (visited.contains(currentCode)) {
            return false;
        }
        if (!visiting.add(currentCode)) {
            return true;
        }
        for (String dependencyCode : parseDependenciesSilently(current.getDependencyIndicators())) {
            String normalizedDependency = normalizeCode(dependencyCode);
            if (normalizedDependency.equals(targetCode)) {
                return true;
            }
            GovIndicatorDefinition dependency = repository.findFirstByCodeIgnoreCase(dependencyCode).orElse(null);
            if (dependency != null && hasPathToTarget(dependency, targetCode, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(currentCode);
        visited.add(currentCode);
        return false;
    }

    private List<String> parseDependencies(String json, List<Issue> issues) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            LinkedHashMap<String, String> unique = new LinkedHashMap<>();
            for (Object item : raw) {
                if (!(item instanceof String text) || !StringUtils.hasText(text)) {
                    throw new IllegalArgumentException("依赖指标编码必须是非空字符串");
                }
                String value = text.trim();
                String normalized = normalizeCode(value);
                if (unique.putIfAbsent(normalized, value) != null) {
                    addIssue(issues, "DERIVATION_DEPENDENCY_DUPLICATE", "依赖指标重复: " + value);
                }
            }
            return List.copyOf(unique.values());
        } catch (Exception ex) {
            addIssue(issues, "DERIVATION_DEPENDENCY_JSON_INVALID", "dependencyIndicators 不是合法的指标编码数组");
            return List.of();
        }
    }

    private List<String> parseDependenciesSilently(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof String text && StringUtils.hasText(text)) {
                    result.add(text.trim());
                }
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Set<String> dimensionFields(String json, List<Issue> issues, String owner) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Object item : raw) {
                String field = null;
                if (item instanceof String text) {
                    field = text;
                } else if (item instanceof Map<?, ?> map && map.get("field") != null) {
                    field = String.valueOf(map.get("field"));
                }
                if (!StringUtils.hasText(field)) {
                    throw new IllegalArgumentException("维度字段格式错误");
                }
                String normalized = field.trim();
                if (!SQL_IDENTIFIER.matcher(normalized).matches()) {
                    throw new IllegalArgumentException("维度字段不能安全映射为 dbt 字段");
                }
                result.add(normalized.toLowerCase(Locale.ROOT));
            }
            return Set.copyOf(result);
        } catch (Exception ex) {
            addIssue(issues, "DERIVATION_DIMENSIONS_INVALID", owner + "的 dimensionFields 格式错误");
            return Set.of();
        }
    }

    private boolean sameCodes(List<String> left, List<String> right) {
        Set<String> normalizedLeft = new LinkedHashSet<>();
        Set<String> normalizedRight = new LinkedHashSet<>();
        left.forEach(value -> normalizedLeft.add(normalizeCode(value)));
        right.forEach(value -> normalizedRight.add(normalizeCode(value)));
        return normalizedLeft.equals(normalizedRight);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTimeGrain(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MONTH";
    }

    private DataLevel dataLevel(String value) {
        DataLevel normalized = DataLevel.normalize(value);
        return normalized != null ? normalized : DataLevel.DATA_INTERNAL;
    }

    private void addIssue(List<Issue> issues, String code, String message) {
        boolean duplicate = issues.stream().anyMatch(issue -> code.equals(issue.code()) && message.equals(issue.message()));
        if (!duplicate) {
            issues.add(new Issue(code, message));
        }
    }
}
