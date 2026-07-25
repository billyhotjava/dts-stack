package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Emits allowlisted model-import audit facts independently from HTTP fallback headers. */
@Component
public class ModelSpecImportPreviewAudit {

    private static final String ACTION = "MODEL_SPEC_IMPORT_PREVIEW";
    private final AuditService auditService;

    public ModelSpecImportPreviewAudit(AuditService auditService) {
        this.auditService = auditService;
    }

    public void success(PreviewResponse response) {
        PreviewSummary summary = response == null ? null : response.summary();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previewHash", response == null ? null : response.previewHash());
        if (summary != null) {
            payload.put("total", summary.total());
            payload.put("ready", summary.ready());
            payload.put("blocked", summary.blocked());
            payload.put("create", summary.create());
            payload.put("update", summary.update());
            payload.put("skip", summary.skip());
            payload.put("conflict", summary.conflict());
        }
        auditService.auditAction(ACTION, AuditStage.SUCCESS, response == null || response.runId() == null ? "preview" : response.runId().toString(), payload);
    }

    public void rejected(String code) {
        auditService.auditAction(ACTION, AuditStage.FAIL, "preview", Map.of("code", code == null ? "MODEL_IMPORT_PREVIEW_REJECTED" : code));
    }
}
