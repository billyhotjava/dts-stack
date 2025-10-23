package com.yuzhi.dts.admin.security.session;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionControlService {

    private static final int MAX_TOKENS_PER_SESSION = 6;

    public enum ValidationResult {
        ACTIVE,
        EXPIRED,
        CONCURRENT
    }

    private final Duration maxIdle;
    private final Duration cleanupTtl;
    private final ConcurrentMap<String, SessionRecord> sessionsByToken = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionRecord> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> userLocks = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupTicker = new AtomicInteger();

    public SessionControlService(@Value("${dts.session.max-idle-minutes:10}") long maxIdleMinutes) {
        this.maxIdle = maxIdleMinutes <= 0 ? Duration.ZERO : Duration.ofMinutes(maxIdleMinutes);
        Duration base = this.maxIdle.isZero() ? Duration.ofMinutes(30) : this.maxIdle.multipliedBy(3);
        this.cleanupTtl = base.isNegative() ? Duration.ofMinutes(30) : base;
    }

    public Duration maxIdle() {
        return maxIdle;
    }

    public void register(String username, String sessionState, String tokenKey, Instant now) {
        String userKey = normalize(username);
        if (userKey == null || tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        Object lock = userLocks.computeIfAbsent(userKey, k -> new Object());
        synchronized (lock) {
            SessionRecord current = sessionsByUser.get(userKey);
            if (current != null && current.matches(sessionState)) {
                bindToken(current, tokenKey, now);
                cleanup(now);
                return;
            }
            if (current != null) {
                current.revoke(now, ValidationResult.CONCURRENT);
                current.removeAllTokensFrom(sessionsByToken);
            }
            SessionRecord record = new SessionRecord(userKey, sessionState, now);
            bindToken(record, tokenKey, now);
            sessionsByUser.put(userKey, record);
            cleanup(now);
        }
    }

    public ValidationResult touch(String username, String sessionState, String tokenKey, Instant now) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return ValidationResult.ACTIVE;
        }

        SessionRecord record = sessionsByToken.get(tokenKey);
        if (record != null) {
            if (record.isRevoked()) {
                return record.revocationResult == null ? ValidationResult.EXPIRED : record.revocationResult;
            }
            if (maxIdle.compareTo(Duration.ZERO) > 0 && record.lastSeen != null && now.minus(maxIdle).isAfter(record.lastSeen)) {
                record.revoke(now, ValidationResult.EXPIRED);
                record.removeAllTokensFrom(sessionsByToken);
                sessionsByUser.remove(record.username);
                cleanup(now);
                return ValidationResult.EXPIRED;
            }
            record.lastSeen = now;
            cleanup(now);
            return ValidationResult.ACTIVE;
        }

        String userKey = normalize(username);
        if (userKey == null) {
            return ValidationResult.ACTIVE;
        }

        Object lock = userLocks.computeIfAbsent(userKey, k -> new Object());
        synchronized (lock) {
            SessionRecord current = sessionsByUser.get(userKey);
            if (current == null) {
                SessionRecord fresh = new SessionRecord(userKey, sessionState, now);
                bindToken(fresh, tokenKey, now);
                sessionsByUser.put(userKey, fresh);
                cleanup(now);
                return ValidationResult.ACTIVE;
            }
            if (current.isRevoked()) {
                return current.revocationResult == null ? ValidationResult.EXPIRED : current.revocationResult;
            }
            if (current.matches(sessionState)) {
                bindToken(current, tokenKey, now);
                cleanup(now);
                return ValidationResult.ACTIVE;
            }
            cleanup(now);
            return ValidationResult.CONCURRENT;
        }
    }

    public void invalidate(String tokenKey) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        SessionRecord record = sessionsByToken.get(tokenKey);
        if (record == null) {
            return;
        }
        Object lock = userLocks.computeIfAbsent(record.username, k -> new Object());
        synchronized (lock) {
            SessionRecord current = sessionsByUser.get(record.username);
            if (current == record) {
                current.revoke(Instant.now(), ValidationResult.EXPIRED);
                current.removeAllTokensFrom(sessionsByToken);
                sessionsByUser.remove(record.username);
            } else {
                sessionsByToken.remove(tokenKey);
            }
        }
        cleanup(Instant.now());
    }

    private void bindToken(SessionRecord record, String tokenKey, Instant now) {
        if (tokenKey == null || tokenKey.isBlank()) {
            return;
        }
        if (record.tokenKeys.add(tokenKey)) {
            if (record.tokenKeys.size() > MAX_TOKENS_PER_SESSION) {
                Iterator<String> it = record.tokenKeys.iterator();
                if (it.hasNext()) {
                    String oldest = it.next();
                    it.remove();
                    sessionsByToken.remove(oldest);
                }
            }
        }
        sessionsByToken.put(tokenKey, record);
        record.lastSeen = now;
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

    private void cleanup(Instant now) {
        if (cleanupTicker.incrementAndGet() % 200 != 0) {
            return;
        }
        Instant threshold = now.minus(cleanupTtl.isNegative() ? Duration.ofMinutes(30) : cleanupTtl);
        sessionsByToken.entrySet().removeIf(entry -> {
            SessionRecord rec = entry.getValue();
            return rec == null || (rec.isRevoked() && rec.revokedAt != null && rec.revokedAt.isBefore(threshold));
        });
        sessionsByUser.entrySet().removeIf(entry -> {
            SessionRecord rec = entry.getValue();
            if (rec == null) {
                return true;
            }
            if (rec.isRevoked() && rec.revokedAt != null && rec.revokedAt.isBefore(threshold)) {
                rec.removeAllTokensFrom(sessionsByToken);
                return true;
            }
            return false;
        });
    }

    private static final class SessionRecord {
        final String username;
        final String sessionState;
        final Set<String> tokenKeys = new LinkedHashSet<>();
        volatile Instant lastSeen;
        volatile Instant revokedAt;
        volatile ValidationResult revocationResult;

        SessionRecord(String username, String sessionState, Instant lastSeen) {
            this.username = username;
            this.sessionState = sessionState == null || sessionState.isBlank() ? null : sessionState;
            this.lastSeen = lastSeen;
        }

        boolean matches(String state) {
            if (this.sessionState == null || state == null || state.isBlank()) {
                return this.sessionState == null && (state == null || state.isBlank());
            }
            return this.sessionState.equals(state);
        }

        void removeAllTokensFrom(ConcurrentMap<String, SessionRecord> registry) {
            for (String key : tokenKeys) {
                registry.remove(key);
            }
            tokenKeys.clear();
        }

        boolean isRevoked() {
            return revokedAt != null;
        }

        void revoke(Instant when, ValidationResult result) {
            this.revokedAt = when == null ? Instant.now() : when.truncatedTo(ChronoUnit.MILLIS);
            this.revocationResult = result == null ? ValidationResult.EXPIRED : result;
        }
    }
}
