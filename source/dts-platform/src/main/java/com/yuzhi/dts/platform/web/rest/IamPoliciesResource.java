package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.iam.PolicyService;
import com.yuzhi.dts.platform.service.iam.dto.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/policies")
public class IamPoliciesResource {

    private final PolicyService policyService;
    private final AuditService auditService;

    public IamPoliciesResource(PolicyService policyService, AuditService auditService) {
        this.policyService = policyService;
        this.auditService = auditService;
    }

    @GetMapping("/domains-with-datasets")
    public ApiResponse<List<Map<String, Object>>> domainsWithDatasets() {
        List<Map<String, Object>> tree = policyService.domainsWithDatasets();
        auditService.audit("READ", "iam.policy", "domains");
        return ApiResponses.ok(tree);
    }

    @GetMapping("/dataset/{datasetId}/policies")
    public ApiResponse<DatasetPoliciesDto> datasetPolicies(@PathVariable String datasetId) {
        DatasetPoliciesDto dto = policyService.datasetPolicies(UUID.fromString(datasetId));
        auditService.audit("READ", "iam.policy.dataset", datasetId);
        return ApiResponses.ok(dto);
    }

    @GetMapping("/subject/{type}/{id}/visible")
    public ApiResponse<SubjectVisibleDto> subjectVisible(@PathVariable String type, @PathVariable String id) {
        SubjectVisibleDto dto = policyService.subjectVisible(type, id);
        auditService.audit("READ", "iam.policy.subject", type + ":" + id);
        return ApiResponses.ok(dto);
    }

    @GetMapping("/subjects")
    public ApiResponse<List<SubjectSummaryDto>> searchSubjects(@RequestParam String type, @RequestParam String keyword) {
        List<SubjectSummaryDto> list = policyService.searchSubjects(type, keyword);
        auditService.audit("READ", "iam.policy.subjects", type + ":" + keyword);
        return ApiResponses.ok(list);
    }

    @PostMapping("/preview")
    public ApiResponse<ConflictPreviewDto> preview(@RequestBody BatchAuthorizationInputDto input) {
        ConflictPreviewDto dto = policyService.previewConflicts(input);
        auditService.audit("READ", "iam.policy.preview", "batch");
        return ApiResponses.ok(dto);
    }

    @PostMapping("/apply")
    public ApiResponse<BatchApplyResultDto> apply(@RequestBody BatchAuthorizationInputDto input) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        BatchApplyResultDto result = policyService.apply(input, user);
        auditService.audit("UPDATE", "iam.policy.apply", user);
        return ApiResponses.ok(result);
    }
}
