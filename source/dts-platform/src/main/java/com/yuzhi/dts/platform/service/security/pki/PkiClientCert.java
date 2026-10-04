package com.yuzhi.dts.platform.service.security.pki;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

public record PkiClientCert(
    boolean present,
    boolean verified,
    String serial,
    String subjectDn,
    String issuerDn,
    Instant notBefore,
    Instant notAfter
) {
    public static PkiClientCert absent() {
        return new PkiClientCert(false, false, null, null, null, null, null);
    }

    public static PkiClientCert fromRequest(HttpServletRequest request) {
        if (request == null) {
            return absent();
        }
        String verify = firstHeader(request, "X-SSL-Client-Verify", "SSL-Client-Verify", "X-Client-Cert-Verify");
        String serial = firstHeader(request, "X-SSL-Client-Serial", "SSL-Client-Serial", "X-Client-Cert-Serial");
        String subject = firstHeader(request, "X-SSL-Client-S-DN", "SSL-Client-S-DN", "X-Client-Cert-Subject");
        String issuer = firstHeader(request, "X-SSL-Client-I-DN", "SSL-Client-I-DN", "X-Client-Cert-Issuer");
        String notBefore = firstHeader(request, "X-SSL-Client-NotBefore", "SSL-Client-NotBefore");
        String notAfter = firstHeader(request, "X-SSL-Client-NotAfter", "SSL-Client-NotAfter");

        boolean hasAny = StringUtils.isNotBlank(verify) || StringUtils.isNotBlank(serial) || StringUtils.isNotBlank(subject) || StringUtils.isNotBlank(issuer);
        if (!hasAny) {
            return absent();
        }
        boolean verified = isVerified(verify);
        return new PkiClientCert(
            true,
            verified,
            trimToNull(serial),
            trimToNull(subject),
            trimToNull(issuer),
            parseInstant(notBefore),
            parseInstant(notAfter)
        );
    }

    private static boolean isVerified(String verifyHeader) {
        if (!StringUtils.isNotBlank(verifyHeader)) {
            return false;
        }
        String v = verifyHeader.trim().toUpperCase(Locale.ROOT);
        return v.equals("SUCCESS") || v.equals("TRUE") || v.equals("1") || v.equals("OK") || v.equals("YES");
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        if (request == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (!StringUtils.isNotBlank(name)) {
                continue;
            }
            String value = request.getHeader(name);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String raw) {
        String trimmed = raw == null ? null : raw.trim();
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static Instant parseInstant(String raw) {
        String text = trimToNull(raw);
        if (text == null) {
            return null;
        }
        try {
            // Prefer ISO-8601; gateway can pass RFC3339 / ISO
            return Instant.parse(text);
        } catch (Exception ignored) {}
        try {
            return DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(java.time.ZoneOffset.UTC).parse(text, Instant::from);
        } catch (Exception ignored) {}
        return null;
    }
}

