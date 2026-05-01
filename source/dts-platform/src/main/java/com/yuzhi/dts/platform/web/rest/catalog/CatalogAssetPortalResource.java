package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.OpenMetadataAssetSyncService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    private final OpenMetadataAssetSyncService syncService;
    private final AuditService audit;
    private final CatalogResourceHelper helper;

    public CatalogAssetPortalResource(
        CatalogAssetPortalService assetPortalService,
        OpenMetadataAssetSyncService syncService,
        AuditService audit,
        CatalogResourceHelper helper
    ) {
        this.assetPortalService = assetPortalService;
        this.syncService = syncService;
        this.audit = audit;
        this.helper = helper;
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
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
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
                page,
                size
            ),
            effDept
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "浏览OpenMetadata主目录资产");
        payload.put("returned", result.returned());
        payload.put("total", result.total());
        helper.putIfHasText(payload, "activeDept", effDept);
        helper.putIfHasText(payload, "keyword", keyword);
        audit.auditAction("CATALOG_ASSET_LIST", AuditStage.SUCCESS, "assets-v2", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<CatalogAssetPortalService.AssetDetail> getAsset(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        CatalogAssetPortalService.AssetDetail result = assetPortalService.getAsset(id, effDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看OpenMetadata主目录资产详情");
        payload.put("assetId", id.toString());
        helper.putIfHasText(payload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), payload);
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
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        CatalogAssetPortalService.AssetDetail result = assetPortalService.updateGovernance(id, body, effDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "更新OpenMetadata资产治理扩展");
        payload.put("assetId", id.toString());
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
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
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
}
