package com.yuzhi.dts.analytics.web.support;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record PlatformContext(String dept, String classification, String roles) {

    public static PlatformContext from(HttpServletRequest request) {
        if (request == null) {
            return new PlatformContext(null, null, null);
        }
        return new PlatformContext(
                firstHeader(request, "X-DTS-Dept", "X-DTS-Dept-Code"),
                resolveClassification(request),
                trimToNull(request.getHeader("X-DTS-Roles")));
    }

    public List<String> rolesList() {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static String resolveClassification(HttpServletRequest request) {
        String explicit = trimToNull(request.getHeader("X-DTS-Classification"));
        if (explicit != null) {
            return explicit;
        }
        return mapPersonnelLevelToMaxClassification(request.getHeader("X-DTS-Personnel-Level"));
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = trimToNull(request.getHeader(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    /**
     * Map the X-DTS-Personnel-Level header to the highest data level the caller may read.
     *
     * <p>The results agree with {@code SecurityLevelCatalog.maxDataLevelForPersonnel}
     * (GENERAL→SECRET, IMPORTANT/CORE→CONFIDENTIAL), but this table is deliberately NOT replaced
     * by {@code SecurityLevelCatalog.parseMaxDataLevel}: the two disagree on bare digits. Here
     * "0"/"1"/"2" are personnel levels (0→GENERAL→SECRET), whereas the catalog reads digits as
     * data levels (0→PUBLIC). Since this header carries a personnel level, the local reading is
     * the correct one for this call site. Collapsing the two needs a dedicated
     * personnel-first catalog API rather than a drop-in swap.
     */
    private static String mapPersonnelLevelToMaxClassification(String value) {
        String token = normalizeToken(value);
        if (token == null) {
            return null;
        }
        return switch (token) {
            case "0", "GENERAL", "GN", "GE", "G" -> "SECRET";
            case "1", "IMPORTANT", "IMPORTAN", "IM", "I", "2", "CORE", "CO", "C" -> "CONFIDENTIAL";
            case "PUBLIC", "DATA_PUBLIC" -> "PUBLIC";
            case "INTERNAL", "DATA_INTERNAL" -> "INTERNAL";
            case "SECRET", "DATA_SECRET" -> "SECRET";
            case "CONFIDENTIAL", "TOP_SECRET", "DATA_CONFIDENTIAL", "DATA_TOP_SECRET" -> "CONFIDENTIAL";
            default -> null;
        };
    }

    private static String normalizeToken(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
