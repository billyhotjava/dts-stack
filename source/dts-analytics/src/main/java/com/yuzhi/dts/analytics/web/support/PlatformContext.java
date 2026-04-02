package com.yuzhi.dts.analytics.web.support;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

public record PlatformContext(String dept, String classification, String roles) {

    public static PlatformContext from(HttpServletRequest request) {
        if (request == null) {
            return new PlatformContext(null, null, null);
        }
        return new PlatformContext(
                trimToNull(request.getHeader("X-DTS-Dept")),
                trimToNull(request.getHeader("X-DTS-Classification")),
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

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }
}

