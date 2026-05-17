package com.yuzhi.dts.platform.service.catalog;

import java.util.Locale;

public final class CodeAssetLifecycleMapper {

    private CodeAssetLifecycleMapper() {}

    public static String fromIndicatorStatus(String status) {
        return switch (normalize(status)) {
            case "DRAFT", "PENDING_APPROVAL", "APPROVED" -> "DRAFT_GOVERNANCE";
            case "PUBLISHED", "ACTIVE", "PROMOTED" -> "ACTIVE";
            case "TESTING" -> "TESTING";
            case "ARCHIVED", "RETIRED", "DISABLED" -> "ARCHIVED";
            case "DEPRECATED" -> "DEPRECATED";
            default -> "PENDING_GOVERNANCE";
        };
    }

    public static String fromModelingSqlModelStatus(String status, Boolean enabled) {
        if (Boolean.FALSE.equals(enabled)) {
            return "ARCHIVED";
        }
        return switch (normalize(status)) {
            case "DRAFT", "PENDING_APPROVAL", "APPROVED" -> "DRAFT_GOVERNANCE";
            case "PUBLISHED", "ACTIVE", "PROMOTED" -> "ACTIVE";
            case "TESTING" -> "TESTING";
            case "ARCHIVED", "RETIRED", "DISABLED" -> "ARCHIVED";
            case "DEPRECATED" -> "DEPRECATED";
            default -> "PENDING_GOVERNANCE";
        };
    }

    public static String fromApiServiceStatus(String status) {
        return switch (normalize(status)) {
            case "DRAFT", "PENDING_APPROVAL", "APPROVED" -> "DRAFT_GOVERNANCE";
            case "PUBLISHED", "ACTIVE", "PROMOTED" -> "ACTIVE";
            case "TESTING" -> "TESTING";
            case "ARCHIVED", "RETIRED", "DISABLED" -> "ARCHIVED";
            case "DEPRECATED" -> "DEPRECATED";
            default -> "PENDING_GOVERNANCE";
        };
    }

    public static String fromDataStandardStatus(String status) {
        return switch (normalize(status)) {
            case "DRAFT", "PENDING_APPROVAL", "APPROVED" -> "DRAFT_GOVERNANCE";
            case "PUBLISHED", "ACTIVE", "PROMOTED" -> "ACTIVE";
            case "TESTING" -> "TESTING";
            case "ARCHIVED", "RETIRED", "DISABLED" -> "ARCHIVED";
            case "DEPRECATED" -> "DEPRECATED";
            default -> "PENDING_GOVERNANCE";
        };
    }

    private static String normalize(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
