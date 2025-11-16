package com.yuzhi.dts.admin.domain.enumeration;

/**
 * 人员生命周期状态。
 */
public enum PersonLifecycleStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    LEFT;

    public static PersonLifecycleStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ACTIVE;
        }
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "INACTIVE", "DISABLED", "OFFBOARD" -> INACTIVE;
            case "SUSPENDED", "LOCKED" -> SUSPENDED;
            case "LEFT", "DEPARTED", "QUIT", "RESIGNED" -> LEFT;
            default -> ACTIVE;
        };
    }
}
