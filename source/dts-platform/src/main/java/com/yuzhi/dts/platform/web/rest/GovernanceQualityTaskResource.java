package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.governance.GovQualityTask;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.governance.QualityTaskService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance/quality/tasks")
@Transactional
public class GovernanceQualityTaskResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final QualityTaskService taskService;

    public GovernanceQualityTaskResource(QualityTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<List<GovQualityTask>> list(@RequestHeader(value = "X-Active-Dept", required = false) String activeDept) {
        return ApiResponses.ok(taskService.list(activeDept));
    }

    @PostMapping
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovQualityTask> create(
        @Valid @RequestBody GovQualityTask request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        return ApiResponses.ok(taskService.create(request, actor, activeDept));
    }

    @PutMapping("/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovQualityTask> update(
        @PathVariable UUID id,
        @Valid @RequestBody GovQualityTask request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        return ApiResponses.ok(taskService.update(id, request, actor, activeDept));
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovQualityTask> toggle(
        @PathVariable UUID id,
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        boolean enabled = Boolean.TRUE.equals(body.getOrDefault("enabled", Boolean.TRUE));
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        return ApiResponses.ok(taskService.toggle(id, enabled, actor, activeDept));
    }

    @PostMapping("/{id}/trigger")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<Map<String, Object>>> trigger(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        return ApiResponses.ok(taskService.trigger(id, actor, activeDept));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        taskService.delete(id, actor);
        return ApiResponses.ok(Boolean.TRUE);
    }
}
