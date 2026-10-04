package com.yuzhi.dts.platform.security.policy;

import java.util.Arrays;
import java.util.Locale;

/**
 * Canonical action vocabulary for asset operation authorization.
 */
public enum AssetAction {
    CREATE("新增", true),
    DELETE("删除", true),
    UPDATE("修改", true),
    COPY("复制", true),
    IMPORT("导入", true),
    EXPORT("导出", false),
    ARCHIVE("归档", true),
    DESTROY("销毁", true);

    private final String displayName;
    private final boolean mutating;

    AssetAction(String displayName, boolean mutating) {
        this.displayName = displayName;
        this.mutating = mutating;
    }

    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public boolean mutating() {
        return mutating;
    }

    public static AssetAction from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Asset action must not be blank");
        }
        String normalized = raw.trim();
        return Arrays
            .stream(values())
            .filter(action ->
                action.name().equals(normalized.toUpperCase(Locale.ROOT)) || action.displayName.equals(normalized)
            )
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported asset action: " + raw));
    }
}
