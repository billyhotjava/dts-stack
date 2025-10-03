package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.services.SvcTokenService;
import com.yuzhi.dts.platform.service.services.dto.TokenCreationResultDto;
import com.yuzhi.dts.platform.service.services.dto.TokenInfoDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
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

    @DeleteMapping("/tokens/{id}")
    public ApiResponse<Boolean> deleteToken(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        tokenService.revokeToken(user, id);
        audit.audit("DELETE", "svc.token", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }
}
