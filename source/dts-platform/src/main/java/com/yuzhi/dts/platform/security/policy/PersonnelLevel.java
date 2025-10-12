package com.yuzhi.dts.platform.security.policy;

import java.util.Locale;

public enum PersonnelLevel {
    GENERAL,
    IMPORTANT,
    CORE;

    public int rank() {
        return switch (this) {
            // Align with admin service securityRank: NON_SECRET=0, GENERAL=1, IMPORTANT=2, CORE=3
            // Platform does not model NON_SECRET explicitly; start from 1 here for consistency
            case GENERAL -> 1;
            case IMPORTANT -> 2;
            case CORE -> 3;
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
