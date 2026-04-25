package com.yuzhi.dts.platform.service.workbench;

import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetAccessRequestRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.security.DatasetDataAccessApprovalService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class WorkbenchService {

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogSchemaDriftEventRepository driftRepository;
    private final GovQualityRunRepository qualityRunRepository;
    private final DatasetDataAccessApprovalService accessApprovalService;
    private final CatalogDatasetAccessRequestRepository accessRequestRepository;

    public WorkbenchService(
        CatalogDatasetRepository datasetRepository,
        CatalogSchemaDriftEventRepository driftRepository,
        GovQualityRunRepository qualityRunRepository,
        DatasetDataAccessApprovalService accessApprovalService,
        CatalogDatasetAccessRequestRepository accessRequestRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.driftRepository = driftRepository;
        this.qualityRunRepository = qualityRunRepository;
        this.accessApprovalService = accessApprovalService;
        this.accessRequestRepository = accessRequestRepository;
    }

    public Map<String, Object> overview(String userLogin) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        String user = normalize(userLogin);

        if (StringUtils.hasText(user)) {
            data.put("myAssets", datasetRepository.countByCreatedBy(user));
        } else {
            data.put("myAssets", 0);
        }

        Instant todayStart = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        data.put("todayNewAssets", datasetRepository.countByCreatedDateAfter(todayStart));

        return data;
    }

    public List<Map<String, Object>> todoItems(String activeDept) {
        List<Map<String, Object>> items = new ArrayList<>();

        List<CatalogDatasetAccessTask> tasks = accessApprovalService.listPendingTasksForCurrentUser(activeDept);
        for (CatalogDatasetAccessTask task : tasks) {
            CatalogDatasetAccessRequest req = task.getRequestId() != null
                ? accessRequestRepository.findById(task.getRequestId()).orElse(null)
                : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "ACCESS_APPROVAL");
            row.put("title", req != null && StringUtils.hasText(req.getDatasetName()) ? req.getDatasetName() : "数据集访问申请");
            row.put("status", task.getStatus());
            row.put("createdAt", task.getCreatedDate());
            row.put("taskId", task.getId());
            row.put("requestId", task.getRequestId());
            row.put("requester", req != null ? req.getRequesterName() : null);
            row.put("datasetId", req != null ? req.getDatasetId() : null);
            items.add(row);
        }

        List<GovQualityRun> failedRuns = qualityRunRepository.findTop100ByStatusOrderByCreatedDateDesc("FAILED");
        for (GovQualityRun run : failedRuns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "QUALITY");
            row.put("title", run.getRule() != null ? run.getRule().getName() : "质量规则");
            row.put("status", run.getStatus());
            row.put("createdAt", run.getCreatedDate());
            row.put("datasetId", run.getDatasetId());
            row.put("message", run.getMessage());
            items.add(row);
            if (items.size() >= 50) break;
        }

        List<CatalogSchemaDriftEvent> driftEvents = driftRepository
            .findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate")))
            .getContent();
        for (CatalogSchemaDriftEvent event : driftEvents) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "SCHEMA_DRIFT");
            row.put("title", buildDriftTitle(event));
            row.put("status", "PENDING");
            row.put("createdAt", event.getCreatedDate());
            row.put("datasetId", event.getDatasetId());
            row.put("details", event.getDetailsJson());
            items.add(row);
            if (items.size() >= 80) break;
        }

        return items;
    }

    private String buildDriftTitle(CatalogSchemaDriftEvent event) {
        if (event == null) return "Schema Drift";
        String table = Optional.ofNullable(event.getHiveTable()).orElse("").trim();
        String database = Optional.ofNullable(event.getHiveDatabase()).orElse("").trim();
        if (!database.isEmpty() && !table.isEmpty()) {
            return database + "." + table;
        }
        return !table.isEmpty() ? table : "Schema Drift";
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
