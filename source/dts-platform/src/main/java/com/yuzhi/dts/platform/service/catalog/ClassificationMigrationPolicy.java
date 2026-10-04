package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import org.springframework.util.StringUtils;

final class ClassificationMigrationPolicy {

    private ClassificationMigrationPolicy() {}

    static Decision evaluate(String legacyRaw, String detectedRaw, String existingRaw) {
        String legacy = validLevel(legacyRaw);
        String detected = validLevel(detectedRaw);
        if (StringUtils.hasText(legacyRaw) && legacy == null) {
            return new Decision("BLOCKED_INVALID", null, "Legacy classification code is unknown");
        }
        String existing = validLevel(existingRaw);
        String computed = SecurityLevelCatalog.maxDataCode(legacy, detected, existing);
        if (computed == null) {
            return new Decision("BLOCKED_MISSING", null, "No trusted legacy, detected or existing classification");
        }
        if (
            existing != null &&
            legacy != null &&
            SecurityLevelCatalog.isDataDowngrade(legacy, existing)
        ) {
            return new Decision(
                "BLOCKED_DOWNGRADE",
                computed,
                "Current fact is lower than the legacy value; retain legacy and require review"
            );
        }
        if (existing == null) {
            return new Decision("CREATE", computed, "Create initial immutable classification fact");
        }
        if (
            !existing.equals(computed) &&
            SecurityLevelCatalog.isDataAtLeast(computed, existing)
        ) {
            return new Decision("RAISE", computed, "Raise existing fact to the highest migration candidate");
        }
        return new Decision("UNCHANGED", existing, "Existing fact already satisfies the migration candidate");
    }

    private static String validLevel(String value) {
        var parsed = SecurityLevelCatalog.DataSecurityLevel.parse(value);
        return parsed == null ? null : parsed.code();
    }

    record Decision(String code, String computedLevel, String reason) {}
}
