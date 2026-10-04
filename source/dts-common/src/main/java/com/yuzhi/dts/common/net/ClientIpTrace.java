package com.yuzhi.dts.common.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;

/**
 * Immutable evidence captured while resolving the client IP from reverse-proxy headers.
 */
public record ClientIpTrace(
    String resolved,
    String forwarded,
    String forwardedFor,
    String realIp,
    String remoteAddr,
    List<String> candidates,
    boolean fallbackToRemote,
    boolean missingForwarded
) {
    public ClientIpTrace {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static ClientIpTrace empty() {
        return new ClientIpTrace(null, null, null, null, null, List.of(), false, true);
    }

    public static ClientIpTrace from(IpAddressUtils.HeaderLookup headers, String remoteAddr) {
        if (headers == null) {
            return from(null, null, null, remoteAddr);
        }
        return from(headers.header("Forwarded"), headers.header("X-Forwarded-For"), headers.header("X-Real-IP"), remoteAddr);
    }

    public static ClientIpTrace from(String forwarded, String forwardedFor, String realIp, String remoteAddr) {
        List<String> candidates = new ArrayList<>();
        appendForwardedCandidates(forwarded, candidates);
        appendCommaCandidates(forwardedFor, candidates);
        appendCandidate(realIp, candidates);
        appendCandidate(remoteAddr, candidates);
        String resolved = IpAddressUtils.resolveClientIp(forwarded, forwardedFor, realIp, remoteAddr);
        String normalizedRemote = IpAddressUtils.resolveClientIp(remoteAddr);
        boolean fallbackToRemote = hasText(resolved) && hasText(normalizedRemote) && resolved.trim().equals(normalizedRemote.trim());
        boolean missingForwarded = !hasText(forwarded) && !hasText(forwardedFor) && !hasText(realIp);
        return new ClientIpTrace(
            resolved,
            trimToNull(forwarded),
            trimToNull(forwardedFor),
            trimToNull(realIp),
            trimToNull(remoteAddr),
            Collections.unmodifiableList(candidates),
            fallbackToRemote,
            missingForwarded
        );
    }

    public void logInfo(Logger log, String marker, String method, String uri) {
        if (log == null || !log.isInfoEnabled()) {
            return;
        }
        log.info(
            "[{}-client-ip] method={} uri={} resolved={} forwarded='{}' forwardedStd='{}' real='{}' remote='{}' candidates={} fallbackToRemote={} missingForwarded={}",
            nullSafe(marker),
            nullSafe(method),
            nullSafe(uri),
            nullSafe(resolved),
            nullSafe(forwardedFor),
            nullSafe(forwarded),
            nullSafe(realIp),
            nullSafe(remoteAddr),
            candidates,
            fallbackToRemote,
            missingForwarded
        );
    }

    private static void appendForwardedCandidates(String header, List<String> candidates) {
        if (!hasText(header)) {
            return;
        }
        for (String segment : header.split(",")) {
            if (!hasText(segment)) {
                continue;
            }
            for (String part : segment.split(";")) {
                String trimmed = trimToNull(part);
                if (trimmed == null || !trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
                    continue;
                }
                appendCandidate(trimmed, candidates);
            }
        }
    }

    private static void appendCommaCandidates(String value, List<String> candidates) {
        if (!hasText(value)) {
            return;
        }
        for (String segment : value.split(",")) {
            appendCandidate(segment, candidates);
        }
    }

    private static void appendCandidate(String value, List<String> candidates) {
        String resolved = IpAddressUtils.resolveClientIp(value);
        if (hasText(resolved)) {
            candidates.add(resolved.trim());
        }
    }

    private static String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
