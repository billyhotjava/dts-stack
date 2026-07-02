package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.StandardPackageApplyService;
import com.yuzhi.dts.platform.service.modeling.StandardPackageBuiltinService;
import com.yuzhi.dts.platform.service.modeling.StandardPackageImportService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/modeling/standard-packages")
public class StandardPackageResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final StandardPackageImportService importService;
    private final StandardPackageApplyService applyService;
    private final StandardPackageBuiltinService builtinService;
    private final AuditService audit;

    public StandardPackageResource(
        StandardPackageImportService importService,
        StandardPackageApplyService applyService,
        StandardPackageBuiltinService builtinService,
        AuditService audit
    ) {
        this.importService = importService;
        this.applyService = applyService;
        this.builtinService = builtinService;
        this.audit = audit;
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> previewImport(@RequestPart("file") MultipartFile file) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        try {
            Map<String, Object> preview = importService.previewZip(file, actor);
            audit.auditAction(
                "MODELING_STANDARD_PACKAGE_PREVIEW",
                AuditStage.SUCCESS,
                String.valueOf(preview.get("runId")),
                Map.of("summary", "标准包导入预检：" + preview.get("packageName"))
            );
            return ApiResponses.ok(preview);
        } catch (IllegalArgumentException ex) {
            audit.auditAction("MODELING_STANDARD_PACKAGE_PREVIEW", AuditStage.FAIL, "preview", Map.of("summary", ex.getMessage()));
            return ApiResponses.error(ex.getMessage());
        }
    }

    public record ApplyRequest(UUID runId) {}

    @PostMapping("/import/apply")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> applyImport(@RequestBody ApplyRequest request) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        if (request == null || request.runId() == null) {
            return ApiResponses.error("runId 不能为空");
        }
        try {
            Map<String, Object> result = applyService.apply(request.runId(), actor);
            audit.auditAction(
                "MODELING_STANDARD_PACKAGE_APPLY",
                AuditStage.SUCCESS,
                request.runId().toString(),
                Map.of("summary", "标准包导入应用：新增 " + result.get("totalCreated") + "，更新 " + result.get("totalUpdated"))
            );
            return ApiResponses.ok(result);
        } catch (IllegalArgumentException ex) {
            audit.auditAction("MODELING_STANDARD_PACKAGE_APPLY", AuditStage.FAIL, request.runId().toString(), Map.of("summary", ex.getMessage()));
            return ApiResponses.error(ex.getMessage());
        }
    }

    @PostMapping("/runs/{runId}/rollback")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> rollbackRun(@PathVariable UUID runId) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        try {
            Map<String, Object> result = applyService.rollback(runId, actor);
            audit.auditAction(
                "MODELING_STANDARD_PACKAGE_ROLLBACK",
                AuditStage.SUCCESS,
                runId.toString(),
                Map.of("summary", "标准包导入回滚")
            );
            return ApiResponses.ok(result);
        } catch (IllegalArgumentException ex) {
            audit.auditAction("MODELING_STANDARD_PACKAGE_ROLLBACK", AuditStage.FAIL, runId.toString(), Map.of("summary", ex.getMessage()));
            return ApiResponses.error(ex.getMessage());
        }
    }

    @GetMapping("/runs")
    public ApiResponse<Map<String, Object>> listRuns(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        audit.auditAction("MODELING_STANDARD_PACKAGE_RUNS", AuditStage.SUCCESS, "page=" + page, Map.of("summary", "查看标准包导入历史"));
        return ApiResponses.ok(applyService.listRuns(page, size));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<Map<String, Object>> getRun(@PathVariable UUID runId) {
        audit.auditAction("MODELING_STANDARD_PACKAGE_RUN_VIEW", AuditStage.SUCCESS, runId.toString(), Map.of("summary", "查看标准包导入明细"));
        return ApiResponses.ok(applyService.getRun(runId));
    }

    @GetMapping("/builtin")
    public ApiResponse<List<Map<String, Object>>> listBuiltin() {
        audit.auditAction("MODELING_STANDARD_PACKAGE_BUILTIN_LIST", AuditStage.SUCCESS, "list", Map.of("summary", "查看内置标准包"));
        return ApiResponses.ok(builtinService.listBuiltin());
    }

    @PostMapping("/builtin/{code}/install")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> installBuiltin(@PathVariable String code) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        try {
            Map<String, Object> result = builtinService.install(code, actor);
            audit.auditAction(
                "MODELING_STANDARD_PACKAGE_BUILTIN_INSTALL",
                AuditStage.SUCCESS,
                code,
                Map.of("summary", "安装内置标准包：" + code)
            );
            return ApiResponses.ok(result);
        } catch (IllegalArgumentException ex) {
            audit.auditAction("MODELING_STANDARD_PACKAGE_BUILTIN_INSTALL", AuditStage.FAIL, code, Map.of("summary", ex.getMessage()));
            return ApiResponses.error(ex.getMessage());
        }
    }
}
