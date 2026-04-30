package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AuditActionRequest;
import com.yuzhi.dts.admin.service.audit.AuditResultStatus;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.audit.ButtonCodes;
import com.yuzhi.dts.admin.service.workflow.AdminWorkflowConfigService;
import com.yuzhi.dts.admin.service.workflow.AdminWorkflowConfigService.UpsertWorkflowTemplatePayload;
import com.yuzhi.dts.admin.service.workflow.AdminWorkflowConfigService.WorkflowTemplateDto;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowConfigResource {

    private final AdminWorkflowConfigService workflowService;
    private final AuditV2Service auditV2Service;

    public WorkflowConfigResource(AdminWorkflowConfigService workflowService, AuditV2Service auditV2Service) {
        this.workflowService = workflowService;
        this.auditV2Service = auditV2Service;
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<List<WorkflowTemplateDto>>> listTemplates(
        @RequestParam(name = "type") String workflowType,
        @RequestParam(name = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly,
        HttpServletRequest request
    ) {
        List<WorkflowTemplateDto> list = workflowService.listTemplates(workflowType, enabledOnly);
        recordAudit(
            ButtonCodes.WORKFLOW_TEMPLATE_LIST,
            "查看审批工作流配置",
            requestMeta(workflowType, enabledOnly),
            null,
            null,
            null,
            request
        );
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<WorkflowTemplateDto>> getTemplate(@PathVariable UUID id, HttpServletRequest request) {
        return workflowService
            .getTemplate(id)
            .map(dto -> {
                recordAudit(
                    ButtonCodes.WORKFLOW_TEMPLATE_VIEW,
                    "查看审批工作流配置详情",
                    null,
                    null,
                    null,
                    dto,
                    request
                );
                return ResponseEntity.ok(ApiResponse.ok(dto));
            })
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.error("not found")));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<WorkflowTemplateDto>> createTemplate(
        @Valid @RequestBody UpsertWorkflowTemplatePayload payload,
        HttpServletRequest request
    ) {
        WorkflowTemplateDto created = workflowService.create(payload);
        recordAudit(
            ButtonCodes.WORKFLOW_TEMPLATE_CREATE,
            "新增审批工作流配置",
            null,
            null,
            null,
            created,
            request
        );
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<WorkflowTemplateDto>> updateTemplate(
        @PathVariable UUID id,
        @Valid @RequestBody UpsertWorkflowTemplatePayload payload,
        HttpServletRequest request
    ) {
        WorkflowTemplateDto before = workflowService.getTemplate(id).orElse(null);
        WorkflowTemplateDto updated = workflowService.update(id, payload);
        recordAudit(
            ButtonCodes.WORKFLOW_TEMPLATE_UPDATE,
            "更新审批工作流配置",
            null,
            before,
            updated,
            updated,
            request
        );
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable UUID id, HttpServletRequest request) {
        WorkflowTemplateDto before = workflowService.getTemplate(id).orElse(null);
        workflowService.delete(id);
        recordAudit(
            ButtonCodes.WORKFLOW_TEMPLATE_DELETE,
            "删除审批工作流配置",
            null,
            before,
            null,
            before,
            request
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private Map<String, Object> requestMeta(String workflowType, boolean enabledOnly) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workflowType", workflowType);
        meta.put("enabledOnly", enabledOnly);
        return meta;
    }

    private void recordAudit(
        String buttonCode,
        String summary,
        Map<String, Object> meta,
        WorkflowTemplateDto before,
        WorkflowTemplateDto after,
        WorkflowTemplateDto target
    ) {
        recordAudit(buttonCode, summary, meta, before, after, target, null);
    }

    private void recordAudit(
        String buttonCode,
        String summary,
        Map<String, Object> meta,
        WorkflowTemplateDto before,
        WorkflowTemplateDto after,
        WorkflowTemplateDto target,
        HttpServletRequest request
    ) {
        try {
            String actor = SecurityUtils.getCurrentUserLogin().orElse("sysadmin");
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorName(actor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .metadata("resourceType", "WORKFLOW_TEMPLATE");

            if (meta != null) {
                meta.forEach(builder::metadata);
            }

            if (request != null) {
                String clientIp = IpAddressUtils.resolveClientIp(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"),
                    request.getRemoteAddr()
                );
                builder.client(clientIp, request.getHeader("User-Agent"));
                builder.request(request.getRequestURI(), request.getMethod());
            }

            Map<String, Object> beforeMap = snapshot(before);
            Map<String, Object> afterMap = snapshot(after);
            if (!beforeMap.isEmpty() || !afterMap.isEmpty()) {
                builder.changeSnapshot(beforeMap, afterMap, "WORKFLOW_TEMPLATE");
            }

            if (target != null && target.id() != null) {
                builder.target("admin_workflow_template", target.id().toString(), Objects.toString(target.name(), String.valueOf(target.id())));
                builder.metadata("templateId", target.id().toString());
            } else {
                builder.allowEmptyTargets();
            }

            auditV2Service.record(builder.build());
        } catch (Exception ignored) {
            // audit failure must not break business calls
        }
    }

    private Map<String, Object> snapshot(WorkflowTemplateDto dto) {
        if (dto == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        if (dto.id() != null) map.put("id", dto.id().toString());
        put(map, "workflowType", dto.workflowType());
        put(map, "name", dto.name());
        map.put("enabled", dto.enabled());
        map.put("priority", dto.priority());
        put(map, "ownerScope", dto.ownerScope());
        put(map, "classificationMin", dto.classificationMin());
        put(map, "classificationMax", dto.classificationMax());
        if (dto.steps() != null) {
            map.put(
                "steps",
                dto.steps().stream().map(step -> Map.of(
                    "stepOrder",
                    step.stepOrder(),
                    "approverRole",
                    step.approverRole(),
                    "deptBinding",
                    step.deptBinding()
                )).toList()
            );
        }
        return map;
    }

    private void put(Map<String, Object> map, String key, String value) {
        if (map == null) return;
        if (value == null) return;
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            map.put(key, trimmed);
        }
    }
}
