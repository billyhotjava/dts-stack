package com.yuzhi.dts.platform.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortalSessionActivityService {

    public enum ValidationResult {
        ACTIVE,
        EXPIRED,
        CONCURRENT
    }

    private final Duration maxIdle;
    private final Duration cleanupTtl;
    private final ConcurrentMap<String, SessionRecord> sessionsByToken = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> tokenByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RevokedToken> revokedTokens = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupTicker = new AtomicInteger();

    public PortalSessionActivityService(@Value("${dts.platform.session.timeout-minutes:10}") long minutes) {
        this.maxIdle = minutes <= 0 ? Duration.ZERO : Duration.ofMinutes(minutes);
        this.cleanupTtl = this.maxIdle.isZero() ? Duration.ofMinutes(30) : this.maxIdle.multipliedBy(2);
    }

    public void register(String username, String sessionId, String tokenKey, Instant now) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        SessionRecord record = new SessionRecord(normalize(username), sessionId, tokenKey, effectiveNow);
        revokedTokens.remove(tokenKey);
        sessionsByToken.put(tokenKey, record);
        if (record.username != null) {
            String previous = tokenByUser.put(record.username, tokenKey);
            if (previous != null && !previous.equals(tokenKey)) {
                sessionsByToken.remove(previous);
                markRevoked(previous, ValidationResult.CONCURRENT, effectiveNow);
            }
        }
        cleanup(effectiveNow);
    }

    public ValidationResult touch(String tokenKey, Instant now) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return ValidationResult.ACTIVE;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;

        ValidationResult revoked = lookupRevocation(tokenKey, effectiveNow);
        if (revoked != null) {
            return revoked;
        }

        SessionRecord record = sessionsByToken.get(tokenKey);
        if (record == null) {
            return ValidationResult.EXPIRED;
        }
        if (maxIdle.compareTo(Duration.ZERO) > 0 && record.lastSeen.plus(maxIdle).isBefore(effectiveNow)) {
            invalidate(tokenKey, ValidationResult.EXPIRED, effectiveNow);
            return ValidationResult.EXPIRED;
        }
        record.lastSeen = effectiveNow;
        cleanup(effectiveNow);
        return ValidationResult.ACTIVE;
    }

    public void invalidate(String tokenKey) {
        invalidate(tokenKey, ValidationResult.EXPIRED, Instant.now());
    }

    public void invalidate(String tokenKey, ValidationResult reason) {
        invalidate(tokenKey, reason, Instant.now());
    }

    private void invalidate(String tokenKey, ValidationResult reason, Instant when) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        ValidationResult effectiveReason = reason == null ? ValidationResult.EXPIRED : reason;
        SessionRecord record = sessionsByToken.remove(tokenKey);
        if (record != null) {
            if (record.username != null) {
                tokenByUser.computeIfPresent(record.username, (key, value) -> value.equals(tokenKey) ? null : value);
            }
        } else {
            // In case record already removed, still clear token-by-user mapping if any.
            for (Map.Entry<String, String> entry : tokenByUser.entrySet()) {
                if (tokenKey.equals(entry.getValue())) {
                    tokenByUser.remove(entry.getKey(), tokenKey);
                }
            }
        }
        markRevoked(tokenKey, effectiveReason, when == null ? Instant.now() : when);
        cleanup(when == null ? Instant.now() : when);
    }

    private String normalize(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static final class SessionRecord {
        final String username;
        final String sessionId;
        final String tokenKey;
        volatile Instant lastSeen;

        SessionRecord(String username, String sessionId, String tokenKey, Instant lastSeen) {
            this.username = username;
            this.sessionId = sessionId;
            this.tokenKey = tokenKey;
            this.lastSeen = lastSeen == null ? Instant.now() : lastSeen;
        }
    }

    private static final class RevokedToken {
        final ValidationResult reason;
        final Instant revokedAt;

        RevokedToken(ValidationResult reason, Instant revokedAt) {
            this.reason = reason;
            this.revokedAt = revokedAt == null ? Instant.now() : revokedAt;
        }

        Instant revokedAt() {
            return revokedAt;
        }

        boolean expired(Instant now, Duration ttl) {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                return false;
            }
            return revokedAt.plus(ttl).isBefore(now);
        }
    }

    private void markRevoked(String tokenKey, ValidationResult reason, Instant when) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        revokedTokens.put(tokenKey, new RevokedToken(reason == null ? ValidationResult.EXPIRED : reason, when == null ? Instant.now() : when));
    }

    private ValidationResult lookupRevocation(String tokenKey, Instant now) {
        RevokedToken revoked = revokedTokens.get(tokenKey);
        if (revoked == null) {
            return null;
        }
        if (revoked.expired(now, cleanupTtl)) {
            revokedTokens.remove(tokenKey, revoked);
            return ValidationResult.EXPIRED;
        }
        return revoked.reason;
    }

    private void cleanup(Instant now) {
        if (cleanupTicker.incrementAndGet() % 200 != 0) {
            return;
        }
        Duration ttl = cleanupTtl.isZero() ? Duration.ofMinutes(30) : cleanupTtl;
        Instant threshold = now.minus(ttl);
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().revokedAt().isBefore(threshold));
    }
}
