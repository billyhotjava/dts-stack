package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.config.DbtProperties;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtModelDiagnosticsService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtModelDiagnosticsService.class);

    private final ObjectMapper objectMapper;
    private final DbtProperties dbtProperties;
    private final DbtConfigService configService;
    private final DbtTargetConnectionFactory connectionFactory;
    private final DbtRunResultService dbtRunResultService;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;

    public DbtModelDiagnosticsService(
        ObjectMapper objectMapper,
        DbtProperties dbtProperties,
        DbtConfigService configService,
        DbtTargetConnectionFactory connectionFactory,
        DbtRunResultService dbtRunResultService,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties
    ) {
        this.objectMapper = objectMapper;
        this.dbtProperties = dbtProperties;
        this.configService = configService;
        this.connectionFactory = connectionFactory;
        this.dbtRunResultService = dbtRunResultService;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
    }

    public ModelDiagnostics diagnose(String modelName) {
        if (!dbtProperties.isEnabled()) {
            return ModelDiagnostics.disabled(modelName, "dbt 未启用");
        }
        if (!StringUtils.hasText(modelName)) {
            return ModelDiagnostics.error(modelName, "model 不能为空");
        }
        Path manifestPath = resolveManifestPath();
        if (manifestPath == null) {
            return ModelDiagnostics.error(modelName, "dbt 项目目录未配置");
        }
        File manifestFile = manifestPath.toFile();
        if (!manifestFile.exists()) {
            return ModelDiagnostics.error(modelName, "manifest.json 不存在，请先执行 dbt run/docs");
        }
        try {
            ManifestBundle bundle = readManifest(manifestFile);
            ManifestNode modelNode = bundle.findModel(modelName);
            if (modelNode == null) {
                return ModelDiagnostics.error(modelName, "模型 '" + modelName + "' 在 manifest 中不存在");
            }

            List<DependencyNode> upstreams = resolveUpstreams(bundle, modelNode);
            DbtTargetConnectionFactory.TargetWarehouse target = connectionFactory.resolveTarget();
            RelationStats currentStats;
            List<DependencyDiagnostic> upstreamDiagnostics = new ArrayList<>();
            try (Connection connection = connectionFactory.open(target)) {
                currentStats = inspectRelation(connection, modelNode);
                for (DependencyNode dependencyNode : upstreams) {
                    upstreamDiagnostics.add(new DependencyDiagnostic(dependencyNode, inspectRelation(connection, dependencyNode.node())));
                }
            }

            RuntimeDiagnostics runtime = buildRuntimeDiagnostics(modelNode);
            List<String> findings = buildFindings(modelNode, currentStats, upstreamDiagnostics, runtime);
            List<String> recommendedQueries = buildRecommendedQueries(modelNode, upstreamDiagnostics);

            return new ModelDiagnostics(
                true,
                true,
                modelNode.name(),
                modelNode.uniqueId(),
                modelNode.resourceType(),
                modelNode.path(),
                relationName(modelNode),
                currentStats,
                upstreamDiagnostics,
                runtime,
                findings,
                recommendedQueries,
                null
            );
        } catch (Exception ex) {
            LOG.warn("Failed to diagnose dbt model {}: {}", modelName, ex.getMessage());
            return ModelDiagnostics.error(modelName, "诊断失败: " + ex.getMessage());
        }
    }

    private Path resolveManifestPath() {
        String projectDir = configService.loadConfig().config() != null
            ? configService.loadConfig().config().projectDir()
            : dbtProperties.getProjectDir();
        if (!StringUtils.hasText(projectDir)) {
            return null;
        }
        return Path.of(projectDir, "target", "manifest.json");
    }

    private ManifestBundle readManifest(File manifestFile) throws Exception {
        Map<String, Object> raw = objectMapper.readValue(manifestFile, new TypeReference<>() {});
        return new ManifestBundle(asNodeMap(raw.get("nodes")), asNodeMap(raw.get("sources")));
    }

    private Map<String, ManifestNode> asNodeMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, ManifestNode> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> nodeMap)) {
                continue;
            }
            ManifestNode node = toManifestNode(String.valueOf(entry.getKey()), nodeMap);
            if (node != null) {
                result.put(node.uniqueId(), node);
            }
        }
        return result;
    }

    private ManifestNode toManifestNode(String uniqueId, Map<?, ?> nodeMap) {
        String name = text(nodeMap.get("name"));
        String resourceType = text(nodeMap.get("resource_type"));
        if (!StringUtils.hasText(name) || !StringUtils.hasText(resourceType)) {
            return null;
        }
        String schema = firstNonBlank(text(nodeMap.get("schema")), text(nodeMap.get("source_schema")));
        String alias = firstNonBlank(text(nodeMap.get("alias")), text(nodeMap.get("identifier")), name);
        String path = text(nodeMap.get("path"));
        Set<String> dependsOn = new LinkedHashSet<>();
        Object dependsObj = nodeMap.get("depends_on");
        if (dependsObj instanceof Map<?, ?> dependsMap) {
            Object nodesObj = dependsMap.get("nodes");
            if (nodesObj instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    String dep = text(item);
                    if (StringUtils.hasText(dep)) {
                        dependsOn.add(dep);
                    }
                }
            }
        }
        return new ManifestNode(uniqueId, name, resourceType, schema, alias, path, dependsOn);
    }

    private List<DependencyNode> resolveUpstreams(ManifestBundle bundle, ManifestNode modelNode) {
        List<DependencyNode> results = new ArrayList<>();
        for (String depId : modelNode.dependsOn()) {
            ManifestNode dep = bundle.findByUniqueId(depId);
            if (dep == null) {
                continue;
            }
            results.add(new DependencyNode(dep.uniqueId(), dep.name(), dep.resourceType(), dep.path(), relationName(dep), dep));
        }
        results.sort(Comparator.comparing(DependencyNode::name));
        return results;
    }

    private RelationStats inspectRelation(Connection connection, ManifestNode node) {
        String schema = node.schema();
        String identifier = node.alias();
        String relation = relationName(node);
        try {
            if (!relationExists(connection, schema, identifier)) {
                return new RelationStats(schema, identifier, relation, false, null, null);
            }
            Long rowCount = queryRowCount(connection, relation);
            return new RelationStats(schema, identifier, relation, true, rowCount, null);
        } catch (Exception ex) {
            return new RelationStats(schema, identifier, relation, false, null, ex.getMessage());
        }
    }

    private boolean relationExists(Connection connection, String schema, String identifier) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, schema, identifier, new String[] { "TABLE", "VIEW" })) {
            return tables.next();
        }
    }

    private Long queryRowCount(Connection connection, String relation) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + relation)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        }
    }

    private RuntimeDiagnostics buildRuntimeDiagnostics(ManifestNode modelNode) {
        DbtRunResultService.DbtRunSummary latestRun = dbtRunResultService.loadLatestSummary(20);
        boolean relevantRun = isRelevantRunForModel(latestRun, modelNode);
        RuntimeRunInfo runInfo = new RuntimeRunInfo(
            relevantRun && latestRun.present(),
            relevantRun ? latestRun.invocationId() : null,
            relevantRun ? latestRun.status() : null,
            relevantRun ? latestRun.command() : null,
            relevantRun ? latestRun.generatedAt() : null
        );
        AirflowRunInfo airflowRun = resolveLatestAirflowRun(modelNode);
        return new RuntimeDiagnostics(runInfo, airflowRun);
    }

    private AirflowRunInfo resolveLatestAirflowRun(ManifestNode modelNode) {
        if (!airflowProperties.isEnabled() || !StringUtils.hasText(airflowProperties.getDagId())) {
            return new AirflowRunInfo(false, null, null, null, null, null);
        }
        Map<String, Object> payload = airflowClient.listDagRuns(airflowProperties.getDagId(), 5).orElse(Map.of());
        Object dagRunsObj = payload.get("dag_runs");
        if (!(dagRunsObj instanceof Iterable<?> dagRuns)) {
            return new AirflowRunInfo(false, null, null, null, null, null);
        }
        Map<String, Object> latest = null;
        for (Object item : dagRuns) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                if (!isRelevantDagRunForModel(cast, modelNode)) {
                    continue;
                }
                latest = cast;
                break;
            }
        }
        if (latest == null) {
            return new AirflowRunInfo(false, null, null, null, null, null);
        }
        String dagRunId = firstNonBlank(text(latest.get("dag_run_id")), text(latest.get("run_id")));
        String state = text(latest.get("state"));
        String logicalDate = firstNonBlank(text(latest.get("logical_date")), text(latest.get("execution_date")));
        String logSnippet = null;
        String taskId = null;
        if (StringUtils.hasText(dagRunId)) {
            JsonNode taskInstances = airflowClient.listTaskInstances(airflowProperties.getDagId(), dagRunId);
            taskId = resolvePreferredTaskId(taskInstances, modelNode.name());
            if (StringUtils.hasText(taskId)) {
                String log = airflowClient.getTaskInstanceLog(airflowProperties.getDagId(), dagRunId, taskId, 1);
                logSnippet = abbreviate(log, 600);
            }
        }
        return new AirflowRunInfo(true, dagRunId, state, logicalDate, taskId, logSnippet);
    }

    private boolean isRelevantRunForModel(DbtRunResultService.DbtRunSummary summary, ManifestNode modelNode) {
        if (summary == null || !summary.present() || modelNode == null) {
            return false;
        }
        if (commandTargetsModel(summary.command(), modelNode.name())) {
            return true;
        }
        if (summary.failures() != null) {
            for (DbtRunResultService.DbtRunFailure failure : summary.failures()) {
                if (modelNode.uniqueId().equals(failure.uniqueId()) || modelNode.name().equals(failure.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRelevantDagRunForModel(Map<String, Object> dagRun, ManifestNode modelNode) {
        if (dagRun == null || modelNode == null) {
            return false;
        }
        Object confObj = dagRun.get("conf");
        if (confObj instanceof Map<?, ?> confMap) {
            String models = text(confMap.get("models"));
            if (commandTargetsModel(models, modelNode.name())) {
                return true;
            }
        }
        return false;
    }

    private boolean commandTargetsModel(String commandOrSelector, String modelName) {
        String normalizedCommand = normalizeToken(commandOrSelector);
        String normalizedModel = normalizeToken(modelName);
        if (!StringUtils.hasText(normalizedCommand) || !StringUtils.hasText(normalizedModel)) {
            return false;
        }
        return normalizedCommand.contains(normalizedModel)
            || normalizedCommand.contains("model:" + normalizedModel)
            || normalizedCommand.contains("--select " + normalizedModel);
    }

    private String normalizeToken(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String resolvePreferredTaskId(JsonNode taskInstances, String modelName) {
        if (taskInstances == null || taskInstances.isMissingNode()) {
            return "dbt_run";
        }
        JsonNode items = taskInstances.path("task_instances");
        if (items.isArray()) {
            for (JsonNode item : items) {
                String candidate = text(item.path("task_id").asText(null));
                if ("dbt_run".equals(candidate)) {
                    return candidate;
                }
            }
            if (!items.isEmpty()) {
                return text(items.get(0).path("task_id").asText(null));
            }
        }
        return "dbt_run";
    }

    private List<String> buildFindings(
        ManifestNode modelNode,
        RelationStats currentStats,
        List<DependencyDiagnostic> upstreams,
        RuntimeDiagnostics runtime
    ) {
        List<String> findings = new ArrayList<>();
        long upstreamNonEmpty = upstreams.stream().filter(dep -> dep.stats().rowCount() != null && dep.stats().rowCount() > 0).count();
        if (Boolean.FALSE.equals(currentStats.exists())) {
            findings.add("当前模型产出 relation 不存在，可能尚未执行 dbt build/run 或构建失败");
        } else if (currentStats.rowCount() != null && currentStats.rowCount() == 0 && upstreamNonEmpty > 0) {
            findings.add("当前模型 0 行，但存在 " + upstreamNonEmpty + " 个非空上游，优先检查模型 WHERE 过滤、日期解析和聚合条件");
        } else if (currentStats.rowCount() != null && currentStats.rowCount() == 0 && upstreams.isEmpty()) {
            findings.add("当前模型无上游依赖，且结果为 0 行，优先检查源表是否为空或模型为静态空集");
        }
        if (runtime.dbtRun().present() && "ERROR".equalsIgnoreCase(runtime.dbtRun().status())) {
            findings.add("最近一次 dbt 运行状态为 ERROR，建议先查看运行日志");
        }
        if (runtime.airflowRun().present() && "failed".equalsIgnoreCase(runtime.airflowRun().state())) {
            findings.add("最近一次 Airflow DAG run 失败，可直接检查 task log");
        }
        if (modelNode.path() != null && modelNode.path().contains("/dws/") && currentStats.rowCount() != null && currentStats.rowCount() == 0) {
            findings.add("该模型位于 DWS 层，若 DWD 非空但 DWS 为空，重点检查时间维字段是否全部解析失败");
        }
        findings.addAll(buildModelSpecificFindings(modelNode, currentStats, upstreams));
        if (findings.isEmpty()) {
            findings.add("未发现明显断链信号，可继续查看上游 relation 行数和最近日志");
        }
        return findings;
    }

    private List<String> buildModelSpecificFindings(ManifestNode modelNode, RelationStats currentStats, List<DependencyDiagnostic> upstreams) {
        String modelName = modelNode.name();
        List<String> findings = new ArrayList<>();
        DependencyDiagnostic primaryUpstream = upstreams.isEmpty() ? null : upstreams.get(0);
        Long upstreamRowCount = primaryUpstream != null ? primaryUpstream.stats().rowCount() : null;
        boolean currentEmpty = currentStats.rowCount() != null && currentStats.rowCount() == 0;

        if ("biz_dws_progress_monthly_v2".equals(modelName) && currentEmpty && upstreamRowCount != null && upstreamRowCount > 0) {
            findings.add("PJM 进度月报依赖 biz_dwd_project_node_v2.plan_year；若 DWD 非空但 DWS 为空，通常是 plan_date 解析失败导致 plan_year 全空");
            findings.add("PJM 进度 DWD 还要求 project_no 和 plan_date 原始文本非空，需先检查 ODS 是否已在导入阶段被过滤");
        }
        if ("biz_dws_quality_monthly_v2".equals(modelName) && currentEmpty && upstreamRowCount != null && upstreamRowCount > 0) {
            findings.add("PJM 质量月报依赖 biz_dwd_quality_issue_v2.issue_year；若 DWD 非空但 DWS 为空，优先检查 issue_date 解析是否全部失败");
        }
        if ("biz_dws_tech_state_monthly_v2".equals(modelName) && currentEmpty && upstreamRowCount != null && upstreamRowCount > 0) {
            findings.add("PJM 技术状态月报依赖 biz_dwd_tech_state_v2.submit_year；若 DWD 非空但 DWS 为空，优先检查 change_submit_time 解析结果");
        }
        if ("biz_dws_risk_monthly_v2".equals(modelName) && currentEmpty && upstreamRowCount != null && upstreamRowCount > 0) {
            findings.add("PJM 风险月报依赖 biz_dwd_risk_info_v2.submit_year；若 DWD 非空但 DWS 为空，优先检查 risk_submit_time 解析结果");
        }
        if ("biz_dwd_project_node_v2".equals(modelName) && currentEmpty) {
            findings.add("PJM 进度 DWD 内置过滤 project_no 和 plan_date 原始文本非空，ODS 有脏值时会在进入 DWD 前被直接过滤");
        }
        if ("biz_dwd_quality_issue_v2".equals(modelName) && currentEmpty) {
            findings.add("PJM 质量 DWD 要求 project_no 非空；若 ODS 行数存在但 DWD 为空，优先检查 project_no 占位值和空白字符");
        }
        if ("biz_dwd_tech_state_v2".equals(modelName) && currentEmpty) {
            findings.add("PJM 技术状态 DWD 要求 project_no 非空；若后续 submit_year 全空，再检查 change_submit_time 的格式归一化");
        }
        if ("biz_dwd_risk_info_v2".equals(modelName) && currentEmpty) {
            findings.add("PJM 风险 DWD 要求 project_no 非空；若后续 submit_year 全空，再检查 risk_submit_time 的格式归一化");
        }
        return findings;
    }

    private List<String> buildRecommendedQueries(ManifestNode modelNode, List<DependencyDiagnostic> upstreams) {
        String modelName = modelNode.name();
        List<String> queries = new ArrayList<>();
        if ("biz_dws_progress_monthly_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS dwd_cnt, COUNT(*) FILTER (WHERE plan_year IS NOT NULL) AS dwd_plan_year_ok FROM biz_dwd_project_node_v2;");
            queries.add("SELECT project_no, plan_date, plan_year, actual_date, actual_year FROM biz_dwd_project_node_v2 WHERE plan_year IS NULL LIMIT 50;");
            queries.add("SELECT project_no, plan_date, actual_date, last_update_time FROM ods_project_subject_domain_v2 LIMIT 50;");
        } else if ("biz_dws_quality_monthly_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS dwd_cnt, COUNT(*) FILTER (WHERE issue_year IS NOT NULL) AS dwd_issue_year_ok FROM biz_dwd_quality_issue_v2;");
            queries.add("SELECT project_no, issue_name, issue_date, issue_year, zero_complete_date FROM biz_dwd_quality_issue_v2 WHERE issue_year IS NULL LIMIT 50;");
            queries.add("SELECT project_no, issue_name, issue_date, zero_complete_date FROM ods_quality_issue_v2 LIMIT 50;");
        } else if ("biz_dws_tech_state_monthly_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS dwd_cnt, COUNT(*) FILTER (WHERE submit_year IS NOT NULL) AS dwd_submit_year_ok FROM biz_dwd_tech_state_v2;");
            queries.add("SELECT project_no, tech_state_name, change_submit_time, submit_year, submit_month FROM biz_dwd_tech_state_v2 WHERE submit_year IS NULL LIMIT 50;");
            queries.add("SELECT project_no, tech_state_name, change_submit_time, file_signature_date FROM ods_tech_state_v2 LIMIT 50;");
        } else if ("biz_dws_risk_monthly_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS dwd_cnt, COUNT(*) FILTER (WHERE submit_year IS NOT NULL) AS dwd_submit_year_ok FROM biz_dwd_risk_info_v2;");
            queries.add("SELECT project_no, risk_name, risk_submit_date, submit_year, submit_month FROM biz_dwd_risk_info_v2 WHERE submit_year IS NULL LIMIT 50;");
            queries.add("SELECT project_no, risk_name, risk_submit_time, final_release_time FROM ods_risk_info_v2 LIMIT 50;");
        } else if ("biz_dwd_project_node_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS ods_cnt, COUNT(*) FILTER (WHERE btrim(COALESCE(project_no, '')) != '' AND btrim(COALESCE(plan_date, '')) != '') AS ods_pass_filter_cnt FROM ods_project_subject_domain_v2;");
            queries.add("SELECT project_no, subsystem, node_task, plan_date, actual_date FROM ods_project_subject_domain_v2 WHERE btrim(COALESCE(project_no, '')) = '' OR btrim(COALESCE(plan_date, '')) = '' LIMIT 50;");
        } else if ("biz_dwd_quality_issue_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS ods_cnt, COUNT(*) FILTER (WHERE btrim(COALESCE(project_no, '')) != '') AS ods_pass_filter_cnt FROM ods_quality_issue_v2;");
            queries.add("SELECT project_no, issue_name, issue_date, dept FROM ods_quality_issue_v2 WHERE btrim(COALESCE(project_no, '')) = '' LIMIT 50;");
        } else if ("biz_dwd_tech_state_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS ods_cnt, COUNT(*) FILTER (WHERE btrim(COALESCE(project_no, '')) != '') AS ods_pass_filter_cnt FROM ods_tech_state_v2;");
            queries.add("SELECT project_no, tech_state_name, change_submit_time, dept FROM ods_tech_state_v2 WHERE btrim(COALESCE(project_no, '')) = '' LIMIT 50;");
        } else if ("biz_dwd_risk_info_v2".equals(modelName)) {
            queries.add("SELECT COUNT(*) AS ods_cnt, COUNT(*) FILTER (WHERE btrim(COALESCE(project_no, '')) != '') AS ods_pass_filter_cnt FROM ods_risk_info_v2;");
            queries.add("SELECT project_no, risk_name, risk_submit_time, dept FROM ods_risk_info_v2 WHERE btrim(COALESCE(project_no, '')) = '' LIMIT 50;");
        }

        if (queries.isEmpty() && !upstreams.isEmpty()) {
            for (DependencyDiagnostic upstream : upstreams) {
                queries.add("SELECT COUNT(*) FROM " + upstream.stats().relationName() + ";");
            }
        }
        if (queries.isEmpty()) {
            queries.add("SELECT COUNT(*) FROM " + relationName(modelNode) + ";");
        }
        return queries;
    }

    private String relationName(ManifestNode node) {
        if (StringUtils.hasText(node.schema())) {
            return quote(node.schema()) + "." + quote(node.alias());
        }
        return quote(node.alias());
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "") + "\"";
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
    }

    private record ManifestBundle(Map<String, ManifestNode> nodes, Map<String, ManifestNode> sources) {
        ManifestNode findModel(String modelName) {
            return nodes.values()
                .stream()
                .filter(node -> "model".equalsIgnoreCase(node.resourceType()))
                .filter(node -> modelName.equals(node.name()) || modelName.equals(node.uniqueId()))
                .findFirst()
                .orElse(null);
        }

        ManifestNode findByUniqueId(String uniqueId) {
            ManifestNode node = nodes.get(uniqueId);
            if (node != null) {
                return node;
            }
            return sources.get(uniqueId);
        }
    }

    private record ManifestNode(
        String uniqueId,
        String name,
        String resourceType,
        String schema,
        String alias,
        String path,
        Set<String> dependsOn
    ) {}

    public record RelationStats(
        String schema,
        String identifier,
        String relationName,
        boolean exists,
        Long rowCount,
        String error
    ) {}

    public record DependencyNode(
        String uniqueId,
        String name,
        String resourceType,
        String path,
        String relationName,
        ManifestNode node
    ) {}

    public record DependencyDiagnostic(DependencyNode dependency, RelationStats stats) {}

    public record RuntimeRunInfo(
        boolean present,
        String invocationId,
        String status,
        String command,
        String generatedAt
    ) {}

    public record AirflowRunInfo(
        boolean present,
        String dagRunId,
        String state,
        String logicalDate,
        String taskId,
        String logSnippet
    ) {}

    public record RuntimeDiagnostics(RuntimeRunInfo dbtRun, AirflowRunInfo airflowRun) {}

    public record ModelDiagnostics(
        boolean enabled,
        boolean success,
        String model,
        String uniqueId,
        String resourceType,
        String path,
        String relationName,
        RelationStats current,
        List<DependencyDiagnostic> upstreams,
        RuntimeDiagnostics runtime,
        List<String> findings,
        List<String> recommendedQueries,
        String message
    ) {
        public static ModelDiagnostics disabled(String model, String message) {
            return new ModelDiagnostics(false, false, model, null, null, null, null, null, List.of(), null, List.of(), List.of(), message);
        }

        public static ModelDiagnostics error(String model, String message) {
            return new ModelDiagnostics(true, false, model, null, null, null, null, null, List.of(), null, List.of(), List.of(), message);
        }
    }
}
