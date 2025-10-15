package com.yuzhi.dts.common.net;

import java.util.Objects;

/**
 * Network utility helpers used across admin/platform modules.
 */
public final class IpAddressUtils {

    private IpAddressUtils() {}

    public static String resolveClientIp(String... candidates) {
        if (candidates == null) {
            return "127.0.0.1";
        }
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty() && !"unknown".equalsIgnoreCase(trimmed)) {
                int space = trimmed.indexOf(' ');
                if (space > 0) {
                    trimmed = trimmed.substring(0, space);
                }
                if (trimmed.indexOf(',') > 0) {
                    trimmed = trimmed.split(",")[0].trim();
                }
                return trimmed;
            }
        }
        return "127.0.0.1";
    }
}
