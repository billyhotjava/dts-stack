package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
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
        audit.audit("READ", "svc.token", "me");
        return ApiResponses.ok(list);
    }

    @PostMapping("/tokens")
    public ApiResponse<Map<String, Object>> createToken() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        TokenCreationResultDto created = tokenService.createToken(user, 30);
        audit.audit("CREATE", "svc.token", created.info().id().toString());
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
        audit.audit("CREATE", "svc.token.internal-service", created.info().id().toString());
        return ApiResponses.ok(Map.of("token", created.plainToken(), "info", created.info()));
    }

    @DeleteMapping("/tokens/{id}")
    public ApiResponse<Boolean> deleteToken(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        tokenService.revokeToken(user, id);
        audit.audit("DELETE", "svc.token", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @DeleteMapping("/tokens/internal-service/{id}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteInternalServiceToken(@PathVariable UUID id) {
        tokenService.revokeServiceToken(id);
        audit.audit("DELETE", "svc.token.internal-service", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }
}
