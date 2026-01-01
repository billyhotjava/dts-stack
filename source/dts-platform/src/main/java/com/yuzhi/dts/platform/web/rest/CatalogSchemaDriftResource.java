package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog")
@Transactional(readOnly = true)
public class CatalogSchemaDriftResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final CatalogSchemaDriftEventRepository driftRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public CatalogSchemaDriftResource(
        CatalogSchemaDriftEventRepository driftRepo,
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        AuditService audit
    ) {
        this.driftRepo = driftRepo;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
    }

    @GetMapping("/datasets/{datasetId}/schema-drift")
    public ApiResponse<List<Map<String, Object>>> listForDataset(
        @PathVariable UUID datasetId,
        @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
        @RequestParam(name = "includeDetails", required = false, defaultValue = "false") boolean includeDetails
    ) {
        CatalogDataset dataset = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        if (!accessChecker.canRead(dataset)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该数据集");
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> payload = driftRepo
            .findTop200ByDatasetIdOrderByCreatedDateDesc(datasetId)
            .stream()
            .limit(safeLimit)
            .map(event -> toDto(event, includeDetails))
            .toList();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据集 Schema 漂移记录");
        auditPayload.put("datasetId", datasetId.toString());
        auditPayload.put("limit", safeLimit);
        auditPayload.put("includeDetails", includeDetails);
        audit.auditAction("CATALOG_SCHEMA_DRIFT_LIST", AuditStage.SUCCESS, datasetId.toString(), auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/schema-drift/by-run")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<List<Map<String, Object>>> listForRun(
        @RequestParam(name = "runId") UUID runId,
        @RequestParam(name = "limit", required = false, defaultValue = "200") int limit,
        @RequestParam(name = "includeDetails", required = false, defaultValue = "false") boolean includeDetails
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> payload = driftRepo
            .findTop200ByRunIdOrderByCreatedDateDesc(runId)
            .stream()
            .limit(safeLimit)
            .map(event -> toDto(event, includeDetails))
            .toList();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看采集批次 Schema 漂移记录");
        auditPayload.put("runId", runId.toString());
        auditPayload.put("limit", safeLimit);
        auditPayload.put("includeDetails", includeDetails);
        audit.auditAction("CATALOG_SCHEMA_DRIFT_LIST", AuditStage.SUCCESS, runId.toString(), auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/schema-drift/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable UUID id, @RequestParam(name = "includeDetails", required = false, defaultValue = "true") boolean includeDetails) {
        CatalogSchemaDriftEvent event = driftRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        CatalogDataset dataset = datasetRepo.findById(event.getDatasetId()).orElse(null);
        if (dataset != null && !accessChecker.canRead(dataset)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该数据集");
        }
        Map<String, Object> payload = toDto(event, includeDetails);
        audit.auditAction("CATALOG_SCHEMA_DRIFT_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看 Schema 漂移详情"));
        return ApiResponses.ok(payload);
    }

    private Map<String, Object> toDto(CatalogSchemaDriftEvent event, boolean includeDetails) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (event == null) {
            return dto;
        }
        if (event.getId() != null) dto.put("id", event.getId().toString());
        if (event.getRunId() != null) dto.put("runId", event.getRunId().toString());
        dto.put("integration", event.getIntegration());
        dto.put("datasetId", event.getDatasetId() != null ? event.getDatasetId().toString() : null);
        dto.put("hiveDatabase", event.getHiveDatabase());
        dto.put("hiveTable", event.getHiveTable());
        dto.put("addedCount", event.getAddedCount());
        dto.put("removedCount", event.getRemovedCount());
        dto.put("changedCount", event.getChangedCount());
        dto.put("createdDate", event.getCreatedDate());
        if (includeDetails) {
            dto.put("detailsJson", event.getDetailsJson());
        }
        return dto;
    }
}
