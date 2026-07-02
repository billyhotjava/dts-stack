package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.StandardPackageImportService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/modeling/standard-packages")
public class StandardPackageResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final StandardPackageImportService importService;
    private final AuditService audit;

    public StandardPackageResource(StandardPackageImportService importService, AuditService audit) {
        this.importService = importService;
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
}
