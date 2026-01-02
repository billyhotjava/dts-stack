package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.security.SecurityBackupPlan;
import com.yuzhi.dts.platform.domain.security.SecurityBackupRun;
import com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.backup.SecurityBackupService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/security/backup")
@Transactional
public class SecurityBackupRecoveryResource {

    private static final String INSTITUTE_PRIVILEGED_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INSTITUTE_PRIVILEGED_ROLES)";

    private final SecurityBackupService backupService;
    private final AuditService auditService;

    public SecurityBackupRecoveryResource(SecurityBackupService backupService, AuditService auditService) {
        this.backupService = backupService;
        this.auditService = auditService;
    }

    @GetMapping("/plans")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<List<SecurityBackupPlan>> listPlans() {
        List<SecurityBackupPlan> list = backupService.listPlans();
        auditService.auditAction(
            "SECURITY_BACKUP_PLAN_LIST",
            AuditStage.SUCCESS,
            "plans",
            Map.of("summary", "查看备份策略清单", "count", list.size())
        );
        return ApiResponses.ok(list);
    }

    @PostMapping("/plans")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<SecurityBackupPlan> createPlan(@RequestBody SecurityBackupPlan plan) {
        try {
            SecurityBackupPlan saved = backupService.createPlan(plan);
            auditService.auditAction(
                "SECURITY_BACKUP_PLAN_CREATE",
                AuditStage.SUCCESS,
                saved.getId() != null ? saved.getId().toString() : "new",
                Map.of("summary", "新增备份策略", "targetKey", saved.getTargetKey(), "title", saved.getTitle())
            );
            return ApiResponses.ok(saved);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<SecurityBackupPlan> updatePlan(@PathVariable UUID id, @RequestBody SecurityBackupPlan patch) {
        try {
            SecurityBackupPlan saved = backupService.updatePlan(id, patch);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", "更新备份策略");
            payload.put("targetKey", saved.getTargetKey());
            payload.put("enabled", saved.getEnabled());
            auditService.auditAction("SECURITY_BACKUP_PLAN_UPDATE", AuditStage.SUCCESS, id.toString(), payload);
            return ApiResponses.ok(saved);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<Boolean> deletePlan(@PathVariable UUID id) {
        backupService.deletePlan(id);
        auditService.auditAction(
            "SECURITY_BACKUP_PLAN_DELETE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "删除备份策略")
        );
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/plans/{id}/runs")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<List<SecurityBackupRun>> listRuns(@PathVariable UUID id) {
        List<SecurityBackupRun> list = backupService.listRuns(id);
        auditService.auditAction(
            "SECURITY_BACKUP_RUN_LIST",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看备份运行记录", "count", list.size())
        );
        return ApiResponses.ok(list);
    }

    @PostMapping("/plans/{id}/runs")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<SecurityBackupRun> recordRun(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        try {
            SecurityBackupRun run = backupService.recordRun(id, body);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", "记录备份运行结果");
            payload.put("result", run.getResult());
            payload.put("artifactUri", run.getArtifactUri());
            auditService.auditAction("SECURITY_BACKUP_RUN_RECORD", AuditStage.SUCCESS, run.getId().toString(), payload);
            return ApiResponses.ok(run);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/drills")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<List<SecurityDisasterRecoveryDrill>> listDrills() {
        List<SecurityDisasterRecoveryDrill> list = backupService.listDrills();
        auditService.auditAction(
            "SECURITY_DR_DRILL_LIST",
            AuditStage.SUCCESS,
            "drills",
            Map.of("summary", "查看灾备演练记录", "count", list.size())
        );
        return ApiResponses.ok(list);
    }

    @PostMapping("/drills")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<SecurityDisasterRecoveryDrill> recordDrill(@RequestBody SecurityDisasterRecoveryDrill drill) {
        try {
            SecurityDisasterRecoveryDrill saved = backupService.recordDrill(drill);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", "新增灾备演练记录");
            payload.put("drillDate", saved.getDrillDate() != null ? saved.getDrillDate().toString() : null);
            payload.put("scenario", saved.getScenario());
            payload.put("result", saved.getResult());
            auditService.auditAction("SECURITY_DR_DRILL_RECORD", AuditStage.SUCCESS, saved.getId().toString(), payload);
            return ApiResponses.ok(saved);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/report")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<Map<String, Object>> report() {
        Map<String, Object> report = backupService.exportReport();
        auditService.auditAction(
            "SECURITY_BACKUP_REPORT_VIEW",
            AuditStage.SUCCESS,
            "report",
            Map.of("summary", "导出备份恢复与灾备演练报告", "generatedAt", report.get("generatedAt"))
        );
        return ApiResponses.ok(report);
    }
}

