package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.DecisionCommand;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.MatrixView;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.PolicyChangeRequestCommand;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/action-policies")
public class AssetActionPolicyResource {

    private static final String IAM_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).IAM_MAINTAINERS)";
    private static final String INSTITUTE_PRIVILEGED_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INSTITUTE_PRIVILEGED_ROLES)";

    private final AssetActionPolicyService service;
    private final AuditService audit;

    public AssetActionPolicyResource(AssetActionPolicyService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/matrix")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<MatrixView> matrix(
        @RequestParam String subjectType,
        @RequestParam String subjectId,
        @RequestParam String resourceType,
        @RequestParam String resourceId
    ) {
        MatrixView matrix = service.matrix(subjectType, subjectId, resourceType, resourceId);
        audit.auditAction(
            "IAM_ASSET_ACTION_POLICY_VIEW",
            AuditStage.SUCCESS,
            resourceType + ":" + resourceId,
            Map.of("subjectType", subjectType, "subjectId", subjectId)
        );
        return ApiResponses.ok(matrix);
    }

    @GetMapping("/requests")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<List<IamAssetActionPolicyRequest>> listRequests(
        @RequestParam(defaultValue = "PENDING") String status
    ) {
        List<IamAssetActionPolicyRequest> requests = service.listRequests(status);
        audit.auditAction(
            "IAM_ASSET_ACTION_POLICY_REQUEST_LIST",
            AuditStage.SUCCESS,
            status,
            Map.of("count", requests.size())
        );
        return ApiResponses.ok(requests);
    }

    @PostMapping("/requests")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<IamAssetActionPolicyRequest> requestChange(
        @RequestBody PolicyChangeRequestCommand command
    ) {
        IamAssetActionPolicyRequest request = service.requestChange(command);
        audit.auditAction(
            "IAM_ASSET_ACTION_POLICY_REQUEST",
            AuditStage.SUCCESS,
            request.getId().toString(),
            Map.of(
                "subjectType",
                request.getSubjectType(),
                "subjectId",
                request.getSubjectId(),
                "resourceType",
                request.getResourceType(),
                "resourceId",
                request.getResourceId()
            )
        );
        return ApiResponses.ok(request);
    }

    @PostMapping("/requests/{id}/decision")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<IamAssetActionPolicyRequest> decide(
        @PathVariable UUID id,
        @RequestBody DecisionCommand command
    ) {
        IamAssetActionPolicyRequest request = service.decide(id, command);
        audit.auditAction(
            "APPROVED".equals(request.getStatus())
                ? "IAM_ASSET_ACTION_POLICY_APPROVE"
                : "IAM_ASSET_ACTION_POLICY_REJECT",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("requestedBy", request.getRequestedBy(), "decidedBy", request.getDecidedBy())
        );
        return ApiResponses.ok(request);
    }
}
