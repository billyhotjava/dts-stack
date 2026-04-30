package com.yuzhi.dts.admin.service.auditv2;

import com.yuzhi.dts.admin.config.AuditIngestProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates the bearer token presented by sibling services when posting to /api/audit-events.
 * <p>
 * Constant-time comparison prevents timing-based token brute force. Decisions are emitted as
 * a structured {@link Decision} so the controller can shape the HTTP response and audit the
 * rejection without exposing the configured token.
 */
@Component
public class AuditIngestAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestAuthenticator.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuditIngestProperties properties;
    private final AtomicLong rejectedCount = new AtomicLong();
    private final AtomicLong faultyConfigWarnCount = new AtomicLong();

    public AuditIngestAuthenticator(AuditIngestProperties properties) {
        this.properties = properties;
    }

    public Decision authenticate(HttpServletRequest request) {
        String authorization = request != null ? request.getHeader(HttpHeaders.AUTHORIZATION) : null;
        String serviceName = request != null ? request.getHeader("X-DTS-Service") : null;
        return authenticate(authorization, serviceName);
    }

    public Decision authenticate(String authorizationHeader, String serviceName) {
        if (!properties.isRequireToken()) {
            return Decision.accept(serviceName, "token check disabled");
        }
        if (!properties.hasConfiguredTokens()) {
            long warnCount = faultyConfigWarnCount.incrementAndGet();
            if (warnCount == 1 || warnCount % 1000 == 0) {
                log.warn(
                    "auditing.ingest.service-tokens is empty while require-token=true; accepting events with WARN. " +
                    "Configure DTS_ADMIN_SERVICE_TOKEN (or auditing.ingest.service-tokens) to enforce authentication."
                );
            }
            return Decision.accept(serviceName, "fail-soft: no tokens configured");
        }

        String presented = extractBearer(authorizationHeader);
        if (!StringUtils.hasText(presented)) {
            return reject(serviceName, "missing bearer token");
        }
        if (!matchesAnyConfiguredToken(presented)) {
            return reject(serviceName, "token mismatch");
        }
        return Decision.accept(serviceName, "valid token");
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    private Decision reject(String serviceName, String reason) {
        rejectedCount.incrementAndGet();
        return Decision.reject(serviceName, reason);
    }

    private boolean matchesAnyConfiguredToken(String presented) {
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        boolean match = false;
        // Loop through every configured token to keep total time roughly constant.
        for (String configured : properties.getServiceTokensView()) {
            byte[] configuredBytes = configured.getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(presentedBytes, configuredBytes)) {
                match = true;
            }
        }
        return match;
    }

    private static String extractBearer(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        String trimmed = authorizationHeader.trim();
        if (trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return trimmed.substring(BEARER_PREFIX.length()).trim();
        }
        // Allow raw token without "Bearer " prefix to ease misconfigurations during rollout.
        return trimmed;
    }

    /** Outcome of the authentication step. */
    public record Decision(boolean accepted, String serviceName, String reason) {
        static Decision accept(String serviceName, String reason) {
            return new Decision(true, normalize(serviceName), reason);
        }

        static Decision reject(String serviceName, String reason) {
            return new Decision(false, normalize(serviceName), reason);
        }

        private static String normalize(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
