package com.yuzhi.dts.common.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Network utility helpers used across admin/platform modules.
 * <p>
 * The audit subsystem uses these helpers to capture the real client IP behind a reverse proxy
 * or k8s ingress chain. Production clusters are typically deployed on private networks where
 * <em>both</em> the proxy and the client live in 10/8 or 192.168/16, so any logic that treats
 * private addresses as "proxies to skip" would regress to the container IP — which is exactly
 * the BUG-B symptom we are fixing.
 *
 * <p>The resolution rule is therefore intentionally simple:
 * <ol>
 *   <li>Walk the {@code candidates} in declaration order (typically Forwarded → X-Forwarded-For →
 *       X-Real-IP → request.getRemoteAddr()).</li>
 *   <li>For the first non-blank candidate, pick the first segment if comma-delimited (the
 *       outermost client in the {@code X-Forwarded-For} chain — RFC 7239 says left-most is the
 *       original client).</li>
 *   <li>Sanitize port / IPv6 brackets / RFC 7239 {@code for=} prefix off and return.</li>
 * </ol>
 * If the proxy chain is misconfigured and X-Forwarded-* are missing entirely, the
 * {@code remoteAddr} fallback is returned — preserving the previous behaviour for direct connections.
 */
public final class IpAddressUtils {

    private IpAddressUtils() {}

    public static String resolveClientIp(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            String resolved = firstNonBlankSegment(candidate);
            if (resolved == null) {
                continue;
            }
            String sanitized = sanitize(resolved);
            if (sanitized == null || "unknown".equalsIgnoreCase(sanitized)) {
                continue;
            }
            return normalize(sanitized);
        }
        return null;
    }

    /**
     * Servlet-free view over an HTTP request's headers, so dts-common can read the proxy header
     * chain without taking a {@code jakarta.servlet} dependency. Call sites pass {@code request::getHeader}.
     */
    @FunctionalInterface
    public interface HeaderLookup {
        String header(String name);
    }

    /**
     * Canonical client-IP resolution for every audit/security call site behind a reverse proxy.
     * <p>
     * Reads the proxy header chain in priority order — RFC 7239 {@code Forwarded} first, then the
     * de-facto {@code X-Forwarded-For} / {@code X-Real-IP}, finally the socket {@code remoteAddr}.
     * Centralising the header list (and its order) here is deliberate: call sites used to inline
     * their own 3-header list and several omitted {@code Forwarded}, so behind a Forwarded-only
     * proxy they regressed to the container IP. Route new call sites through this method instead of
     * re-listing headers.
     *
     * @param headers    header accessor, typically {@code request::getHeader}; {@code null} is tolerated
     * @param remoteAddr the socket remote address, typically {@code request.getRemoteAddr()}
     */
    public static String resolveClientIp(HeaderLookup headers, String remoteAddr) {
        if (headers == null) {
            return resolveClientIp(remoteAddr);
        }
        return resolveClientIp(
            headers.header("Forwarded"),
            headers.header("X-Forwarded-For"),
            headers.header("X-Real-IP"),
            remoteAddr
        );
    }

    private static String firstNonBlankSegment(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (!trimmed.contains(",")) {
            return trimmed;
        }
        // X-Forwarded-For can carry multiple hops; the left-most non-empty segment is the original client.
        for (String segment : trimmed.split(",")) {
            String s = segment.trim();
            if (!s.isEmpty() && !"unknown".equalsIgnoreCase(s)) {
                return s;
            }
        }
        return null;
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        // RFC 7239 Forwarded header format: "for=192.0.2.43" or "for=[2001:db8::1]:47011"
        if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
            trimmed = forwardedForValue(trimmed.substring(4));
        }
        // Drop trailing comment after a space.
        int space = trimmed.indexOf(' ');
        if (space > 0) {
            trimmed = trimmed.substring(0, space);
        }
        // Strip [::ipv6]:port wrappers.
        if (trimmed.startsWith("[")) {
            int bracket = trimmed.indexOf(']');
            if (bracket > 0) {
                trimmed = trimmed.substring(1, bracket);
            }
        }
        // For "1.2.3.4:5678" strip the port; leave bare IPv6 (multiple colons) untouched.
        int colon = trimmed.indexOf(':');
        if (colon > 0 && trimmed.indexOf(':', colon + 1) == -1) {
            trimmed = trimmed.substring(0, colon);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String forwardedForValue(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.startsWith("\"")) {
            int closeQuote = value.indexOf('"', 1);
            if (closeQuote > 0) {
                return value.substring(1, closeQuote).trim();
            }
        }
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    /**
     * Normalises {@code ::ffff:1.2.3.4} → {@code 1.2.3.4} and IPv4-compatible IPv6 addresses to
     * the dotted-quad form. Any value the runtime cannot parse is returned verbatim — better to
     * record an unparsed string than throw away client evidence at audit time.
     */
    private static String normalize(String literal) {
        if (literal == null) {
            return null;
        }
        if (literal.startsWith("::ffff:")) {
            String tail = literal.substring(7);
            if (tail.contains(".")) {
                return tail;
            }
        }
        try {
            InetAddress address = InetAddress.getByName(literal);
            if (address instanceof Inet6Address inet6 && inet6.isIPv4CompatibleAddress()) {
                byte[] bytes = inet6.getAddress();
                return (bytes[12] & 0xFF) + "." + (bytes[13] & 0xFF) + "." + (bytes[14] & 0xFF) + "." + (bytes[15] & 0xFF);
            }
            return address.getHostAddress();
        } catch (UnknownHostException ex) {
            return literal;
        }
    }
}
