package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.ImpactView;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.PolicyMissingException;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.PolicyRevisionConflictException;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.PolicyView;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Governance policy administration under the verified system administrator's identity. */
@RestController
@RequestMapping("/api/internal/modeling/governance-policy")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
public class ModelGovernancePolicyInternalResource {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGovernancePolicyInternalResource.class);

    private final ModelGovernancePolicyAdministrationService policies;

    public ModelGovernancePolicyInternalResource(ModelGovernancePolicyAdministrationService policies) {
        this.policies = policies;
    }

    public record UpdateRequest(QualityGate qualityGate, Integer expectedRevision, String reason) {}

    @GetMapping
    public PolicyView current() {
        return policies.current();
    }

    @GetMapping("/impact")
    public ImpactView impact() {
        return policies.impact();
    }

    @PutMapping
    public PolicyView update(@RequestBody UpdateRequest request, Authentication authentication) {
        if (request == null || request.qualityGate() == null || request.expectedRevision() == null || request.expectedRevision() < 1) {
            throw new IllegalArgumentException("qualityGate and expectedRevision are required");
        }
        if (!StringUtils.hasText(request.reason()) || request.reason().trim().length() > 200) {
            throw new IllegalArgumentException("修改原因须为 1 至 200 个字");
        }
        PolicyView before = policies.current();
        PolicyView after = policies.update(request.qualityGate(), request.expectedRevision(), authentication.getName());
        LOG.info(
            "event=model_governance_policy_changed from={} to={} revision={} actor={}",
            before.qualityGate(),
            after.qualityGate(),
            after.revision(),
            after.lastModifiedBy()
        );
        return after;
    }

    @ExceptionHandler(PolicyRevisionConflictException.class)
    ResponseEntity<Map<String, String>> conflict(PolicyRevisionConflictException conflict) {
        return error(HttpStatus.CONFLICT, "GOVERNANCE_POLICY_REVISION_CONFLICT", conflict.getMessage());
    }

    @ExceptionHandler(PolicyMissingException.class)
    ResponseEntity<Map<String, String>> missing(PolicyMissingException missing) {
        return error(HttpStatus.NOT_FOUND, "GOVERNANCE_POLICY_NOT_FOUND", missing.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException invalid) {
        return error(HttpStatus.BAD_REQUEST, "GOVERNANCE_POLICY_REQUEST_INVALID", invalid.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
