package com.yuzhi.dts.platform.service.services;

import com.yuzhi.dts.platform.domain.service.SvcToken;
import com.yuzhi.dts.platform.repository.service.SvcTokenRepository;
import com.yuzhi.dts.platform.service.services.dto.TokenCreationResultDto;
import com.yuzhi.dts.platform.service.services.dto.TokenInfoDto;
import jakarta.persistence.EntityNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SvcTokenService {

    private final SvcTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

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
        String plain = generateToken();
        String hint = buildHint(plain);
        String hash = hashToken(plain);

        SvcToken entity = new SvcToken();
        entity.setTokenHash(hash);
        entity.setTokenHint(hint);
        entity.setExpiresAt(Instant.now().plusSeconds(ttlDays * 24 * 3600));
        entity.setRevoked(Boolean.FALSE);
        entity.setCreatedBy(username);
        entity.setLastModifiedBy(username);
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

    @Transactional
    public void deleteToken(String username, UUID id) {
        SvcToken token = repository.findById(id).orElseThrow(EntityNotFoundException::new);
        if (!username.equalsIgnoreCase(valueOrEmpty(token.getCreatedBy()))) {
            throw new EntityNotFoundException("Token not found");
        }
        repository.delete(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildHint(String token) {
        if (token.length() <= 6) {
            return token;
        }
        return "****" + token.substring(token.length() - 4);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private TokenInfoDto toDto(SvcToken token) {
        return new TokenInfoDto(token.getId(), token.getTokenHint(), token.getExpiresAt(), Boolean.TRUE.equals(token.getRevoked()), token.getCreatedDate());
    }

    private String valueOrEmpty(String input) {
        return input == null ? "" : input;
    }
}
