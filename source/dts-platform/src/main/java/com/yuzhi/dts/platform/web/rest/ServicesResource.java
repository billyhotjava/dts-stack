package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.services.SvcTokenService;
import com.yuzhi.dts.platform.service.services.dto.TokenCreationResultDto;
import com.yuzhi.dts.platform.service.services.dto.TokenInfoDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Transactional
public class ServicesResource {

    private final SvcTokenService tokenService;
    private final AuditService audit;

    public ServicesResource(SvcTokenService tokenService, AuditService audit) {
        this.tokenService = tokenService;
        this.audit = audit;
    }

    public record InternalServiceTokenRequest(String serviceName, Long ttlDays) {}

    @GetMapping("/tokens/me")
    public ApiResponse<List<TokenInfoDto>> myTokens() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        List<TokenInfoDto> list = tokenService.listForUser(user);
        audit.auditAction("SERVICE_TOKEN_ISSUE", AuditStage.SUCCESS, "me", null);
        return ApiResponses.ok(list);
    }

    @PostMapping("/tokens")
    public ApiResponse<Map<String, Object>> createToken() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        TokenCreationResultDto created = tokenService.createToken(user, 30);
        audit.auditAction("SERVICE_TOKEN_ISSUE", AuditStage.SUCCESS, created.info().id().toString(), null);
        return ApiResponses.ok(Map.of("token", created.plainToken(), "info", created.info()));
    }

    @PostMapping("/tokens/internal-service")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Map<String, Object>> createInternalServiceToken(@RequestBody InternalServiceTokenRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        String serviceName = request != null ? request.serviceName() : null;
        if (!StringUtils.hasText(serviceName)) {
            return ApiResponses.error("BAD_REQUEST", "serviceName不能为空");
        }
        long ttlDays = request != null && request.ttlDays() != null && request.ttlDays() > 0 ? request.ttlDays() : 90;
        TokenCreationResultDto created = tokenService.createServiceToken(user, serviceName, ttlDays);
        audit.auditAction("SVC_TOKEN_INTERNAL_SERVICE_CREATE", AuditStage.SUCCESS, created.info().id().toString(), null);
        return ApiResponses.ok(Map.of("token", created.plainToken(), "info", created.info()));
    }

    @DeleteMapping("/tokens/{id}")
    public ApiResponse<Boolean> deleteToken(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        tokenService.revokeToken(user, id);
        audit.auditAction("SERVICE_TOKEN_REVOKE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @DeleteMapping("/tokens/internal-service/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteInternalServiceToken(@PathVariable UUID id) {
        tokenService.revokeServiceToken(id);
        audit.auditAction("SVC_TOKEN_INTERNAL_SERVICE_DELETE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(Boolean.TRUE);
    }
}
