package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AuditActionRequest;
import com.yuzhi.dts.admin.service.audit.AuditResultStatus;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.audit.ButtonCodes;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient.ImpactView;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient.PlatformPolicyException;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient.PolicyView;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置 → 模型发布治理 (F13-T09). Only a system administrator changes the gate; the platform never switches
 * it by itself. The actor comes from the login, never from the request body.
 */
@RestController
@RequestMapping("/api/admin/infra/model-governance-policy")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
public class AdminModelGovernancePolicyResource {

    static final int REASON_MAX_LENGTH = 200;
    private static final Set<String> QUALITY_GATES = Set.of("ADVISORY", "BLOCKING");
    private static final Logger LOG = LoggerFactory.getLogger(AdminModelGovernancePolicyResource.class);

    private final PlatformGovernancePolicyClient platform;
    private final AuditV2Service auditV2Service;

    public AdminModelGovernancePolicyResource(PlatformGovernancePolicyClient platform, AuditV2Service auditV2Service) {
        this.platform = platform;
        this.auditV2Service = auditV2Service;
    }

    public record UpdateRequest(String qualityGate, Integer expectedRevision, String reason) {}

    @GetMapping
    public ResponseEntity<ApiResponse<PolicyView>> current() {
        return ResponseEntity.ok(ApiResponse.ok(platform.current()));
    }

    @GetMapping("/impact")
    public ResponseEntity<ApiResponse<ImpactView>> impact() {
        return ResponseEntity.ok(ApiResponse.ok(platform.impact()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PolicyView>> update(@RequestBody UpdateRequest request, HttpServletRequest httpRequest) {
        String invalid = validate(request);
        if (invalid != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(invalid));
        }
        String actor = SecurityUtils.getCurrentAuditableLogin();
        String reason = request.reason().trim();
        String before = null;
        try {
            before = platform.current().qualityGate();
            PolicyView after = platform.update(request.qualityGate(), request.expectedRevision(), actor, reason);
            audit(actor, before, after.qualityGate(), reason, AuditResultStatus.SUCCESS, null, httpRequest);
            return ResponseEntity.ok(ApiResponse.ok(after));
        } catch (PlatformPolicyException failure) {
            audit(actor, before, request.qualityGate(), reason, AuditResultStatus.FAILED, failure.code(), httpRequest);
            throw failure;
        }
    }

    @ExceptionHandler(PlatformPolicyException.class)
    ResponseEntity<ApiResponse<Void>> platformFailure(PlatformPolicyException failure) {
        return ResponseEntity.status(failure.status()).body(ApiResponse.error(failure.getMessage()));
    }

    static String validate(UpdateRequest request) {
        if (request == null || request.qualityGate() == null || !QUALITY_GATES.contains(request.qualityGate())) {
            return "请选择策略：提示（ADVISORY）或阻断（BLOCKING）";
        }
        if (request.expectedRevision() == null || request.expectedRevision() < 1) {
            return "缺少策略版本号，请刷新后重试";
        }
        if (!StringUtils.hasText(request.reason())) {
            return "请填写修改原因";
        }
        if (request.reason().trim().length() > REASON_MAX_LENGTH) {
            return "修改原因不能超过 " + REASON_MAX_LENGTH + " 个字";
        }
        return null;
    }

    private void audit(
        String actor,
        String before,
        String after,
        String reason,
        AuditResultStatus result,
        String errorCode,
        HttpServletRequest request
    ) {
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.MODEL_GOVERNANCE_POLICY_UPDATE)
                .actorName(actor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(result == AuditResultStatus.SUCCESS ? "修改模型发布治理策略" : "修改模型发布治理策略失败")
                .result(result)
                .metadata("resourceType", "MODEL_GOVERNANCE_POLICY")
                .metadata("reason", reason)
                .changeSnapshot(
                    before == null ? Map.<String, Object>of() : Map.<String, Object>of("qualityGate", before),
                    Map.<String, Object>of("qualityGate", after),
                    "MODEL_GOVERNANCE_POLICY"
                )
                .target("model_governance_policy", "PLATFORM_DEFAULT", "模型发布治理策略");
            if (errorCode != null) {
                builder.metadata("error", errorCode);
            }
            if (request != null) {
                builder.client(IpAddressUtils.resolveClientIp(request::getHeader, request.getRemoteAddr()), request.getHeader("User-Agent"));
                builder.request(request.getRequestURI(), request.getMethod());
            }
            auditV2Service.record(builder.build());
        } catch (RuntimeException auditFailure) {
            // Same rule as the other infra settings: an audit write failure must not undo the business change.
            LOG.warn("event=model_governance_policy_audit_failed actor={} failureType={}", actor, auditFailure.getClass().getSimpleName());
        }
    }
}
