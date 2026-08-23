package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.governance.QualityWorkflowOrchestrator;
import com.yuzhi.dts.platform.service.governance.QualityWorkflowQueryService;
import com.yuzhi.dts.platform.service.governance.dto.QualityWorkflowRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance/quality/workflows")
public class GovernanceQualityWorkflowResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final QualityWorkflowQueryService queryService;
    private final QualityWorkflowOrchestrator orchestrator;

    public GovernanceQualityWorkflowResource(
        QualityWorkflowQueryService queryService,
        QualityWorkflowOrchestrator orchestrator
    ) {
        this.queryService = queryService;
        this.orchestrator = orchestrator;
    }

    @GetMapping
    public ApiResponse<List<QualityWorkflowRunDto>> list(
        @RequestParam(required = false) UUID taskId,
        @RequestParam(required = false) UUID datasetId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "100") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(queryService.list(taskId, datasetId, status, limit, activeDept));
    }

    @GetMapping("/{id}")
    public ApiResponse<QualityWorkflowRunDto> get(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(queryService.get(id, activeDept));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityWorkflowRunDto> retry(
        @PathVariable UUID id,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        String effectiveKey = StringUtils.defaultIfBlank(
            idempotencyKey,
            "quality-workflow:retry:" + id
        );
        return ApiResponses.ok(orchestrator.retryAuthorized(id, actor, activeDept, effectiveKey));
    }

    @PostMapping("/trigger")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityWorkflowRunDto> trigger(
        @RequestBody QualityRunTriggerRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        String effectiveKey = StringUtils.defaultIfBlank(
            idempotencyKey,
            "quality-workflow:manual-rule:" + UUID.randomUUID()
        );
        return ApiResponses.ok(orchestrator.startAuthorizedRule(request, actor, activeDept, effectiveKey));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityWorkflowRunDto> cancel(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        return ApiResponses.ok(orchestrator.cancelAuthorized(id, actor, activeDept));
    }
}
