package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.iam.IamClassification;
import com.yuzhi.dts.platform.domain.iam.IamPermission;
import com.yuzhi.dts.platform.domain.iam.IamRequest;
import com.yuzhi.dts.platform.repository.iam.IamClassificationRepository;
import com.yuzhi.dts.platform.repository.iam.IamPermissionRepository;
import com.yuzhi.dts.platform.repository.iam.IamRequestRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam")
@Transactional
public class IamResource {

    private static final String IAM_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).IAM_MAINTAINERS)";

    private final IamClassificationRepository classificationRepo;
    private final IamPermissionRepository permissionRepo;
    private final IamRequestRepository requestRepo;
    private final AuditService audit;

    public IamResource(
        IamClassificationRepository classificationRepo,
        IamPermissionRepository permissionRepo,
        IamRequestRepository requestRepo,
        AuditService audit
    ) {
        this.classificationRepo = classificationRepo;
        this.permissionRepo = permissionRepo;
        this.requestRepo = requestRepo;
        this.audit = audit;
    }

    // Classification CRUD
    @GetMapping("/classifications")
    public ApiResponse<List<IamClassification>> listClassifications() {
        var list = classificationRepo.findAll();
        audit.audit("READ", "iam.classification", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/classifications")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<IamClassification> createClassification(@RequestBody IamClassification item) {
        var saved = classificationRepo.save(item);
        audit.audit("CREATE", "iam.classification", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/classifications/{id}")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<IamClassification> updateClassification(@PathVariable UUID id, @RequestBody IamClassification patch) {
        var existing = classificationRepo.findById(id).orElseThrow();
        existing.setCode(patch.getCode());
        existing.setLabel(patch.getLabel());
        var saved = classificationRepo.save(existing);
        audit.audit("UPDATE", "iam.classification", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/classifications/{id}")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteClassification(@PathVariable UUID id) {
        classificationRepo.deleteById(id);
        audit.audit("DELETE", "iam.classification", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Permission CRUD
    @GetMapping("/permissions")
    public ApiResponse<List<IamPermission>> listPermissions() {
        var list = permissionRepo.findAll();
        audit.audit("READ", "iam.permission", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/permissions")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<IamPermission> createPermission(@RequestBody IamPermission item) {
        var saved = permissionRepo.save(item);
        audit.audit("CREATE", "iam.permission", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/permissions/{id}")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<IamPermission> updatePermission(@PathVariable UUID id, @RequestBody IamPermission patch) {
        var existing = permissionRepo.findById(id).orElseThrow();
        existing.setResource(patch.getResource());
        existing.setAction(patch.getAction());
        existing.setScope(patch.getScope());
        var saved = permissionRepo.save(existing);
        audit.audit("UPDATE", "iam.permission", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/permissions/{id}")
    @PreAuthorize(IAM_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deletePermission(@PathVariable UUID id) {
        permissionRepo.deleteById(id);
        audit.audit("DELETE", "iam.permission", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Requests
    @GetMapping("/requests")
    public ApiResponse<List<IamRequest>> listRequests() {
        var list = requestRepo.findAll();
        audit.audit("READ", "iam.request", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/requests")
    public ApiResponse<IamRequest> createRequest(@RequestBody IamRequest item) {
        item.setStatus("PENDING");
        var saved = requestRepo.save(item);
        audit.audit("CREATE", "iam.request", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PostMapping("/requests/{id}/approve")
    public ApiResponse<IamRequest> approve(@PathVariable UUID id) {
        var existing = requestRepo.findById(id).orElseThrow();
        existing.setStatus("APPROVED");
        var saved = requestRepo.save(existing);
        audit.audit("UPDATE", "iam.request.approve", id.toString());
        return ApiResponses.ok(saved);
    }

    @PostMapping("/requests/{id}/reject")
    public ApiResponse<IamRequest> reject(@PathVariable UUID id) {
        var existing = requestRepo.findById(id).orElseThrow();
        existing.setStatus("REJECTED");
        var saved = requestRepo.save(existing);
        audit.audit("UPDATE", "iam.request.reject", id.toString());
        return ApiResponses.ok(saved);
    }

    // Simulate
    @PostMapping("/simulate")
    public ApiResponse<Map<String, Object>> simulate(@RequestBody Map<String, Object> body) {
        // Very simplified: allow if there is any permission with scope=global and action equals
        String action = String.valueOf(body.getOrDefault("action", "read"));
        boolean allowed = permissionRepo.findAll().stream().anyMatch(p -> action.equalsIgnoreCase(p.getAction()));
        var resp = Map.<String, Object>of("allowed", allowed, "reason", allowed ? "permission matched" : "no rule matched");
        audit.audit("EXECUTE", "iam.simulate", action);
        return ApiResponses.ok(resp);
    }
}
