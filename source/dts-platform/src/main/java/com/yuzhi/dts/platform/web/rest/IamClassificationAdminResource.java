package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.iam.ClassificationService;
import com.yuzhi.dts.platform.service.iam.dto.DatasetClassificationDto;
import com.yuzhi.dts.platform.service.iam.dto.SyncStatusDto;
import com.yuzhi.dts.platform.service.iam.dto.UserClassificationDto;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/classification")
public class IamClassificationAdminResource {

    private final ClassificationService classificationService;
    private final AuditService auditService;

    public IamClassificationAdminResource(ClassificationService classificationService, AuditService auditService) {
        this.classificationService = classificationService;
        this.auditService = auditService;
    }

    @GetMapping("/users/search")
    public ApiResponse<List<UserClassificationDto>> searchUsers(@RequestParam String keyword) {
        List<UserClassificationDto> list = classificationService.searchUsers(keyword);
        auditService.audit("READ", "iam.classification.users", keyword);
        return ApiResponses.ok(list);
    }

    @PostMapping("/users/{id}/refresh")
    public ApiResponse<UserClassificationDto> refreshUser(@PathVariable String id) {
        UUID userId = UUID.fromString(id);
        UserClassificationDto dto = classificationService.refreshUser(userId);
        auditService.audit("UPDATE", "iam.classification.user", id);
        return ApiResponses.ok(dto);
    }

    @GetMapping("/datasets")
    public ApiResponse<List<DatasetClassificationDto>> datasets() {
        List<DatasetClassificationDto> list = classificationService.datasets();
        auditService.audit("READ", "iam.classification.datasets", "list");
        return ApiResponses.ok(list);
    }

    @GetMapping("/sync/status")
    public ApiResponse<SyncStatusDto> syncStatus() {
        SyncStatusDto status = classificationService.syncStatus();
        auditService.audit("READ", "iam.classification.sync", "status");
        return ApiResponses.ok(status);
    }

    @PostMapping("/sync/execute")
    public ApiResponse<SyncStatusDto> runSync() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        SyncStatusDto status = classificationService.executeSync(user);
        auditService.audit("EXECUTE", "iam.classification.sync", "execute");
        return ApiResponses.ok(status);
    }

    @PostMapping("/sync/retry/{id}")
    public ApiResponse<SyncStatusDto> retry(@PathVariable String id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        SyncStatusDto status = classificationService.retryFailure(id, user);
        auditService.audit("EXECUTE", "iam.classification.sync", "retry:" + id);
        return ApiResponses.ok(status);
    }
}
