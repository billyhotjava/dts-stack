package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogGovernanceWorkbenchService;
import com.yuzhi.dts.platform.service.catalog.CatalogGovernanceWorkbenchService.AssetWorkspace;
import com.yuzhi.dts.platform.service.catalog.CatalogGovernanceWorkbenchService.ClassificationFactView;
import com.yuzhi.dts.platform.service.catalog.CatalogGovernanceWorkbenchService.GovernanceIssueView;
import com.yuzhi.dts.platform.service.catalog.CatalogGovernanceWorkbenchService.LifecycleMetrics;
import com.yuzhi.dts.platform.service.catalog.CatalogGovernanceWorkbenchService.SubjectRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/governance-workbench")
@PreAuthorize("isAuthenticated()")
public class CatalogGovernanceWorkbenchResource {

    private static final String MAINTAIN =
        "hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN','ROLE_GOV_ADMIN','ROLE_DATA_STEWARD','ROLE_INFRA_ADMIN')";

    private final CatalogGovernanceWorkbenchService workbenchService;

    public CatalogGovernanceWorkbenchResource(CatalogGovernanceWorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @PostMapping("/classification-facts")
    public ApiResponse<List<ClassificationFactView>> classificationFacts(
        @RequestBody List<SubjectRequest> requests
    ) {
        return ApiResponses.ok(workbenchService.classificationFacts(requests));
    }

    @GetMapping("/assets/{datasetId}")
    public ApiResponse<AssetWorkspace> assetWorkspace(
        @PathVariable UUID datasetId,
        @RequestParam String subjectKey,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(workbenchService.workspace(datasetId, subjectKey, activeDept));
    }

    @GetMapping("/lifecycle-metrics")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<LifecycleMetrics> lifecycleMetrics(
        @RequestParam(required = false) String ownerDept,
        @RequestParam(required = false) String classification,
        @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponses.ok(workbenchService.lifecycleMetrics(ownerDept, classification, days));
    }

    @GetMapping("/issues")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<List<GovernanceIssueView>> issues(
        @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponses.ok(workbenchService.governanceIssues(limit));
    }
}
