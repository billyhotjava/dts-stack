package com.yuzhi.dts.common.service.util;

import java.util.Locale;
import java.util.Set;

public final class TargetRefValidator {

    private static final Set<String> ALLOWED_SCHEMES =
        Set.of("db", "file", "hdfs", "s3", "kafka", "http", "https", "api", "other");

    private TargetRefValidator() {}

    public static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("targetRef must not be null");
        }
        String ref = input.trim();
        if (ref.isEmpty() || ref.length() > 512 || ref.contains(" ")) {
            throw new IllegalArgumentException("Invalid targetRef: empty/too long/contains spaces");
        }
        int idx = ref.indexOf("://");
        if (idx <= 0) {
            throw new IllegalArgumentException("Invalid targetRef: missing scheme");
        }
        String scheme = ref.substring(0, idx).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("Invalid targetRef scheme: " + scheme);
        }
        String rest = ref.substring(idx + 3);
        // remove trailing slash except for root
        while (rest.endsWith("/") && rest.length() > 1) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return scheme + "://" + rest;
    }
}

