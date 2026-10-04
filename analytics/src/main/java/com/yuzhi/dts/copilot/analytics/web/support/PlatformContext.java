package com.yuzhi.dts.copilot.analytics.web.support;

import jakarta.servlet.http.HttpServletRequest;

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

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }
}

