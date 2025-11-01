package com.yuzhi.dts.common.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Network utility helpers used across admin/platform modules.
 */
public final class IpAddressUtils {

    private IpAddressUtils() {}

    public static String resolveClientIp(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        List<String> chain = new ArrayList<>();
        boolean sawPublic = false;
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String raw = candidate.trim();
            if (raw.isEmpty() || "unknown".equalsIgnoreCase(raw)) {
                continue;
            }
            String[] segments = raw.split(",");
            if (segments.length == 0) {
                sawPublic |= appendCandidate(chain, raw);
            } else {
                for (String segment : segments) {
                    sawPublic |= appendCandidate(chain, segment);
                }
            }
        }
        if (chain.isEmpty()) {
            return null;
        }
        if (!sawPublic) {
            for (int idx = 0; idx < chain.size(); idx++) {
                String candidate = chain.get(idx);
                InetAddress address = parseInetAddress(candidate);
                if (address == null) {
                    continue;
                }
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                    continue;
                }
                return normalize(address, candidate);
            }
            return null;
        }
        String fallback = chain.get(chain.size() - 1);
        for (int idx = chain.size() - 1; idx >= 0; idx--) {
            String candidate = chain.get(idx);
            InetAddress address = parseInetAddress(candidate);
            if (address == null) {
                continue;
            }
            if (isLikelyProxy(address, candidate)) {
                continue;
            }
            return normalize(address, candidate);
        }
        InetAddress fallbackAddress = parseInetAddress(fallback);
        if (fallbackAddress != null) {
            return normalize(fallbackAddress, fallback);
        }
        return fallback;
    }

    private static boolean appendCandidate(List<String> chain, String raw) {
        String sanitized = sanitize(raw);
        if (sanitized != null) {
            chain.add(sanitized);
            InetAddress address = parseInetAddress(sanitized);
            return address != null && !isPrivateAddress(address, sanitized) && !address.isAnyLocalAddress()
                && !address.isLinkLocalAddress() && !address.isLoopbackAddress();
        }
        return false;
    }

    private static boolean isLikelyProxy(InetAddress address, String literal) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        return isPrivateAddress(address, literal);
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        int space = trimmed.indexOf(' ');
        if (space > 0) {
            trimmed = trimmed.substring(0, space);
        }
        if (trimmed.startsWith("[")) {
            int idx = trimmed.indexOf(']');
            if (idx > 0) {
                trimmed = trimmed.substring(1, idx);
            }
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0 && trimmed.indexOf(':', colon + 1) == -1) {
            trimmed = trimmed.substring(0, colon);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static InetAddress parseInetAddress(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private static boolean isPublicAddress(InetAddress address, String literal) {
        if (address instanceof Inet6Address inet6) {
            if (literal != null && literal.startsWith("::ffff:")) {
                return isPublicIpv4(literal.substring(7));
            }
            if (inet6.isIPv4CompatibleAddress()) {
                return isPublicIpv4(ipv4FromIpv6(inet6));
            }
            return !isPrivateIpv6Literal(literal) && !address.isSiteLocalAddress();
        }
        if (literal != null && literal.contains(".")) {
            return isPublicIpv4(literal);
        }
        return !address.isSiteLocalAddress();
    }

    private static boolean isPrivateAddress(InetAddress address, String literal) {
        if (address instanceof Inet6Address inet6) {
            if (literal != null && literal.startsWith("::ffff:")) {
                return !isPublicIpv4(literal.substring(7));
            }
            if (inet6.isIPv4CompatibleAddress()) {
                return !isPublicIpv4(ipv4FromIpv6(inet6));
            }
            return isPrivateIpv6Literal(literal) || address.isSiteLocalAddress();
        }
        if (literal != null && literal.contains(".")) {
            return !isPublicIpv4(literal);
        }
        return address.isSiteLocalAddress();
    }

    private static String ipv4FromIpv6(Inet6Address address) {
        byte[] addr = address.getAddress();
        return (addr[12] & 0xFF) + "." + (addr[13] & 0xFF) + "." + (addr[14] & 0xFF) + "." + (addr[15] & 0xFF);
    }

    private static boolean isPrivateIpv6Literal(String literal) {
        if (literal == null) {
            return false;
        }
        String value = literal.toLowerCase();
        return value.startsWith("fc") || value.startsWith("fd");
    }

    private static boolean isPublicIpv4(String value) {
        if (value == null) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            return false;
        }
        if (candidate.startsWith("::ffff:")) {
            candidate = candidate.substring(7);
        }
        int slash = candidate.indexOf('/');
        if (slash > 0) {
            candidate = candidate.substring(0, slash);
        }
        int colon = candidate.indexOf(':');
        if (colon > 0) {
            candidate = candidate.substring(0, colon);
        }
        String[] parts = candidate.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int p0 = Integer.parseInt(parts[0]);
            int p1 = Integer.parseInt(parts[1]);
            if (p0 == 10 || p0 == 127 || p0 == 0) {
                return false;
            }
            if (p0 == 169 && p1 == 254) {
                return false;
            }
            if (p0 == 192 && p1 == 168) {
                return false;
            }
            if (p0 == 172 && p1 >= 16 && p1 <= 31) {
                return false;
            }
            if (p0 == 100 && p1 >= 64 && p1 <= 127) {
                return false;
            }
            if (p0 == 198 && (p1 == 18 || p1 == 19)) {
                return false;
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    private static String normalize(InetAddress address, String literal) {
        if (address instanceof Inet6Address inet6) {
            if (literal != null && literal.startsWith("::ffff:")) {
                return literal.substring(7);
            }
            if (inet6.isIPv4CompatibleAddress()) {
                return ipv4FromIpv6(inet6);
            }
        }
        return address.getHostAddress();
    }
}
