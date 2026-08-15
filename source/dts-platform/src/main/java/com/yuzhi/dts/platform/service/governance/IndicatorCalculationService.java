package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorRun;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorRunRepository;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Executes published indicators against their pinned model-field implementation or controlled formula. */
@Service
@Transactional
public class IndicatorCalculationService {

    private static final Pattern MODEL_FIELD_TARGET = Pattern.compile(
        "([0-9a-fA-F-]{36})@([1-9][0-9]*)#([A-Za-z_][A-Za-z0-9_]*)"
    );
    private static final Pattern RELATION = Pattern.compile(
        "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_DEPTH = 32;

    private final GovIndicatorDefinitionRepository indicators;
    private final GovIndicatorReferenceRepository references;
    private final GovIndicatorRunRepository runs;
    private final ModelSpecApplicationService models;
    private final QueryGateway queryGateway;
    private final ControlledIndicatorDerivationCompiler compiler;
    private final ObjectMapper objectMapper;
    private final String serverTenantId;

    public IndicatorCalculationService(
        GovIndicatorDefinitionRepository indicators,
        GovIndicatorReferenceRepository references,
        GovIndicatorRunRepository runs,
        ModelSpecApplicationService models,
        QueryGateway queryGateway,
        ControlledIndicatorDerivationCompiler compiler,
        ObjectMapper objectMapper,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.indicators = indicators;
        this.references = references;
        this.runs = runs;
        this.models = models;
        this.queryGateway = queryGateway;
        this.compiler = compiler;
        this.objectMapper = objectMapper;
        this.serverTenantId = serverTenantId;
    }

    public CalculationBatch calculate(List<UUID> indicatorIds) {
        if (indicatorIds == null || indicatorIds.isEmpty()) {
            throw new IndicatorRequestException("计算指标不能为空");
        }
        String requestId = "metric-calc-" + UUID.randomUUID();
        Instant submittedAt = Instant.now();
        Map<UUID, CalculationItem> calculated = new LinkedHashMap<>();
        List<CalculationItem> requested = new ArrayList<>();
        for (UUID indicatorId : indicatorIds) {
            try {
                requested.add(calculateNode(indicatorId, requestId, calculated, new LinkedHashSet<>(), 0));
            } catch (RuntimeException error) {
                CalculationItem failed = calculated.get(indicatorId);
                requested.add(failed != null ? failed : saveFailure(indicatorId, requestId, safeMessage(error)));
            }
        }
        long success = requested.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        return new CalculationBatch(requestId, submittedAt, requested, success, requested.size() - success);
    }

    private CalculationItem calculateNode(
        UUID indicatorId,
        String requestId,
        Map<UUID, CalculationItem> calculated,
        Set<UUID> visiting,
        int depth
    ) {
        CalculationItem existing = calculated.get(indicatorId);
        if (existing != null) return existing;
        if (depth > MAX_DEPTH || !visiting.add(indicatorId)) {
            throw new IndicatorConflictException("指标计算依赖存在循环或深度超过限制");
        }
        long startedAt = System.currentTimeMillis();
        GovIndicatorDefinition indicator = indicators
            .findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));
        try {
            if (!"PUBLISHED".equalsIgnoreCase(indicator.getStatus())) {
                throw new IndicatorConflictException("只有已发布指标可以提交计算: " + indicator.getCode());
            }
            GovIndicatorReference modelField = modelFieldReference(indicator);
            Computed computed = modelField != null
                ? computeFromModel(indicator, modelField)
                : computeFromFormula(indicator, requestId, calculated, visiting, depth);
            CalculationItem item = saveSuccess(indicator, requestId, computed, System.currentTimeMillis() - startedAt);
            calculated.put(indicatorId, item);
            return item;
        } catch (RuntimeException error) {
            CalculationItem failed = saveFailure(
                indicator,
                requestId,
                safeMessage(error),
                System.currentTimeMillis() - startedAt
            );
            calculated.put(indicatorId, failed);
            throw error;
        } finally {
            visiting.remove(indicatorId);
        }
    }

    private GovIndicatorReference modelFieldReference(GovIndicatorDefinition indicator) {
        return references
            .findByIndicatorOrderByCreatedDateAsc(indicator)
            .stream()
            .filter(reference -> "MODEL_SPEC_FIELD".equalsIgnoreCase(reference.getRefType()))
            .findFirst()
            .orElse(null);
    }

    private Computed computeFromModel(GovIndicatorDefinition indicator, GovIndicatorReference reference) {
        Matcher target = MODEL_FIELD_TARGET.matcher(String.valueOf(reference.getRefTarget()));
        if (!target.matches()) {
            throw new IndicatorConflictException("模型字段实现引用无效，请重新绑定指标实现");
        }
        UUID modelId = UUID.fromString(target.group(1));
        int revision = Integer.parseInt(target.group(2));
        String fieldName = target.group(3);
        ModelSpecView model = models.revision(serverTenantId, new ModelRevisionRef(modelId, revision));
        if (model.status() != ModelStatus.PUBLISHED || model.revision() != revision) {
            throw new IndicatorConflictException("指标固定的模型版本尚未发布");
        }
        ModelField field = model
            .fields()
            .stream()
            .filter(candidate -> candidate != null && fieldName.equals(candidate.name()))
            .findFirst()
            .orElseThrow(() -> new IndicatorConflictException("指标实现字段已不存在: " + fieldName));
        if (field.role() != FieldRole.MEASURE) {
            throw new IndicatorConflictException("指标实现字段不再是 MEASURE: " + fieldName);
        }
        String relation = model.implementationPolicy() != null && StringUtils.hasText(model.implementationPolicy().physicalName())
            ? model.implementationPolicy().physicalName()
            : model.name();
        requireRelation(relation);
        List<String> periodKeys = temporalKeys(model);
        String sql = modelCalculationSql(
            relation,
            fieldName,
            aggregation(indicator.getAggregationType(), IndicatorDefinitionSemantics.isDerivedLike(indicator)),
            periodKeys
        );
        Map<String, Object> payload = queryGateway.execute(sql);
        ResultRow row = resultRow(payload);
        return new Computed(row.value(), row.rowsProcessed(), "MODEL_FIELD", relation, fieldName);
    }

    private Computed computeFromFormula(
        GovIndicatorDefinition indicator,
        String requestId,
        Map<UUID, CalculationItem> calculated,
        Set<UUID> visiting,
        int depth
    ) {
        if (!IndicatorDefinitionSemantics.isDerivedLike(indicator) || !StringUtils.hasText(indicator.getExpressionSql())) {
            throw new IndicatorConflictException("指标缺少固定模型字段实现");
        }
        List<String> dependencyCodes = dependencyCodes(indicator.getDependencyIndicators());
        if (dependencyCodes.isEmpty()) {
            throw new IndicatorConflictException("派生指标缺少上游指标");
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        List<String> inputs = new ArrayList<>();
        int rowsProcessed = 0;
        for (String code : dependencyCodes) {
            GovIndicatorDefinition dependency = indicators
                .findFirstByCodeIgnoreCase(code)
                .orElseThrow(() -> new IndicatorConflictException("上游指标不存在: " + code));
            CalculationItem item = calculateNode(dependency.getId(), requestId, calculated, visiting, depth + 1);
            if (!"SUCCESS".equals(item.status()) || item.value() == null) {
                throw new IndicatorConflictException("上游指标计算失败: " + code);
            }
            aliases.put(code, quoteIdentifier(code));
            inputs.add(item.value().toPlainString() + "::numeric AS " + quoteIdentifier(code));
            rowsProcessed += Math.max(0, item.rowsProcessed());
        }
        String expression = compiler.compile(indicator.getExpressionSql(), aliases);
        String sql = "SELECT CAST(" + expression + " AS numeric) AS metric_value, 1::bigint AS rows_processed " +
            "FROM (SELECT " + String.join(", ", inputs) + ") metric_inputs";
        ResultRow row = resultRow(queryGateway.execute(sql));
        return new Computed(row.value(), rowsProcessed, "FORMULA", null, null);
    }

    private CalculationItem saveSuccess(
        GovIndicatorDefinition indicator,
        String requestId,
        Computed computed,
        long durationMs
    ) {
        GovIndicatorRun previous = runs
            .findByIndicatorIdOrderByRunAtDesc(indicator.getId())
            .stream()
            .filter(run -> "SUCCESS".equalsIgnoreCase(run.getStatus()) && run.getComputedValue() != null)
            .findFirst()
            .orElse(null);
        BigDecimal previousValue = previous != null ? previous.getComputedValue() : null;
        BigDecimal changeRate = null;
        if (computed.value() != null && previousValue != null && previousValue.signum() != 0) {
            changeRate = computed.value().subtract(previousValue).divide(previousValue.abs(), 6, RoundingMode.HALF_UP);
        }
        String alertLevel = alertLevel(indicator, computed.value());
        GovIndicatorRun run = new GovIndicatorRun();
        run.setIndicatorId(indicator.getId());
        run.setRunAt(Instant.now());
        run.setStatus("SUCCESS");
        run.setComputedValue(computed.value());
        run.setPreviousValue(previousValue);
        run.setChangeRate(changeRate);
        run.setRowsProcessed(computed.rowsProcessed());
        run.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs)));
        run.setDbtRunId(requestId);
        run.setAlertLevel(alertLevel);
        run.setThresholdHit(!"GREEN".equals(alertLevel));
        run.setAlertReason(alertReason(indicator, computed.value(), alertLevel));
        GovIndicatorRun saved = runs.save(run);
        return new CalculationItem(
            indicator.getId(),
            indicator.getCode(),
            indicator.getName(),
            "SUCCESS",
            computed.value(),
            previousValue,
            changeRate,
            computed.rowsProcessed(),
            run.getDurationMs(),
            computed.sourceMode(),
            computed.relation(),
            computed.field(),
            null,
            saved.getRunAt()
        );
    }

    private CalculationItem saveFailure(UUID indicatorId, String requestId, String message) {
        GovIndicatorDefinition indicator = indicators.findById(indicatorId).orElse(null);
        if (indicator == null) {
            return new CalculationItem(indicatorId, null, null, "FAILED", null, null, null, 0, 0, null, null, null, message, Instant.now());
        }
        return saveFailure(indicator, requestId, message, 0);
    }

    private CalculationItem saveFailure(
        GovIndicatorDefinition indicator,
        String requestId,
        String message,
        long durationMs
    ) {
        GovIndicatorRun run = new GovIndicatorRun();
        run.setIndicatorId(indicator.getId());
        run.setRunAt(Instant.now());
        run.setStatus("FAILED");
        run.setRowsProcessed(0);
        run.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs)));
        run.setDbtRunId(requestId);
        run.setAlertLevel("RED");
        run.setThresholdHit(true);
        run.setAlertReason("指标计算失败");
        run.setErrorMessage(message);
        GovIndicatorRun saved = runs.save(run);
        return new CalculationItem(
            indicator.getId(), indicator.getCode(), indicator.getName(), "FAILED", null, null, null, 0,
            run.getDurationMs(), null, null, null, message, saved.getRunAt()
        );
    }

    private ResultRow resultRow(Map<String, Object> payload) {
        Object rawRows = payload != null ? payload.get("rows") : null;
        if (!(rawRows instanceof List<?> rows) || rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> row)) {
            throw new IndicatorConflictException("指标计算未返回结果");
        }
        BigDecimal value = decimal(row.get("metric_value"));
        int rowsProcessed = number(row.get("rows_processed"));
        if (value == null) {
            throw new IndicatorConflictException("指标计算结果为空，请检查物化表数据");
        }
        return new ResultRow(value, rowsProcessed);
    }

    private List<String> dependencyCodes(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof String code && IDENTIFIER.matcher(code.trim()).matches()) {
                    result.add(code.trim());
                }
            }
            return List.copyOf(result);
        } catch (Exception error) {
            throw new IndicatorConflictException("派生指标依赖格式无效");
        }
    }

    private List<String> temporalKeys(ModelSpecView model) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (model.timeSemantics() != null) {
            model.timeSemantics().fields().stream().filter(IndicatorCalculationService::safeIdentifier).forEach(keys::add);
        }
        if (keys.isEmpty() && model.grain() != null) {
            model.grain()
                .keys()
                .stream()
                .filter(IndicatorCalculationService::safeIdentifier)
                .filter(IndicatorCalculationService::looksTemporal)
                .forEach(keys::add);
        }
        return List.copyOf(keys);
    }

    private String modelCalculationSql(String relation, String field, String aggregation, List<String> periodKeys) {
        String table = quoteRelation(relation);
        String measure = "source." + quoteIdentifier(field);
        String valueExpression = "COUNT_DISTINCT".equals(aggregation)
            ? "COUNT(DISTINCT " + measure + ")"
            : aggregation + "(" + measure + ")";
        if (periodKeys.isEmpty()) {
            return "SELECT CAST(" + valueExpression + " AS numeric) AS metric_value, COUNT(*)::bigint AS rows_processed FROM " + table + " source";
        }
        String keys = String.join(", ", periodKeys.stream().map(IndicatorCalculationService::quoteIdentifier).toList());
        String order = String.join(", ", periodKeys.stream().map(key -> quoteIdentifier(key) + " DESC NULLS LAST").toList());
        String join = String.join(
            " AND ",
            periodKeys.stream().map(key -> "source." + quoteIdentifier(key) + " IS NOT DISTINCT FROM latest." + quoteIdentifier(key)).toList()
        );
        return "WITH latest_period AS (SELECT " + keys + " FROM " + table + " ORDER BY " + order + " LIMIT 1) " +
            "SELECT CAST(" + valueExpression + " AS numeric) AS metric_value, COUNT(*)::bigint AS rows_processed " +
            "FROM " + table + " source JOIN latest_period latest ON " + join;
    }

    private static String aggregation(String value, boolean derived) {
        if (derived) return "MAX";
        return switch (String.valueOf(value).trim().toUpperCase(Locale.ROOT)) {
            case "COUNT" -> "COUNT";
            case "COUNT_DISTINCT" -> "COUNT_DISTINCT";
            case "AVG" -> "AVG";
            case "MIN" -> "MIN";
            case "MAX" -> "MAX";
            default -> "SUM";
        };
    }

    private static void requireRelation(String value) {
        if (!StringUtils.hasText(value) || !RELATION.matcher(value.trim()).matches()) {
            throw new IndicatorConflictException("指标实现物理表名无效");
        }
    }

    private static String quoteRelation(String value) {
        requireRelation(value);
        return String.join(".", java.util.Arrays.stream(value.trim().split("\\.")).map(IndicatorCalculationService::quoteIdentifier).toList());
    }

    private static String quoteIdentifier(String value) {
        if (!safeIdentifier(value)) throw new IndicatorConflictException("指标实现字段名无效: " + value);
        return '"' + value + '"';
    }

    private static boolean safeIdentifier(String value) {
        return StringUtils.hasText(value) && IDENTIFIER.matcher(value.trim()).matches();
    }

    private static boolean looksTemporal(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        return key.contains("date") || key.contains("time") || key.contains("year") || key.contains("month") || key.contains("period");
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String alertLevel(GovIndicatorDefinition indicator, BigDecimal value) {
        if (value == null) return "RED";
        if (indicator.getThresholdMin() != null && value.compareTo(indicator.getThresholdMin()) < 0) return "RED";
        if (indicator.getThresholdMax() != null && value.compareTo(indicator.getThresholdMax()) > 0) return "RED";
        return "GREEN";
    }

    private static String alertReason(GovIndicatorDefinition indicator, BigDecimal value, String alertLevel) {
        if ("GREEN".equals(alertLevel)) return null;
        if (value == null) return "指标计算结果为空";
        if (indicator.getThresholdMin() != null && value.compareTo(indicator.getThresholdMin()) < 0) {
            return "当前值 " + value + " 低于阈值下限 " + indicator.getThresholdMin();
        }
        if (indicator.getThresholdMax() != null && value.compareTo(indicator.getThresholdMax()) > 0) {
            return "当前值 " + value + " 高于阈值上限 " + indicator.getThresholdMax();
        }
        return "指标计算异常";
    }

    private static String safeMessage(Throwable error) {
        String message = error != null ? error.getMessage() : null;
        if (!StringUtils.hasText(message)) return "指标计算失败";
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private record Computed(BigDecimal value, int rowsProcessed, String sourceMode, String relation, String field) {}
    private record ResultRow(BigDecimal value, int rowsProcessed) {}

    public record CalculationBatch(
        String requestId,
        Instant submittedAt,
        List<CalculationItem> items,
        long successCount,
        long failedCount
    ) {}

    public record CalculationItem(
        UUID indicatorId,
        String code,
        String name,
        String status,
        BigDecimal value,
        BigDecimal previousValue,
        BigDecimal changeRate,
        int rowsProcessed,
        int durationMs,
        String sourceMode,
        String relation,
        String field,
        String errorMessage,
        Instant runAt
    ) {}
}
