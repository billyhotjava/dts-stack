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
    private final IndicatorService indicatorService;
    private final PublishedIndicatorVersionReader publishedVersions;
    private final com.yuzhi.dts.platform.service.security.AccessChecker accessChecker;
    private final com.yuzhi.dts.platform.service.modeling.ModelSpecReader modelReader;
    private final com.yuzhi.dts.platform.service.permission.AssetPermissionService permissions;
    private final com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository serving;

    public IndicatorCalculationService(
        GovIndicatorDefinitionRepository indicators,
        GovIndicatorReferenceRepository references,
        GovIndicatorRunRepository runs,
        ModelSpecApplicationService models,
        QueryGateway queryGateway,
        ControlledIndicatorDerivationCompiler compiler,
        ObjectMapper objectMapper,
        IndicatorService indicatorService,
        PublishedIndicatorVersionReader publishedVersions,
        com.yuzhi.dts.platform.service.security.AccessChecker accessChecker,
        com.yuzhi.dts.platform.service.modeling.ModelSpecReader modelReader,
        com.yuzhi.dts.platform.service.permission.AssetPermissionService permissions,
        com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository serving,
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
        this.indicatorService = indicatorService;
        this.modelReader = modelReader;
        this.accessChecker = accessChecker;
        this.publishedVersions = publishedVersions; this.permissions = permissions;
        this.serving = serving;
    }

    public record ConsumerActor(String username, List<String> roles, String dept, String classification) {}
    public record ConsumerPlan(String tenantId, UUID datasourceId, String sql, List<Object> bindings,
        List<String> columns, List<IndicatorAnalysisContract.VersionRef> resolvedVersions, int limit) {}

    @Transactional(readOnly = true)
    public ConsumerPlan planForConsumer(IndicatorAnalysisContract.Query query, ConsumerActor actor) {
        if (actor == null || !StringUtils.hasText(actor.username()) || actor.roles() == null || !StringUtils.hasText(actor.classification())) {
            throw new org.springframework.security.access.AccessDeniedException("分析用户上下文缺失");
        }
        var plan = new IndicatorQueryPlan(query, ref -> {
            var historical = publishedVersions.read(ref);
            var current = indicators.findById(ref.id()).orElseThrow(() -> new IndicatorNotFoundException("指标不存在"));
            authorizeConsumer(actor, current, current.getDataLevel());
            authorizeConsumer(actor, current, historical.getDataLevel());
            return historical;
        }, definition -> {
            var implementation = publishedVersions.implementation(definition);
            if (implementation == null) throw new IndicatorConflictException("缺少固定实现");
            String assetKey = com.yuzhi.dts.platform.service.catalog.CatalogAssetKey.semanticModel(implementation.modelSpecId().toString());
            var decision = permissions.checkAction(new com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionCheckCommand(
                actor.username(), actor.roles(), actor.dept(), actor.classification(), "SEMANTIC_MODEL", implementation.modelSpecId().toString(), assetKey, "READ", null));
            if (!decision.allowed()) throw new org.springframework.security.access.AccessDeniedException("无权读取指标来源资产");
            return querySource(definition, true);
        }, compiler).build();
        List<String> columns = new ArrayList<>(query.dimensions());
        for (int i = 0; i < query.indicatorRefs().size(); i++) { columns.add("metric_" + i); columns.add("metric_" + i + "_null_reason"); columns.add("metric_" + i + "_invalid"); }
        return new ConsumerPlan(serverTenantId, plan.datasourceId(), plan.sql(), plan.parameters(), columns, plan.versions(), query.limit());
    }

    private void authorizeConsumer(ConsumerActor actor, GovIndicatorDefinition definition, String level) {
        String key = com.yuzhi.dts.platform.service.catalog.CatalogAssetKey.codeAsset(com.yuzhi.dts.platform.service.catalog.CatalogAssetType.GOV_INDICATOR, "default", definition.getCode());
        var decision = permissions.checkAction(new com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionCheckCommand(
            actor.username(), actor.roles(), actor.dept(), actor.classification(), "GOV_INDICATOR", definition.getId().toString(), key, "READ", level));
        if (!decision.allowed()) throw new org.springframework.security.access.AccessDeniedException("无权读取指标或其历史版本");
    }

    @Transactional(readOnly = true)
    public IndicatorAnalysisContract.Result query(IndicatorAnalysisContract.Query request, String activeDept) {
        String queryId = "metric-query-" + UUID.randomUUID();
        Instant dataAsOf = Instant.now();
        IndicatorQueryPlan.Plan plan = new IndicatorQueryPlan(request, ref -> {
            var snapshot = indicatorService.getVersion(ref.id(), ref.version(), activeDept);
            if (!"PUBLISHED".equals(snapshot.getStatus())) throw new IndicatorConflictException("固定指标版本尚未发布");
            var definition = restoreSnapshot(ref.id(), ref.version(), snapshot.getSnapshotJson());
            var current = indicators.findById(ref.id()).orElseThrow(() -> new IndicatorNotFoundException("指标不存在"));
            if ("ARCHIVED".equals(current.getStatus()) || "DEPRECATED".equals(current.getStatus())) throw new IndicatorConflictException("指标已停用");
            if (definition.getAnalysisConfig() == null && "LATEST_PERIOD".equals(request.scope()) && !IndicatorDefinitionSemantics.isDerivedLike(definition)) {
                var implementation = publishedVersions.implementation(definition);
                if (implementation == null) throw new IndicatorConflictException("存量指标缺少可还原的固定实现");
                var model = models.revision(serverTenantId, new ModelRevisionRef(implementation.modelSpecId(), implementation.modelRevision()));
                Map<String, String> bindings = new LinkedHashMap<>();
                model.fields().stream().filter(field -> field.role() != FieldRole.MEASURE).forEach(field -> bindings.put(field.name(), field.name()));
                var times = temporalKeys(model);
                if (times.size() > 1) throw new IndicatorConflictException("该存量版本有多个时间键，请修订并声明分析时间字段");
                definition.setAnalysisConfig(IndicatorMapper.writeAnalysisConfig(new IndicatorAnalysisContract.Config(bindings,
                    times.isEmpty() ? null : new IndicatorAnalysisContract.TimeBinding("business_time", times.get(0), "Asia/Shanghai", "native"),
                    List.of(), List.of(String.valueOf(definition.getAggregationType()).toUpperCase(Locale.ROOT)), List.of(), List.of(), null, null, false)));
            }
            return definition;
        }, this::querySource, compiler).build();
        var payload = queryGateway.executeBound(plan.sql(), plan.datasourceId(), plan.parameters(), request.limit() + 1);
        Object raw = payload.get("rows");
        if (!(raw instanceof List<?> list)) throw new IndicatorConflictException("查询结果格式无效");
        if (Boolean.TRUE.equals(payload.get("truncated")) || list.size() > request.limit()) {
            throw new IndicatorConflictException("结果超过行数上限，请缩小时间或维度筛选范围");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>(request.dimensions());
        for (int i = 0; i < request.indicatorRefs().size(); i++) {
            columns.add("metric_" + i); columns.add("metric_" + i + "_null_reason");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) throw new IndicatorConflictException("查询结果行格式无效");
            for (int i = 0; i < request.indicatorRefs().size(); i++) {
                if (Boolean.TRUE.equals(row.get("metric_" + i + "_invalid"))) throw new IndicatorConflictException("预计算结果在声明粒度上重复，已拒绝返回聚合值");
            }
            Map<String, Object> visible = new LinkedHashMap<>();
            columns.forEach(column -> visible.put(column, row.get(column)));
            rows.add(visible);
        }
        return new IndicatorAnalysisContract.Result(columns, rows, plan.versions(), queryId, dataAsOf, false,
            List.of("同源单条语句快照；空维度键保留为 null；结果不补完整日历"));
    }

    private IndicatorQueryPlan.Source querySource(GovIndicatorDefinition indicator) {
        return querySource(indicator, false);
    }

    private IndicatorQueryPlan.Source querySource(GovIndicatorDefinition indicator, boolean consumerAuthorized) {
        GovIndicatorReference reference = modelFieldReference(indicator);
        if (reference == null) throw new IndicatorConflictException("该版本缺少精确模型字段引用");
        Matcher target = MODEL_FIELD_TARGET.matcher(reference.getRefTarget());
        if (!target.matches()) throw new IndicatorConflictException("模型字段引用无效");
        UUID modelId = UUID.fromString(target.group(1)); int revision = Integer.parseInt(target.group(2));
        ModelSpecView model = consumerAuthorized ? modelReader.revision(serverTenantId, new ModelRevisionRef(modelId, revision))
            : models.revision(serverTenantId, new ModelRevisionRef(modelId, revision));
        if (model.status() != ModelStatus.PUBLISHED || model.fields().stream().noneMatch(field -> field.role() == FieldRole.MEASURE && target.group(3).equals(field.name()))) throw new IndicatorConflictException("固定模型版本或度量字段不可用");
        if (!consumerAuthorized) {
            String login = com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("请先登录"));
            var decision = permissions.checkAction(new com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionCheckCommand(
                login, com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserAuthorities(),
                com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserDept().orElse(null), accessChecker.resolveHighestDataLevel().name(),
                "SEMANTIC_MODEL", modelId.toString(), com.yuzhi.dts.platform.service.catalog.CatalogAssetKey.semanticModel(modelId.toString()), "READ", null));
            if (!decision.allowed()) throw new org.springframework.security.access.AccessDeniedException("无权读取指标来源资产");
        }
        var projection = serving.findProjection(serverTenantId, modelId)
            .orElseThrow(() -> new IndicatorConflictException("固定模型尚无可分析资产"));
        var ref = projection.servingRef();
        if (ref == null || ref.modelRevision() != revision || !java.util.Objects.equals(ref.modelChecksum(), model.checksum())) {
            throw new IndicatorConflictException("固定模型版本当前没有匹配的服务数据，不能切换到最新模型");
        }
        if (ref.sourceId() == null) throw new IndicatorConflictException("模型服务数据源缺失");
        Set<String> fields = model.fields().stream().filter(java.util.Objects::nonNull).map(ModelField::name).collect(java.util.stream.Collectors.toSet());
        if (!target.group(3).equals(indicator.getMeasureField())) throw new IndicatorConflictException("度量字段与固定实现不一致");
        return new IndicatorQueryPlan.Source(ref.sourceId(), ref.schemaName(), ref.identifier(), fields);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public CalculationBatch calculate(List<UUID> indicatorIds) { return calculate(indicatorIds, null); }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public CalculationBatch calculate(List<UUID> indicatorIds, String activeDept) {
        if (indicatorIds == null || indicatorIds.isEmpty() || indicatorIds.size() > 64) throw new IndicatorRequestException("计算指标数量须为1至64");
        String requestId = "metric-calc-" + UUID.randomUUID();
        Instant submittedAt = Instant.now();
        List<CalculationItem> requested = new ArrayList<>();
        for (UUID id : indicatorIds) {
            long started = System.currentTimeMillis();
            try {
                var current = indicators.findById(id).orElseThrow(() -> new IndicatorNotFoundException("指标不存在"));
                var snapshot = indicatorService.getVersion(id, current.getVersion(), activeDept);
                var definition = restoreSnapshot(id, current.getVersion(), snapshot.getSnapshotJson());
                var response = query(new IndicatorAnalysisContract.Query(
                    List.of(new IndicatorAnalysisContract.VersionRef(id, current.getVersion())), null, List.of(), List.of(), 1, "LATEST_PERIOD"), activeDept);
                var row = response.rows().isEmpty() ? Map.<String, Object>of() : response.rows().get(0);
                var value = decimal(row.get("metric_0"));
                String reason = row.get("metric_0_null_reason") == null ? null : String.valueOf(row.get("metric_0_null_reason"));
                requested.add(saveSuccess(definition, requestId, new Computed(value, response.rows().size(), StringUtils.hasText(definition.getDateColumn()) ? "LATEST_PERIOD" : "ALL_DATA", null, null, reason, response), System.currentTimeMillis() - started));
            } catch (RuntimeException error) {
                requested.add(saveFailure(id, requestId, safeMessage(error)));
            }
        }
        long success = requested.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        return new CalculationBatch(requestId, submittedAt, requested, success, requested.size() - success);
    }

    private GovIndicatorDefinition restoreSnapshot(UUID id, String version, String json) {
        try {
            var request = objectMapper.readerFor(com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest.class)
                .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .<com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest>readValue(json);
            GovIndicatorDefinition result = new GovIndicatorDefinition();
            IndicatorMapper.apply(result, request);
            result.setId(id);
            result.setVersion(version);
            result.setStatus("PUBLISHED");
            return result;
        } catch (Exception error) {
            throw new IndicatorConflictException("指标版本快照无法读取，请修复版本记录");
        }
    }

    private GovIndicatorReference modelFieldReference(GovIndicatorDefinition indicator) {
        IndicatorImplementationRef ref = IndicatorMapper.readImplementationRef(indicator.getImplementationRef());
        String target = ref != null ? ref.modelSpecId() + "@" + ref.modelRevision() + "#" + ref.fieldName()
            : IndicatorDefinitionSemantics.expectedModelFieldReferenceTarget(indicator).orElse(null);
        // Never read the mutable current reference when executing an old snapshot.
        if (target == null) return null;
        GovIndicatorReference reference = new GovIndicatorReference();
        reference.setRefType("MODEL_SPEC_FIELD");
        reference.setRefTarget(target);
        return reference;
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
            .filter(run -> "SUCCESS".equalsIgnoreCase(run.getStatus()) && run.getComputedValue() != null && indicator.getVersion().equals(run.getIndicatorVersion()))
            .findFirst()
            .orElse(null);
        BigDecimal previousValue = previous != null ? previous.getComputedValue() : null;
        BigDecimal changeRate = null;
        if (computed.value() != null && previousValue != null && previousValue.signum() != 0) {
            changeRate = computed.value().subtract(previousValue).divide(previousValue.abs(), 6, RoundingMode.HALF_UP);
        }
        String alertLevel = computed.value() == null ? "UNKNOWN" : alertLevel(indicator, computed.value());
        GovIndicatorRun run = new GovIndicatorRun();
        run.setIndicatorId(indicator.getId());
        run.setIndicatorVersion(indicator.getVersion());
        try { run.setDependencyVersions(objectMapper.writeValueAsString(computed.result().resolvedVersions())); }
        catch (Exception error) { throw new IndicatorConflictException("运行版本追溯无法保存"); }
        run.setQueryId(computed.result().queryId()); run.setDataAsOf(computed.result().dataAsOf());
        run.setSourceMode(computed.sourceMode()); run.setNullReason(computed.nullReason());
        run.setRunAt(Instant.now());
        run.setStatus("SUCCESS");
        run.setComputedValue(computed.value());
        run.setPreviousValue(previousValue);
        run.setChangeRate(changeRate);
        run.setRowsProcessed(computed.rowsProcessed());
        run.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs)));
        run.setDbtRunId(requestId);
        run.setAlertLevel(alertLevel);
        run.setThresholdHit(computed.value() != null && !"GREEN".equals(alertLevel));
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
            saved.getRunAt(),
            indicator.getVersion(),
            computed.value() == null ? (computed.nullReason() == null ? "EMPTY_DATA" : computed.nullReason()) : null
        );
    }

    private CalculationItem saveFailure(UUID indicatorId, String requestId, String message) {
        GovIndicatorDefinition indicator = indicators.findById(indicatorId).orElse(null);
        if (indicator == null) {
            return new CalculationItem(indicatorId, null, null, "FAILED", null, null, null, 0, 0, null, null, null, message, Instant.now(), null, null);
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
        run.setIndicatorVersion(indicator.getVersion());
        run.setDependencyVersions(indicator.getSourceRefs());
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
            run.getDurationMs(), null, null, null, message, saved.getRunAt(), indicator.getVersion(), null
        );
    }

    private ResultRow resultRow(Map<String, Object> payload) {
        Object rawRows = payload != null ? payload.get("rows") : null;
        if (!(rawRows instanceof List<?> rows) || rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> row)) {
            throw new IndicatorConflictException("指标计算未返回结果");
        }
        BigDecimal value = decimal(row.get("metric_value"));
        int rowsProcessed = number(row.get("rows_processed"));
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

    private record Computed(BigDecimal value, int rowsProcessed, String sourceMode, String relation, String field, String nullReason, IndicatorAnalysisContract.Result result) {}
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
        Instant runAt,
        String indicatorVersion,
        String nullReason
    ) {}
}
