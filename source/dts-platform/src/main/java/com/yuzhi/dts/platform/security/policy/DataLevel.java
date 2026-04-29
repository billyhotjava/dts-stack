package com.yuzhi.dts.platform.security.policy;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum DataLevel {
    DATA_PUBLIC(SecurityLevelCatalog.DataSecurityLevel.PUBLIC),
    DATA_INTERNAL(SecurityLevelCatalog.DataSecurityLevel.INTERNAL),
    DATA_SECRET(SecurityLevelCatalog.DataSecurityLevel.SECRET),
    DATA_CONFIDENTIAL(SecurityLevelCatalog.DataSecurityLevel.CONFIDENTIAL);

    private final SecurityLevelCatalog.DataSecurityLevel catalogLevel;

    DataLevel(SecurityLevelCatalog.DataSecurityLevel catalogLevel) {
        this.catalogLevel = catalogLevel;
    }

    public int rank() {
        return catalogLevel.number();
    }

    public List<String> synonyms() {
        return List.copyOf(catalogLevel.tokens());
    }

    public Set<String> tokens() {
        return catalogLevel.tokens();
    }

    public static DataLevel normalize(String value) {
        SecurityLevelCatalog.DataSecurityLevel parsed = SecurityLevelCatalog.DataSecurityLevel.parse(value);
        if (parsed != null) {
            for (DataLevel level : values()) {
                if (level.catalogLevel == parsed) {
                    return level;
                }
            }
        } else if (value != null) {
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (DataLevel level : values()) {
                if (level.name().equals(normalized)) {
                    return level;
                }
            }
        }
        return null;
    }

    static DataLevel fromCatalog(SecurityLevelCatalog.DataSecurityLevel catalogLevel) {
        if (catalogLevel == null) {
            return null;
        }
        for (DataLevel level : values()) {
            if (level.catalogLevel == catalogLevel) {
                return level;
            }
        }
        return null;
    }

    /**
     * 返回去掉 DATA_ 前缀后的密级名称，保持与表字段中常见的 PUBLIC/INTERNAL 等取值一致。
     */
    public String classification() {
        return catalogLevel.code();
    }
}
