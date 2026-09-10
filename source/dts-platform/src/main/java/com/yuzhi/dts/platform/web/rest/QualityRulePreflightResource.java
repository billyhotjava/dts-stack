package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.governance.QualityAuditRecorder;
import com.yuzhi.dts.platform.service.governance.QualityDatasetStatementExecutor.Validation;
import com.yuzhi.dts.platform.service.governance.QualityRulePreflightService;
import com.yuzhi.dts.platform.service.governance.QualityRulePreflightService.*;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/governance/quality/rules")
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)")
public class QualityRulePreflightResource {
    private final QualityRulePreflightService preflight;
    private final QualityAuditRecorder audit;
    public QualityRulePreflightResource(QualityRulePreflightService preflight, QualityAuditRecorder audit) {
        this.preflight = preflight; this.audit = audit;
    }
    @PostMapping("/validate-sql")
    public ApiResponse<Validation> validate(@RequestBody Request request,
        @RequestHeader(value="X-Active-Dept", required=false) String activeDept) {
        return ApiResponses.ok(preflight.validate(request, activeDept));
    }
    @PostMapping("/dry-run")
    public ApiResponse<Preview> preview(@RequestBody Request request,
        @RequestHeader(value="X-Active-Dept", required=false) String activeDept) {
        Preview preview = preflight.preview(request, activeDept);
        audit.recordAction("GOV_RULE_DRY_RUN", AuditStage.SUCCESS, request.datasetId().toString(),
            Map.of("summary", "草稿质量规则试跑", "checksum", preview.checksum(), "qualityOutcome", preview.outcome().qualityOutcome()));
        return ApiResponses.ok(preview);
    }
}
