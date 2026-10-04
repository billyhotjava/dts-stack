package com.yuzhi.dts.platform.service.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Issues short-lived, one-time confirmations for destructive rollback requests. */
@Component
public class RollbackConfirmationTokenService {

    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final String MODAL = "MODAL";
    private static final String TYPE_TEXT = "TYPE_TEXT";

    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, ConfirmationGrant> grants = new ConcurrentHashMap<>();

    public RollbackConfirmationTokenService() {
        this(Clock.systemUTC(), DEFAULT_TTL, new SecureRandom());
    }

    RollbackConfirmationTokenService(Clock clock, Duration ttl, SecureRandom secureRandom) {
        this.clock = clock;
        this.ttl = ttl;
        this.secureRandom = secureRandom;
    }

    public IssuedConfirmation issue(
        RollbackCommand executionPlan,
        String confirmationType,
        String confirmationText,
        String actor,
        String trustedCascadeDataSourceId
    ) {
        String type = requireSupportedType(confirmationType, HttpStatus.BAD_GATEWAY);
        String text = normalizeConfirmationText(type, confirmationText, executionPlan);
        Instant now = clock.instant();
        purgeExpired(now);

        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant expiresAt = now.plus(ttl);
        grants.put(
            sha256(token),
            new ConfirmationGrant(
                requestFingerprint(executionPlan),
                type,
                text,
                normalizeActor(actor),
                normalizeText(trustedCascadeDataSourceId),
                expiresAt
            )
        );
        return new IssuedConfirmation(token, type, text, expiresAt);
    }

    public ConfirmedRollbackPlan consume(
        RollbackCommand executionPlan,
        String confirmationToken,
        String confirmationType,
        String confirmationText,
        String actor
    ) {
        if (!StringUtils.hasText(confirmationToken)) {
            throw conflict("缺少回退确认令牌，请重新分析影响范围");
        }
        String tokenKey = sha256(confirmationToken.trim());
        ConfirmationGrant grant = grants.remove(tokenKey);
        if (grant == null) {
            throw conflict("回退确认令牌无效或已使用，请重新分析影响范围");
        }
        String type = requireSupportedType(confirmationType, HttpStatus.CONFLICT);

        Instant now = clock.instant();
        purgeExpired(now);
        if (!grant.expiresAt().isAfter(now)) {
            throw conflict("回退确认令牌已过期，请重新分析影响范围");
        }
        if (
            !grant.confirmationType().equals(type) ||
            (TYPE_TEXT.equals(type) && !grant.confirmationText().equals(normalizeText(confirmationText))) ||
            !grant.actor().equals(normalizeActor(actor)) ||
            !grant.requestFingerprint().equals(requestFingerprint(executionPlan))
        ) {
            throw conflict("回退确认内容或请求范围已变化，请重新分析影响范围");
        }
        return new ConfirmedRollbackPlan(grant.cascadeDataSourceId());
    }

    private String normalizeConfirmationText(
        String type,
        String confirmationText,
        RollbackCommand executionPlan
    ) {
        String normalized = normalizeText(confirmationText);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (MODAL.equals(type)) {
            return "确认执行 L" + (executionPlan == null ? "" : executionPlan.level()) + " 数据回退";
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "接入服务未返回文本确认内容，拒绝签发回退令牌"
        );
    }

    private String requireSupportedType(String value, HttpStatus status) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        if (!MODAL.equals(normalized) && !TYPE_TEXT.equals(normalized)) {
            throw new ResponseStatusException(status, "不支持的回退确认类型，拒绝继续执行");
        }
        return normalized;
    }

    private String requestFingerprint(RollbackCommand executionPlan) {
        if (executionPlan == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回退执行计划不能为空");
        }
        return sha256(executionPlan.canonicalFingerprintSource());
    }

    private void purgeExpired(Instant now) {
        grants.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String normalizeActor(String actor) {
        String normalized = text(actor);
        return StringUtils.hasText(normalized) ? normalized : "system";
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record ConfirmationGrant(
        String requestFingerprint,
        String confirmationType,
        String confirmationText,
        String actor,
        String cascadeDataSourceId,
        Instant expiresAt
    ) {}

    public record IssuedConfirmation(
        String token,
        String confirmationType,
        String confirmationText,
        Instant expiresAt
    ) {}

    public record ConfirmedRollbackPlan(String cascadeDataSourceId) {}
}
