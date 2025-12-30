package com.yuzhi.dts.platform.security.policy;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public enum DataLevel {
    // 数据密级（classification/data_level）：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL
    // 备注：仍保留部分旧值/中文口径作为兼容别名（如 NON_SECRET/GENERAL/IMPORTANT/CORE/TOP_SECRET）。
    DATA_PUBLIC(List.of("0", "PUBLIC", "NON_SECRET", "公开", "非密", "公开级")),
    DATA_INTERNAL(List.of("1", "INTERNAL", "GENERAL", "内部", "一般", "内部级")),
    DATA_SECRET(List.of("2", "SECRET", "IMPORTANT", "秘密", "重要", "秘密级")),
    DATA_CONFIDENTIAL(List.of("3", "CONFIDENTIAL", "TOP_SECRET", "DATA_TOP_SECRET", "机密", "核心", "机密级"));

    private final List<String> normalizedSynonyms;
    private final List<String> literalSynonyms;

    DataLevel(List<String> synonyms) {
        List<String> literals = synonyms == null
            ? List.of()
            : synonyms
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> token.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableList());
        this.literalSynonyms = literals;
        this.normalizedSynonyms = literals
            .stream()
            .map(token -> token.replace('-', '_').replace(' ', '_'))
            .collect(Collectors.toUnmodifiableList());
    }

    public int rank() {
        return switch (this) {
            case DATA_PUBLIC -> 0;
            case DATA_INTERNAL -> 1;
            case DATA_SECRET -> 2;
            case DATA_CONFIDENTIAL -> 3;
        };
    }

    public List<String> synonyms() {
        return normalizedSynonyms;
    }

    public Set<String> tokens() {
        Set<String> tokens = new LinkedHashSet<>();
        addVariants(tokens, name());
        addVariants(tokens, classification());
        for (String literal : literalSynonyms) {
            addVariants(tokens, literal);
        }
        for (String normalized : normalizedSynonyms) {
            addVariants(tokens, normalized);
        }
        return tokens;
    }

    public static DataLevel normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Preferred: unify parsing (supports numeric 0/1/2/3 and Chinese labels)
        SecurityLevelCatalog.DataSecurityLevel parsed = SecurityLevelCatalog.DataSecurityLevel.parse(trimmed);
        if (parsed != null) {
            return switch (parsed) {
                case PUBLIC -> DATA_PUBLIC;
                case INTERNAL -> DATA_INTERNAL;
                case SECRET -> DATA_SECRET;
                case CONFIDENTIAL -> DATA_CONFIDENTIAL;
            };
        }

        String canonical = trimmed.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (canonical.equals("TOP_SECRET") || canonical.equals("DATA_TOP_SECRET")) {
            return DATA_CONFIDENTIAL;
        }
        for (DataLevel level : values()) {
            if (level.name().equals(canonical)) {
                return level;
            }
            if (level.classification().equals(canonical)) {
                return level;
            }
            if (level.normalizedSynonyms.contains(canonical)) {
                return level;
            }
        }
        // Secondary pass without replacing spaces for synonyms that may rely on original text (e.g. Chinese)
        String plainUpper = trimmed.toUpperCase(Locale.ROOT);
        for (DataLevel level : values()) {
            if (level.literalSynonyms.contains(plainUpper)) {
                return level;
            }
        }
        return null;
    }

    /**
     * 返回去掉 DATA_ 前缀后的密级名称，保持与表字段中常见的 PUBLIC/INTERNAL 等取值一致。
     */
    public String classification() {
        String name = name();
        return name.startsWith("DATA_") ? name.substring("DATA_".length()) : name;
    }

    private static void addVariants(Set<String> target, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String upper = token.trim().toUpperCase(Locale.ROOT);
        target.add(upper);
        if (upper.contains("_")) {
            target.add(upper.replace('_', '-'));
        }
        if (upper.contains("-")) {
            target.add(upper.replace('-', '_'));
        }
        if (upper.contains(" ")) {
            target.add(upper.replace(' ', '_'));
            target.add(upper.replace(' ', '-'));
        }
    }
}
