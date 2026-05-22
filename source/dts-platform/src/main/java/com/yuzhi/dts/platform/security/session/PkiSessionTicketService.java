package com.yuzhi.dts.platform.security.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PkiSessionTicketService {

    private static final String TICKET_HEADER = "X-Pki-Session-Ticket";
    private static final int MAX_TICKET_LENGTH = 3800;
    private static final List<String> COMPACT_USER_KEYS = List.of(
        "username",
        "loginName",
        "login_name",
        "preferredUsername",
        "preferred_username",
        "email",
        "fullName",
        "displayName",
        "name",
        "id",
        "enabled",
        "roles",
        "attributes",
        "dept_code",
        "deptCode",
        "department",
        "deptName",
        "personnel_level",
        "person_security_level",
        "person_level",
        "pkiLoginClientIp",
        "pkiLoginUserAgent"
    );
    private static final List<String> COMPACT_ATTRIBUTE_KEYS = List.of(
        "dept_code",
        "deptCode",
        "department",
        "personnel_level",
        "person_security_level",
        "person_level"
    );

    private final ObjectMapper objectMapper;
    private final Duration ticketTtl;
    private final String ticketCookieName;
    private final String cookiePath;
    private final String cookieSecureMode;
    private final String sameSite;
    private final byte[] signingSecret;

    public PkiSessionTicketService(
        ObjectMapper objectMapper,
        @Value("${dts.platform.session.pki-ticket.ttl-seconds:120}") long ticketTtlSeconds,
        @Value("${dts.platform.session.pki-ticket.cookie-name:pki_session_ticket}") String ticketCookieName,
        @Value("${dts.platform.session.pki-ticket.cookie-path:/}") String cookiePath,
        @Value("${dts.platform.session.pki-ticket.cookie-secure:auto}") String cookieSecureMode,
        @Value("${dts.platform.session.pki-ticket.cookie-same-site:Lax}") String sameSite,
        @Value("${dts.platform.session.pki-ticket.signing-secret:${DATA_STANDARD_ENCRYPTION_KEY:}}") String signingSecret
    ) {
        this.objectMapper = objectMapper;
        long ttlSeconds = ticketTtlSeconds <= 0 ? 120 : ticketTtlSeconds;
        this.ticketTtl = Duration.ofSeconds(ttlSeconds);
        this.ticketCookieName = StringUtils.hasText(ticketCookieName) ? ticketCookieName.trim() : "pki_session_ticket";
        this.cookiePath = StringUtils.hasText(cookiePath) ? cookiePath.trim() : "/";
        this.cookieSecureMode = StringUtils.hasText(cookieSecureMode) ? cookieSecureMode.trim() : "auto";
        this.sameSite = StringUtils.hasText(sameSite) ? sameSite.trim() : "Lax";
        this.signingSecret = resolveSigningSecret(signingSecret);
    }

    public ResponseCookie issue(String username, Map<String, Object> user, HttpServletRequest request) {
        String normalizedUsername = sanitizeUsername(username);
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("pki_ticket_username_missing");
        }
        String ticket = createSignedTicket(normalizedUsername, user);
        return buildCookie(ticket, ticketTtl, request);
    }

    public VerifiedPkiPrincipal resolve(HttpServletRequest request, String expectedUsername) {
        String ticket = resolveTicket(request);
        if (ticket == null) {
            return null;
        }
        TicketEnvelope envelope = decodeTicket(ticket);
        if (envelope == null) {
            return null;
        }
        String username = sanitizeUsername(envelope.sub());
        if (username == null || envelope.exp() <= Instant.now().getEpochSecond()) {
            return null;
        }
        String expected = sanitizeUsername(expectedUsername);
        if (expected != null && !expected.equalsIgnoreCase(username)) {
            return null;
        }
        Map<String, Object> user = new LinkedHashMap<>(copyMap(envelope.user()));
        user.putIfAbsent("username", username);
        return new VerifiedPkiPrincipal(username, user);
    }

    public ResponseCookie clearTicketCookie(HttpServletRequest request) {
        return buildCookie("", Duration.ZERO, request);
    }

    private ResponseCookie buildCookie(String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie
            .from(ticketCookieName, value)
            .httpOnly(true)
            .secure(resolveCookieSecure(request))
            .sameSite(sameSite)
            .path(cookiePath)
            .maxAge(maxAge)
            .build();
    }

    private String createSignedTicket(String username, Map<String, Object> user) {
        Map<String, Object> fullUser = new LinkedHashMap<>(copyMap(user));
        fullUser.putIfAbsent("username", username);
        long expiresAt = Instant.now().plus(ticketTtl).getEpochSecond();
        String ticket = encodeTicket(new TicketEnvelope(username, expiresAt, fullUser));
        if (ticket.length() <= MAX_TICKET_LENGTH) {
            return ticket;
        }

        Map<String, Object> compactUser = compactUser(fullUser, username);
        ticket = encodeTicket(new TicketEnvelope(username, expiresAt, compactUser));
        if (ticket.length() <= MAX_TICKET_LENGTH) {
            return ticket;
        }
        throw new IllegalStateException("pki_ticket_too_large");
    }

    private String encodeTicket(TicketEnvelope envelope) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(envelope));
            return payload + "." + computeSignature(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("pki_ticket_encode_failed", ex);
        }
    }

    private TicketEnvelope decodeTicket(String candidate) {
        String ticket = sanitizeToken(candidate, MAX_TICKET_LENGTH);
        if (ticket == null) {
            return null;
        }
        int separator = ticket.lastIndexOf('.');
        if (separator <= 0 || separator >= ticket.length() - 1) {
            return null;
        }
        String payload = sanitizeToken(ticket.substring(0, separator), MAX_TICKET_LENGTH);
        String signature = sanitizeToken(ticket.substring(separator + 1), 128);
        if (payload == null || signature == null) {
            return null;
        }
        String expectedSignature = computeSignature(payload);
        if (
            !MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
            )
        ) {
            return null;
        }
        try {
            byte[] decodedPayload = Base64.getUrlDecoder().decode(payload);
            return objectMapper.readValue(decodedPayload, TicketEnvelope.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String computeSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("pki_ticket_signing_failed", ex);
        }
    }

    private byte[] resolveSigningSecret(String signingSecret) {
        String secret = StringUtils.hasText(signingSecret) ? signingSecret.trim() : null;
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("pki_ticket_signing_secret_missing");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length > 0) {
                return decoded;
            }
        } catch (IllegalArgumentException ignore) {}
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private boolean resolveCookieSecure(HttpServletRequest request) {
        if ("true".equalsIgnoreCase(cookieSecureMode)) {
            return true;
        }
        if ("false".equalsIgnoreCase(cookieSecureMode)) {
            return false;
        }
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.hasText(forwardedProto)) {
            for (String value : forwardedProto.split(",")) {
                if ("https".equalsIgnoreCase(value.trim())) {
                    return true;
                }
            }
        }
        String forwarded = request.getHeader("Forwarded");
        return StringUtils.hasText(forwarded) && forwarded.toLowerCase().contains("proto=https");
    }

    private String resolveTicket(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String headerValue = sanitizeToken(request.getHeader(TICKET_HEADER), MAX_TICKET_LENGTH);
        if (headerValue != null) {
            return headerValue;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && ticketCookieName.equals(cookie.getName())) {
                return sanitizeToken(cookie.getValue(), MAX_TICKET_LENGTH);
            }
        }
        return null;
    }

    private String sanitizeToken(String candidate, int maxLength) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String value = candidate.trim();
        if (value.length() > maxLength) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean allowed =
                (ch >= 'a' && ch <= 'z') ||
                (ch >= 'A' && ch <= 'Z') ||
                (ch >= '0' && ch <= '9') ||
                ch == '-' ||
                ch == '_' ||
                ch == '.';
            if (!allowed) {
                return null;
            }
        }
        return value;
    }

    private String sanitizeUsername(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String username = candidate.trim();
        return username.isEmpty() ? null : username;
    }

    private Map<String, Object> compactUser(Map<String, Object> source, String username) {
        Map<String, Object> compact = new LinkedHashMap<>();
        for (String key : COMPACT_USER_KEYS) {
            if (!source.containsKey(key)) {
                continue;
            }
            Object value = "attributes".equals(key) ? compactAttributes(source.get(key)) : copyValue(source.get(key));
            if (value != null) {
                compact.put(key, value);
            }
        }
        compact.putIfAbsent("username", username);
        return compact;
    }

    private Object compactAttributes(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        for (String key : COMPACT_ATTRIBUTE_KEYS) {
            Object attributeValue = map.get(key);
            if (attributeValue != null) {
                compact.put(key, copyValue(attributeValue));
            }
        }
        return compact.isEmpty() ? null : compact;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            copy.put(key, copyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                nested.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }

    private record TicketEnvelope(String sub, long exp, Map<String, Object> user) implements Serializable {}

    public record VerifiedPkiPrincipal(String username, Map<String, Object> user) implements Serializable {}
}
