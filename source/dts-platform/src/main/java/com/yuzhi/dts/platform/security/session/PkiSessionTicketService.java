package com.yuzhi.dts.platform.security.session;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PkiSessionTicketService {

    private static final String TICKET_HEADER = "X-Pki-Session-Ticket";
    private static final String MAP_NAME = "pkiSessionTickets";

    private final IMap<String, VerifiedPkiPrincipal> ticketStore;
    private final Duration ticketTtl;
    private final String ticketCookieName;
    private final String cookiePath;
    private final boolean cookieSecure;
    private final String sameSite;

    public PkiSessionTicketService(
        HazelcastInstance hazelcastInstance,
        @Value("${dts.platform.session.pki-ticket.ttl-seconds:120}") long ticketTtlSeconds,
        @Value("${dts.platform.session.pki-ticket.cookie-name:pki_session_ticket}") String ticketCookieName,
        @Value("${dts.platform.session.pki-ticket.cookie-path:/}") String cookiePath,
        @Value("${dts.platform.session.pki-ticket.cookie-secure:false}") boolean cookieSecure,
        @Value("${dts.platform.session.pki-ticket.cookie-same-site:Lax}") String sameSite
    ) {
        this.ticketStore = hazelcastInstance.getMap(MAP_NAME);
        long ttlSeconds = ticketTtlSeconds <= 0 ? 120 : ticketTtlSeconds;
        this.ticketTtl = Duration.ofSeconds(ttlSeconds);
        this.ticketCookieName = StringUtils.hasText(ticketCookieName) ? ticketCookieName.trim() : "pki_session_ticket";
        this.cookiePath = StringUtils.hasText(cookiePath) ? cookiePath.trim() : "/";
        this.cookieSecure = cookieSecure;
        this.sameSite = StringUtils.hasText(sameSite) ? sameSite.trim() : "Lax";
    }

    public ResponseCookie issue(String username, Map<String, Object> user) {
        String normalizedUsername = sanitizeUsername(username);
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("pki_ticket_username_missing");
        }
        String ticket = "pki-" + UUID.randomUUID();
        ticketStore.put(ticket, new VerifiedPkiPrincipal(normalizedUsername, copyMap(user)), ticketTtl.toSeconds(), TimeUnit.SECONDS);
        return ResponseCookie
            .from(ticketCookieName, ticket)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(sameSite)
            .path(cookiePath)
            .maxAge(ticketTtl)
            .build();
    }

    public VerifiedPkiPrincipal consume(HttpServletRequest request, String expectedUsername) {
        String ticket = resolveTicket(request);
        if (ticket == null) {
            return null;
        }
        VerifiedPkiPrincipal principal = ticketStore.remove(ticket);
        if (principal == null) {
            return null;
        }
        String expected = sanitizeUsername(expectedUsername);
        if (expected != null && !expected.equalsIgnoreCase(principal.username())) {
            return null;
        }
        return principal;
    }

    public ResponseCookie clearTicketCookie() {
        return ResponseCookie
            .from(ticketCookieName, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(sameSite)
            .path(cookiePath)
            .maxAge(Duration.ZERO)
            .build();
    }

    private String resolveTicket(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String headerValue = sanitizeToken(request.getHeader(TICKET_HEADER));
        if (headerValue != null) {
            return headerValue;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && ticketCookieName.equals(cookie.getName())) {
                return sanitizeToken(cookie.getValue());
            }
        }
        return null;
    }

    private String sanitizeToken(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String value = candidate.trim();
        if (value.length() > 256) {
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

    public record VerifiedPkiPrincipal(String username, Map<String, Object> user) implements Serializable {}
}
