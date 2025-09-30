package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.service.SvcToken;
import com.yuzhi.dts.platform.repository.service.SvcTokenRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Transactional
public class ServicesResource {

    private final SvcTokenRepository tokenRepo;
    private final AuditService audit;

    public ServicesResource(SvcTokenRepository tokenRepo, AuditService audit) {
        this.tokenRepo = tokenRepo;
        this.audit = audit;
    }

    @GetMapping("/tokens/me")
    public ApiResponse<List<SvcToken>> myTokens() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        List<SvcToken> list = tokenRepo.findByCreatedBy(user);
        audit.audit("READ", "svc.token", "me");
        return ApiResponses.ok(list);
    }

    @PostMapping("/tokens")
    public ApiResponse<SvcToken> createToken() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        SvcToken t = new SvcToken();
        t.setToken(generateToken());
        t.setExpiresAt(Instant.now().plusSeconds(3600L * 24 * 30));
        SvcToken saved = tokenRepo.save(t);
        saved.setCreatedBy(user);
        audit.audit("CREATE", "svc.token", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/tokens/{id}")
    public ApiResponse<Boolean> deleteToken(@PathVariable UUID id) {
        tokenRepo.deleteById(id);
        audit.audit("DELETE", "svc.token", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    private static String generateToken() {
        byte[] buf = new byte[24];
        new SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}

