package com.yuzhi.dts.admin.security;

import com.yuzhi.dts.admin.config.AdminInboundServiceAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Strict header authenticator for pairwise sibling-service calls into dts-admin. */
@Component
public class AdminInboundServiceAuthenticator {

    static final String SERVICE_HEADER = "X-DTS-Service";
    static final String TOKEN_HEADER = "X-DTS-Service-Token";

    private final AdminInboundServiceAuthProperties properties;

    public AdminInboundServiceAuthenticator(
        AdminInboundServiceAuthProperties properties
    ) {
        this.properties = properties;
    }

    public Decision authenticate(
        HttpServletRequest request,
        String requiredService
    ) {
        if (request == null || !StringUtils.hasText(requiredService)) {
            return Decision.denied("request_invalid");
        }
        String declared = request.getHeader(SERVICE_HEADER);
        String canonical = properties.canonicalServiceName(declared);
        if (
            canonical == null ||
            !canonical.equalsIgnoreCase(requiredService.trim())
        ) {
            return Decision.denied("service_unknown");
        }
        String supplied = request.getHeader(TOKEN_HEADER);
        String expected = properties.expectedToken(canonical);
        if (!StringUtils.hasText(supplied) || !StringUtils.hasText(expected)) {
            return Decision.denied("token_missing_or_unconfigured");
        }
        boolean matches = MessageDigest.isEqual(
            supplied.trim().getBytes(StandardCharsets.UTF_8),
            expected.getBytes(StandardCharsets.UTF_8)
        );
        return matches
            ? new Decision(true, canonical, "accepted")
            : Decision.denied("token_mismatch");
    }

    public record Decision(
        boolean accepted,
        String serviceName,
        String reason
    ) {
        static Decision denied(String reason) {
            return new Decision(false, null, reason);
        }
    }
}
