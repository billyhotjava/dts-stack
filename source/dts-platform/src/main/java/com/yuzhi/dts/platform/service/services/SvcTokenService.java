package com.yuzhi.dts.platform.service.services;

import com.yuzhi.dts.platform.domain.service.SvcToken;
import com.yuzhi.dts.platform.repository.service.SvcTokenRepository;
import com.yuzhi.dts.platform.service.services.dto.TokenCreationResultDto;
import com.yuzhi.dts.platform.service.services.dto.TokenInfoDto;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class SvcTokenService {

    private final SvcTokenRepository repository;

    public SvcTokenService(SvcTokenRepository repository) {
        this.repository = repository;
    }

    public List<TokenInfoDto> listForUser(String username) {
        return repository
            .findByCreatedBy(username)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public TokenCreationResultDto createToken(String username, long ttlDays) {
        String plain = SvcTokenHasher.generatePlainToken();
        String hint = SvcTokenHasher.buildHint(plain);
        String hash = SvcTokenHasher.hashToken(plain);

        SvcToken entity = new SvcToken();
        entity.setTokenHash(hash);
        entity.setTokenHint(hint);
        entity.setExpiresAt(Instant.now().plusSeconds(ttlDays * 24 * 3600));
        entity.setRevoked(Boolean.FALSE);
        entity.setCreatedBy(username);
        entity.setLastModifiedBy(username);
        entity.setSubjectDeptCode(resolveDeptCode());
        entity.setSubjectPersonnelLevel(resolvePersonnelLevelNumber());
        SvcToken saved = repository.save(entity);
        return new TokenCreationResultDto(toDto(saved), plain);
    }

    @Transactional
    public void revokeToken(String username, UUID id) {
        SvcToken token = repository.findById(id).orElseThrow(EntityNotFoundException::new);
        if (!username.equalsIgnoreCase(valueOrEmpty(token.getCreatedBy()))) {
            throw new EntityNotFoundException("Token not found");
        }
        token.setRevoked(Boolean.TRUE);
        repository.save(token);
    }

    @Transactional(readOnly = true)
    public SvcToken validatePlainToken(String plainToken) {
        if (!StringUtils.hasText(plainToken)) {
            return null;
        }
        String hash = SvcTokenHasher.hashToken(plainToken.trim());
        SvcToken token = repository.findFirstByTokenHashAndRevokedFalse(hash).orElse(null);
        if (token == null) {
            return null;
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        return token;
    }

    @Transactional
    public void deleteToken(String username, UUID id) {
        SvcToken token = repository.findById(id).orElseThrow(EntityNotFoundException::new);
        if (!username.equalsIgnoreCase(valueOrEmpty(token.getCreatedBy()))) {
            throw new EntityNotFoundException("Token not found");
        }
        repository.delete(token);
    }

    private TokenInfoDto toDto(SvcToken token) {
        return new TokenInfoDto(token.getId(), token.getTokenHint(), token.getExpiresAt(), Boolean.TRUE.equals(token.getRevoked()), token.getCreatedDate());
    }

    private String valueOrEmpty(String input) {
        return input == null ? "" : input;
    }

    private String resolveDeptCode() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get("dept_code");
                if (v == null) v = token.getToken().getClaims().get("deptCode");
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    return s.isEmpty() ? null : s;
                }
            }
            Object principal = auth != null ? auth.getPrincipal() : null;
            if (principal instanceof OAuth2AuthenticatedPrincipal p) {
                Object v = p.getAttribute("dept_code");
                if (v == null) v = p.getAttribute("deptCode");
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    return s.isEmpty() ? null : s;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Integer resolvePersonnelLevelNumber() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Object raw = null;
            if (auth instanceof JwtAuthenticationToken token) {
                raw = token.getToken().getClaims().get("person_security_level");
                if (raw == null) raw = token.getToken().getClaims().get("personnel_level");
                if (raw == null) raw = token.getToken().getClaims().get("person_ssecurity_level");
            } else if (auth != null && auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal p) {
                raw = p.getAttribute("person_security_level");
                if (raw == null) raw = p.getAttribute("personnel_level");
                if (raw == null) raw = p.getAttribute("person_ssecurity_level");
            }
            if (raw instanceof Number n) {
                return n.intValue();
            }
            if (raw != null) {
                String s = String.valueOf(raw).trim();
                if (!s.isEmpty() && s.chars().allMatch(Character::isDigit)) {
                    return Integer.parseInt(s);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
