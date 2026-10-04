package com.yuzhi.dts.analytics.service.semantic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsField;
import com.yuzhi.dts.analytics.domain.AnalyticsMetric;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticJoin;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticModel;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.domain.AnalyticsVirtualDataset;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticJoinRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticModelRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsVirtualDatasetRepository;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryGateway;
import com.yuzhi.dts.analytics.service.analysis.AnalysisRequestContext;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class SemanticQueryService {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern USER_TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*user\\.([a-zA-Z0-9_]+)\\s*}}");
    private static final Pattern BRACKET_REF_PATTERN = Pattern.compile("\\[([^\\]]+)]");
    private static final Set<String> ALLOWED_FILTER_OPS = Set.of(
        "=",
        "!=",
        ">",
        ">=",
        "<",
        "<=",
        "in",
        "not_in",
        "between",
        "like",
        "is_null",
        "is_not_null"
    );
    private static final Set<String> ALLOWED_DERIVED_FUNCTIONS = Set.of("ABS", "ROUND", "COALESCE", "NULLIF");
    // Ladder order comes from the canonical catalog so it cannot drift from the rest of the platform.
    private static final List<String> SECURITY_LEVELS = SecurityLevelCatalog.dataCodesInOrder();

    private final AnalyticsSemanticModelRepository semanticModelRepository;
    private final AnalyticsSemanticJoinRepository semanticJoinRepository;
    private final AnalyticsMetricRepository metricRepository;
    private final AnalyticsFieldRepository fieldRepository;
    private final AnalyticsVirtualDatasetRepository virtualDatasetRepository;
    private final AnalysisQueryGateway analysisQueryGateway;
    private final ObjectMapper objectMapper;
    private final PlatformIndicatorPlanClient indicatorPlans;
    private final com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository databases;

    public SemanticQueryService(
        AnalyticsSemanticModelRepository semanticModelRepository,
        AnalyticsSemanticJoinRepository semanticJoinRepository,
        AnalyticsMetricRepository metricRepository,
        AnalyticsFieldRepository fieldRepository,
        AnalyticsVirtualDatasetRepository virtualDatasetRepository,
        AnalysisQueryGateway analysisQueryGateway,
        ObjectMapper objectMapper,
        PlatformIndicatorPlanClient indicatorPlans,
        com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository databases
    ) {
        this.semanticModelRepository = semanticModelRepository;
        this.semanticJoinRepository = semanticJoinRepository;
        this.metricRepository = metricRepository;
        this.fieldRepository = fieldRepository;
        this.virtualDatasetRepository = virtualDatasetRepository;
        this.analysisQueryGateway = analysisQueryGateway;
        this.objectMapper = objectMapper;
        this.indicatorPlans = indicatorPlans; this.databases = databases;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMeta(String subjectArea, Boolean exposedToModeler, String maxClassification) {
        boolean exposed = exposedToModeler == null || exposedToModeler.booleanValue();
        List<AnalyticsSemanticModel> models;
        if (StringUtils.hasText(subjectArea)) {
            models = semanticModelRepository.findAllBySubjectAreaIgnoreCaseAndExposedToModelerOrderByLabelAscModelNameAsc(
                subjectArea.trim(),
                exposed
            );
        } else {
            models = semanticModelRepository.findAllByExposedToModelerOrderBySubjectAreaAscLabelAscModelNameAsc(exposed);
        }
        LevelGate levelGate = LevelGate.of(maxClassification);
        List<Map<String, Object>> responseModels = models.stream()
            .filter(model -> levelGate.allows(model.getSecurityLevel()))
            .map(model -> toModelMeta(model, levelGate))
            .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("spec_version", "1");
        response.put("generated_at", Instant.now().toString());
        response.put("models", responseModels);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getGraph(String maxClassification) {
        LevelGate levelGate = LevelGate.of(maxClassification);
        Map<String, AnalyticsSemanticModel> modelMap = loadAllModelMap();
        List<Map<String, Object>> nodes = modelMap.values().stream()
            .filter(model -> levelGate.allows(model.getSecurityLevel()))
            .sorted(Comparator.comparing(AnalyticsSemanticModel::getModelName, String.CASE_INSENSITIVE_ORDER))
            .map(model -> {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", model.getModelName());
                node.put("label", defaultText(model.getLabel(), model.getModelName()));
                node.put("subject_area", model.getSubjectArea());
                node.put("security_level", normalizeSecurityLevel(model.getSecurityLevel()));
                return node;
            })
            .toList();
        List<Map<String, Object>> edges = semanticJoinRepository.findAll().stream()
            .filter(edge -> {
                AnalyticsSemanticModel from = modelMap.get(normalizeKey(edge.getFromModel()));
                AnalyticsSemanticModel to = modelMap.get(normalizeKey(edge.getToModel()));
                return from != null && to != null && levelGate.allows(from.getSecurityLevel()) && levelGate.allows(to.getSecurityLevel());
            })
            .sorted(Comparator.comparing(AnalyticsSemanticJoin::getFromModel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AnalyticsSemanticJoin::getToModel, String.CASE_INSENSITIVE_ORDER))
            .map(edge -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", edge.getId());
                item.put("source", edge.getFromModel());
                item.put("target", edge.getToModel());
                item.put("type", defaultText(edge.getJoinType(), "many_to_one"));
                item.put("relationship", defaultText(edge.getRelationship(), "left"));
                item.put("approval_required", edge.isApprovalRequired());
                item.put("fanout_warning", edge.isFanoutWarning());
                item.put("description", edge.getDescription());
                return item;
            })
            .toList();
        return Map.of("nodes", nodes, "edges", edges);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewSql(JsonNode body, PlatformContext context) { return previewSql(body, context, null); }

    @Transactional(readOnly = true)
    public Map<String, Object> previewSql(JsonNode body, PlatformContext context, Long userId) {
        CompiledSemanticQuery compiled = compileForActor(body, context, userId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("meta", Map.of(
            "sql_preview", compiled.sql(),
            "security_applied", compiled.securityApplied(),
            "warnings", compiled.warnings()
        ));
        response.put("columns", visibleColumns(compiled).stream().map(ColumnMeta::toMap).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> runQuery(JsonNode body, PlatformContext context, Long userId) throws SQLException {
        CompiledSemanticQuery compiled = compileForActor(body, context, userId);
        boolean skipCache = compiled.securityApplied().contains("fixed_indicator_versions") || "fresh".equalsIgnoreCase(trimToNull(body != null ? body.path("cache_hint").asText(null) : null));
        AnalysisQueryGateway.GatewayExecution execution = executeThroughGateway(compiled, context, userId, skipCache, "/api/semantic/query");
        DatasetQueryService.DatasetResult result = execution.datasetResult();
        validateGovernedResult(compiled, result);
        result = visibleGovernedResult(compiled, result);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("row_count", result.rows().size());
        meta.put("elapsed_ms", execution.result().durationMs());
        meta.put("cache_hit", execution.result().cacheHit());
        meta.put("query_id", execution.result().queryId());
        meta.put("security_applied", compiled.securityApplied());
        meta.put("warnings", compiled.warnings());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("meta", meta);
        response.put("columns", visibleColumns(compiled).stream().map(ColumnMeta::toMap).toList());
        response.put("rows", result.rows());
        return response;
    }

    @Transactional(readOnly = true)
    public SemanticExecutionResult executeForCard(JsonNode body, PlatformContext context, Long userId) throws SQLException {
        CompiledSemanticQuery compiled = compileForActor(body, context, userId);
        boolean skipCache = compiled.securityApplied().contains("fixed_indicator_versions") || "fresh".equalsIgnoreCase(trimToNull(body != null ? body.path("cache_hint").asText(null) : null));
        AnalysisQueryGateway.GatewayExecution execution = executeThroughGateway(compiled, context, userId, skipCache, "/api/card/query");
        DatasetQueryService.DatasetResult rawResult = execution.datasetResult();
        validateGovernedResult(compiled, rawResult);
        rawResult = visibleGovernedResult(compiled, rawResult);

        DatasetQueryService.DatasetResult cardResult = new DatasetQueryService.DatasetResult(
            rawResult.rows(),
            toMetabaseCols(visibleColumns(compiled)),
            toMetabaseResultsMetadata(visibleColumns(compiled)),
            rawResult.resultsTimezone()
        );
        return new SemanticExecutionResult(
            compiled.databaseId(),
            compiled.sql(),
            cardResult,
            compiled.securityApplied(),
            compiled.warnings(),
            execution.result().cacheHit(),
            execution.result().durationMs()
        );
    }

    private AnalysisQueryGateway.GatewayExecution executeThroughGateway(
        CompiledSemanticQuery compiled,
        PlatformContext context,
        Long userId,
        boolean skipCache,
        String requestUri
    ) {
        AnalyticsUser actor = new AnalyticsUser();
        actor.setId(userId == null ? -1L : userId);
        actor.setEmail("analytics-user-" + actor.getId());
        actor.setActive(true);
        PlatformContext safeContext = context == null ? new PlatformContext(null, null, null) : context;
        AnalysisRequestContext requestContext = new AnalysisRequestContext(
            safeContext.dept(),
            safeContext.classification(),
            safeContext.roles(),
            null,
            requestUri,
            null
        );
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
            compiled.databaseId(),
            "native",
            compiled.sql(),
            compiled.bindings(),
            null,
            compiled.constraints()
        );
        return analysisQueryGateway.executePrepared(
            actor,
            prepared,
            requestContext,
            "legacy-semantic",
            compiled.constraints().maxResults(),
            skipCache
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> previewCardColumns(JsonNode body, PlatformContext context) { return previewCardColumns(body, context, null); }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> previewCardColumns(JsonNode body, PlatformContext context, Long userId) {
        CompiledSemanticQuery compiled = compileForActor(body, context, userId);
        return toMetabaseResultsMetadata(visibleColumns(compiled));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listVirtualDatasets(AnalyticsUser user, String owner, Long workspaceId) {
        List<AnalyticsVirtualDataset> rows;
        if (user != null && user.isSuperuser() && !"me".equalsIgnoreCase(trimToNull(owner))) {
            rows = virtualDatasetRepository.findAllByArchivedFalseOrderByUpdatedAtDescIdDesc();
        } else if (workspaceId != null) {
            rows = virtualDatasetRepository.findAllByOwnerIdAndWorkspaceIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(user.getId(), workspaceId);
        } else {
            rows = virtualDatasetRepository.findAllByOwnerIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(user.getId());
        }
        return rows.stream().map(this::toVirtualDatasetResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVirtualDataset(long id, AnalyticsUser user) {
        return toVirtualDatasetResponse(requireVirtualDataset(id, user));
    }

    public Map<String, Object> createVirtualDataset(JsonNode body, AnalyticsUser user) {
        AnalyticsVirtualDataset entity = new AnalyticsVirtualDataset();
        applyVirtualDataset(entity, body, user, true);
        return toVirtualDatasetResponse(virtualDatasetRepository.save(entity));
    }

    public Map<String, Object> updateVirtualDataset(long id, JsonNode body, AnalyticsUser user) {
        AnalyticsVirtualDataset entity = requireVirtualDataset(id, user);
        applyVirtualDataset(entity, body, user, false);
        return toVirtualDatasetResponse(virtualDatasetRepository.save(entity));
    }

    public void deleteVirtualDataset(long id, AnalyticsUser user) {
        AnalyticsVirtualDataset entity = requireVirtualDataset(id, user);
        entity.setArchived(true);
        virtualDatasetRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> promoteVirtualDataset(long id, AnalyticsUser user) {
        AnalyticsVirtualDataset entity = requireVirtualDataset(id, user);
        JsonNode state = safeReadTree(entity.getStateJson());
        CompiledSemanticQuery compiled = compile(state, new PlatformContext(null, "CONFIDENTIAL", null));
        String modelName = "vds_" + id;

        StringBuilder schema = new StringBuilder();
        schema.append("version: 2\n");
        schema.append("models:\n");
        schema.append("  - name: ").append(modelName).append('\n');
        schema.append("    description: ").append(defaultText(entity.getDescription(), entity.getName())).append('\n');
        schema.append("    meta:\n");
        schema.append("      dts:\n");
        schema.append("        spec_version: \"1\"\n");
        schema.append("        exposed_to_modeler: true\n");
        schema.append("        security_level: INTERNAL\n");
        schema.append("    columns:\n");
        for (ColumnMeta column : compiled.columns()) {
            schema.append("      - name: ").append(column.id()).append('\n');
            schema.append("        description: ").append(defaultText(column.label(), column.id())).append('\n');
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("virtual_dataset_id", entity.getId());
        response.put("model_name", modelName);
        response.put("sql", compiled.sql());
        response.put("schema_yml", schema.toString());
        response.put("summary", "已生成 dbt model 草稿，可提交到审批/PR 流程。");
        return response;
    }

    private AnalyticsVirtualDataset requireVirtualDataset(long id, AnalyticsUser user) {
        AnalyticsVirtualDataset entity = virtualDatasetRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("虚拟数据集不存在"));
        if (entity.isArchived()) {
            throw new IllegalArgumentException("虚拟数据集不存在");
        }
        if (user != null && !user.isSuperuser() && !Objects.equals(entity.getOwnerId(), user.getId())) {
            throw new IllegalArgumentException("虚拟数据集不存在");
        }
        return entity;
    }

    private void applyVirtualDataset(AnalyticsVirtualDataset entity, JsonNode body, AnalyticsUser user, boolean creating) {
        String name = trimToNull(body != null ? body.path("name").asText(null) : null);
        JsonNode state = body != null ? body.get("state") : null;
        if (creating && name == null) {
            throw new IllegalArgumentException("虚拟数据集名称不能为空");
        }
        if (creating && (state == null || !state.isObject())) {
            throw new IllegalArgumentException("虚拟数据集 state 不能为空");
        }
        if (name != null) {
            entity.setName(name);
        }
        if (body != null && body.has("description")) {
            entity.setDescription(trimToNull(body.path("description").asText(null)));
        }
        if (body != null && body.has("workspace_id") && body.path("workspace_id").canConvertToLong()) {
            entity.setWorkspaceId(body.path("workspace_id").asLong());
        }
        if (state != null && state.isObject()) {
            entity.setStateJson(state.toString());
            entity.setBaseModel(trimToNull(state.path("base").asText(null)));
        }
        if (creating) {
            entity.setOwnerId(user.getId());
            entity.setArchived(false);
        }
    }

    private Map<String, Object> toVirtualDatasetResponse(AnalyticsVirtualDataset entity) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", entity.getId());
        response.put("name", entity.getName());
        response.put("description", entity.getDescription());
        response.put("owner_id", entity.getOwnerId());
        response.put("workspace_id", entity.getWorkspaceId());
        response.put("base_model", entity.getBaseModel());
        response.put("archived", entity.isArchived());
        response.put("state", safeReadTree(entity.getStateJson()));
        response.put("created_at", entity.getCreatedAt());
        response.put("updated_at", entity.getUpdatedAt());
        return response;
    }

    private Map<String, Object> toModelMeta(AnalyticsSemanticModel model, LevelGate levelGate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", model.getModelName());
        response.put("label", defaultText(model.getLabel(), model.getModelName()));
        response.put("subject_area", model.getSubjectArea());
        response.put("security_level", normalizeSecurityLevel(model.getSecurityLevel()));
        response.put("grain", model.getGrain());
        response.put("database_id", model.getDatabaseId());
        response.put("schema_name", model.getSchemaName());
        response.put("table_name", model.getTableName());
        response.put("description", safeMetaText(model.getMetaJson(), "description", null));
        response.put("metrics", loadMetricsForModel(model, levelGate));
        response.put("dimensions", loadDimensionsForModel(model, levelGate));
        response.put("joins", loadJoinsForModel(model, levelGate));
        return response;
    }

    private List<Map<String, Object>> loadMetricsForModel(AnalyticsSemanticModel model, LevelGate levelGate) {
        if (model.getTableId() == null) {
            return List.of();
        }
        return metricRepository.findAllByArchivedFalseAndBaseTableIdOrderByIdAsc(model.getTableId()).stream()
            .map(metric -> toMetricMeta(model, metric))
            .filter(item -> levelGate.allows((String) item.get("security_level")))
            .toList();
    }

    private Map<String, Object> toMetricMeta(AnalyticsSemanticModel model, AnalyticsMetric metric) {
        Map<String, Object> metricJson = safeReadJsonMap(metric.getMetricJson());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", metricId(model.getModelName(), metric.getName()));
        response.put("name", metric.getName());
        response.put("label", defaultText(metric.getDisplayName(), defaultText(asText(metricJson.get("label")), metric.getName())));
        response.put("type", normalizeMetricType(metric.getAggregation()));
        response.put("source_column", defaultText(metric.getExpressionField(), asText(metricJson.get("field"))));
        response.put("security_level", normalizeSecurityLevel(defaultText(asText(metricJson.get("security_level")), model.getSecurityLevel())));
        response.put("format", metricJson.get("format"));
        response.put("description", asText(metricJson.get("description")));
        if (metricJson.get("indicatorId") != null) {
            response.put("indicatorId", metricJson.get("indicatorId")); response.put("indicatorVersion", metricJson.get("indicatorVersion"));
            response.put("assetType", metricJson.get("assetType")); response.put("assetKey", metricJson.get("assetKey"));
            response.put("analysisConfig", metricJson.get("analysisConfig"));
        }
        return response;
    }

    private List<Map<String, Object>> loadDimensionsForModel(AnalyticsSemanticModel model, LevelGate levelGate) {
        if (model.getTableId() == null) {
            return List.of();
        }
        return fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(model.getTableId()).stream()
            .filter(field -> !"measure".equalsIgnoreCase(trimToNull(field.getFieldRole())))
            .map(field -> toDimensionMeta(model, field))
            .filter(item -> levelGate.allows((String) item.get("security_level")))
            .toList();
    }

    private Map<String, Object> toDimensionMeta(AnalyticsSemanticModel model, AnalyticsField field) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", dimensionId(model.getModelName(), field.getName()));
        response.put("name", field.getName());
        response.put("label", defaultText(field.getDisplayName(), field.getName()));
        response.put("type", inferDimensionType(field));
        response.put("security_level", normalizeSecurityLevel(model.getSecurityLevel()));
        response.put("granularities", inferGranularities(field));
        response.put("base_type", field.getBaseType());
        response.put("semantic_type", field.getSemanticType());
        return response;
    }

    private List<Map<String, Object>> loadJoinsForModel(AnalyticsSemanticModel model, LevelGate levelGate) {
        return semanticJoinRepository.findAllByFromModelIgnoreCaseOrderByToModelAsc(model.getModelName()).stream()
            .filter(edge -> {
                AnalyticsSemanticModel toModel = semanticModelRepository.findByModelNameIgnoreCase(edge.getToModel()).orElse(null);
                return toModel == null || levelGate.allows(toModel.getSecurityLevel());
            })
            .map(edge -> {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("from", edge.getFromModel());
                response.put("to", edge.getToModel());
                response.put("type", defaultText(edge.getJoinType(), "many_to_one"));
                response.put("relationship", defaultText(edge.getRelationship(), "left"));
                response.put("path", edge.getOnClause());
                response.put("approval_required", edge.isApprovalRequired());
                response.put("fanout_warning", edge.isFanoutWarning());
                response.put("description", edge.getDescription());
                return response;
            })
            .toList();
    }

    private List<ColumnMeta> visibleColumns(CompiledSemanticQuery compiled) {
        return compiled.securityApplied().contains("fixed_indicator_versions")
            ? compiled.columns().stream().filter(column -> !column.id().endsWith("_invalid")).toList() : compiled.columns();
    }

    private DatasetQueryService.DatasetResult visibleGovernedResult(CompiledSemanticQuery compiled, DatasetQueryService.DatasetResult result) {
        if (!compiled.securityApplied().contains("fixed_indicator_versions")) return result;
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < compiled.columns().size(); i++) if (!compiled.columns().get(i).id().endsWith("_invalid")) selected.add(i);
        List<List<Object>> rows = result.rows().stream().map(row -> selected.stream().map(row::get).toList()).toList();
        return new DatasetQueryService.DatasetResult(rows, toMetabaseCols(visibleColumns(compiled)), toMetabaseResultsMetadata(visibleColumns(compiled)), result.resultsTimezone());
    }

    private void validateGovernedResult(CompiledSemanticQuery compiled, DatasetQueryService.DatasetResult result) {
        if (!compiled.securityApplied().contains("fixed_indicator_versions")) return;
        if (result.rows().size() >= compiled.constraints().maxResults()) throw new IllegalArgumentException("指标结果超过上限，请缩小筛选范围");
        for (var row : result.rows()) {
            for (int i = 0; i < compiled.columns().size(); i++) {
                if (compiled.columns().get(i).id().endsWith("_invalid") && Boolean.TRUE.equals(row.get(i))) {
                    throw new IllegalArgumentException("预计算指标在声明粒度上存在重复记录");
                }
            }
        }
    }

    private CompiledSemanticQuery compileForActor(JsonNode body, PlatformContext context, Long userId) {
        JsonNode request = governedRequest(body);
        if (request == null) return compile(body, context);
        var plan = indicatorPlans.plan(request, context, userId);
        var database = databases.findByTenantIdAndPlatformDataSourceId(plan.tenantId(), plan.datasourceId())
            .orElseThrow(() -> new IllegalArgumentException("指标数据源尚未注册到分析服务"));
        List<ColumnMeta> columns = plan.columns().stream().map(name -> new ColumnMeta(name, name,
            name.endsWith("_invalid") ? "boolean" : name.startsWith("metric_") && !name.endsWith("_null_reason") ? "number" : "string", null)).toList();
        return new CompiledSemanticQuery(database.getId(), plan.sql(), plan.bindings(), columns,
            List.of("fixed_indicator_versions", "platform_current_permissions"),
            List.of("固定版本: " + plan.resolvedVersions()), new DatasetQueryService.DatasetConstraints(plan.limit() + 1, 30, "UTC"));
    }

    private JsonNode governedRequest(JsonNode body) {
        if (body == null) return null;
        if (body.has("indicatorRefs") && body.path("measures").isEmpty()) {
            if (!body.path("joins").isEmpty() || !body.path("derived_metrics").isEmpty() || !body.path("order_by").isEmpty()) throw new IllegalArgumentException("公共指标不支持临时关联、公式或排序");
            return body;
        }
        if (!body.path("measures").isArray()) return null;
        var metrics = loadMetricMap(loadAllModelMap().values());
        var refs = objectMapper.createArrayNode();
        List<Map<String, Object>> governedMetadata = new ArrayList<>();
        int selected = 0;
        for (JsonNode node : body.path("measures")) {
            selected++;
            String key = node.isTextual() ? node.asText() : node.path("id").asText();
            var metric = metrics.get(normalizeKey(key));
            if (metric == null) continue;
            var metadata = safeReadJsonMap(metric.getMetricJson());
            if (metadata.get("indicatorId") != null) {
                governedMetadata.add(metadata);
                refs.addObject().put("id", String.valueOf(metadata.get("indicatorId"))).put("version", String.valueOf(metadata.get("indicatorVersion")));
            }
        }
        if (refs.isEmpty()) return null;
        if (refs.size() != selected) throw new IllegalArgumentException("公共指标请单独分析，不能混入临时度量");
        if (body.has("indicatorRefs") && !body.get("indicatorRefs").equals(refs)) throw new IllegalArgumentException("卡片固定版本引用与所选指标不一致");
        if (!body.path("joins").isEmpty() || !body.path("derived_metrics").isEmpty() || !body.path("order_by").isEmpty()) throw new IllegalArgumentException("公共指标使用已发布口径，不能追加临时关联、公式或排序");
        var query = objectMapper.createObjectNode(); query.set("indicatorRefs", refs);
        var dimensions = query.putArray("dimensions");
        for (JsonNode dimension : body.path("dimensions")) {
            String key = dimension.isTextual() ? dimension.asText() : dimension.path("id").asText();
            if (dimension.isObject() && dimension.hasNonNull("granularity") && !"native".equals(dimension.get("granularity").asText())) throw new IllegalArgumentException("公共指标只支持模型已声明的时间粒度");
            dimensions.add(publicDimension(key, governedMetadata));
        }
        if (body.has("timeRange")) query.set("timeRange", body.get("timeRange").deepCopy());
        var filters = query.putArray("filters");
        for (JsonNode filter : body.path("filters")) {
            String key = filter.path("field").asText(filter.path("id").asText());
            String op = filter.path("op").asText();
            if (applyGovernedTimeFilter(query, key, filter, governedMetadata)) continue;
            var rule = filters.addObject(); rule.put("fieldRef", publicDimension(key, governedMetadata));
            rule.put("op", switch (op) { case "=" -> "EQ"; case "in" -> "IN"; case "between" -> "BETWEEN"; default -> throw new IllegalArgumentException("公共指标不支持该筛选操作"); });
            if ("between".equals(op) && filter.has("value_to")) {
                var range = objectMapper.createArrayNode(); range.add(filter.path("value")); range.add(filter.get("value_to")); rule.set("value", range);
            } else rule.set("value", filter.path("value"));
        }
        query.put("limit", body.path("limit").asInt(200));
        return query;
    }

    private boolean applyGovernedTimeFilter(com.fasterxml.jackson.databind.node.ObjectNode query, String key, JsonNode filter,
                                              List<Map<String, Object>> metadata) {
        String field = key.substring(key.lastIndexOf('.') + 1);
        JsonNode first = objectMapper.valueToTree(metadata.get(0).get("analysisConfig")).path("timeBinding");
        if (first.isMissingNode() || first.isNull() || (!field.equals(first.path("fieldRef").asText()) && !field.equals(first.path("fieldName").asText()))) return false;
        for (var metric : metadata) {
            JsonNode time = objectMapper.valueToTree(metric.get("analysisConfig")).path("timeBinding");
            if (!first.path("fieldRef").equals(time.path("fieldRef")) || !first.path("timezone").equals(time.path("timezone"))) throw new IllegalArgumentException("指标时间映射不兼容");
        }
        String op = filter.path("op").asText();
        JsonNode value = filter.path("value");
        java.time.ZoneId zone = java.time.ZoneId.of(first.path("timezone").asText());
        java.time.OffsetDateTime start;
        java.time.OffsetDateTime end;
        if ("=".equals(op) && value.isTextual()) {
            var day = java.time.LocalDate.parse(value.asText());
            start = day.atStartOfDay(zone).toOffsetDateTime(); end = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        } else if ("between".equals(op) || ("in".equals(op) && value.isArray() && value.size() == 2)) {
            String a = value.isArray() ? value.path(0).asText() : value.asText();
            String b = value.isArray() ? value.path(1).asText() : filter.path("value_to").asText();
            start = java.time.LocalDate.parse(a).atStartOfDay(zone).toOffsetDateTime();
            end = java.time.LocalDate.parse(b).plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        } else throw new IllegalArgumentException("时间筛选需一个日期或两个日期组成的区间");
        JsonNode previous = query.path("timeRange");
        if (!previous.isMissingNode() && !previous.isNull()) {
            if (!first.path("fieldRef").equals(previous.path("fieldRef")) || !first.path("timezone").equals(previous.path("timezone"))) throw new IllegalArgumentException("卡片与看板时间映射不兼容");
            var oldStart = java.time.OffsetDateTime.parse(previous.path("start").asText());
            var oldEnd = java.time.OffsetDateTime.parse(previous.path("endExclusive").asText());
            if (oldStart.isAfter(start)) start = oldStart;
            if (oldEnd.isBefore(end)) end = oldEnd;
        }
        if (!start.isBefore(end)) throw new IllegalArgumentException("卡片与看板时间范围无交集");
        var range = query.putObject("timeRange"); range.put("fieldRef", first.path("fieldRef").asText());
        range.put("timezone", zone.getId()); range.put("start", start.toString()); range.put("endExclusive", end.toString());
        return true;
    }

    private String publicDimension(String reference, List<Map<String, Object>> metadata) {
        String field = reference.substring(reference.lastIndexOf('.') + 1);
        String resolved = null;
        for (var metric : metadata) {
            JsonNode config = objectMapper.valueToTree(metric.get("analysisConfig"));
            JsonNode bindings = config.path("dimensionBindings");
            String key = bindings.has(field) ? field : null;
            var entries = bindings.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (field.equals(entry.getValue().asText())) {
                    if (key != null && !key.equals(entry.getKey())) throw new IllegalArgumentException("公共维度映射存在歧义");
                    key = entry.getKey();
                }
            }
            if (key == null || (resolved != null && !resolved.equals(key))) throw new IllegalArgumentException("所选指标的公共维度映射不兼容");
            resolved = key;
        }
        return resolved;
    }

    private CompiledSemanticQuery compile(JsonNode body, PlatformContext context) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("语义查询体不能为空");
        }
        if (governedRequest(body) != null) throw new IllegalArgumentException("公共指标需通过已登录用户的固定版本分析入口预览");
        String baseModelName = trimToNull(body.path("base").asText(null));
        if (baseModelName == null) {
            throw new IllegalArgumentException("base 不能为空");
        }
        AnalyticsSemanticModel baseModel = requireSemanticModel(baseModelName);
        LevelGate userGate = LevelGate.of(defaultText(context != null ? context.classification() : null, "CONFIDENTIAL"));
        if (!userGate.allows(baseModel.getSecurityLevel())) {
            throw new SemanticAccessDeniedException("当前用户密级不足，无法访问模型 " + baseModel.getModelName());
        }

        String baseAlias = "m0";
        LinkedHashMap<String, String> modelAliases = new LinkedHashMap<>();
        modelAliases.put(normalizeKey(baseModel.getModelName()), baseAlias);

        Set<String> securityApplied = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        List<Object> bindings = new ArrayList<>();
        List<ResolvedJoin> resolvedJoins = resolveJoins(body.path("joins"), baseModel, modelAliases, userGate, warnings);

        Map<String, AnalyticsSemanticModel> modelMap = loadAllModelMap();
        Map<String, AnalyticsMetric> metricMap = loadMetricMap(modelMap.values());
        Map<String, AnalyticsField> dimensionMap = loadDimensionMap(modelMap.values());

        List<SelectedDimension> selectedDimensions = parseDimensions(body.path("dimensions"), dimensionMap, modelMap, userGate);
        List<SelectedMetric> selectedMetrics = parseMetrics(body.path("measures"), metricMap, modelMap, userGate);
        if (selectedDimensions.isEmpty() && selectedMetrics.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个维度或指标");
        }
        enforceFanoutSafety(resolvedJoins, selectedMetrics, selectedDimensions);

        List<String> predicates = new ArrayList<>();
        predicates.addAll(buildRowSecurityPredicates(modelAliases, modelMap, bindings, context, securityApplied));
        predicates.addAll(parseFilters(body.path("filters"), dimensionMap, modelAliases, bindings));

        List<String> innerSelects = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        List<ColumnMeta> baseColumns = new ArrayList<>();
        Map<String, String> aliasByRef = new LinkedHashMap<>();
        Set<String> usedAliases = new HashSet<>();

        for (SelectedDimension dimension : selectedDimensions) {
            String alias = uniqueAlias(aliasSeed(dimension.field().getName(), dimension.granularity()), usedAliases);
            String expr = compileDimensionExpression(dimension, modelAliases);
            innerSelects.add(expr + " AS " + alias);
            groupBy.add(expr);
            aliasByRef.put(normalizeKey(dimension.refId()), alias);
            baseColumns.add(new ColumnMeta(alias, defaultText(dimension.field().getDisplayName(), dimension.field().getName()), inferDimensionType(dimension.field()), null));
        }

        for (SelectedMetric metric : selectedMetrics) {
            String alias = uniqueAlias(aliasSeed(metric.metric().getName(), null), usedAliases);
            String expr = compileMetricExpression(metric, modelAliases);
            innerSelects.add(expr + " AS " + alias);
            aliasByRef.put(normalizeKey(metric.refId()), alias);
            baseColumns.add(new ColumnMeta(alias, metric.label(), "number", metric.format()));
        }

        List<String> joinClauses = resolvedJoins.stream().map(ResolvedJoin::sql).toList();

        StringBuilder innerSql = new StringBuilder();
        innerSql.append("SELECT ").append(String.join(", ", innerSelects));
        innerSql.append(" FROM ").append(tableRef(baseModel)).append(' ').append(baseAlias);
        if (!joinClauses.isEmpty()) {
            innerSql.append(' ').append(String.join(" ", joinClauses));
        }
        if (!predicates.isEmpty()) {
            innerSql.append(" WHERE ").append(String.join(" AND ", predicates));
        }
        if (!groupBy.isEmpty()) {
            innerSql.append(" GROUP BY ").append(String.join(", ", groupBy));
        }

        List<DerivedMetric> derivedMetrics = parseDerivedMetrics(body.path("derived_metrics"));
        List<ColumnMeta> finalColumns = new ArrayList<>(baseColumns);
        String sql = innerSql.toString();
        if (!derivedMetrics.isEmpty()) {
            List<String> outerSelects = new ArrayList<>();
            for (ColumnMeta column : baseColumns) {
                outerSelects.add(column.id());
            }
            Set<String> allowedWords = new HashSet<>(aliasByRef.values());
            Set<String> derivedAliases = new HashSet<>(aliasByRef.values());
            for (DerivedMetric derivedMetric : derivedMetrics) {
                String alias = uniqueAlias(aliasSeed(derivedMetric.id(), null), derivedAliases);
                String expr = compileDerivedExpression(derivedMetric.expression(), aliasByRef, allowedWords);
                outerSelects.add(expr + " AS " + alias);
                aliasByRef.put(normalizeKey(derivedMetric.id()), alias);
                allowedWords.add(alias);
                finalColumns.add(new ColumnMeta(alias, derivedMetric.label(), "number", null));
            }
            sql = "SELECT " + String.join(", ", outerSelects) + " FROM (" + sql + ") sq";
        }

        List<String> orderBy = parseOrderBy(body.path("order_by"), aliasByRef);
        if (!orderBy.isEmpty()) {
            sql = sql + " ORDER BY " + String.join(", ", orderBy);
        }
        int limit = normalizeLimit(body.path("limit").asInt(200));
        sql = sql + " LIMIT " + limit;
        securityApplied.add("classification_filter");

        return new CompiledSemanticQuery(
            baseModel.getDatabaseId(),
            sql,
            bindings,
            finalColumns,
            List.copyOf(securityApplied),
            List.copyOf(warnings),
            new DatasetQueryService.DatasetConstraints(limit, 30, ZoneId.systemDefault().getId())
        );
    }

    private List<ResolvedJoin> resolveJoins(
        JsonNode joinsNode,
        AnalyticsSemanticModel baseModel,
        Map<String, String> modelAliases,
        LevelGate userGate,
        List<String> warnings
    ) {
        if (joinsNode == null || !joinsNode.isArray() || joinsNode.isEmpty()) {
            return List.of();
        }
        List<AnalyticsSemanticJoin> allEdges = semanticJoinRepository.findAll();
        List<ResolvedJoin> result = new ArrayList<>();
        int aliasSeq = 1;
        for (JsonNode joinNode : joinsNode) {
            String to = joinNode == null ? null : trimToNull(joinNode.path("to").asText(null));
            if (to == null) {
                throw new IllegalArgumentException("join.to 不能为空");
            }
            AnalyticsSemanticModel toModel = requireSemanticModel(to);
            if (!userGate.allows(toModel.getSecurityLevel())) {
                throw new SemanticAccessDeniedException("当前用户密级不足，无法访问模型 " + toModel.getModelName());
            }
            AnalyticsSemanticJoin edge = findJoinEdge(modelAliases.keySet(), normalizeKey(toModel.getModelName()), allEdges);
            if (edge == null) {
                throw new IllegalArgumentException("Join 不在白名单内: " + to);
            }
            if ("many_to_many".equalsIgnoreCase(trimToNull(edge.getJoinType()))) {
                throw new IllegalArgumentException("当前版本不支持 many_to_many join: " + edge.getFromModel() + " -> " + edge.getToModel());
            }
            String alias = "m" + aliasSeq++;
            modelAliases.put(normalizeKey(toModel.getModelName()), alias);
            String fromAlias = modelAliases.get(normalizeKey(edge.getFromModel()));
            if (fromAlias == null) {
                throw new IllegalArgumentException("Join 起点未加入图中: " + edge.getFromModel());
            }
            String relationship = defaultText(trimToNull(edge.getRelationship()), "left").toUpperCase(Locale.ROOT);
            if (!"LEFT".equals(relationship) && !"INNER".equals(relationship)) {
                relationship = "LEFT";
            }
            String renderedOn = renderJoinOnClause(edge.getOnClause(), fromAlias, alias);
            if (edge.isApprovalRequired()) {
                warnings.add("Join " + edge.getFromModel() + " -> " + edge.getToModel() + " 标记为需要审批，请在正式发布前补审批记录。");
            }
            result.add(new ResolvedJoin(edge, relationship + " JOIN " + tableRef(toModel) + " " + alias + " ON " + renderedOn));
        }
        return result;
    }

    private AnalyticsSemanticJoin findJoinEdge(Collection<String> availableModels, String toModelKey, List<AnalyticsSemanticJoin> allEdges) {
        for (AnalyticsSemanticJoin edge : allEdges) {
            if (!availableModels.contains(normalizeKey(edge.getFromModel()))) {
                continue;
            }
            if (normalizeKey(edge.getToModel()).equals(toModelKey)) {
                return edge;
            }
        }
        return null;
    }

    private void enforceFanoutSafety(List<ResolvedJoin> joins, List<SelectedMetric> metrics, List<SelectedDimension> dimensions) {
        if (joins.isEmpty() || metrics.isEmpty()) {
            return;
        }
        Set<String> dimensionModels = dimensions.stream().map(item -> normalizeKey(item.model().getModelName())).collect(LinkedHashSet::new, Set::add, Set::addAll);
        for (ResolvedJoin join : joins) {
            String joinType = trimToNull(join.edge().getJoinType());
            if (!"one_to_many".equalsIgnoreCase(joinType) && !"many_to_many".equalsIgnoreCase(joinType)) {
                continue;
            }
            if (dimensionModels.contains(normalizeKey(join.edge().getToModel()))) {
                throw new IllegalArgumentException(
                    "Join " + join.edge().getFromModel() + " -> " + join.edge().getToModel() + " 会导致 fanout，当前版本拒绝该查询。"
                );
            }
        }
    }

    private List<String> buildRowSecurityPredicates(
        Map<String, String> modelAliases,
        Map<String, AnalyticsSemanticModel> modelMap,
        List<Object> bindings,
        PlatformContext context,
        Set<String> securityApplied
    ) {
        List<String> predicates = new ArrayList<>();
        for (Map.Entry<String, String> entry : modelAliases.entrySet()) {
            AnalyticsSemanticModel model = modelMap.get(entry.getKey());
            if (model == null) {
                continue;
            }
            String predicate = trimToNull(model.getRowSecurityPredicate());
            if (predicate == null) {
                continue;
            }
            predicates.add(renderUserPredicate(predicate, bindings, context));
            securityApplied.add("row_level");
        }
        return predicates;
    }

    private List<SelectedMetric> parseMetrics(
        JsonNode measuresNode,
        Map<String, AnalyticsMetric> metricMap,
        Map<String, AnalyticsSemanticModel> modelMap,
        LevelGate userGate
    ) {
        if (measuresNode == null || !measuresNode.isArray()) {
            return List.of();
        }
        List<SelectedMetric> result = new ArrayList<>();
        for (JsonNode measureNode : measuresNode) {
            String id = trimToNull(measureNode != null ? measureNode.asText(null) : null);
            if (id == null) {
                continue;
            }
            AnalyticsMetric metric = metricMap.get(normalizeKey(id));
            if (metric == null) {
                throw new IllegalArgumentException("指标不存在: " + id);
            }
            AnalyticsSemanticModel model = modelMap.get(normalizeKey(modelNameFromRef(id)));
            if (model == null) {
                throw new IllegalArgumentException("指标所属模型不存在: " + id);
            }
            String level = safeMetricSecurityLevel(metric, model);
            if (!userGate.allows(level)) {
                throw new SemanticAccessDeniedException("当前用户密级不足，无法访问指标 " + id);
            }
            result.add(new SelectedMetric(id, model, metric, defaultText(metric.getDisplayName(), metric.getName()), safeReadJsonMap(metric.getMetricJson()).get("format")));
        }
        return result;
    }

    private List<SelectedDimension> parseDimensions(
        JsonNode dimensionsNode,
        Map<String, AnalyticsField> dimensionMap,
        Map<String, AnalyticsSemanticModel> modelMap,
        LevelGate userGate
    ) {
        if (dimensionsNode == null || !dimensionsNode.isArray()) {
            return List.of();
        }
        List<SelectedDimension> result = new ArrayList<>();
        for (JsonNode item : dimensionsNode) {
            String id;
            String granularity = null;
            if (item != null && item.isObject()) {
                id = trimToNull(item.path("id").asText(null));
                granularity = trimToNull(item.path("granularity").asText(null));
            } else {
                id = trimToNull(item != null ? item.asText(null) : null);
            }
            if (id == null) {
                continue;
            }
            AnalyticsField field = dimensionMap.get(normalizeKey(id));
            if (field == null) {
                throw new IllegalArgumentException("维度不存在: " + id);
            }
            AnalyticsSemanticModel model = modelMap.get(normalizeKey(modelNameFromRef(id)));
            if (model == null) {
                throw new IllegalArgumentException("维度所属模型不存在: " + id);
            }
            if (!userGate.allows(model.getSecurityLevel())) {
                throw new SemanticAccessDeniedException("当前用户密级不足，无法访问维度 " + id);
            }
            result.add(new SelectedDimension(id, granularity, model, field));
        }
        return result;
    }

    private List<DerivedMetric> parseDerivedMetrics(JsonNode derivedNode) {
        if (derivedNode == null || !derivedNode.isArray()) {
            return List.of();
        }
        List<DerivedMetric> result = new ArrayList<>();
        for (JsonNode item : derivedNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String id = trimToNull(item.path("id").asText(null));
            String label = trimToNull(item.path("label").asText(null));
            String expression = trimToNull(item.path("expression").asText(null));
            if (id == null || expression == null) {
                continue;
            }
            result.add(new DerivedMetric(id, defaultText(label, id), expression));
        }
        return result;
    }

    private List<String> parseFilters(
        JsonNode filtersNode,
        Map<String, AnalyticsField> dimensionMap,
        Map<String, String> modelAliases,
        List<Object> bindings
    ) {
        if (filtersNode == null || !filtersNode.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : filtersNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String fieldId = trimToNull(item.path("field").asText(null));
            String op = normalizeFilterOp(item.path("op").asText(null));
            if (fieldId == null || op == null) {
                throw new IllegalArgumentException("filter.field / filter.op 不能为空");
            }
            if (!ALLOWED_FILTER_OPS.contains(op)) {
                throw new IllegalArgumentException("不支持的筛选操作: " + op);
            }
            AnalyticsField field = dimensionMap.get(normalizeKey(fieldId));
            if (field == null) {
                throw new IllegalArgumentException("筛选字段不存在: " + fieldId);
            }
            String modelAlias = modelAliases.get(normalizeKey(modelNameFromRef(fieldId)));
            if (modelAlias == null) {
                throw new IllegalArgumentException("筛选字段所属模型未加入查询图: " + fieldId);
            }
            String column = modelAlias + "." + safeIdentifier(field.getName());
            switch (op) {
                case "is_null" -> result.add(column + " IS NULL");
                case "is_not_null" -> result.add(column + " IS NOT NULL");
                case "between" -> {
                    JsonNode valueNode = item.get("value");
                    if (valueNode == null || !valueNode.isArray() || valueNode.size() != 2) {
                        throw new IllegalArgumentException("between 需要两个值");
                    }
                    result.add(column + " BETWEEN ? AND ?");
                    bindings.add(readScalarValue(valueNode.get(0)));
                    bindings.add(readScalarValue(valueNode.get(1)));
                }
                case "in", "not_in" -> {
                    JsonNode valueNode = item.get("value");
                    if (valueNode == null || !valueNode.isArray() || valueNode.isEmpty()) {
                        throw new IllegalArgumentException(op + " 需要非空数组");
                    }
                    List<String> placeholders = new ArrayList<>();
                    for (JsonNode value : valueNode) {
                        placeholders.add("?");
                        bindings.add(readScalarValue(value));
                    }
                    result.add(column + ("in".equals(op) ? " IN (" : " NOT IN (") + String.join(", ", placeholders) + ")");
                }
                case "like" -> {
                    result.add(column + " LIKE ?");
                    bindings.add(readScalarValue(item.get("value")));
                }
                default -> {
                    result.add(column + " " + normalizeSqlOperator(op) + " ?");
                    bindings.add(readScalarValue(item.get("value")));
                }
            }
        }
        return result;
    }

    private List<String> parseOrderBy(JsonNode orderByNode, Map<String, String> aliasByRef) {
        if (orderByNode == null || !orderByNode.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : orderByNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String field = trimToNull(item.path("field").asText(null));
            if (field == null) {
                continue;
            }
            String alias = aliasByRef.get(normalizeKey(field));
            if (alias == null && IDENTIFIER_PATTERN.matcher(field).matches()) {
                alias = field;
            }
            if (alias == null) {
                throw new IllegalArgumentException("order_by 字段不存在: " + field);
            }
            String direction = "desc".equalsIgnoreCase(trimToNull(item.path("direction").asText(null))) ? "DESC" : "ASC";
            result.add(alias + " " + direction);
        }
        return result;
    }

    private String compileDimensionExpression(SelectedDimension dimension, Map<String, String> modelAliases) {
        String alias = modelAliases.get(normalizeKey(dimension.model().getModelName()));
        String column = alias + "." + safeIdentifier(dimension.field().getName());
        if (!StringUtils.hasText(dimension.granularity()) || !"time".equalsIgnoreCase(inferDimensionType(dimension.field()))) {
            return column;
        }
        return switch (dimension.granularity().toLowerCase(Locale.ROOT)) {
            case "day" -> "CAST(" + column + " AS DATE)";
            case "week", "month", "quarter", "year" -> "DATE_TRUNC('" + dimension.granularity().toLowerCase(Locale.ROOT) + "', " + column + ")";
            default -> column;
        };
    }

    private String compileMetricExpression(SelectedMetric metric, Map<String, String> modelAliases) {
        String alias = modelAliases.get(normalizeKey(metric.model().getModelName()));
        String field = safeIdentifier(defaultText(metric.metric().getExpressionField(), metric.metric().getName()));
        String aggregation = normalizeMetricType(metric.metric().getAggregation());
        String column = alias + "." + field;
        return switch (aggregation) {
            case "count" -> "COUNT(*)";
            case "count_distinct" -> "COUNT(DISTINCT " + column + ")";
            case "avg" -> "AVG(" + column + ")";
            case "min" -> "MIN(" + column + ")";
            case "max" -> "MAX(" + column + ")";
            default -> "SUM(" + column + ")";
        };
    }

    private String compileDerivedExpression(String expression, Map<String, String> aliasByRef, Set<String> allowedWords) {
        String normalized = trimToNull(expression);
        if (normalized == null) {
            throw new IllegalArgumentException("派生指标表达式不能为空");
        }
        Matcher matcher = BRACKET_REF_PATTERN.matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String ref = normalizeKey(matcher.group(1));
            String alias = aliasByRef.get(ref);
            if (alias == null) {
                throw new IllegalArgumentException("派生指标引用不存在: [" + matcher.group(1) + "]");
            }
            matcher.appendReplacement(buffer, alias);
        }
        matcher.appendTail(buffer);
        String rendered = buffer.toString();
        if (rendered.contains(";")) {
            throw new IllegalArgumentException("派生指标表达式不允许包含分号");
        }
        Matcher wordMatcher = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\b").matcher(rendered);
        while (wordMatcher.find()) {
            String word = wordMatcher.group(1);
            String upper = word.toUpperCase(Locale.ROOT);
            if (!allowedWords.contains(word) && !ALLOWED_DERIVED_FUNCTIONS.contains(upper)) {
                throw new IllegalArgumentException("派生指标表达式包含未授权标识符: " + word);
            }
        }
        return rendered;
    }

    private static final Set<String> JOIN_CLAUSE_ALLOWED_KEYWORDS = Set.of(
        "AND", "OR", "NOT", "IS", "NULL", "TRUE", "FALSE", "ON"
    );
    /**
     * Tokens permitted between {{this}}/{{to}} substitutions in a Join on_clause.
     * <p>Allowed:
     * <ul>
     *   <li>identifiers matching {@link #IDENTIFIER_PATTERN}</li>
     *   <li>operators {@code = != <> < <= > >= ( ) , .}</li>
     *   <li>boolean keywords above</li>
     * </ul>
     * Explicitly rejected: comments ({@code -- /* *\/}), semicolons, quotes, function calls,
     * subqueries, and any token that would let a malicious dbt schema.yml inject SQL.
     */
    private static final Pattern JOIN_CLAUSE_TOKEN_PATTERN = Pattern.compile(
        "\\s+|[A-Za-z_][A-Za-z0-9_]*|[=().,]|<>|!=|<=|>=|<|>|\\b\\d+\\b"
    );

    private String renderJoinOnClause(String template, String fromAlias, String toAlias) {
        String raw = defaultText(template, "");
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Join on_clause 不能为空");
        }
        // Reject obviously dangerous constructs before substitution so that an attacker
        // cannot smuggle them via a single schema.yml change. The dbt contract is that
        // on_clause is a simple equality predicate using {{this}} and {{to}} placeholders.
        if (raw.contains(";") || raw.contains("--") || raw.contains("/*") || raw.contains("*/")
            || raw.contains("'") || raw.contains("\"") || raw.contains("`")) {
            throw new IllegalArgumentException(
                "Join on_clause 含有未授权字符（;/注释/引号）: " + raw
            );
        }
        String rendered = raw.replace("{{this}}", fromAlias).replace("{{to}}", toAlias);
        // Tokenize and enforce a strict whitelist. Anything that does not parse as a
        // recognized token (e.g. a function call like NOW(), or an unexpected keyword
        // like SELECT/UNION/INSERT) blows up the request rather than reaching the DB.
        Matcher tokenMatcher = JOIN_CLAUSE_TOKEN_PATTERN.matcher(rendered);
        int cursor = 0;
        while (tokenMatcher.find()) {
            if (tokenMatcher.start() != cursor) {
                String stray = rendered.substring(cursor, tokenMatcher.start());
                throw new IllegalArgumentException("Join on_clause 含有未授权字符: " + stray);
            }
            cursor = tokenMatcher.end();
            String token = tokenMatcher.group().trim();
            if (token.isEmpty()) continue;
            if (IDENTIFIER_PATTERN.matcher(token).matches()) {
                String upper = token.toUpperCase(Locale.ROOT);
                // Reject reserved words that have no business in a join predicate.
                if (!JOIN_CLAUSE_ALLOWED_KEYWORDS.contains(upper)
                    && (upper.equals("SELECT") || upper.equals("UNION") || upper.equals("INSERT")
                        || upper.equals("UPDATE") || upper.equals("DELETE") || upper.equals("DROP")
                        || upper.equals("EXEC") || upper.equals("CALL") || upper.equals("WHERE")
                        || upper.equals("FROM") || upper.equals("JOIN") || upper.equals("CASE"))) {
                    throw new IllegalArgumentException("Join on_clause 不允许关键字: " + token);
                }
            }
        }
        if (cursor != rendered.length()) {
            throw new IllegalArgumentException(
                "Join on_clause 含有未授权字符: " + rendered.substring(cursor)
            );
        }
        return rendered;
    }

    private String renderUserPredicate(String predicate, List<Object> bindings, PlatformContext context) {
        Matcher matcher = USER_TOKEN_PATTERN.matcher(predicate);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = switch (key) {
                case "dept", "dept_id" -> defaultText(context != null ? context.dept() : null, "");
                case "classification" -> defaultText(context != null ? context.classification() : null, "");
                default -> "";
            };
            bindings.add(value);
            matcher.appendReplacement(buffer, "?");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private JsonNode buildCacheBody(JsonNode body, long databaseId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("database", databaseId);
        payload.put("type", "semantic");
        payload.put("query", body);
        return objectMapper.valueToTree(payload);
    }

    private Map<String, AnalyticsSemanticModel> loadAllModelMap() {
        Map<String, AnalyticsSemanticModel> map = new LinkedHashMap<>();
        for (AnalyticsSemanticModel model : semanticModelRepository.findAll()) {
            map.put(normalizeKey(model.getModelName()), model);
        }
        return map;
    }

    private Map<String, AnalyticsMetric> loadMetricMap(Collection<AnalyticsSemanticModel> models) {
        Map<Long, String> modelByTableId = new HashMap<>();
        for (AnalyticsSemanticModel model : models) {
            if (model.getTableId() != null) {
                modelByTableId.put(model.getTableId(), model.getModelName());
            }
        }
        Map<String, AnalyticsMetric> map = new LinkedHashMap<>();
        for (AnalyticsMetric metric : metricRepository.findAllByArchivedFalseOrderByIdAsc()) {
            if (metric.getBaseTableId() == null) {
                continue;
            }
            String modelName = modelByTableId.get(metric.getBaseTableId());
            if (!StringUtils.hasText(modelName)) {
                continue;
            }
            map.put(normalizeKey(metricId(modelName, metric.getName())), metric);
        }
        return map;
    }

    private Map<String, AnalyticsField> loadDimensionMap(Collection<AnalyticsSemanticModel> models) {
        Map<Long, String> modelByTableId = new HashMap<>();
        for (AnalyticsSemanticModel model : models) {
            if (model.getTableId() != null) {
                modelByTableId.put(model.getTableId(), model.getModelName());
            }
        }
        Map<String, AnalyticsField> map = new LinkedHashMap<>();
        for (AnalyticsField field : fieldRepository.findAllByActiveTrueOrderByTableIdAscPositionAscIdAsc()) {
            if ("measure".equalsIgnoreCase(trimToNull(field.getFieldRole()))) {
                continue;
            }
            String modelName = modelByTableId.get(field.getTableId());
            if (!StringUtils.hasText(modelName)) {
                continue;
            }
            map.put(normalizeKey(dimensionId(modelName, field.getName())), field);
        }
        return map;
    }

    private AnalyticsSemanticModel requireSemanticModel(String modelName) {
        return semanticModelRepository.findByModelNameIgnoreCase(modelName)
            .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + modelName));
    }

    private String tableRef(AnalyticsSemanticModel model) {
        String tableName = safeIdentifier(defaultText(model.getTableName(), model.getModelName()));
        String schemaName = trimToNull(model.getSchemaName());
        return StringUtils.hasText(schemaName) ? safeIdentifier(schemaName) + "." + tableName : tableName;
    }

    private String safeIdentifier(String identifier) {
        String trimmed = trimToNull(identifier);
        if (trimmed == null || !IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("非法标识符: " + identifier);
        }
        return trimmed;
    }

    private String inferDimensionType(AnalyticsField field) {
        String semanticType = trimToNull(field.getSemanticType());
        if (semanticType != null && semanticType.toLowerCase(Locale.ROOT).contains("date")) {
            return "time";
        }
        String baseType = trimToNull(field.getBaseType());
        if (baseType != null && (baseType.contains("Date") || baseType.contains("Time"))) {
            return "time";
        }
        if (baseType != null && baseType.contains("Number")) {
            return "numeric";
        }
        if (baseType != null && baseType.contains("Boolean")) {
            return "boolean";
        }
        return "categorical";
    }

    private List<String> inferGranularities(AnalyticsField field) {
        return "time".equalsIgnoreCase(inferDimensionType(field)) ? List.of("day", "week", "month", "quarter", "year") : List.of();
    }

    private String normalizeMetricType(String aggregation) {
        String normalized = trimToNull(aggregation);
        if (normalized == null) {
            return "sum";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "count", "count_distinct", "avg", "min", "max" -> normalized;
            default -> "sum";
        };
    }

    private String normalizeFilterOp(String op) {
        String normalized = trimToNull(op);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return "not in".equals(normalized) ? "not_in" : normalized;
    }

    private String normalizeSqlOperator(String op) {
        return "!=".equals(op) ? "<>" : op;
    }

    private int normalizeLimit(int requested) {
        if (requested <= 0) {
            return 200;
        }
        return Math.min(requested, 5000);
    }

    private Object readScalarValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private String safeMetricSecurityLevel(AnalyticsMetric metric, AnalyticsSemanticModel model) {
        Map<String, Object> json = safeReadJsonMap(metric.getMetricJson());
        return normalizeSecurityLevel(defaultText(asText(json.get("security_level")), model.getSecurityLevel()));
    }

    private Map<String, Object> safeReadJsonMap(String text) {
        String normalized = trimToNull(text);
        if (normalized == null) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {});
            return value != null ? value : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private JsonNode safeReadTree(String text) {
        String normalized = trimToNull(text);
        if (normalized == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String safeMetaText(String metaJson, String field, String fallback) {
        return defaultText(asText(safeReadJsonMap(metaJson).get(field)), fallback);
    }

    private List<Map<String, Object>> toMetabaseCols(List<ColumnMeta> columns) {
        return columns.stream().map(column -> {
            String baseType = toMetabaseBaseType(column.type());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("name", column.id());
            response.put("display_name", defaultText(column.label(), column.id()));
            response.put("base_type", baseType);
            response.put("effective_type", baseType);
            response.put("semantic_type", column.type());
            response.put("source", "semantic");
            if (column.format() != null) {
                response.put("format", column.format());
            }
            return response;
        }).toList();
    }

    private List<Map<String, Object>> toMetabaseResultsMetadata(List<ColumnMeta> columns) {
        return columns.stream().map(column -> {
            String baseType = toMetabaseBaseType(column.type());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("name", column.id());
            response.put("display_name", defaultText(column.label(), column.id()));
            response.put("base_type", baseType);
            response.put("effective_type", baseType);
            response.put("semantic_type", column.type());
            response.put("field_ref", List.of("field", column.id(), Map.of("base-type", baseType)));
            if (column.format() != null) {
                response.put("format", column.format());
            }
            return response;
        }).toList();
    }

    private String toMetabaseBaseType(String semanticType) {
        String normalized = trimToNull(semanticType);
        if (normalized == null) {
            return "type/Text";
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "number", "integer", "decimal", "float" -> "type/Float";
            case "date" -> "type/Date";
            case "datetime", "timestamp", "time" -> "type/DateTime";
            case "boolean" -> "type/Boolean";
            default -> "type/Text";
        };
    }

    /**
     * Normalize to a canonical bare code via {@link SecurityLevelCatalog}. Behaviour is unchanged
     * (SENSITIVE and TOP_SECRET still fold onto SECRET and CONFIDENTIAL, blank and unrecognized
     * input still default to INTERNAL); the ladder is no longer duplicated here.
     */
    private String normalizeSecurityLevel(String level) {
        return SecurityLevelCatalog.normalizeDataCodeOrDefault(level);
    }

    private String metricId(String modelName, String metricName) {
        return modelName + "." + metricName;
    }

    private String dimensionId(String modelName, String fieldName) {
        return modelName + "." + fieldName;
    }

    private String modelNameFromRef(String ref) {
        int dot = ref.indexOf('.');
        return dot > 0 ? ref.substring(0, dot) : ref;
    }

    private String aliasSeed(String value, String suffix) {
        String base = normalizeKey(defaultText(value, "col")).replace('.', '_');
        return suffix != null ? base + "_" + normalizeKey(suffix) : base;
    }

    private String uniqueAlias(String seed, Set<String> usedAliases) {
        String base = safeIdentifier(seed.replace('-', '_'));
        String alias = base;
        int seq = 2;
        while (!usedAliases.add(alias)) {
            alias = base + "_" + seq++;
        }
        return alias;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private record LevelGate(String maxLevel) {
        static LevelGate of(String maxLevel) {
            return new LevelGate(normalize(maxLevel));
        }

        boolean allows(String level) {
            if (maxLevel == null) {
                return true;
            }
            return rank(normalize(level)) <= rank(maxLevel);
        }

        private static String normalize(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            // SENSITIVE/TOP_SECRET fold onto SECRET/CONFIDENTIAL inside the catalog now.
            // Unrecognized values are still passed through upper-cased so rank() keeps its
            // existing INTERNAL fallback.
            String canonical = SecurityLevelCatalog.normalizeDataCode(value);
            return canonical != null ? canonical : value.trim().toUpperCase(Locale.ROOT);
        }

        private static int rank(String value) {
            if (value == null) {
                return SECURITY_LEVELS.size();
            }
            int index = SECURITY_LEVELS.indexOf(value);
            return index >= 0 ? index : SECURITY_LEVELS.indexOf("INTERNAL");
        }
    }

    private record SelectedMetric(String refId, AnalyticsSemanticModel model, AnalyticsMetric metric, String label, Object format) {}

    private record SelectedDimension(String refId, String granularity, AnalyticsSemanticModel model, AnalyticsField field) {}

    private record DerivedMetric(String id, String label, String expression) {}

    private record ResolvedJoin(AnalyticsSemanticJoin edge, String sql) {}

    private record ColumnMeta(String id, String label, String type, Object format) {
        Map<String, Object> toMap() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", id);
            response.put("label", label);
            response.put("type", type);
            response.put("format", format);
            return response;
        }
    }

    private record CompiledSemanticQuery(
        long databaseId,
        String sql,
        List<Object> bindings,
        List<ColumnMeta> columns,
        List<String> securityApplied,
        List<String> warnings,
        DatasetQueryService.DatasetConstraints constraints
    ) {}

    public record SemanticExecutionResult(
        long databaseId,
        String sqlPreview,
        DatasetQueryService.DatasetResult datasetResult,
        List<String> securityApplied,
        List<String> warnings,
        boolean cacheHit,
        long elapsedMs
    ) {}

    public static class SemanticAccessDeniedException extends RuntimeException {
        public SemanticAccessDeniedException(String message) {
            super(message);
        }
    }
}
