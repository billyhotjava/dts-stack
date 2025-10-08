package com.yuzhi.dts.platform.security.policy;

import java.util.Locale;

public enum PersonnelLevel {
    GENERAL,
    IMPORTANT,
    CORE;

    public int rank() {
        return switch (this) {
            case GENERAL -> 0;
            case IMPORTANT -> 1;
            case CORE -> 2;
        };
    }

    public static PersonnelLevel normalize(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "GENERAL" -> GENERAL;
            case "IMPORTANT" -> IMPORTANT;
            case "CORE" -> CORE;
            default -> null;
        };
    }
}

