package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogGovernanceResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogTableSchemaRepository tableRepo;
    private final CatalogDatasetGrantRepository grantRepo;
    private final GovQualityRunRepository qualityRunRepo;
    private final GovIssueTicketRepository issueTicketRepo;
    private final AuditService audit;
    private final AccessChecker accessChecker;
    private final CatalogResourceHelper helper;

    public CatalogGovernanceResource(
        CatalogDatasetRepository datasetRepo,
        CatalogTableSchemaRepository tableRepo,
        CatalogDatasetGrantRepository grantRepo,
        GovQualityRunRepository qualityRunRepo,
        GovIssueTicketRepository issueTicketRepo,
        AuditService audit,
        AccessChecker accessChecker,
        CatalogResourceHelper helper
    ) {
        this.datasetRepo = datasetRepo;
        this.tableRepo = tableRepo;
        this.grantRepo = grantRepo;
        this.qualityRunRepo = qualityRunRepo;
        this.issueTicketRepo = issueTicketRepo;
        this.audit = audit;
        this.accessChecker = accessChecker;
        this.helper = helper;
    }

    @GetMapping("/datasets/{id}/governance-health")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getDatasetGovernanceHealth(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> payload = buildDatasetGovernanceHealth(dataset);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看资产治理健康");
        auditPayload.put("datasetId", id.toString());
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/ops/reconciliation")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> catalogReconciliation(
        @RequestParam(name = "sampleLimit", defaultValue = "20") int sampleLimit
    ) {
        int safeSampleLimit = Math.max(5, Math.min(sampleLimit, 100));
        Map<String, Object> payload = buildCatalogReconciliation(safeSampleLimit);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "执行资产中心一致性核对");
        auditPayload.put("sampleLimit", safeSampleLimit);
        Object assertionCount = payload.get("assertionCount");
        if (assertionCount != null) {
            auditPayload.put("assertionCount", assertionCount);
        }
        audit.auditAction("CATALOG_RECONCILIATION_CHECK", AuditStage.SUCCESS, "catalog", auditPayload);
        return ApiResponses.ok(payload);
    }

    private Map<String, Object> buildDatasetGovernanceHealth(CatalogDataset dataset) {
        UUID datasetId = dataset != null ? dataset.getId() : null;
        if (datasetId == null) {
            return Map.of();
        }
        Instant now = Instant.now();
        Instant trendStart = now.minusSeconds(86400L * GOVERNANCE_TREND_DAYS);
        List<GovQualityRun> recentRuns = qualityRunRepo.findByDatasetId(datasetId, PageRequest.of(0, 200, Sort.by("createdDate").descending()));
        long totalRuns = qualityRunRepo.countByDatasetId(datasetId);
        long passRuns = 0;
        long failRuns = 0;
        long runningRuns = 0;
        Map<String, Integer> failureCategoryCount = new LinkedHashMap<>();
        Map<String, Map<String, Object>> trend = new LinkedHashMap<>();
        for (int i = GOVERNANCE_TREND_DAYS - 1; i >= 0; i--) {
            Instant day = now.minusSeconds(86400L * i);
            String key = day.toString().substring(0, 10);
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("date", key);
            slot.put("total", 0);
            slot.put("passed", 0);
            slot.put("failed", 0);
            trend.put(key, slot);
        }
        for (GovQualityRun run : recentRuns) {
            String status = helper.normalizeUpper(run != null ? run.getStatus() : null);
            if (QUALITY_PASS_STATUSES.contains(status)) {
                passRuns += 1;
            } else if (QUALITY_FAIL_STATUSES.contains(status)) {
                failRuns += 1;
                String category = helper.trimToNull(run != null ? run.getErrorCategory() : null);
                failureCategoryCount.merge(category != null ? category : "UNKNOWN", 1, Integer::sum);
            } else if ("RUNNING".equals(status) || "PENDING".equals(status) || "QUEUED".equals(status)) {
                runningRuns += 1;
            }
            Instant createdAt = run != null ? run.getCreatedDate() : null;
            if (createdAt == null || createdAt.isBefore(trendStart)) {
                continue;
            }
            String dayKey = createdAt.toString().substring(0, 10);
            Map<String, Object> slot = trend.get(dayKey);
            if (slot == null) {
                continue;
            }
            slot.put("total", ((Number) slot.get("total")).intValue() + 1);
            if (QUALITY_PASS_STATUSES.contains(status)) {
                slot.put("passed", ((Number) slot.get("passed")).intValue() + 1);
            }
            if (QUALITY_FAIL_STATUSES.contains(status)) {
                slot.put("failed", ((Number) slot.get("failed")).intValue() + 1);
            }
        }
        List<Map<String, Object>> failureTop = failureCategoryCount
            .entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", entry.getKey());
                row.put("count", entry.getValue());
                return row;
            })
            .toList();
        GovQualityRun latestRun = qualityRunRepo.findFirstByDatasetIdOrderByCreatedDateDesc(datasetId).orElse(null);

        List<GovIssueTicket> recentIssues = issueTicketRepo.findTop100ByDatasetIdOrderByCreatedDateDesc(datasetId);
        long issueTotal = issueTicketRepo.countByDatasetId(datasetId);
        long issueOpen = issueTicketRepo.countByDatasetIdAndStatusIn(datasetId, ISSUE_OPEN_STATUSES);
        long issueClosed = issueTicketRepo.countByDatasetIdAndStatusIn(datasetId, ISSUE_CLOSED_STATUSES);
        long overdueIssue = recentIssues
            .stream()
            .filter(issue -> {
                String status = helper.normalizeUpper(issue != null ? issue.getStatus() : null);
                if (!ISSUE_OPEN_STATUSES.contains(status)) {
                    return false;
                }
                Instant dueAt = issue != null ? issue.getDueAt() : null;
                return dueAt != null && dueAt.isBefore(now) && issue.getResolvedAt() == null;
            })
            .count();
        List<Map<String, Object>> issueTop = recentIssues
            .stream()
            .filter(Objects::nonNull)
            .limit(5)
            .map(issue -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", issue.getId() != null ? issue.getId().toString() : null);
                row.put("title", helper.trimToNull(issue.getTitle()));
                row.put("status", helper.normalizeUpper(issue.getStatus()));
                row.put("priority", helper.normalizeUpper(issue.getPriority()));
                row.put("severity", helper.normalizeUpper(issue.getSeverity()));
                row.put("dueAt", issue.getDueAt());
                row.put("updatedAt", issue.getLastModifiedDate());
                return row;
            })
            .toList();

        int healthScore = 100;
        healthScore -= Math.min(40, (int) failRuns * 8);
        healthScore -= Math.min(30, (int) issueOpen * 2);
        healthScore -= Math.min(20, (int) overdueIssue * 5);
        if (failRuns == 0 && passRuns > 0) {
            healthScore = Math.min(100, healthScore + 5);
        }
        String healthLevel = healthScore >= 80 ? "HEALTHY" : healthScore >= 60 ? "WARN" : "RISK";

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("totalRuns", totalRuns);
        quality.put("passRuns", passRuns);
        quality.put("failRuns", failRuns);
        quality.put("runningRuns", runningRuns);
        quality.put("latestRunAt", latestRun != null ? latestRun.getCreatedDate() : null);
        quality.put("latestStatus", latestRun != null ? helper.normalizeUpper(latestRun.getStatus()) : null);
        quality.put("failureTop", failureTop);
        quality.put("trend", new ArrayList<>(trend.values()));

        Map<String, Object> issues = new LinkedHashMap<>();
        issues.put("total", issueTotal);
        issues.put("open", issueOpen);
        issues.put("closed", issueClosed);
        issues.put("overdue", overdueIssue);
        issues.put("top", issueTop);

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("qualityRulesPath", "/governance/rules?runDatasetId=" + datasetId + "&runStatus=FAILED");
        links.put("qualityReportPath", "/governance/quality?datasetId=" + datasetId);
        links.put("issuesPath", "/governance/rules?issueDatasetId=" + datasetId + "&issueStatus=OPEN");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("datasetName", dataset.getName());
        payload.put("healthScore", healthScore);
        payload.put("healthLevel", healthLevel);
        payload.put("quality", quality);
        payload.put("issues", issues);
        payload.put("links", links);
        return payload;
    }

    private Map<String, Object> buildCatalogReconciliation(int sampleLimit) {
        long datasetTotal = datasetRepo.count();
        long enabledDatasets = datasetRepo.countByEnabledTrue();
        long staleDatasets = datasetRepo.countByLifecycleStatusIgnoreCase("STALE");
        long enabledNoSnapshot = datasetRepo.countByEnabledTrueAndSnapshotTimeIsNull();
        long noOwnerDept = datasetRepo.countByOwnerDeptIsNull();
        long qualityRunTotal = qualityRunRepo.count();
        long issueTicketTotal = issueTicketRepo.count();

        Set<UUID> datasetIds = datasetRepo.findAll(PageRequest.of(0, 10_000, Sort.by("id")))
            .getContent()
            .stream()
            .map(CatalogDataset::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        long orphanRunCount;
        List<GovQualityRun> orphanRuns;
        long orphanIssueCount;
        List<GovIssueTicket> orphanIssues;
        if (datasetIds.isEmpty()) {
            orphanRunCount = qualityRunTotal;
            orphanRuns = Collections.emptyList();
            orphanIssueCount = issueTicketTotal;
            orphanIssues = Collections.emptyList();
        } else {
            orphanRunCount = qualityRunRepo.countByDatasetIdNotIn(datasetIds);
            orphanRuns = qualityRunRepo.findByDatasetIdNotIn(datasetIds, PageRequest.of(0, sampleLimit));
            orphanIssueCount = issueTicketRepo.countByDatasetIdNotIn(datasetIds);
            orphanIssues = issueTicketRepo.findByDatasetIdNotIn(datasetIds, PageRequest.of(0, sampleLimit));
        }

        List<Map<String, Object>> assertions = new ArrayList<>();
        assertions.add(helper.assertion("A01", "资产目录非空", datasetTotal > 0, "ERROR", "datasetCount=" + datasetTotal, "至少完成一批元数据采集后再发布。"));
        assertions.add(
            helper.assertion(
                "A02",
                "质量运行无孤儿记录",
                orphanRunCount == 0,
                "ERROR",
                "orphanQualityRuns=" + orphanRunCount,
                "检查治理运行数据中的 dataset_id 是否仍在资产目录中。"
            )
        );
        assertions.add(
            helper.assertion(
                "A03",
                "问题工单无孤儿记录",
                orphanIssueCount == 0,
                "ERROR",
                "orphanIssueTickets=" + orphanIssueCount,
                "检查问题单中的 dataset_id 与资产目录同步状态。"
            )
        );
        assertions.add(
            helper.assertion(
                "A04",
                "启用资产存在快照时间",
                enabledNoSnapshot == 0,
                "WARN",
                "enabledWithoutSnapshot=" + enabledNoSnapshot,
                "建议先执行元数据采集，补齐 snapshot_time。"
            )
        );
        assertions.add(
            helper.assertion(
                "A05",
                "资产负责人部门已维护",
                noOwnerDept == 0,
                "WARN",
                "noOwnerDept=" + noOwnerDept,
                "建议补充 owner_dept，避免权限策略和工单路由失效。"
            )
        );
        assertions.add(
            helper.assertion(
                "A06",
                "失效资产占比可控",
                datasetTotal == 0 || ((double) staleDatasets / (double) datasetTotal) < 0.3d,
                "WARN",
                "staleRatio=" + (datasetTotal == 0 ? 0 : String.format(Locale.ROOT, "%.4f", ((double) staleDatasets / (double) datasetTotal))),
                "建议清理失效资产或重新采集，避免模型映射到历史表。"
            )
        );

        long failedCount = assertions.stream().filter(item -> !Boolean.TRUE.equals(item.get("passed"))).count();
        long errorCount = assertions
            .stream()
            .filter(item -> !Boolean.TRUE.equals(item.get("passed")))
            .filter(item -> Objects.equals(item.get("severity"), "ERROR"))
            .count();
        long warningCount = assertions
            .stream()
            .filter(item -> !Boolean.TRUE.equals(item.get("passed")))
            .filter(item -> Objects.equals(item.get("severity"), "WARN"))
            .count();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("datasetTotal", datasetTotal);
        counts.put("datasetEnabled", enabledDatasets);
        counts.put("datasetStale", staleDatasets);
        counts.put("tableTotal", tableRepo.count());
        counts.put("grantTotal", grantRepo.count());
        counts.put("qualityRunTotal", qualityRunTotal);
        counts.put("issueTicketTotal", issueTicketTotal);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put(
            "orphanQualityRuns",
            orphanRuns
                .stream()
                .map(run -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", run.getId() != null ? run.getId().toString() : null);
                    item.put("datasetId", run.getDatasetId() != null ? run.getDatasetId().toString() : null);
                    item.put("status", helper.normalizeUpper(run.getStatus()));
                    return item;
                })
                .toList()
        );
        details.put(
            "orphanIssueTickets",
            orphanIssues
                .stream()
                .map(issue -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", issue.getId() != null ? issue.getId().toString() : null);
                    item.put("datasetId", issue.getDatasetId() != null ? issue.getDatasetId().toString() : null);
                    item.put("status", helper.normalizeUpper(issue.getStatus()));
                    return item;
                })
                .toList()
        );

        List<Map<String, Object>> regressionChecklist = List.of(
            helper.checklistItem("UI-01", "资产列表筛选与分页", "/catalog/datasets", "验证关键字/主题域/密级/分层过滤与分页一致性。"),
            helper.checklistItem("UI-02", "资产详情信息完整性", "/catalog/datasets", "验证基础信息、结构信息、治理状态三页签数据完整。"),
            helper.checklistItem("UI-03", "搜索页命中一致性", "/catalog/search", "同一关键字在搜索页与资产列表返回主数据一致。"),
            helper.checklistItem("UI-04", "血缘影响查询", "/catalog/lineage", "资产详情跳转血缘后节点/边数量可复核。"),
            helper.checklistItem("UI-05", "权限审批闭环", "/security/dataset-access-approval", "申请-审批-授权记录可闭环追踪。")
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now());
        payload.put("assertionCount", assertions.size());
        payload.put("failedCount", failedCount);
        payload.put("errorCount", errorCount);
        payload.put("warningCount", warningCount);
        payload.put("counts", counts);
        payload.put("assertions", assertions);
        payload.put("details", details);
        payload.put("regressionChecklist", regressionChecklist);
        return payload;
    }
}
