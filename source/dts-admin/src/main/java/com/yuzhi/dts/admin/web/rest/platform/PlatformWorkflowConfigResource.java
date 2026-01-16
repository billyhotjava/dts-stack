package com.yuzhi.dts.admin.web.rest.platform;

import com.yuzhi.dts.admin.service.workflow.AdminWorkflowConfigService;
import com.yuzhi.dts.admin.service.workflow.AdminWorkflowConfigService.WorkflowTemplateDto;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow configuration exposed to the platform service.
 * These endpoints are intended for internal service consumption.
 */
@RestController
@RequestMapping("/api/platform/workflows")
public class PlatformWorkflowConfigResource {

    private final AdminWorkflowConfigService workflowService;

    public PlatformWorkflowConfigResource(AdminWorkflowConfigService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<WorkflowTemplateDto>>> enabledTemplates(@RequestParam(name = "type") String workflowType) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.listTemplates(workflowType, true)));
    }
}

