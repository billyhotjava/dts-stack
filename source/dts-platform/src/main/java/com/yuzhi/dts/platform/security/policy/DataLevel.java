package com.yuzhi.dts.platform.security.policy;

import java.util.Locale;

public enum DataLevel {
    DATA_PUBLIC,
    DATA_INTERNAL,
    DATA_SECRET,
    DATA_TOP_SECRET;

    public int rank() {
        return switch (this) {
            case DATA_PUBLIC -> 0;
            case DATA_INTERNAL -> 1;
            case DATA_SECRET -> 2;
            case DATA_TOP_SECRET -> 3;
        };
    }

    public static DataLevel normalize(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.startsWith("DATA_")) {
            return DataLevel.valueOf(v);
        }
        return switch (v) {
            case "PUBLIC" -> DATA_PUBLIC;
            case "INTERNAL" -> DATA_INTERNAL;
            case "SECRET" -> DATA_SECRET;
            case "TOP_SECRET" -> DATA_TOP_SECRET;
            default -> null;
        };
    }

    /**
     * 返回去掉 DATA_ 前缀后的密级名称，保持与表字段中常见的 PUBLIC/INTERNAL 等取值一致。
     */
    public String classification() {
        String name = name();
        return name.startsWith("DATA_") ? name.substring("DATA_".length()) : name;
    }
}
