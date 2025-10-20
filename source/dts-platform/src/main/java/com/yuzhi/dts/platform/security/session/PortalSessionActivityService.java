package com.yuzhi.dts.platform.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortalSessionActivityService {

    public enum ValidationResult {
        ACTIVE,
        EXPIRED
    }

    private final Duration maxIdle;
    private final ConcurrentMap<String, SessionRecord> sessionsByToken = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> tokenByUser = new ConcurrentHashMap<>();

    public PortalSessionActivityService(@Value("${dts.platform.session.timeout-minutes:10}") long minutes) {
        this.maxIdle = minutes <= 0 ? Duration.ZERO : Duration.ofMinutes(minutes);
    }

    public void register(String username, String sessionId, String tokenKey, Instant now) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        SessionRecord record = new SessionRecord(normalize(username), sessionId, tokenKey, now);
        sessionsByToken.put(tokenKey, record);
        if (record.username != null) {
            String previous = tokenByUser.put(record.username, tokenKey);
            if (previous != null && !previous.equals(tokenKey)) {
                sessionsByToken.remove(previous);
            }
        }
    }

    public ValidationResult touch(String tokenKey, Instant now) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return ValidationResult.ACTIVE;
        }
        SessionRecord record = sessionsByToken.get(tokenKey);
        if (record == null) {
            return ValidationResult.EXPIRED;
        }
        if (maxIdle.compareTo(Duration.ZERO) > 0 && record.lastSeen.plus(maxIdle).isBefore(now)) {
            invalidate(tokenKey);
            return ValidationResult.EXPIRED;
        }
        record.lastSeen = now;
        return ValidationResult.ACTIVE;
    }

    public void invalidate(String tokenKey) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        SessionRecord record = sessionsByToken.remove(tokenKey);
        if (record != null && record.username != null) {
            tokenByUser.computeIfPresent(record.username, (key, value) -> value.equals(tokenKey) ? null : value);
        }
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
}
