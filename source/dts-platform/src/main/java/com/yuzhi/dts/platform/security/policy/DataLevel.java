package com.yuzhi.dts.platform.security.policy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public enum DataLevel {
    DATA_PUBLIC(List.of("NON_SECRET", "公开", "非密", "公开级")),
    DATA_INTERNAL(List.of("GENERAL", "内部", "一般", "内部级")),
    DATA_SECRET(List.of("SECRET", "秘密", "重要", "秘密级")),
    DATA_CONFIDENTIAL(List.of("CONFIDENTIAL", "机密", "核心", "机密级"));

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
        String canonical = trimmed.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (canonical.equals("TOP_SECRET")) {
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
