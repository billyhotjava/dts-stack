package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitMasterDataGateway;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitMasterDataGateway.ProjectCockpitMasterDataState;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitTopicBindingGateway;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitTopicBindingGateway.TopicBindingState;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitWarehouseGateway;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitWarehouseGateway.ProjectCockpitBatchSummary;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitWarehouseGateway.ProjectCockpitWarehouseNode;
import com.yuzhi.dts.analytics.service.projectcockpit.ProjectCockpitWarehouseGateway.ProjectCockpitWarehouseSnapshot;
import org.springframework.stereotype.Service;

@Service
public class ProjectCockpitService {

    private static final List<String> RISK_ORDER = List.of("高", "中", "低", "未知");
    private static final List<String> DELAY_REASON_ORDER = List.of("technical", "quality", "change", "coordination", "supplier", "test", "archive", "normal");
    private static final String EMPTY_SUBTITLE = "暂无正式数据，请先通过数据中台上传项目主体域 Excel/CSV 并完成建模刷新。";

    private final ObjectMapper objectMapper;
    private final ProjectCockpitMasterDataGateway masterDataGateway;
    private final ProjectCockpitTopicBindingGateway topicBindingGateway;
    private final ProjectCockpitWarehouseGateway warehouseGateway;

    private volatile List<MajorProjectRow> majorProjects = List.of();
    private volatile List<SubprojectRow> subprojects = List.of();
    private volatile List<NodeRow> nodes = List.of();
    private volatile ProjectCockpitBatchSummary latestBatch;
    private volatile boolean formalDataReady;
    private volatile boolean warehouseEnabled;
    private volatile ProjectCockpitMasterDataState masterDataState;
    private volatile TopicBindingState topicBindingState;

    public ProjectCockpitService(
            ObjectMapper objectMapper,
            ProjectCockpitMasterDataGateway masterDataGateway,
            ProjectCockpitTopicBindingGateway topicBindingGateway,
            ProjectCockpitWarehouseGateway warehouseGateway) {
        this.objectMapper = objectMapper;
        this.masterDataGateway = masterDataGateway;
        this.topicBindingGateway = topicBindingGateway;
        this.warehouseGateway = warehouseGateway;
        this.masterDataState = masterDataGateway.currentState();
        this.topicBindingState = topicBindingGateway.currentProjectManagementState();
    }

    public ObjectNode summary(Filters filters) {
        refreshFormalData();
        List<NodeRow> scoped = applyScopeFilters(filters);
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode hero = root.putObject("hero");
        hero.put("title", "项目看板");
        hero.put("subtitle", "");
        hero.put("updatedAt", latestUpdate(filtered));
        hero.put("scope", buildScopeText(filters, filtered));

        root.set("dataState", buildDataState());
        root.set("filters", buildFilters(filters));
        root.set("kpis", buildSummaryKpis(scoped, filtered, filters));
        root.set("ranking", buildRanking(filtered));
        root.set("alerts", buildAlerts(filtered));
        root.set("spotlight", buildSpotlight(filtered));
        return root;
    }

    public ObjectNode trends(Filters filters) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("dataState", buildDataState());
        root.set("weekly", buildWeeklyTrend(filtered));
        root.set("programSeries", buildProgramSeries(filtered));
        root.set("majorProjectSeries", buildMajorProjectSeries(filtered));
        return root;
    }

    public ObjectNode execution(Filters filters) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("dataState", buildDataState());
        root.set("ganttTasks", buildExecutionGantt(filtered));
        root.set("milestones", buildMilestones(filtered));
        root.set("dueList", buildDueList(filtered));
        root.set("workload", buildWorkload(filtered));
        root.set("stageBuckets", buildStageBuckets(filtered));
        return root;
    }

    public ObjectNode riskAttribution(Filters filters) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("dataState", buildDataState());
        root.set("riskBreakdown", buildRiskBreakdown(filtered));
        root.set("delayReasonBreakdown", buildDelayReasonBreakdown(filtered));
        root.set("delayReasonMatrix", buildDelayReasonMatrix(filtered));
        root.set("weeklyDelayTrend", buildWeeklyDelayTrend(filtered));
        root.set("delayedProjects", buildDelayedProjects(filtered));
        return root;
    }

    public ObjectNode majorProjectTree(Filters filters) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);
        String selectedMajorProjectId = filters.majorProjectId();
        if (selectedMajorProjectId == null || selectedMajorProjectId.isBlank()) {
            selectedMajorProjectId = filtered.stream()
                    .map(NodeRow::majorProjectId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(majorProjects.isEmpty() ? "" : majorProjects.get(0).majorProjectId());
        }
        final String effectiveMajorProjectId = selectedMajorProjectId;
        List<NodeRow> selectedRows = filtered.stream()
                .filter(row -> Objects.equals(row.majorProjectId(), effectiveMajorProjectId))
                .toList();

        ObjectNode root = objectMapper.createObjectNode();
        root.put("selectedMajorProjectId", effectiveMajorProjectId);
        root.set("dataState", buildDataState());
        root.set("tree", buildProjectTree(effectiveMajorProjectId, selectedRows));
        root.set("summary", buildTreeSummary(effectiveMajorProjectId, selectedRows));
        return root;
    }

    public ObjectNode dataSupport(Filters filters) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("lastUpdatedAt", latestUpdate(filtered));
        root.set("dataState", buildDataState());
        root.set("batch", buildBatch());
        root.set("quality", buildQuality(filtered));
        root.set("coverage", buildCoverage(filtered));
        root.set("glossary", buildGlossary());
        root.set("missingChecklist", buildMissingChecklist());
        root.set("dataSources", buildDataSources());
        return root;
    }

    public ObjectNode drillDetail(String target, Filters filters, String extraDept, String extraReason) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);

        List<NodeRow> drillRows = switch (target) {
            case "high-risk" -> filtered.stream()
                    .filter(row -> "高".equals(row.riskLevel()))
                    .toList();
            case "overdue" -> filtered.stream()
                    .filter(NodeRow::delayed)
                    .toList();
            case "completion" -> filtered;
            case "milestone" -> filtered.stream()
                    .filter(NodeRow::milestoneNode)
                    .toList();
            case "delay-reason" -> filtered.stream()
                    .filter(NodeRow::delayed)
                    .filter(row -> extraDept == null || extraDept.isBlank() || extraDept.equals(row.dept()))
                    .filter(row -> extraReason == null || extraReason.isBlank() || extraReason.equals(row.delayReasonCategory()))
                    .toList();
            case "delay-dept" -> filtered.stream()
                    .filter(NodeRow::delayed)
                    .filter(row -> extraDept == null || extraDept.isBlank() || extraDept.equals(row.dept()))
                    .toList();
            default -> List.of();
        };

        ObjectNode root = objectMapper.createObjectNode();
        root.put("target", target);
        root.put("total", drillRows.size());
        ArrayNode items = root.putArray("items");
        for (NodeRow row : drillRows) {
            ObjectNode item = items.addObject();
            item.put("id", row.nodeId());
            item.put("name", row.nodeTask());
            item.put("majorProjectName", row.majorProjectName());
            item.put("subprojectName", row.subprojectName());
            item.put("riskLevel", row.riskLevel());
            item.put("status", row.statusLabel());
            item.put("progressRate", row.completed() ? 100 : 0);
            item.put("delayDays", row.delayDays());
            item.put("planDate", row.planDate() != null ? row.planDate().toString() : "");
            item.put("actualDate", row.actualDate() != null ? row.actualDate().toString() : "");
            item.put("reason", row.delayReasonCategory());
            item.put("ownerDept", row.dept());
            item.put("ownerUser", row.owner());
            item.put("nodeType", row.normalizedNodeType());
        }
        return root;
    }

    public ObjectNode screenHeader(Filters filters) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", "科研项目管理指挥大屏");
        root.put("subtitle", formalDataReady
                ? "面向科研院所项目群的总体态势、执行推进与风险变更轮播大屏。"
                : EMPTY_SUBTITLE);
        root.put("updatedAt", latestUpdate(filtered));
        root.put("scope", buildScopeText(filters, filtered));
        root.set("dataState", buildDataState());
        root.set("filters", buildFilters(filters));
        return root;
    }

    public ObjectNode screenMetricsOverview(Filters filters) {
        refreshFormalData();
        List<NodeRow> scoped = applyScopeFilters(filters);
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = buildScreenPayload("metrics-overview", "指标全览", filters, filtered);
        // Combines all 4 dimensions into a single KPI array with "dimension" field
        ArrayNode allKpis = objectMapper.createArrayNode();

        // Dimension 1: 项目（含一般节点）— from buildScreenOverviewKpis
        OverviewPeriodMetrics metrics = computeOverviewPeriodMetrics(scoped, filters);
        addDimensionKpi(allKpis, "项目（含一般节点）", "periodNodeTotalCount", "项目本周期节点总数", metrics.periodNodeTotalCount(), "个");
        addDimensionKpi(allKpis, "项目（含一般节点）", "pendingNormalCount", "正常待完成", metrics.pendingNormalCount(), "个");
        addDimensionKpi(allKpis, "项目（含一般节点）", "dueNodeCount", "项目本周期节点已到时间节点总数", metrics.dueNodeCount(), "个");
        addDimensionKpi(allKpis, "项目（含一般节点）", "outsideCompletedCount", "项目本周期以外完成节点总数", metrics.outsideCompletedCount(), "个");
        addDimensionKpi(allKpis, "项目（含一般节点）", "incompleteNodeCount", "项目本周期节点未完成总数", metrics.incompleteNodeCount(), "个");
        addDimensionKpi(allKpis, "项目（含一般节点）", "onTimeCount", "节点按时完成数", metrics.onTimeCount(), "个");
        addDimensionKpiDuplicate(allKpis, "项目（含一般节点）", "pendingNormalCount2", "正常待完成（重复校验）", metrics.pendingNormalCount(), "个");
        addDimensionKpi(allKpis, "项目（含一般节点）", "overdueCompletedCount", "节点超期完成数", metrics.overdueCompletedCount(), "个");
        addDimensionKpi(allKpis, "项目（含一般节点）", "completedNodeCount", "节点完成总数", metrics.completedNodeCount(), "个");
        addDimensionKpiPercent(allKpis, "项目（含一般节点）", "completionRate", "节点完成百分比", metrics.completedNodeCount(), metrics.dueNodeCount() + metrics.outsideCompletedCount());
        addDimensionKpiPercent(allKpis, "项目（含一般节点）", "onTimeRate", "节点按时完成百分比", metrics.onTimeCount(), metrics.dueNodeCount() + metrics.outsideCompletedCount());
        addDimensionKpiPercent(allKpis, "项目（含一般节点）", "overdueCompletionRate", "超期完成节点百分比",
                metrics.overdueCompletedCount() + metrics.outsideCompletedCount(),
                metrics.periodNodeTotalCount() + metrics.outsideCompletedCount());

        // Dimension 2: 项目（除一般节点）— from buildScreenRiskKpis
        List<NodeRow> dueRowsNonGeneral = filterDueByEnd(scoped, filters).stream().filter(row -> !row.generalNode()).toList();
        long abnormalPendingCount = dueRowsNonGeneral.stream().filter(NodeRow::abnormalPending).count();
        long overdueIncompleteUnchangedCount = dueRowsNonGeneral.stream().filter(NodeRow::overdueIncompleteUnchanged).count();
        long overdueIncompleteChangedCount = dueRowsNonGeneral.stream().filter(NodeRow::overdueIncompleteChanged).count();
        long overdueCompletedUnchangedCount = dueRowsNonGeneral.stream().filter(NodeRow::overdueCompletedUnchanged).count();
        long dueNonGeneralCount = Math.max(0, dueRowsNonGeneral.size() - dueRowsNonGeneral.stream().filter(NodeRow::normalPending).count());
        addDimensionKpi(allKpis, "项目（除一般节点）", "abnormalPendingNonGeneral", "不正常待变更节点数", abnormalPendingCount, "个");
        addDimensionKpi(allKpis, "项目（除一般节点）", "overdueIncompleteUnchangedNonGeneral", "超期未完成且未走变更流程的节点数", overdueIncompleteUnchangedCount, "个");
        addDimensionKpi(allKpis, "项目（除一般节点）", "overdueIncompleteChangedNonGeneral", "超期未完成但走完变更流程节点数", overdueIncompleteChangedCount, "个");
        addDimensionKpi(allKpis, "项目（除一般节点）", "overdueCompletedUnchangedNonGeneral", "超期已完成未变更", overdueCompletedUnchangedCount, "个");
        addDimensionKpiPercent(allKpis, "项目（除一般节点）", "abnormalRate", "节点已经不正常待变更的百分比", abnormalPendingCount + overdueIncompleteUnchangedCount, dueNonGeneralCount);
        addDimensionKpiPercent(allKpis, "项目（除一般节点）", "overdueRate", "节点超期百分比", overdueIncompleteUnchangedCount + overdueIncompleteChangedCount, dueNonGeneralCount);

        // Dimension 3: 项目（截止目前未完成节点）— from buildScreenIncompleteKpis
        addDimensionKpi(allKpis, "截止目前未完成节点", "incompleteHighRisk", "截止目前未完成高风险节点数",
                filtered.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel())) && row.openRisk()).count(), "个");
        addDimensionKpi(allKpis, "截止目前未完成节点", "incompleteMidRisk", "截止目前未完成中风险节点数",
                filtered.stream().filter(row -> "中".equals(normalizedRisk(row.riskLevel())) && row.openRisk()).count(), "个");
        addDimensionKpi(allKpis, "截止目前未完成节点", "incompleteMilestone", "截止目前未完成里程碑节点数",
                filtered.stream().filter(row -> row.milestoneNode() && row.openRisk()).count(), "个");
        addDimensionKpi(allKpis, "截止目前未完成节点", "incompleteMajor", "截止目前未完成重大节点数",
                filtered.stream().filter(row -> row.majorNode() && row.openRisk()).count(), "个");
        addDimensionKpi(allKpis, "截止目前未完成节点", "incompleteImportant", "截止目前未完成重要节点数",
                filtered.stream().filter(row -> row.importantNode() && row.openRisk()).count(), "个");

        // Dimension 4: 项目（本周期内节点）— from buildScreenExecutionKpis
        long milestoneOnTime = filtered.stream().filter(row -> row.milestoneNode() && row.onTimeCompleted()).count();
        long milestoneOverdueCompleted = filtered.stream().filter(row -> row.milestoneNode() && row.overdueCompleted()).count();
        long milestonePending = filtered.stream().filter(row -> row.milestoneNode() && row.normalPending()).count();
        long milestoneIncomplete = filtered.stream().filter(row -> row.milestoneNode() && row.openRisk()).count();
        addDimensionKpi(allKpis, "本周期内节点", "milestoneOnTimeCount", "里程碑节点按时完成数", milestoneOnTime, "个");
        addDimensionKpi(allKpis, "本周期内节点", "milestoneOverdueCompletedCount", "里程碑节点超期完成数", milestoneOverdueCompleted, "个");
        addDimensionKpi(allKpis, "本周期内节点", "milestonePendingCount", "里程碑节点正常待完成数", milestonePending, "个");
        addDimensionKpiPercent(allKpis, "本周期内节点", "milestoneCompletionRate", "里程碑节点完成总百分比",
                milestoneOnTime + milestoneOverdueCompleted, milestoneIncomplete + milestoneOnTime + milestoneOverdueCompleted);
        addDimensionKpi(allKpis, "本周期内节点", "highRiskNodeCount", "高风险节点数",
                filtered.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count(), "个");
        addDimensionKpi(allKpis, "本周期内节点", "midRiskNodeCount", "中风险节点数",
                filtered.stream().filter(row -> "中".equals(normalizedRisk(row.riskLevel()))).count(), "个");
        addDimensionKpi(allKpis, "本周期内节点", "milestoneTotalCount", "里程碑节点总数",
                filtered.stream().filter(NodeRow::milestoneNode).count(), "个");
        addDimensionKpi(allKpis, "本周期内节点", "majorNodeTotalCount", "重大节点总数",
                filtered.stream().filter(NodeRow::majorNode).count(), "个");
        addDimensionKpi(allKpis, "本周期内节点", "importantNodeTotalCount", "重要节点总数",
                filtered.stream().filter(NodeRow::importantNode).count(), "个");
        addDimensionKpi(allKpis, "本周期内节点", "milestoneOnTimeCount2", "里程碑节点按时完成数", milestoneOnTime, "个");
        addDimensionKpi(allKpis, "本周期内节点", "milestoneOverdueCompletedCount2", "里程碑节点超期完成数", milestoneOverdueCompleted, "个");

        root.set("kpis", allKpis);
        return root;
    }

    private void addDimensionKpi(ArrayNode kpis, String dimension, String key, String label, long value, String unit) {
        ObjectNode kpi = kpi(key, label, String.valueOf(value), unit);
        kpi.put("dimension", dimension);
        kpis.add(kpi);
    }

    private void addDimensionKpiDuplicate(ArrayNode kpis, String dimension, String key, String label, long value, String unit) {
        addDimensionKpi(kpis, dimension, key, label, value, unit);
    }

    private void addDimensionKpiPercent(ArrayNode kpis, String dimension, String key, String label, long numerator, long denominator) {
        ObjectNode kpi = kpi(key, label, percentValue(numerator, denominator), "%");
        kpi.put("dimension", dimension);
        kpis.add(kpi);
    }

    public ObjectNode screenOverview(Filters filters) {
        refreshFormalData();
        List<NodeRow> scoped = applyScopeFilters(filters);
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = buildScreenPayload("overview", "总体态势", filters, filtered);
        root.set("kpis", buildScreenOverviewKpis(scoped, filters));
        root.set("weekly", buildWeeklyTrend(filtered));
        root.set("programSeries", buildProgramSeries(filtered));
        root.set("ranking", buildRanking(filtered));
        root.set("alerts", buildAlerts(filtered));
        root.set("spotlight", buildSpotlight(filtered));
        root.set("completionBreakdown", buildCompletionBreakdown(scoped, filters));
        return root;
    }

    private ArrayNode buildCompletionBreakdown(List<NodeRow> scopedRows, Filters filters) {
        OverviewPeriodMetrics m = computeOverviewPeriodMetrics(scopedRows, filters);
        ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode onTime = objectMapper.createObjectNode();
        onTime.put("name", "按时完成"); onTime.put("value", m.onTimeCount()); arr.add(onTime);
        ObjectNode overdue = objectMapper.createObjectNode();
        overdue.put("name", "超期完成"); overdue.put("value", m.overdueCompletedCount() + m.outsideCompletedCount()); arr.add(overdue);
        ObjectNode incomplete = objectMapper.createObjectNode();
        incomplete.put("name", "未完成"); incomplete.put("value", m.incompleteNodeCount()); arr.add(incomplete);
        ObjectNode pending = objectMapper.createObjectNode();
        pending.put("name", "正常待完成"); pending.put("value", m.pendingNormalCount()); arr.add(pending);
        return arr;
    }

    public ObjectNode screenExecution(Filters filters) {
        refreshFormalData();
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = buildScreenPayload("execution", "执行与里程碑", filters, filtered);
        root.set("incompleteKpis", buildScreenIncompleteKpis(filtered));
        root.set("milestoneKpis", buildScreenExecutionKpis(filtered));
        root.set("ganttTasks", buildExecutionGantt(filtered));
        root.set("milestones", buildMilestones(filtered));
        root.set("dueList", buildDueList(filtered));
        root.set("workload", buildWorkload(filtered));
        root.set("stageBuckets", buildStageBuckets(filtered));
        return root;
    }

    public ObjectNode screenRisk(Filters filters) {
        refreshFormalData();
        List<NodeRow> scoped = applyScopeFilters(filters);
        List<NodeRow> filtered = applyFilters(filters);
        ObjectNode root = buildScreenPayload("risk", "风险与变更", filters, filtered);
        root.set("changeKpis", buildScreenRiskKpis(scoped, filters));
        root.set("riskBreakdown", buildRiskBreakdown(filtered));
        root.set("delayReasonBreakdown", buildDelayReasonBreakdown(filtered));
        root.set("delayReasonMatrix", buildDelayReasonMatrix(filtered));
        root.set("weeklyDelayTrend", buildWeeklyDelayTrend(filtered));
        root.set("delayedProjects", buildDelayedProjects(filtered));
        root.set("governanceSummary", buildRiskGovernanceSummary(scoped, filters));
        return root;
    }

    private void refreshFormalData() {
        ProjectCockpitWarehouseSnapshot snapshot = warehouseGateway.loadSnapshot();
        warehouseEnabled = snapshot.warehouseEnabled();
        latestBatch = snapshot.batch();
        formalDataReady = snapshot.ready();
        masterDataState = masterDataGateway.currentState();
        topicBindingState = topicBindingGateway.currentProjectManagementState();
        nodes = snapshot.ready() ? snapshot.nodes().stream().map(this::mapNode).toList() : List.of();
        majorProjects = deriveMajorProjects(nodes);
        subprojects = deriveSubprojects(nodes);
    }

    private NodeRow mapNode(ProjectCockpitWarehouseNode row) {
        return new NodeRow(
                row.nodeId(),
                row.projectNo(),
                row.subsystem(),
                row.nodeTask(),
                row.nodeType(),
                row.owner(),
                row.dept(),
                row.projectManager(),
                row.planDate(),
                row.actualDate(),
                row.completionStatus(),
                normalizedRisk(row.riskLevel()),
                row.delayDays(),
                row.delayReasonCategory(),
                row.majorProjectId(),
                row.majorProjectName(),
                row.programId(),
                row.programName(),
                row.subprojectId(),
                row.subprojectName(),
                row.incompleteReason(),
                row.delayImpact(),
                row.keyNode(),
                row.milestone());
    }

    private List<MajorProjectRow> deriveMajorProjects(List<NodeRow> sourceNodes) {
        return sourceNodes.stream()
                .filter(row -> row.majorProjectId() != null && !row.majorProjectId().isBlank())
                .collect(Collectors.groupingBy(NodeRow::majorProjectId, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(rows -> {
                    NodeRow sample = rows.get(0);
                    return new MajorProjectRow(
                            sample.majorProjectId(),
                            sample.majorProjectId(),
                            sample.majorProjectName(),
                            sample.programId(),
                            sample.programName(),
                            firstNonBlank(rows, NodeRow::dept),
                            firstNonBlank(rows, NodeRow::owner),
                            rows.stream()
                                    .map(NodeRow::planDate)
                                    .filter(Objects::nonNull)
                                    .min(LocalDate::compareTo)
                                    .orElse(null),
                            rows.stream()
                                    .map(NodeRow::planDate)
                                    .filter(Objects::nonNull)
                                    .max(LocalDate::compareTo)
                                    .orElse(null),
                            aggregateStatus(rows),
                            "");
                })
                .sorted(Comparator.comparing(MajorProjectRow::majorProjectName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<SubprojectRow> deriveSubprojects(List<NodeRow> sourceNodes) {
        return sourceNodes.stream()
                .filter(row -> row.subprojectId() != null && !row.subprojectId().isBlank())
                .collect(Collectors.groupingBy(NodeRow::subprojectId, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(rows -> {
                    NodeRow sample = rows.get(0);
                    return new SubprojectRow(
                            sample.subprojectId(),
                            sample.subprojectName(),
                            sample.majorProjectId(),
                            sample.projectNo(),
                            sample.subsystem(),
                            firstNonBlank(rows, NodeRow::dept),
                            firstNonBlank(rows, NodeRow::owner),
                            firstNonBlank(rows, NodeRow::projectManager),
                            rows.stream()
                                    .map(NodeRow::planDate)
                                    .filter(Objects::nonNull)
                                    .min(LocalDate::compareTo)
                                    .orElse(null),
                            rows.stream()
                                    .map(NodeRow::planDate)
                                    .filter(Objects::nonNull)
                                    .max(LocalDate::compareTo)
                                    .orElse(null),
                            aggregateStatus(rows),
                            "",
                            "");
                })
                .sorted(Comparator.comparing(SubprojectRow::subprojectName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String firstNonBlank(List<NodeRow> rows, Function<NodeRow, String> getter) {
        return rows.stream()
                .map(getter)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private ObjectNode buildDataState() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("warehouseEnabled", warehouseEnabled);
        node.put("ready", formalDataReady);
        node.put("message", formalDataReady
                ? "已读取项目主体域正式批次数据。"
                : "暂无正式数据，请先上传项目主体域 Excel/CSV 并完成建模刷新。");
        if (latestBatch != null) {
            node.put("batchId", blankToEmpty(latestBatch.batchId()));
            node.put("status", blankToEmpty(latestBatch.status()));
        } else {
            node.put("batchId", "");
            node.put("status", warehouseEnabled ? "EMPTY" : "DISABLED");
        }
        return node;
    }

    private ObjectNode buildBatch() {
        ObjectNode node = objectMapper.createObjectNode();
        if (latestBatch == null) {
            node.put("batchId", "");
            node.put("sourceFileName", "");
            node.put("uploadedAt", "");
            node.put("refreshedAt", "");
            node.put("status", warehouseEnabled ? "EMPTY" : "DISABLED");
            node.put("totalRows", 0);
            node.put("validRows", 0);
            node.put("issueRows", 0);
            node.put("issueCount", 0);
            node.put("coverageRate", 0);
            return node;
        }
        node.put("batchId", blankToEmpty(latestBatch.batchId()));
        node.put("sourceFileName", blankToEmpty(latestBatch.sourceFileName()));
        node.put("uploadedAt", formatDateTime(latestBatch.uploadedAt()));
        node.put("refreshedAt", formatDateTime(latestBatch.refreshedAt()));
        node.put("status", blankToEmpty(latestBatch.status()));
        node.put("totalRows", latestBatch.totalRows());
        node.put("validRows", latestBatch.validRows());
        node.put("issueRows", latestBatch.issueRows());
        node.put("issueCount", latestBatch.issueCount());
        node.put("coverageRate", Math.round(latestBatch.coverageRate() * 10000.0) / 100.0);
        return node;
    }

    private ObjectNode buildQuality(List<NodeRow> filtered) {
        ObjectNode node = objectMapper.createObjectNode();
        int unknownDelayReasonCount = latestBatch == null ? 0 : latestBatch.unknownDelayReasonCount();
        int unmappedSubprojectCount = latestBatch == null ? 0 : latestBatch.unmappedSubprojectCount();
        node.put("issueCount", latestBatch == null ? 0 : latestBatch.issueCount());
        node.put("issueRows", latestBatch == null ? 0 : latestBatch.issueRows());
        node.put("unknownDelayReasonCount", unknownDelayReasonCount);
        node.put("unmappedSubprojectCount", unmappedSubprojectCount);
        node.put("highRiskNodeCount", filtered.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
        node.put("filteredNodeCount", filtered.size());
        return node;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private ObjectNode buildScreenPayload(String screenKey, String screenTitle, Filters filters, List<NodeRow> filtered) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("screenKey", screenKey);
        root.put("screenTitle", screenTitle);
        root.put("updatedAt", latestUpdate(filtered));
        root.put("scope", buildScopeText(filters, filtered));
        root.set("dataState", buildDataState());
        root.set("filters", buildFilters(filters));
        return root;
    }

    private ObjectNode buildFilters(Filters filters) {
        ArrayNode programs = objectMapper.createArrayNode();
        uniqueValues(majorProjects, MajorProjectRow::programId, MajorProjectRow::programName)
                .forEach(option -> programs.add(option));

        ArrayNode majorProjectOptions = objectMapper.createArrayNode();
        majorProjects.stream()
                .sorted(Comparator.comparing(MajorProjectRow::majorProjectName))
                .forEach(row -> majorProjectOptions.add(option(row.majorProjectId(), row.majorProjectName())));

        ArrayNode deptOptions = objectMapper.createArrayNode();
        nodes.stream()
                .map(NodeRow::dept)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .forEach(value -> deptOptions.add(option(value, value)));

        ArrayNode riskOptions = objectMapper.createArrayNode();
        RISK_ORDER.stream()
                .filter(level -> nodes.stream().anyMatch(node -> level.equals(normalizedRisk(node.riskLevel()))))
                .forEach(level -> riskOptions.add(option(level, level)));

        ObjectNode filtersNode = objectMapper.createObjectNode();
        filtersNode.set("programs", programs);
        filtersNode.set("majorProjects", majorProjectOptions);
        filtersNode.set("depts", deptOptions);
        filtersNode.set("riskLevels", riskOptions);
        ObjectNode current = filtersNode.putObject("current");
        current.put("programId", blankToEmpty(filters.programId()));
        current.put("majorProjectId", blankToEmpty(filters.majorProjectId()));
        current.put("dateFrom", filters.dateFrom() == null ? "" : filters.dateFrom().toString());
        current.put("dateTo", filters.dateTo() == null ? "" : filters.dateTo().toString());
        current.put("deptId", blankToEmpty(filters.deptId()));
        current.put("riskLevel", blankToEmpty(filters.riskLevel()));
        return filtersNode;
    }

    private ArrayNode buildSummaryKpis(List<NodeRow> scopedRows, List<NodeRow> filtered, Filters filters) {
        OverviewPeriodMetrics metrics = computeOverviewPeriodMetrics(scopedRows, filters);
        ArrayNode kpis = objectMapper.createArrayNode();
        long majorProjectCount = filtered.stream().map(NodeRow::majorProjectId).filter(Objects::nonNull).distinct().count();
        long subprojectCount = filtered.stream().map(NodeRow::subprojectId).filter(Objects::nonNull).distinct().count();
        long overdueNodes = filtered.stream().filter(NodeRow::delayed).count();
        long highRiskNodes = filtered.stream().filter(node -> "高".equals(normalizedRisk(node.riskLevel()))).count();
        long milestoneCompletedCount = metrics.milestoneOnTimeCount() + metrics.milestoneOverdueCompletedCount();
        long milestoneDueCount = metrics.milestoneIncompleteCount() + milestoneCompletedCount;

        kpis.add(kpi("majorProjectCount", "重大项目数", String.valueOf(majorProjectCount), "个"));
        kpis.add(kpi("subprojectCount", "子项目数", String.valueOf(subprojectCount), "个"));
        kpis.add(kpi("completionRate", "节点完成率", percentValue(
                metrics.completedNodeCount(),
                metrics.dueNodeCount() + metrics.outsideCompletedCount()), "%"));
        kpis.add(kpi("overdueNodeCount", "延期节点数", String.valueOf(overdueNodes), "个"));
        kpis.add(kpi("highRiskNodeCount", "高风险节点数", String.valueOf(highRiskNodes), "个"));
        kpis.add(kpi("milestoneCompletionRate", "里程碑完成率", percentValue(
                milestoneCompletedCount,
                milestoneDueCount), "%"));
        return kpis;
    }

    private ArrayNode buildScreenOverviewKpis(List<NodeRow> scopedRows, Filters filters) {
        OverviewPeriodMetrics metrics = computeOverviewPeriodMetrics(scopedRows, filters);

        ArrayNode kpis = objectMapper.createArrayNode();
        kpis.add(kpi("periodNodeTotalCount", "项目本周期节点总数", String.valueOf(metrics.periodNodeTotalCount()), "个"));
        kpis.add(kpi("pendingNormalCount", "正常待完成", String.valueOf(metrics.pendingNormalCount()), "个"));
        kpis.add(kpi("dueNodeCount", "项目本周期节点已到时间节点总数", String.valueOf(metrics.dueNodeCount()), "个"));
        kpis.add(kpi("outsideCompletedCount", "项目本周期以外完成节点总数", String.valueOf(metrics.outsideCompletedCount()), "个"));
        kpis.add(kpi("incompleteNodeCount", "项目本周期节点未完成总数", String.valueOf(metrics.incompleteNodeCount()), "个"));
        kpis.add(kpi("onTimeCount", "节点按时完成数", String.valueOf(metrics.onTimeCount()), "个"));
        kpis.add(kpi("overdueCompletedCount", "节点超期完成数", String.valueOf(metrics.overdueCompletedCount()), "个"));
        kpis.add(kpi("completedNodeCount", "节点完成总数", String.valueOf(metrics.completedNodeCount()), "个"));
        kpis.add(kpi("completionRate", "节点完成百分比", percentValue(
                metrics.completedNodeCount(),
                metrics.dueNodeCount() + metrics.outsideCompletedCount()), "%"));
        kpis.add(kpi("onTimeRate", "节点按时完成百分比", percentValue(
                metrics.onTimeCount(),
                metrics.dueNodeCount() + metrics.outsideCompletedCount()), "%"));
        kpis.add(kpi("overdueCompletionRate", "超期完成节点百分比", percentValue(
                metrics.overdueCompletedCount() + metrics.outsideCompletedCount(),
                metrics.periodNodeTotalCount() + metrics.outsideCompletedCount()), "%"));
        return kpis;
    }

    private OverviewPeriodMetrics computeOverviewPeriodMetrics(List<NodeRow> scopedRows, Filters filters) {
        List<NodeRow> periodRows = filterPlannedInPeriod(scopedRows, filters);
        List<NodeRow> outsideCompletedRows = filterOutsideCompletedRows(scopedRows, filters, periodRows);
        long periodNodeTotalCount = periodRows.size();
        long pendingNormalCount = periodRows.stream().filter(NodeRow::normalPending).count();
        long dueNodeCount = Math.max(0, periodNodeTotalCount - pendingNormalCount);
        long incompleteNodeCount = periodRows.stream().filter(NodeRow::openRisk).count();
        long onTimeCount = periodRows.stream().filter(NodeRow::onTimeCompleted).count();
        long overdueCompletedCount = periodRows.stream().filter(NodeRow::overdueCompleted).count();
        long outsideCompletedCount = outsideCompletedRows.size();
        long completedNodeCount = onTimeCount + overdueCompletedCount + outsideCompletedCount;
        long milestoneOnTimeCount = periodRows.stream().filter(row -> row.milestoneNode() && row.onTimeCompleted()).count();
        long milestoneOverdueCompletedCount = periodRows.stream().filter(row -> row.milestoneNode() && row.overdueCompleted()).count();
        long milestoneIncompleteCount = periodRows.stream().filter(row -> row.milestoneNode() && row.openRisk()).count();

        return new OverviewPeriodMetrics(
                periodNodeTotalCount,
                pendingNormalCount,
                dueNodeCount,
                outsideCompletedCount,
                incompleteNodeCount,
                onTimeCount,
                overdueCompletedCount,
                completedNodeCount,
                milestoneOnTimeCount,
                milestoneOverdueCompletedCount,
                milestoneIncompleteCount);
    }

    private ArrayNode buildRanking(List<NodeRow> filtered) {
        ArrayNode ranking = objectMapper.createArrayNode();
        Map<String, List<NodeRow>> grouped = filtered.stream()
                .filter(node -> node.majorProjectId() != null)
                .collect(Collectors.groupingBy(NodeRow::majorProjectId, LinkedHashMap::new, Collectors.toList()));
        grouped.values().stream()
                .map(this::toMajorRanking)
                .sorted(Comparator
                        .comparing((ObjectNode node) -> -node.path("highRiskCount").asInt())
                        .thenComparing(node -> -node.path("overdueCount").asInt())
                        .thenComparing(node -> node.path("healthScore").asDouble()))
                .forEach(ranking::add);
        return ranking;
    }

    private ObjectNode toMajorRanking(List<NodeRow> rows) {
        NodeRow sample = rows.get(0);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("majorProjectId", sample.majorProjectId());
        node.put("majorProjectName", sample.majorProjectName());
        node.put("programName", sample.programName());
        node.put("subprojectCount", rows.stream().map(NodeRow::subprojectId).distinct().count());
        node.put("healthScore", averageHealth(rows));
        node.put("completionRate", percent(rows.stream().filter(NodeRow::completed).count(), rows.size()));
        node.put("highRiskCount", rows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
        node.put("overdueCount", rows.stream().filter(NodeRow::delayed).count());
        node.put("topDelayReason", topDelayReason(rows));
        return node;
    }

    private ArrayNode buildAlerts(List<NodeRow> filtered) {
        ArrayNode alerts = objectMapper.createArrayNode();
        filtered.stream()
                .filter(NodeRow::delayed)
                .sorted(Comparator.comparingInt((NodeRow row) -> riskWeight(row.riskLevel())).reversed()
                        .thenComparingInt(NodeRow::delayDays).reversed())
                .limit(6)
                .forEach(row -> {
                    ObjectNode alert = objectMapper.createObjectNode();
                    alert.put("title", row.nodeTask());
                    alert.put("majorProjectName", row.majorProjectName());
                    alert.put("subprojectName", row.subprojectName());
                    alert.put("riskLevel", normalizedRisk(row.riskLevel()));
                    alert.put("delayDays", Math.max(row.delayDays(), 0));
                    alert.put("reason", blankToDash(row.incompleteReason()));
                    alerts.add(alert);
                });
        return alerts;
    }

    private ObjectNode buildSpotlight(List<NodeRow> filtered) {
        ObjectNode spotlight = objectMapper.createObjectNode();
        filtered.stream()
                .filter(node -> node.majorProjectId() != null)
                .collect(Collectors.groupingBy(NodeRow::majorProjectId))
                .values().stream()
                .max(Comparator.comparingInt((List<NodeRow> rows) ->
                                rows.stream().mapToInt(row -> riskWeight(row.riskLevel())).sum())
                        .thenComparingInt(rows -> rows.stream().mapToInt(row -> Math.max(row.delayDays(), 0)).sum()))
                .ifPresentOrElse(rows -> {
                    NodeRow row = rows.get(0);
                    spotlight.put("majorProjectId", row.majorProjectId());
                    spotlight.put("majorProjectName", row.majorProjectName());
                    spotlight.put("summary", "当前最需要盯防的主项目包含高风险或延期节点。");
                    spotlight.put("highRiskCount", rows.stream().filter(item -> "高".equals(normalizedRisk(item.riskLevel()))).count());
                    spotlight.put("delayCount", rows.stream().filter(NodeRow::delayed).count());
                    spotlight.put("nextMilestone", rows.stream()
                            .filter(NodeRow::milestone)
                            .filter(item -> !item.completed())
                            .sorted(Comparator.comparing(NodeRow::planDate, Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(NodeRow::nodeTask)
                            .findFirst()
                            .orElse("暂无待完成里程碑"));
                }, () -> spotlight.put("summary", "当前筛选范围暂无可展示项目。"));
        return spotlight;
    }

    private ArrayNode buildWeeklyTrend(List<NodeRow> filtered) {
        ArrayNode weekly = objectMapper.createArrayNode();
        Map<LocalDate, List<NodeRow>> byWeek = filtered.stream()
                .filter(node -> node.planDate() != null)
                .collect(Collectors.groupingBy(node -> weekStart(node.planDate()), LinkedHashMap::new, Collectors.toList()));
        byWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<NodeRow> rows = entry.getValue();
                    ObjectNode point = objectMapper.createObjectNode();
                    point.put("weekLabel", formatWeek(entry.getKey()));
                    point.put("completionRate", numericPercent(rows.stream().filter(NodeRow::completed).count(), rows.size()));
                    point.put("delayedNodes", rows.stream().filter(NodeRow::delayed).count());
                    point.put("highRiskNodes", rows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
                    long milestoneCount = rows.stream().filter(NodeRow::milestone).count();
                    long milestoneDone = rows.stream().filter(row -> row.milestone() && row.completed()).count();
                    point.put("milestoneCompletionRate", numericPercent(milestoneDone, milestoneCount));
                    weekly.add(point);
                });
        return weekly;
    }

    private ArrayNode buildProgramSeries(List<NodeRow> filtered) {
        ArrayNode series = objectMapper.createArrayNode();
        filtered.stream()
                .filter(node -> node.programId() != null)
                .collect(Collectors.groupingBy(NodeRow::programId, LinkedHashMap::new, Collectors.toList()))
                .forEach((programId, rows) -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("programId", programId);
                    item.put("name", rows.get(0).programName());
                    item.set("points", buildSeriesPoints(rows, valueRows -> numericPercent(valueRows.stream().filter(NodeRow::completed).count(), valueRows.size())));
                    series.add(item);
                });
        return series;
    }

    private ArrayNode buildMajorProjectSeries(List<NodeRow> filtered) {
        ArrayNode series = objectMapper.createArrayNode();
        filtered.stream()
                .filter(node -> node.majorProjectId() != null)
                .collect(Collectors.groupingBy(NodeRow::majorProjectId, LinkedHashMap::new, Collectors.toList()))
                .forEach((majorProjectId, rows) -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("majorProjectId", majorProjectId);
                    item.put("name", rows.get(0).majorProjectName());
                    item.set("points", buildSeriesPoints(rows, valueRows -> valueRows.stream().filter(NodeRow::delayed).count()));
                    series.add(item);
                });
        return series;
    }

    private ArrayNode buildSeriesPoints(List<NodeRow> rows, Function<List<NodeRow>, Number> metricFn) {
        ArrayNode points = objectMapper.createArrayNode();
        rows.stream()
                .filter(node -> node.planDate() != null)
                .collect(Collectors.groupingBy(node -> weekStart(node.planDate()), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ObjectNode point = objectMapper.createObjectNode();
                    point.put("label", formatWeek(entry.getKey()));
                    Number metric = metricFn.apply(entry.getValue());
                    if (metric instanceof Double || metric instanceof Float) {
                        point.put("value", metric.doubleValue());
                    } else {
                        point.put("value", metric.longValue());
                    }
                    points.add(point);
                });
        return points;
    }

    private ArrayNode buildExecutionGantt(List<NodeRow> filtered) {
        ArrayNode tasks = objectMapper.createArrayNode();
        filtered.stream()
                .sorted(Comparator.comparing(NodeRow::planDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(row -> {
                    ObjectNode task = objectMapper.createObjectNode();
                    task.put("id", row.nodeId());
                    task.put("name", row.nodeTask());
                    task.put("type", row.nodeType());
                    task.put("planDate", row.planDate() == null ? "" : row.planDate().toString());
                    task.put("actualDate", row.actualDate() == null ? "" : row.actualDate().toString());
                    task.put("isCompleted", row.completed());
                    task.put("isOverdue", row.delayed());
                    task.put("isIncomplete", row.openRisk());
                    task.put("delayDays", Math.max(row.delayDays(), 0));
                    task.put("riskLevel", normalizedRisk(row.riskLevel()));
                    task.put("owner", blankToDash(row.owner()));
                    task.put("majorProjectName", row.majorProjectName());
                    task.put("subprojectName", row.subprojectName());
                    tasks.add(task);
                });
        return tasks;
    }

    private ArrayNode buildMilestones(List<NodeRow> filtered) {
        ArrayNode milestones = objectMapper.createArrayNode();
        filtered.stream()
                .filter(NodeRow::milestone)
                .sorted(Comparator.comparing(NodeRow::planDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(10)
                .forEach(row -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("name", row.nodeTask());
                    item.put("majorProjectName", row.majorProjectName());
                    item.put("subprojectName", row.subprojectName());
                    item.put("planDate", row.planDate() == null ? "" : row.planDate().toString());
                    item.put("status", row.statusLabel());
                    milestones.add(item);
                });
        return milestones;
    }

    private ArrayNode buildDueList(List<NodeRow> filtered) {
        ArrayNode dueList = objectMapper.createArrayNode();
        filtered.stream()
                .filter(row -> row.delayed() || (!row.completed() && row.planDate() != null))
                .sorted(Comparator.comparing(NodeRow::planDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(12)
                .forEach(row -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("name", row.nodeTask());
                    item.put("majorProjectName", row.majorProjectName());
                    item.put("dept", blankToDash(row.dept()));
                    item.put("planDate", row.planDate() == null ? "" : row.planDate().toString());
                    item.put("status", row.statusLabel());
                    item.put("delayDays", Math.max(row.delayDays(), 0));
                    dueList.add(item);
                });
        return dueList;
    }

    private ArrayNode buildWorkload(List<NodeRow> filtered) {
        ArrayNode workload = objectMapper.createArrayNode();
        filtered.stream()
                .filter(node -> node.dept() != null)
                .collect(Collectors.groupingBy(NodeRow::dept, LinkedHashMap::new, Collectors.toList()))
                .forEach((dept, rows) -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("dept", blankToDash(dept));
                    item.put("activeCount", rows.stream().filter(row -> !row.completed()).count());
                    item.put("overdueCount", rows.stream().filter(NodeRow::delayed).count());
                    item.put("highRiskCount", rows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
                    workload.add(item);
                });
        return workload;
    }

    private ArrayNode buildStageBuckets(List<NodeRow> filtered) {
        ArrayNode buckets = objectMapper.createArrayNode();
        filtered.stream()
                .filter(node -> node.nodeType() != null)
                .collect(Collectors.groupingBy(NodeRow::nodeType, LinkedHashMap::new, Collectors.toList()))
                .forEach((nodeType, rows) -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("name", blankToDash(nodeType));
                    item.put("value", rows.size());
                    buckets.add(item);
                });
        return buckets;
    }

    private ArrayNode buildScreenIncompleteKpis(List<NodeRow> filtered) {
        ArrayNode kpis = objectMapper.createArrayNode();
        kpis.add(kpi("incompleteHighRiskCount", "截止目前未完成高风险节点数", String.valueOf(
                filtered.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel())) && row.openRisk()).count()), "个"));
        kpis.add(kpi("incompleteMidRiskCount", "截止目前未完成中风险节点数", String.valueOf(
                filtered.stream().filter(row -> "中".equals(normalizedRisk(row.riskLevel())) && row.openRisk()).count()), "个"));
        kpis.add(kpi("incompleteMilestoneCount", "截止目前未完成里程碑节点数", String.valueOf(
                filtered.stream().filter(row -> row.milestoneNode() && row.openRisk()).count()), "个"));
        kpis.add(kpi("incompleteMajorCount", "截止目前未完成重大节点数", String.valueOf(
                filtered.stream().filter(row -> row.majorNode() && row.openRisk()).count()), "个"));
        kpis.add(kpi("incompleteImportantCount", "截止目前未完成重要节点数", String.valueOf(
                filtered.stream().filter(row -> row.importantNode() && row.openRisk()).count()), "个"));
        return kpis;
    }

    private ArrayNode buildScreenExecutionKpis(List<NodeRow> filtered) {
        long milestoneOnTimeCount = filtered.stream().filter(row -> row.milestoneNode() && row.onTimeCompleted()).count();
        long milestoneOverdueCompletedCount = filtered.stream().filter(row -> row.milestoneNode() && row.overdueCompleted()).count();
        long milestonePendingCount = filtered.stream().filter(row -> row.milestoneNode() && row.normalPending()).count();
        long milestoneIncompleteCount = filtered.stream().filter(row -> row.milestoneNode() && row.openRisk()).count();

        ArrayNode kpis = objectMapper.createArrayNode();
        kpis.add(kpi("milestoneOnTimeCount", "里程碑节点按时完成数", String.valueOf(milestoneOnTimeCount), "个"));
        kpis.add(kpi("milestoneOverdueCompletedCount", "里程碑节点超期完成数", String.valueOf(milestoneOverdueCompletedCount), "个"));
        kpis.add(kpi("milestonePendingCount", "里程碑节点正常待完成数", String.valueOf(milestonePendingCount), "个"));
        kpis.add(kpi("milestoneCompletionRate", "里程碑节点完成总百分比", percentValue(
                milestoneOnTimeCount + milestoneOverdueCompletedCount,
                milestoneIncompleteCount + milestoneOnTimeCount + milestoneOverdueCompletedCount), "%"));
        kpis.add(kpi("highRiskNodeCount", "高风险节点数", String.valueOf(
                filtered.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count()), "个"));
        kpis.add(kpi("midRiskNodeCount", "中风险节点数", String.valueOf(
                filtered.stream().filter(row -> "中".equals(normalizedRisk(row.riskLevel()))).count()), "个"));
        kpis.add(kpi("milestoneTotalCount", "里程碑节点总数", String.valueOf(
                filtered.stream().filter(NodeRow::milestoneNode).count()), "个"));
        kpis.add(kpi("majorNodeCount", "重大节点总数", String.valueOf(
                filtered.stream().filter(NodeRow::majorNode).count()), "个"));
        kpis.add(kpi("importantNodeCount", "重要节点总数", String.valueOf(
                filtered.stream().filter(NodeRow::importantNode).count()), "个"));
        return kpis;
    }

    private ArrayNode buildRiskBreakdown(List<NodeRow> filtered) {
        ArrayNode breakdown = objectMapper.createArrayNode();
        RISK_ORDER.stream()
                .filter(level -> filtered.stream().anyMatch(node -> level.equals(normalizedRisk(node.riskLevel()))))
                .forEach(level -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("name", level);
                    item.put("value", filtered.stream().filter(node -> level.equals(normalizedRisk(node.riskLevel()))).count());
                    breakdown.add(item);
                });
        return breakdown;
    }

    private ArrayNode buildDelayReasonBreakdown(List<NodeRow> filtered) {
        ArrayNode breakdown = objectMapper.createArrayNode();
        DELAY_REASON_ORDER.stream()
                .filter(reason -> filtered.stream().anyMatch(node -> reason.equals(node.delayReasonCategory()) && node.delayed()))
                .forEach(reason -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("name", reason);
                    item.put("label", delayReasonLabel(reason));
                    item.put("value", filtered.stream().filter(node -> reason.equals(node.delayReasonCategory()) && node.delayed()).count());
                    breakdown.add(item);
                });
        return breakdown;
    }

    private ArrayNode buildDelayReasonMatrix(List<NodeRow> filtered) {
        ArrayNode matrix = objectMapper.createArrayNode();
        filtered.stream()
                .filter(node -> node.dept() != null)
                .collect(Collectors.groupingBy(NodeRow::dept, LinkedHashMap::new, Collectors.toList()))
                .forEach((dept, rows) -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("dept", blankToDash(dept));
                    item.put("total", rows.stream().filter(NodeRow::delayed).count());
                    for (String reason : DELAY_REASON_ORDER) {
                        item.put(reason, rows.stream().filter(node -> reason.equals(node.delayReasonCategory()) && node.delayed()).count());
                    }
                    matrix.add(item);
                });
        return matrix;
    }

    private ArrayNode buildWeeklyDelayTrend(List<NodeRow> filtered) {
        ArrayNode trend = objectMapper.createArrayNode();
        filtered.stream()
                .filter(node -> node.planDate() != null)
                .collect(Collectors.groupingBy(node -> weekStart(node.planDate()), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<NodeRow> rows = entry.getValue();
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("weekLabel", formatWeek(entry.getKey()));
                    item.put("delayedNodes", rows.stream().filter(NodeRow::delayed).count());
                    item.put("highRiskNodes", rows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
                    trend.add(item);
                });
        return trend;
    }

    private ArrayNode buildDelayedProjects(List<NodeRow> filtered) {
        ArrayNode delayedProjects = objectMapper.createArrayNode();
        filtered.stream()
                .filter(NodeRow::delayed)
                .sorted(Comparator.comparingInt((NodeRow row) -> riskWeight(row.riskLevel())).reversed()
                        .thenComparingInt(NodeRow::delayDays).reversed())
                .limit(12)
                .forEach(row -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("majorProjectId", row.majorProjectId());
                    item.put("majorProjectName", row.majorProjectName());
                    item.put("subprojectId", row.subprojectId());
                    item.put("subprojectName", row.subprojectName());
                    item.put("nodeTask", row.nodeTask());
                    item.put("riskLevel", normalizedRisk(row.riskLevel()));
                    item.put("delayDays", Math.max(row.delayDays(), 0));
                    item.put("reason", blankToDash(row.incompleteReason()));
                    item.put("dept", blankToDash(row.dept()));
                    delayedProjects.add(item);
                });
        return delayedProjects;
    }

    private ArrayNode buildScreenRiskKpis(List<NodeRow> scopedRows, Filters filters) {
        List<NodeRow> dueRows = filterDueByEnd(scopedRows, filters).stream()
                .filter(row -> !row.generalNode())
                .toList();
        ArrayNode kpis = objectMapper.createArrayNode();
        long abnormalPendingCount = dueRows.stream().filter(NodeRow::abnormalPending).count();
        long overdueIncompleteUnchangedCount = dueRows.stream().filter(NodeRow::overdueIncompleteUnchanged).count();
        long overdueIncompleteChangedCount = dueRows.stream().filter(NodeRow::overdueIncompleteChanged).count();
        long overdueCompletedUnchangedCount = dueRows.stream().filter(NodeRow::overdueCompletedUnchanged).count();
        long dueNodeCount = Math.max(0, dueRows.size() - dueRows.stream().filter(NodeRow::normalPending).count());

        kpis.add(kpi("abnormalPendingNonGeneralCount", "不正常待变更节点数", String.valueOf(abnormalPendingCount), "个"));
        kpis.add(kpi("overdueIncompleteUnchangedNonGeneralCount", "超期未完成且未走变更流程的节点数", String.valueOf(overdueIncompleteUnchangedCount), "个"));
        kpis.add(kpi("overdueIncompleteChangedNonGeneralCount", "超期未完成但走完变更流程节点数", String.valueOf(overdueIncompleteChangedCount), "个"));
        kpis.add(kpi("overdueCompletedUnchangedNonGeneralCount", "超期已完成未变更", String.valueOf(overdueCompletedUnchangedCount), "个"));
        kpis.add(kpi("abnormalRate", "节点已经不正常待变更的百分比", percentValue(
                abnormalPendingCount + overdueIncompleteUnchangedCount,
                dueNodeCount), "%"));
        kpis.add(kpi("overdueRate", "节点超期百分比", percentValue(
                overdueIncompleteUnchangedCount + overdueIncompleteChangedCount,
                dueNodeCount), "%"));
        return kpis;
    }

    private ObjectNode buildRiskGovernanceSummary(List<NodeRow> scopedRows, Filters filters) {
        List<NodeRow> dueRows = filterDueByEnd(scopedRows, filters);
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("delayedNodeCount", dueRows.stream().filter(NodeRow::delayed).count());
        summary.put("highRiskNodeCount", dueRows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
        summary.put("openRiskNodeCount", dueRows.stream().filter(NodeRow::openRisk).count());
        summary.put("changedNodeCount", dueRows.stream().filter(NodeRow::changedStatus).count());
        return summary;
    }

    private ArrayNode buildProjectTree(String selectedMajorProjectId, List<NodeRow> selectedRows) {
        ArrayNode tree = objectMapper.createArrayNode();
        MajorProjectRow majorProject = majorProjects.stream()
                .filter(row -> Objects.equals(row.majorProjectId(), selectedMajorProjectId))
                .findFirst()
                .orElse(null);
        if (majorProject == null) {
            return tree;
        }

        ObjectNode majorNode = objectMapper.createObjectNode();
        majorNode.put("id", majorProject.majorProjectId());
        majorNode.put("parentId", "");
        majorNode.put("level", "major");
        majorNode.put("name", majorProject.majorProjectName());
        majorNode.put("status", aggregateStatus(selectedRows));
        majorNode.put("progressRate", numericPercent(selectedRows.stream().filter(NodeRow::completed).count(), selectedRows.size()));
        majorNode.put("riskLevel", aggregateRisk(selectedRows));
        majorNode.put("delayDays", selectedRows.stream().mapToInt(NodeRow::delayDays).map(value -> Math.max(value, 0)).max().orElse(0));
        majorNode.put("ownerDept", majorProject.ownerDept());
        majorNode.put("ownerUser", majorProject.ownerLeader());
        majorNode.put("milestoneCount", selectedRows.stream().filter(NodeRow::milestone).count());
        majorNode.put("incompleteCount", selectedRows.stream().filter(NodeRow::openRisk).count());
        majorNode.put("highRiskCount", selectedRows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
        ArrayNode majorChildren = majorNode.putArray("children");

        Map<String, List<NodeRow>> bySubproject = selectedRows.stream()
                .filter(node -> node.subprojectId() != null)
                .collect(Collectors.groupingBy(NodeRow::subprojectId, LinkedHashMap::new, Collectors.toList()));
        bySubproject.forEach((subprojectId, rows) -> {
            SubprojectRow subproject = subprojects.stream()
                    .filter(item -> Objects.equals(item.subprojectId(), subprojectId))
                    .findFirst()
                    .orElse(null);
            ObjectNode subNode = objectMapper.createObjectNode();
            subNode.put("id", subprojectId);
            subNode.put("parentId", majorProject.majorProjectId());
            subNode.put("level", "subproject");
            subNode.put("name", rows.isEmpty() ? blankToDash(subprojectId) : rows.get(0).subprojectName());
            subNode.put("status", aggregateStatus(rows));
            subNode.put("progressRate", numericPercent(rows.stream().filter(NodeRow::completed).count(), rows.size()));
            subNode.put("riskLevel", aggregateRisk(rows));
            subNode.put("delayDays", rows.stream().mapToInt(NodeRow::delayDays).map(value -> Math.max(value, 0)).max().orElse(0));
            subNode.put("ownerDept", subproject == null ? "" : blankToEmpty(subproject.ownerDept()));
            subNode.put("ownerUser", subproject == null ? "" : blankToEmpty(subproject.ownerUser()));
            subNode.put("milestoneCount", rows.stream().filter(NodeRow::milestone).count());
            subNode.put("incompleteCount", rows.stream().filter(NodeRow::openRisk).count());
            subNode.put("highRiskCount", rows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
            ArrayNode nodeChildren = subNode.putArray("children");
            rows.stream()
                    .sorted(Comparator.comparing(NodeRow::planDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(row -> {
                        ObjectNode node = objectMapper.createObjectNode();
                        node.put("id", row.nodeId());
                        node.put("parentId", subprojectId);
                        node.put("level", "node");
                        node.put("name", row.nodeTask());
                        node.put("status", row.statusLabel());
                        node.put("progressRate", row.completed() ? 100 : 0);
                        node.put("riskLevel", normalizedRisk(row.riskLevel()));
                        node.put("delayDays", Math.max(row.delayDays(), 0));
                        node.put("ownerDept", blankToDash(row.dept()));
                        node.put("ownerUser", blankToDash(row.owner()));
                        node.put("milestoneCount", row.milestone() ? 1 : 0);
                        node.put("incompleteCount", row.openRisk() ? 1 : 0);
                        node.put("highRiskCount", "高".equals(normalizedRisk(row.riskLevel())) ? 1 : 0);
                        node.put("planDate", row.planDate() == null ? "" : row.planDate().toString());
                        node.put("actualDate", row.actualDate() == null ? "" : row.actualDate().toString());
                        node.put("reason", blankToDash(row.incompleteReason()));
                        nodeChildren.add(node);
                    });
            majorChildren.add(subNode);
        });

        tree.add(majorNode);
        return tree;
    }

    private ObjectNode buildTreeSummary(String selectedMajorProjectId, List<NodeRow> selectedRows) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("selectedMajorProjectId", blankToEmpty(selectedMajorProjectId));
        summary.put("totalNodes", selectedRows.size());
        summary.put("completedNodes", selectedRows.stream().filter(NodeRow::completed).count());
        summary.put("highRiskNodes", selectedRows.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count());
        summary.put("delayNodes", selectedRows.stream().filter(NodeRow::delayed).count());
        summary.put("avgHealthScore", averageHealth(selectedRows));
        return summary;
    }

    private ArrayNode buildCoverage(List<NodeRow> filtered) {
        ArrayNode coverage = objectMapper.createArrayNode();
        int validRows = latestBatch == null ? 0 : latestBatch.validRows();
        int totalRows = latestBatch == null ? 0 : latestBatch.totalRows();
        coverage.add(simpleMetric("有效行数", validRows + " / " + totalRows));
        coverage.add(simpleMetric("覆盖重大项目", filtered.stream().map(NodeRow::majorProjectId).filter(Objects::nonNull).distinct().count() + " / " + majorProjects.size()));
        coverage.add(simpleMetric("覆盖子项目", filtered.stream().map(NodeRow::subprojectId).filter(Objects::nonNull).distinct().count() + " / " + subprojects.size()));
        coverage.add(simpleMetric("当前筛选节点", String.valueOf(filtered.size())));
        coverage.add(simpleMetric("高风险节点", String.valueOf(filtered.stream().filter(row -> "高".equals(normalizedRisk(row.riskLevel()))).count())));
        return coverage;
    }

    private ArrayNode buildGlossary() {
        ArrayNode glossary = objectMapper.createArrayNode();
        glossary.add(glossaryItem(
                "基础数据来源",
                "当前阶段项目基础数据优先来自 DTS 项目主体域数仓语义层；主数据接口已预留，状态为%s。".formatted(
                        blankToDash(masterDataState == null ? "" : masterDataState.status()))));
        glossary.add(glossaryItem("节点完成率", "当前筛选范围内已完成节点 / 全部节点，基于 biz_dwd_project_node_enriched 计算。"));
        glossary.add(glossaryItem("里程碑完成率", "里程碑节点中已完成占比，用于判断关键计划推进，当前来自数仓节点事实。"));
        glossary.add(glossaryItem("高风险节点数", "风险等级为高的节点数量，优先用于领导盯防，当前来自数仓节点事实。"));
        glossary.add(glossaryItem("健康度", "综合考虑完成情况、风险等级、延期天数的 0-100 分，当前由项目看板服务基于数仓节点数据聚合。"));
        glossary.add(glossaryItem(
                "专题绑定来源",
                "项目管理专题通过 topic binding 动态映射到现场 ODS 表；当前状态为%s。".formatted(blankToDash(topicBindingState == null ? "" : topicBindingState.status()))));
        return glossary;
    }

    private ArrayNode buildMissingChecklist() {
        ArrayNode checklist = objectMapper.createArrayNode();
        checklist.add(checklistItem(
                "topic-binding-project-management",
                "项目管理专题逻辑实体绑定",
                topicBindingState == null ? "待确认" : blankToDash(topicBindingState.status()),
                topicBindingState == null ? "尚未读取到专题绑定状态。" : blankToDash(topicBindingState.message())));
        checklist.add(checklistItem(
                "master-data-placeholder",
                "项目主数据接口",
                masterDataState == null ? "未接入" : blankToDash(masterDataState.status()),
                masterDataState == null ? "已预留项目主数据接口，当前未启用。" : blankToDash(masterDataState.message())));
        checklist.add(checklistItem(
                "latest-batch",
                "最新项目主体域批次",
                latestBatch == null ? "待上传" : latestBatch.modeled() ? "已建模" : "处理中",
                latestBatch == null ? "尚未检测到项目主体域正式批次。" : blankToDash(latestBatch.sourceFileName())));
        checklist.add(checklistItem(
                "warehouse-authority",
                "项目基础数据权威来源",
                "数仓承载",
                "当前重大项目、子项目、节点基础信息全部来自项目主体域数仓维表和语义层。主数据系统建成后可切换到预留接口。"));
        checklist.add(checklistItem(
                "missing-major-mapping",
                "数仓层级映射维表",
                latestBatch != null && latestBatch.unmappedSubprojectCount() == 0 ? "已覆盖" : "待补充",
                "当前由数仓映射维表维护，未映射子项目数：" + (latestBatch == null ? 0 : latestBatch.unmappedSubprojectCount())));
        checklist.add(checklistItem(
                "missing-delay-dim",
                "数仓延期原因标准枚举",
                latestBatch != null && latestBatch.unknownDelayReasonCount() == 0 ? "已覆盖" : "待确认",
                "当前由数仓维表维护，未分类延期原因数：" + (latestBatch == null ? 0 : latestBatch.unknownDelayReasonCount())));
        checklist.add(checklistItem("missing-resource-load", "资源投入与工时", "待客户补充", "当前数仓尚未沉淀资源与工时事实，后续补齐后可支撑科室负载分析。"));
        return checklist;
    }

    private ArrayNode buildDataSources() {
        ArrayNode sources = objectMapper.createArrayNode();
        sources.add(dataSourceItem(
                "topic_binding_project_subject_domain",
                topicBindingState == null
                        ? "项目管理专题逻辑实体绑定状态未加载。"
                        : "逻辑 source %s.%s 当前绑定到 %s.%s，状态：%s。".formatted(
                                blankToDash(topicBindingState.sourceName()),
                                blankToDash(topicBindingState.logicalTableName()),
                                blankToDash(topicBindingState.schemaName()),
                                blankToDash(topicBindingState.tableName()),
                                blankToDash(topicBindingState.status()))));
        sources.add(dataSourceItem(
                "project_master_data_placeholder",
                masterDataState == null
                        ? "预留的项目主数据接口，当前未接入；正式数据仍以数仓为准。"
                        : "预留的项目主数据接口，当前状态：%s。%s".formatted(
                                blankToDash(masterDataState.status()),
                                blankToDash(masterDataState.message()))));
        sources.add(dataSourceItem(
                latestBatch == null ? "正式项目主体域批次" : blankToDash(latestBatch.sourceFileName()),
                latestBatch == null ? "等待项目主体域正式批次上传到数仓。" : "项目主体域上传批次，进入数仓后作为当前基础数据入口。"));
        sources.add(dataSourceItem("pm_ods_project_progress_batch", "项目主体域批次与质量元数据，当前口径支撑批次信息来自此表。"));
        sources.add(dataSourceItem("pm_dim_major_project / pm_dim_subproject / pm_map_node_subject", "项目基础信息、层级映射与节点归属维表，当前承担原本应由主数据系统提供的基础语义。"));
        sources.add(dataSourceItem("biz_dwd_project_node_enriched", "项目主体域正式 DWD 节点明细，项目看板当前的基础事实全部来自此表。"));
        sources.add(dataSourceItem("biz_dws_week_subproject_summary / biz_ads_major_project_overview / biz_ads_major_project_tree_snapshot", "项目看板趋势、总览和树状进度语义层，当前全部来自数仓模型。"));
        return sources;
    }

    private List<NodeRow> applyScopeFilters(Filters filters) {
        return nodes.stream()
                .filter(node -> filters.programId() == null || filters.programId().isBlank() || filters.programId().equals(node.programId()))
                .filter(node -> filters.majorProjectId() == null || filters.majorProjectId().isBlank() || filters.majorProjectId().equals(node.majorProjectId()))
                .filter(node -> filters.deptId() == null || filters.deptId().isBlank() || filters.deptId().equals(node.dept()))
                .filter(node -> filters.riskLevel() == null || filters.riskLevel().isBlank() || filters.riskLevel().equals(normalizedRisk(node.riskLevel())))
                .toList();
    }

    private List<NodeRow> applyFilters(Filters filters) {
        return applyScopeFilters(filters).stream()
                .filter(node -> filters.dateFrom() == null || node.planDate() == null || !node.planDate().isBefore(filters.dateFrom()))
                .filter(node -> filters.dateTo() == null || node.planDate() == null || !node.planDate().isAfter(filters.dateTo()))
                .toList();
    }

    private List<NodeRow> filterPlannedInPeriod(List<NodeRow> scopedRows, Filters filters) {
        if (filters.dateFrom() == null && filters.dateTo() == null) {
            return scopedRows;
        }
        return scopedRows.stream()
                .filter(node -> inDateRange(node.planDate(), filters.dateFrom(), filters.dateTo()))
                .toList();
    }

    private List<NodeRow> filterDueByEnd(List<NodeRow> scopedRows, Filters filters) {
        if (filters.dateTo() == null) {
            return filters.dateFrom() == null ? scopedRows : filterPlannedInPeriod(scopedRows, filters);
        }
        return scopedRows.stream()
                .filter(node -> node.planDate() != null && !node.planDate().isAfter(filters.dateTo()))
                .toList();
    }

    private List<NodeRow> filterOutsideCompletedRows(List<NodeRow> scopedRows, Filters filters, List<NodeRow> periodRows) {
        if (filters.dateFrom() == null && filters.dateTo() == null) {
            return List.of();
        }
        Map<String, NodeRow> periodById = periodRows.stream()
                .filter(node -> node.nodeId() != null)
                .collect(Collectors.toMap(NodeRow::nodeId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        return scopedRows.stream()
                .filter(NodeRow::completed)
                .filter(node -> inDateRange(node.actualDate(), filters.dateFrom(), filters.dateTo()))
                .filter(node -> !periodById.containsKey(node.nodeId()))
                .toList();
    }

    private boolean inDateRange(LocalDate value, LocalDate dateFrom, LocalDate dateTo) {
        if (value == null) {
            return false;
        }
        if (dateFrom != null && value.isBefore(dateFrom)) {
            return false;
        }
        if (dateTo != null && value.isAfter(dateTo)) {
            return false;
        }
        return true;
    }

    private List<ObjectNode> uniqueValues(List<MajorProjectRow> rows, Function<MajorProjectRow, String> valueFn, Function<MajorProjectRow, String> labelFn) {
        return rows.stream()
                .filter(row -> valueFn.apply(row) != null && !valueFn.apply(row).isBlank())
                .collect(Collectors.toMap(valueFn, labelFn, (left, right) -> left, LinkedHashMap::new))
                .entrySet().stream()
                .map(entry -> option(entry.getKey(), entry.getValue()))
                .toList();
    }

    private ObjectNode option(String value, String label) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("value", blankToEmpty(value));
        option.put("label", blankToDash(label));
        return option;
    }

    private ObjectNode kpi(String key, String label, String value, String unit) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", key);
        node.put("label", label);
        node.put("value", value);
        node.put("unit", unit);
        return node;
    }

    private ObjectNode simpleMetric(String label, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("label", label);
        node.put("value", value);
        return node;
    }

    private ObjectNode glossaryItem(String indicator, String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("indicator", indicator);
        node.put("description", description);
        return node;
    }

    private ObjectNode checklistItem(String id, String title, String status, String detail) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("title", title);
        node.put("status", status);
        node.put("detail", detail);
        return node;
    }

    private ObjectNode dataSourceItem(String name, String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("description", description);
        return node;
    }

    private String latestUpdate(List<NodeRow> filtered) {
        String latest = filtered.stream()
                .map(node -> node.actualDate() != null ? node.actualDate() : node.planDate())
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .map(LocalDate::toString)
                .orElse("");
        if (!latest.isBlank()) {
            return latest;
        }
        if (latestBatch != null && latestBatch.refreshedAt() != null) {
            return latestBatch.refreshedAt().toLocalDate().toString();
        }
        if (latestBatch != null && latestBatch.uploadedAt() != null) {
            return latestBatch.uploadedAt().toLocalDate().toString();
        }
        return "";
    }

    private String buildScopeText(Filters filters, List<NodeRow> filtered) {
        long majorCount = filtered.stream().map(NodeRow::majorProjectId).filter(Objects::nonNull).distinct().count();
        String scope = "%d 个重大项目 / %d 个子项目 / %d 个节点".formatted(
                majorCount,
                filtered.stream().map(NodeRow::subprojectId).filter(Objects::nonNull).distinct().count(),
                filtered.size());
        List<String> tags = new ArrayList<>();
        if (filters.programId() != null && !filters.programId().isBlank()) {
            tags.add(filters.programId());
        }
        if (filters.riskLevel() != null && !filters.riskLevel().isBlank()) {
            tags.add("风险=" + filters.riskLevel());
        }
        return tags.isEmpty() ? scope : scope + "，筛选：" + String.join(" / ", tags);
    }

    private String percent(long numerator, long denominator) {
        return String.valueOf(Math.round(numericPercent(numerator, denominator)));
    }

    private String percentValue(long numerator, long denominator) {
        return String.valueOf(numericPercent(numerator, denominator));
    }

    private double numericPercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.round((numerator * 10000.0) / denominator) / 100.0;
    }

    private double averageHealth(List<NodeRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        double value = rows.stream().mapToDouble(NodeRow::healthScore).average().orElse(0);
        return Math.round(value * 100.0) / 100.0;
    }

    private String topDelayReason(List<NodeRow> rows) {
        return rows.stream()
                .filter(NodeRow::delayed)
                .filter(node -> node.delayReasonCategory() != null)
                .collect(Collectors.groupingBy(NodeRow::delayReasonCategory, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> delayReasonLabel(entry.getKey()))
                .orElse("正常推进");
    }

    private String aggregateStatus(List<NodeRow> rows) {
        if (rows.isEmpty()) {
            return "暂无数据";
        }
        if (rows.stream().allMatch(NodeRow::completed)) {
            return "已完成";
        }
        if (rows.stream().anyMatch(row -> "高".equals(normalizedRisk(row.riskLevel())) && row.delayed())) {
            return "高风险延期";
        }
        if (rows.stream().anyMatch(NodeRow::delayed)) {
            return "延期风险";
        }
        if (rows.stream().anyMatch(NodeRow::openRisk)) {
            return "推进中";
        }
        return "按计划推进";
    }

    private String aggregateRisk(List<NodeRow> rows) {
        if (rows.stream().anyMatch(row -> "高".equals(normalizedRisk(row.riskLevel())))) {
            return "高";
        }
        if (rows.stream().anyMatch(row -> "中".equals(normalizedRisk(row.riskLevel())))) {
            return "中";
        }
        if (rows.stream().anyMatch(row -> "低".equals(normalizedRisk(row.riskLevel())))) {
            return "低";
        }
        return "未知";
    }

    private String formatWeek(LocalDate weekStart) {
        return weekStart == null ? "" : weekStart + "周";
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String delayReasonLabel(String key) {
        return switch (blankToEmpty(key)) {
            case "technical" -> "技术攻关";
            case "quality" -> "质量整改";
            case "change" -> "计划变更";
            case "coordination" -> "接口协同";
            case "supplier" -> "外协外购";
            case "test" -> "试验排期";
            case "archive" -> "资料归档";
            default -> "正常推进";
        };
    }

    private String normalizedRisk(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return "未知";
        }
        String normalized = riskLevel.trim();
        if (normalized.startsWith("高")) {
            return "高";
        }
        if (normalized.startsWith("中")) {
            return "中";
        }
        if (normalized.startsWith("低")) {
            return "低";
        }
        return "未知";
    }

    private int riskWeight(String riskLevel) {
        return switch (normalizedRisk(riskLevel)) {
            case "高" -> 3;
            case "中" -> 2;
            case "低" -> 1;
            default -> 0;
        };
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record Filters(
            String programId,
            String majorProjectId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String deptId,
            String riskLevel) {}

    private record OverviewPeriodMetrics(
            long periodNodeTotalCount,
            long pendingNormalCount,
            long dueNodeCount,
            long outsideCompletedCount,
            long incompleteNodeCount,
            long onTimeCount,
            long overdueCompletedCount,
            long completedNodeCount,
            long milestoneOnTimeCount,
            long milestoneOverdueCompletedCount,
            long milestoneIncompleteCount) {}

    private record MajorProjectRow(
            String majorProjectId,
            String majorProjectCode,
            String majorProjectName,
            String programId,
            String programName,
            String ownerDept,
            String ownerLeader,
            LocalDate startDate,
            LocalDate planEndDate,
            String status,
            String remark) {}

    private record SubprojectRow(
            String subprojectId,
            String subprojectName,
            String majorProjectId,
            String projectNo,
            String subsystemName,
            String ownerDept,
            String ownerUser,
            String projectManager,
            LocalDate planStartDate,
            LocalDate planEndDate,
            String status,
            String priorityLevel,
            String remark) {}

    private record NodeRow(
            String nodeId,
            String projectNo,
            String subsystem,
            String nodeTask,
            String nodeType,
            String owner,
            String dept,
            String projectManager,
            LocalDate planDate,
            LocalDate actualDate,
            String completionStatus,
            String riskLevel,
            int delayDays,
            String delayReasonCategory,
            String majorProjectId,
            String majorProjectName,
            String programId,
            String programName,
            String subprojectId,
            String subprojectName,
            String incompleteReason,
            String delayImpact,
            boolean keyNode,
            boolean milestone) {

        String normalizedNodeType() {
            if (nodeType == null || nodeType.isBlank()) {
                return "";
            }
            return nodeType.trim();
        }

        boolean normalPending() {
            return "正常待完成".equals(completionStatus);
        }

        boolean onTimeCompleted() {
            return "按时完成".equals(completionStatus);
        }

        boolean overdueCompleted() {
            return "超期已完成已变更".equals(completionStatus)
                    || "超期已完成未变更".equals(completionStatus);
        }

        boolean abnormalPending() {
            return "不正常待变更".equals(completionStatus);
        }

        boolean overdueIncompleteUnchanged() {
            return "超期未完成未变更".equals(completionStatus);
        }

        boolean overdueIncompleteChanged() {
            return "超期未完成已变更".equals(completionStatus);
        }

        boolean overdueCompletedUnchanged() {
            return "超期已完成未变更".equals(completionStatus);
        }

        boolean changedStatus() {
            return completionStatus != null && completionStatus.contains("已变更");
        }

        boolean generalNode() {
            return "一般节点".equals(normalizedNodeType());
        }

        boolean milestoneNode() {
            return "里程碑节点".equals(normalizedNodeType()) || milestone;
        }

        boolean majorNode() {
            return "重大节点".equals(normalizedNodeType());
        }

        boolean importantNode() {
            return "重要节点".equals(normalizedNodeType());
        }

        boolean completed() {
            return onTimeCompleted() || overdueCompleted();
        }

        boolean openRisk() {
            return abnormalPending() || overdueIncompleteUnchanged() || overdueIncompleteChanged();
        }

        boolean delayed() {
            return delayDays > 0
                    || "超期已完成已变更".equals(completionStatus)
                    || "超期已完成未变更".equals(completionStatus)
                    || "超期未完成未变更".equals(completionStatus)
                    || "超期未完成已变更".equals(completionStatus);
        }

        double healthScore() {
            double value = 100
                    - switch (riskLevel == null ? "" : riskLevel) {
                        case "高" -> 35;
                        case "中" -> 18;
                        case "低" -> 5;
                        default -> 0;
                    }
                    - Math.min(Math.max(delayDays, 0), 30)
                    - (openRisk() ? 12 : "正常待完成".equals(completionStatus) ? 4 : 0)
                    + (completed() && "按时完成".equals(completionStatus) ? 8 : completed() ? 3 : 0);
            return Math.max(0, Math.min(100, value));
        }

        String statusLabel() {
            if ("按时完成".equals(completionStatus)) {
                return "按时完成";
            }
            if ("超期已完成已变更".equals(completionStatus) || "超期已完成未变更".equals(completionStatus)) {
                return "已完成但有延期";
            }
            if (delayed()) {
                return "延期中";
            }
            if ("正常待完成".equals(completionStatus)) {
                return "计划中";
            }
            return "推进中";
        }
    }
}
