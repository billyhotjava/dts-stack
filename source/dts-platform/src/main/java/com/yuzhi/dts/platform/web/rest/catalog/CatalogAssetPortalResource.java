package com.yuzhi.dts.platform.web.rest.catalog;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetContract;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetMappingReportService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSchemaContract;
import com.yuzhi.dts.platform.service.catalog.CatalogLineageFailureReport;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetOverviewAggregator;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard;
import com.yuzhi.dts.platform.service.catalog.OpenMetadataAssetSyncService;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog/assets-v2")
public class CatalogAssetPortalResource {

    private final CatalogAssetPortalService assetPortalService;
    private final CatalogAssetMappingReportService mappingReportService;
    private final CatalogAssetIdentityResolutionAuditService resolutionAuditService;
    private final OpenMetadataAssetSyncService syncService;
    private final AuditService audit;
    private final CatalogResourceHelper helper;
    private final CatalogAssetTagWriteGuard assetTagWriteGuard;

    public CatalogAssetPortalResource(
        CatalogAssetPortalService assetPortalService,
        CatalogAssetMappingReportService mappingReportService,
        CatalogAssetIdentityResolutionAuditService resolutionAuditService,
        OpenMetadataAssetSyncService syncService,
        AuditService audit,
        CatalogResourceHelper helper,
        CatalogAssetTagWriteGuard assetTagWriteGuard
    ) {
        this.assetPortalService = assetPortalService;
        this.mappingReportService = mappingReportService;
        this.resolutionAuditService = resolutionAuditService;
        this.syncService = syncService;
        this.audit = audit;
        this.helper = helper;
        this.assetTagWriteGuard = assetTagWriteGuard;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<CatalogAssetPortalService.AssetPage> listAssets(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "service", required = false) String service,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "database", required = false) String database,
        @RequestParam(value = "schema", required = false) String schema,
        @RequestParam(value = "syncStatus", required = false) String syncStatus,
        @RequestParam(value = "classification", required = false) String classification,
        @RequestParam(value = "warehouseLayer", required = false) String warehouseLayer,
        @RequestParam(value = "ownerDept", required = false) String ownerDept,
        @RequestParam(value = "governanceStatus", required = false) String governanceStatus,
        @RequestParam(value = "matchStatus", required = false) String matchStatus,
        @RequestParam(value = "domainId", required = false) UUID domainId,
        @RequestParam(value = "domainUnassigned", required = false, defaultValue = "false") boolean domainUnassigned,
        @RequestParam(value = "tagIds", required = false) List<UUID> tagIds,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size,
        @RequestParam(value = "unclassified", required = false) Boolean unclassified,
        @RequestParam(value = "stale", required = false) Boolean stale,
        @RequestParam(value = "eligibility", required = false) String eligibility,
        @RequestParam(value = "servingStatus", required = false) String servingStatus,
        @RequestParam(value = "qualityStatus", required = false) String qualityStatus,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetPortalService.AssetPage result = assetPortalService.listAssets(
            new CatalogAssetPortalService.AssetQuery(
                keyword,
                service,
                type,
                database,
                schema,
                syncStatus,
                classification,
                warehouseLayer,
                ownerDept,
                governanceStatus,
                matchStatus,
                domainId,
                domainUnassigned,
                tagIds,
                page,
                size,
                unclassified,
                stale,
                eligibility,
                servingStatus,
                qualityStatus
            ),
            effDept
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "浏览OpenMetadata主目录资产");
        payload.put("returned", result.returned());
        payload.put("total", result.total());
        helper.putIfHasText(payload, "activeDept", effDept);
        helper.putIfHasText(payload, "keyword", keyword);
        if (tagIds != null && !tagIds.isEmpty()) {
            payload.put("tagCount", tagIds.stream().distinct().count());
        }
        audit.auditAction("CATALOG_ASSET_LIST", AuditStage.SUCCESS, "assets-v2", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/governance-intake")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogAssetPortalService.AssetPage> governanceIntake(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "classification", required = false) String classification,
        @RequestParam(value = "governanceStatus", required = false) String governanceStatus,
        @RequestParam(value = "matchStatus", required = false) String matchStatus,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetPortalService.AssetPage result = assetPortalService.listGovernanceIntakeAssets(
            new CatalogAssetPortalService.AssetQuery(
                keyword,
                null,
                null,
                null,
                null,
                null,
                classification,
                null,
                null,
                governanceStatus,
                matchStatus,
                null,
                false,
                page,
                size
            ),
            effDept
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看待治理资产入口");
        payload.put("returned", result.returned());
        payload.put("total", result.total());
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_GOVERNANCE_INTAKE_VIEW", AuditStage.SUCCESS, "assets-v2-governance-intake", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ApiResponse<CatalogAssetOverviewAggregator.AssetOverview> overview(
        @RequestParam(value = "domainId", required = false) UUID domainId,
        @RequestParam(value = "domainUnassigned", required = false, defaultValue = "false") boolean domainUnassigned,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetOverviewAggregator.AssetOverview overview = assetPortalService.overview(
            new CatalogAssetPortalService.AssetQuery(null, null, null, null, null, null, null, null, null, null, null, domainId, domainUnassigned, 0, 200),
            effDept
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看资产概览统计");
        payload.put("total", overview.total());
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_OVERVIEW", AuditStage.SUCCESS, "assets-v2-overview", payload);
        return ApiResponses.ok(overview);
    }

    @GetMapping("/governance-gaps")
    @Transactional(readOnly = true)
    public ApiResponse<CatalogAssetPortalService.GovernanceGapReport> governanceGaps(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "service", required = false) String service,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "database", required = false) String database,
        @RequestParam(value = "schema", required = false) String schema,
        @RequestParam(value = "syncStatus", required = false) String syncStatus,
        @RequestParam(value = "classification", required = false) String classification,
        @RequestParam(value = "warehouseLayer", required = false) String warehouseLayer,
        @RequestParam(value = "ownerDept", required = false) String ownerDept,
        @RequestParam(value = "governanceStatus", required = false) String governanceStatus,
        @RequestParam(value = "matchStatus", required = false) String matchStatus,
        @RequestParam(value = "domainId", required = false) UUID domainId,
        @RequestParam(value = "domainUnassigned", required = false, defaultValue = "false") boolean domainUnassigned,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "50") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetPortalService.GovernanceGapReport result = assetPortalService.governanceGaps(
            new CatalogAssetPortalService.AssetQuery(
                keyword,
                service,
                type,
                database,
                schema,
                syncStatus,
                classification,
                warehouseLayer,
                ownerDept,
                governanceStatus,
                matchStatus,
                domainId,
                domainUnassigned,
                page,
                size
            ),
            effDept
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看Catalog资产治理缺口报告");
        payload.put("returned", result.content().size());
        payload.put("inspected", result.inspected());
        payload.put("skipped", result.skipped());
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_GOVERNANCE_GAP_VIEW", AuditStage.SUCCESS, "assets-v2-governance-gaps", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/lineage-failures")
    @Transactional(readOnly = true)
    public ApiResponse<CatalogLineageFailureReport> lineageFailures(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "service", required = false) String service,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "database", required = false) String database,
        @RequestParam(value = "schema", required = false) String schema,
        @RequestParam(value = "syncStatus", required = false) String syncStatus,
        @RequestParam(value = "classification", required = false) String classification,
        @RequestParam(value = "warehouseLayer", required = false) String warehouseLayer,
        @RequestParam(value = "ownerDept", required = false) String ownerDept,
        @RequestParam(value = "governanceStatus", required = false) String governanceStatus,
        @RequestParam(value = "matchStatus", required = false) String matchStatus,
        @RequestParam(value = "domainId", required = false) UUID domainId,
        @RequestParam(value = "domainUnassigned", required = false, defaultValue = "false") boolean domainUnassigned,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "50") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogLineageFailureReport result = assetPortalService.lineageFailures(
            new CatalogAssetPortalService.AssetQuery(
                keyword,
                service,
                type,
                database,
                schema,
                syncStatus,
                classification,
                warehouseLayer,
                ownerDept,
                governanceStatus,
                matchStatus,
                domainId,
                domainUnassigned,
                page,
                size
            ),
            effDept
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看Catalog血缘失败和发布阻断报告");
        payload.put("returned", result.content().size());
        payload.put("inspected", result.inspected());
        payload.put("skipped", result.skipped());
        payload.put("blocking", result.severityCounts().getOrDefault("BLOCKING", 0L));
        payload.put("warning", result.severityCounts().getOrDefault("WARNING", 0L));
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_LINEAGE_FAILURE_REPORT_VIEW", AuditStage.SUCCESS, "assets-v2-lineage-failures", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/migration/dry-run")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogAssetMappingReportService.MappingReport> migrationDryRun() {
        CatalogAssetMappingReportService.MappingReport result = mappingReportService.dryRun();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "执行Catalog历史资产映射dry-run");
        payload.put("candidateCount", result.candidateCount());
        payload.put("existingMappingCount", result.existingMappingCount());
        payload.put("conflictCount", result.conflictCount());
        audit.auditAction("CATALOG_ASSET_MIGRATION_DRY_RUN", AuditStage.SUCCESS, "assets-v2-migration-dry-run", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<CatalogAssetPortalService.AssetDetail> getAsset(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetPortalService.AssetDetail result = assetPortalService.getAsset(id, effDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看OpenMetadata主目录资产详情");
        payload.put("assetId", id.toString());
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/{id}/contract")
    @Transactional(readOnly = true)
    public ApiResponse<AssetContractResponse> getAssetContract(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetContract result = assetPortalService.getAssetContract(id, effDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看Catalog资产稳定读取契约");
        payload.put("assetId", id.toString());
        payload.put("grantAssetType", result.grantAssetType());
        payload.put("grantAssetId", result.grantAssetId());
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_CONTRACT_VIEW", AuditStage.SUCCESS, id.toString(), payload);
        boolean canTag = assetTagWriteGuard.canTag(
            new AssetRef(result.grantAssetType(), result.assetKey())
        );
        return ApiResponses.ok(new AssetContractResponse(result, canTag));
    }

    @GetMapping("/{id}/schema-contract")
    @Transactional(readOnly = true)
    public ApiResponse<CatalogAssetSchemaContract> getAssetSchemaContract(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetSchemaContract result = assetPortalService.getAssetSchemaContract(id, effDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看Catalog资产字段读取契约");
        payload.put("assetId", id.toString());
        payload.put("grantAssetType", result.asset().grantAssetType());
        payload.put("grantAssetId", result.asset().grantAssetId());
        payload.put("schemaSource", result.schemaSource());
        payload.put("columnCount", result.columnCount());
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_SCHEMA_CONTRACT_VIEW", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(result);
    }

    @PatchMapping("/{id}/governance")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogAssetPortalService.AssetDetail> updateGovernance(
        @PathVariable UUID id,
        @RequestBody CatalogAssetPortalService.GovernanceUpdate body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetPortalService.AssetDetail result = assetPortalService.updateGovernance(id, body, effDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "更新OpenMetadata资产治理扩展");
        payload.put("assetId", id.toString());
        if (body != null) {
            helper.putIfHasText(payload, "classification", body.classification());
            helper.putIfHasText(payload, "warehouseLayer", body.warehouseLayer());
            helper.putIfHasText(payload, "ownerDept", body.ownerDept());
            helper.putIfHasText(payload, "businessOwner", body.businessOwner());
            helper.putIfHasText(payload, "lifecycleStatus", body.lifecycleStatus());
            if (body.domainId() != null) {
                payload.put("domainId", body.domainId().toString());
            }
            if (body.enabled() != null) {
                payload.put("enabled", body.enabled());
            }
        }
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_UPDATE", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/{id}/lineage")
    @Transactional(readOnly = true)
    public ApiResponse<CatalogAssetPortalService.LineageView> getLineage(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDepartment(activeDept);
        CatalogAssetPortalService.LineageView result = assetPortalService.getLineage(id, effDept);
        audit.auditAction("CATALOG_LINEAGE_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看OpenMetadata资产血缘缓存"));
        return ApiResponses.ok(result);
    }

    @GetMapping("/diagnostics")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogAssetPortalService.MappingDiagnostics> diagnostics() {
        CatalogAssetPortalService.MappingDiagnostics result = assetPortalService.diagnostics();
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "assets-v2-diagnostics", Map.of("summary", "查看OpenMetadata资产映射诊断"));
        return ApiResponses.ok(result);
    }

    @GetMapping("/resolution-failures")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<List<ResolutionFailureResponse>> resolutionFailures(
        @RequestParam(name = "since", required = false) Instant since,
        @RequestParam(name = "limit", required = false, defaultValue = "100") int limit
    ) {
        List<ResolutionFailureResponse> result = resolutionAuditService.recentFailures(since, limit).stream()
            .map(ResolutionFailureResponse::from)
            .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看Catalog资产身份解析失败记录");
        payload.put("returned", result.size());
        if (since != null) {
            payload.put("since", since.toString());
        }
        audit.auditAction("CATALOG_ASSET_RESOLUTION_FAILURE_VIEW", AuditStage.SUCCESS, "assets-v2-resolution-failures", payload);
        return ApiResponses.ok(result);
    }

    @PostMapping("/sync")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<OpenMetadataAssetSyncService.SyncResult> syncAssets(
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        OpenMetadataAssetSyncService.SyncResult result = syncService.syncTables(limit);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "同步OpenMetadata资产缓存");
        payload.put("assetCount", result.assetCount());
        payload.put("mappedCount", result.mappedCount());
        payload.put("columnCount", result.columnCount());
        payload.put("skippedCount", result.skippedCount());
        payload.put("failedCount", result.failedCount());
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "assets-v2-sync", payload);
        return ApiResponses.ok(result);
    }

    @PostMapping("/{id}/lineage/sync")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<OpenMetadataAssetSyncService.SyncResult> syncLineage(
        @PathVariable UUID id,
        @RequestParam(value = "upstreamDepth", required = false, defaultValue = "2") int upstreamDepth,
        @RequestParam(value = "downstreamDepth", required = false, defaultValue = "2") int downstreamDepth
    ) {
        OpenMetadataAssetSyncService.SyncResult result = syncService.syncLineage(id.toString(), upstreamDepth, downstreamDepth);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "同步OpenMetadata资产血缘缓存");
        payload.put("assetId", id.toString());
        payload.put("lineageEdgeCount", result.lineageEdgeCount());
        audit.auditAction("CATALOG_LINEAGE_SYNC", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(result);
    }

    private String resolveActiveDepartment(String requestedActiveDept) {
        String tokenDepartment = SecurityUtils.getCurrentUserDept().orElseGet(() -> helper.claim("dept_code"));
        if (
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES) &&
            StringUtils.hasText(requestedActiveDept)
        ) {
            return requestedActiveDept.trim();
        }
        return StringUtils.hasText(tokenDepartment) ? tokenDepartment.trim() : null;
    }

    public record ResolutionFailureResponse(
        UUID id,
        String ref,
        Instant requestedAt,
        String caller,
        String typeHintGuess,
        String reason
    ) {
        static ResolutionFailureResponse from(CatalogAssetResolutionFailure failure) {
            return new ResolutionFailureResponse(
                failure.getId(),
                failure.getRef(),
                failure.getRequestedAt(),
                failure.getCaller(),
                failure.getTypeHintGuess(),
                failure.getReason()
            );
        }
    }

    public record AssetContractResponse(
        @JsonUnwrapped CatalogAssetContract contract,
        boolean canTag
    ) {}
}
