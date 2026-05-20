package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.config.AuditIngestProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates the service token presented by sibling services when posting to /api/audit-events.
 * <p>
 * Constant-time comparison prevents timing-based token brute force. Decisions are emitted as
 * a structured {@link Decision} so the controller can shape the HTTP response and audit the
 * rejection without exposing the configured token.
 */
@Component
public class AuditIngestAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestAuthenticator.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private final AuditIngestProperties properties;
    private final AtomicLong rejectedCount = new AtomicLong();
    private final AtomicLong faultyConfigWarnCount = new AtomicLong();

    public AuditIngestAuthenticator(AuditIngestProperties properties) {
        this.properties = properties;
    }

    /**
     * Logs the configured token set's fingerprints at startup so auditadmin can confirm a
     * rotation actually landed without ever seeing the secret. Only the first 8 bytes of
     * SHA-256 are exposed — far below brute-force feasibility for HMAC-strength tokens.
     */
    @PostConstruct
    public void logTokenSetFingerprints() {
        if (!properties.isRequireToken()) {
            log.warn("Audit ingest token validation is DISABLED — only safe in local dev");
            return;
        }
        List<String> fingerprints = computeFingerprints(properties.getServiceTokensView());
        if (fingerprints.isEmpty()) {
            log.warn(
                "Audit ingest is configured with require-token=true but service-tokens is empty; " +
                "requests will be rejected. Configure AUDIT_INGEST_SERVICE_TOKENS or DTS_ADMIN_SERVICE_TOKEN."
            );
            return;
        }
        log.info(
            "Audit ingest accepting {} service token(s) with fingerprints: [{}]. " +
            "Persist this in the rotation runbook so auditadmin can spot unintended changes.",
            fingerprints.size(), String.join(", ", fingerprints)
        );
    }

    static List<String> computeFingerprints(List<String> tokens) {
        List<String> out = new ArrayList<>();
        if (tokens == null || tokens.isEmpty()) {
            return out;
        }
        for (String token : tokens) {
            String fingerprint = fingerprint(token);
            if (fingerprint != null) {
                out.add(fingerprint);
            }
        }
        return out;
    }

    /**
     * Returns a short hex fingerprint of the token (first 8 bytes / 16 hex chars of SHA-256).
     * Returning {@code null} for blank input keeps the list output self-consistent.
     */
    static String fingerprint(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8 && i < hash.length; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    public Decision authenticate(HttpServletRequest request) {
        String serviceToken = request != null ? request.getHeader(SERVICE_TOKEN_HEADER) : null;
        String authorization = request != null ? request.getHeader(HttpHeaders.AUTHORIZATION) : null;
        String serviceName = request != null ? request.getHeader("X-DTS-Service") : null;
        return authenticate(StringUtils.hasText(serviceToken) ? serviceToken : authorization, serviceName);
    }

    public Decision authenticate(String authorizationHeader, String serviceName) {
        if (!properties.isRequireToken()) {
            return Decision.accept(serviceName, "token check disabled");
        }
        if (!properties.hasConfiguredTokens()) {
            long warnCount = faultyConfigWarnCount.incrementAndGet();
            if (warnCount == 1 || warnCount % 1000 == 0) {
                log.warn(
                    "auditing.ingest.service-tokens is empty while require-token=true; rejecting audit event. " +
                    "Configure AUDIT_INGEST_SERVICE_TOKENS or DTS_ADMIN_SERVICE_TOKEN."
                );
            }
            return reject(serviceName, "no service tokens configured");
        }

        String presented = extractBearer(authorizationHeader);
        if (!StringUtils.hasText(presented)) {
            return reject(serviceName, "missing bearer token");
        }
        if (!matchesAnyConfiguredToken(presented)) {
            String presentedFp = fingerprint(presented);
            return reject(serviceName, presentedFp != null ? "token mismatch (presented fingerprint=" + presentedFp + ")" : "token mismatch");
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
