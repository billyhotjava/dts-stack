package com.yuzhi.dts.common.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unified security level catalog shared by dts-admin and dts-platform.
 *
 * <p>Canonical codes:
 * <ul>
 *   <li>Personnel: GENERAL(0) / IMPORTANT(1) / CORE(2)</li>
 *   <li>Data: PUBLIC(0) / INTERNAL(1) / SECRET(2) / CONFIDENTIAL(3)</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Keycloak user attribute {@code person_security_level} stores canonical semantic codes and accepts numeric aliases (0/1/2).</li>
 *   <li>Upstream systems might send numeric or Chinese labels for data classification.</li>
 *   <li>Legacy tokens like NON_SECRET/TOP_SECRET are accepted as compatibility aliases.</li>
 * </ul>
 */
public final class SecurityLevelCatalog {

    private SecurityLevelCatalog() {}

    public static final PersonnelSecurityLevel DEFAULT_PERSONNEL_SECURITY_LEVEL = PersonnelSecurityLevel.GENERAL;
    public static final DataSecurityLevel DEFAULT_DATA_SECURITY_LEVEL = DataSecurityLevel.INTERNAL;

    public enum PersonnelSecurityLevel {
        GENERAL(
            0,
            "GENERAL",
            "一般",
            List.of("0", "GN", "GE", "G", "NON_SECRET", "NONE_SECRET", "NS", "PUBLIC", "INTERNAL", "DATA_PUBLIC", "DATA_INTERNAL")
        ),
        IMPORTANT(1, "IMPORTANT", "重要", List.of("1", "IMPORTAN", "IM", "I", "SECRET", "DATA_SECRET")),
        CORE(2, "CORE", "核心", List.of("2", "CO", "C", "CONFIDENTIAL", "TOP_SECRET", "DATA_TOP_SECRET", "DATA_CONFIDENTIAL"));

        private final int number;
        private final String code;
        private final String labelZh;
        private final Set<String> tokens;

        PersonnelSecurityLevel(int number, String code, String labelZh, List<String> aliases) {
            this.number = number;
            this.code = code;
            this.labelZh = labelZh;
            this.tokens = buildTokens(code, labelZh, aliases);
        }

        public int number() {
            return number;
        }

        public String code() {
            return code;
        }

        public String labelZh() {
            return labelZh;
        }

        public Set<String> tokens() {
            return tokens;
        }

        public static PersonnelSecurityLevel parse(Object raw) {
            String token = normalizeToken(raw);
            if (token == null) return null;
            if (isDigits(token)) {
                try {
                    int n = Integer.parseInt(token);
                    return fromNumber(n);
                } catch (Exception ignored) {
                    return null;
                }
            }

            String upper = normalizeCodeToken(token);
            if (upper.startsWith("ROLE_")) upper = upper.substring("ROLE_".length());
            for (PersonnelSecurityLevel level : values()) {
                if (level.tokens.contains(upper)) {
                    return level;
                }
            }
            return null;
        }

        public static PersonnelSecurityLevel fromNumber(int n) {
            if (n <= 0) return GENERAL;
            if (n == 1) return IMPORTANT;
            return CORE;
        }
    }

    public enum DataSecurityLevel {
        PUBLIC(0, "PUBLIC", "公开", List.of("0", "DATA_PUBLIC", "NON_SECRET", "NONE_SECRET", "NS", "非密", "公开级")),
        INTERNAL(1, "INTERNAL", "内部", List.of("1", "DATA_INTERNAL", "GENERAL", "一般", "内部级")),
        SECRET(2, "SECRET", "秘密", List.of("2", "DATA_SECRET", "IMPORTANT", "重要", "秘密级")),
        CONFIDENTIAL(
            3,
            "CONFIDENTIAL",
            "机密",
            List.of("3", "DATA_CONFIDENTIAL", "CORE", "TOP_SECRET", "DATA_TOP_SECRET", "CORE_SECRET", "核心", "机密级")
        );

        private final int number;
        private final String code;
        private final String labelZh;
        private final Set<String> tokens;

        DataSecurityLevel(int number, String code, String labelZh, List<String> aliases) {
            this.number = number;
            this.code = code;
            this.labelZh = labelZh;
            this.tokens = buildTokens(code, labelZh, aliases);
        }

        public int number() {
            return number;
        }

        public String code() {
            return code;
        }

        public String labelZh() {
            return labelZh;
        }

        public Set<String> tokens() {
            return tokens;
        }

        public static DataSecurityLevel parse(Object raw) {
            String token = normalizeToken(raw);
            if (token == null) return null;
            if (isDigits(token)) {
                try {
                    int n = Integer.parseInt(token);
                    return fromNumber(n);
                } catch (Exception ignored) {
                    return null;
                }
            }

            String upper = normalizeCodeToken(token);
            for (DataSecurityLevel level : values()) {
                if (level.tokens.contains(upper)) {
                    return level;
                }
            }
            return null;
        }

        public static DataSecurityLevel fromNumber(int n) {
            return switch (n) {
                case 0 -> PUBLIC;
                case 1 -> INTERNAL;
                case 2 -> SECRET;
                default -> CONFIDENTIAL;
            };
        }
    }

    /**
     * Personnel -> maximum data level accessible (system supports up to CONFIDENTIAL).
     *
     * <p>Rule (per user input):
     * GENERAL can read PUBLIC/INTERNAL/SECRET
     * IMPORTANT can additionally read CONFIDENTIAL
     * CORE is higher (future), but currently capped at CONFIDENTIAL
     */
    public static DataSecurityLevel maxDataLevelForPersonnel(PersonnelSecurityLevel personnel) {
        if (personnel == null) return null;
        return switch (personnel) {
            case GENERAL -> DataSecurityLevel.SECRET;
            case IMPORTANT -> DataSecurityLevel.CONFIDENTIAL;
            case CORE -> DataSecurityLevel.CONFIDENTIAL;
        };
    }

    public static List<DataSecurityLevel> allowedDataLevelsForPersonnel(PersonnelSecurityLevel personnel) {
        DataSecurityLevel max = maxDataLevelForPersonnel(personnel);
        if (max == null) {
            return List.of();
        }
        return Arrays
            .stream(DataSecurityLevel.values())
            .filter(level -> level.number() <= max.number())
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Parse a maximum data level from either:
     * - data level tokens (PUBLIC/INTERNAL/SECRET/CONFIDENTIAL; 0/1/2/3; Chinese labels)
     * - personnel semantic tokens (GENERAL/IMPORTANT/CORE and short aliases)
     */
    public static DataSecurityLevel parseMaxDataLevel(Object raw) {
        PersonnelSecurityLevel personnel = parsePersonnelCodeToken(raw);
        if (personnel != null) {
            return maxDataLevelForPersonnel(personnel);
        }
        DataSecurityLevel data = DataSecurityLevel.parse(raw);
        if (data != null) return data;
        return null;
    }

    public static String normalizePersonnelCode(Object raw) {
        PersonnelSecurityLevel level = PersonnelSecurityLevel.parse(raw);
        return level == null ? null : level.code();
    }

    public static String normalizePersonnelCodeOrDefault(Object raw) {
        String code = normalizePersonnelCode(raw);
        return code == null ? DEFAULT_PERSONNEL_SECURITY_LEVEL.code() : code;
    }

    public static String normalizeDataCode(Object raw) {
        DataSecurityLevel level = DataSecurityLevel.parse(raw);
        return level == null ? null : level.code();
    }

    public static String normalizeDataCodeOrDefault(Object raw) {
        String code = normalizeDataCode(raw);
        return code == null ? DEFAULT_DATA_SECURITY_LEVEL.code() : code;
    }

    public static String normalizePrefixedDataCode(Object raw) {
        DataSecurityLevel level = DataSecurityLevel.parse(raw);
        return level == null ? null : "DATA_" + level.code();
    }

    public static String normalizePrefixedDataCodeOrDefault(Object raw) {
        String code = normalizePrefixedDataCode(raw);
        return code == null ? "DATA_" + DEFAULT_DATA_SECURITY_LEVEL.code() : code;
    }

    public static List<String> dataStorageTokens(DataSecurityLevel level) {
        if (level == null) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        tokens.add(level.code());
        tokens.add("DATA_" + level.code());
        if (level == DataSecurityLevel.PUBLIC) {
            tokens.add("NON_SECRET");
            tokens.add("NONE_SECRET");
        } else if (level == DataSecurityLevel.CONFIDENTIAL) {
            tokens.add("TOP_SECRET");
            tokens.add("DATA_TOP_SECRET");
        }
        return List.copyOf(tokens);
    }

    public static String normalizeMaxDataCode(Object raw) {
        DataSecurityLevel level = parseMaxDataLevel(raw);
        return level == null ? null : level.code();
    }

    public static String normalizeMaxDataCodeOrDefault(Object raw) {
        String code = normalizeMaxDataCode(raw);
        return code == null ? DEFAULT_DATA_SECURITY_LEVEL.code() : code;
    }

    public static Set<String> personnelCodes() {
        return Arrays
            .stream(PersonnelSecurityLevel.values())
            .map(PersonnelSecurityLevel::code)
            .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> dataCodes() {
        return Arrays.stream(DataSecurityLevel.values()).map(DataSecurityLevel::code).collect(Collectors.toUnmodifiableSet());
    }

    public static List<String> dataCodesInOrder() {
        return Arrays.stream(DataSecurityLevel.values()).map(DataSecurityLevel::code).collect(Collectors.toUnmodifiableList());
    }

    public static Map<String, String> personnelLabelsZh() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (PersonnelSecurityLevel level : PersonnelSecurityLevel.values()) {
            labels.put(level.code(), level.labelZh());
        }
        return Collections.unmodifiableMap(labels);
    }

    public static Map<String, String> dataLabelsZh() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (DataSecurityLevel level : DataSecurityLevel.values()) {
            labels.put(level.code(), level.labelZh());
        }
        return Collections.unmodifiableMap(labels);
    }

    public static int dataRank(Object raw) {
        DataSecurityLevel level = DataSecurityLevel.parse(raw);
        return level == null ? -1 : level.number();
    }

    public static Integer dataRankOrNull(Object raw) {
        DataSecurityLevel level = DataSecurityLevel.parse(raw);
        return level == null ? null : level.number();
    }

    public static Integer maxDataRankForPersonnel(Object raw) {
        PersonnelSecurityLevel personnel = PersonnelSecurityLevel.parse(raw);
        DataSecurityLevel max = maxDataLevelForPersonnel(personnel);
        return max == null ? null : max.number();
    }

    public static boolean canPersonnelAccessData(Object personnelRaw, Object dataRaw) {
        Integer personnelMax = maxDataRankForPersonnel(personnelRaw);
        Integer data = dataRankOrNull(dataRaw);
        return personnelMax != null && data != null && personnelMax >= data;
    }

    private static String normalizeToken(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return String.valueOf(n.intValue());
        String s = Objects.toString(raw, "").trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static PersonnelSecurityLevel parsePersonnelCodeToken(Object raw) {
        String token = normalizeToken(raw);
        if (token == null || isDigits(token)) {
            return null;
        }
        String upper = normalizeCodeToken(token);
        if (upper.startsWith("ROLE_")) upper = upper.substring("ROLE_".length());
        return switch (upper) {
            case "GENERAL", "GN", "GE", "G" -> PersonnelSecurityLevel.GENERAL;
            case "IMPORTANT", "IMPORTAN", "IM", "I" -> PersonnelSecurityLevel.IMPORTANT;
            case "CORE", "CO", "C" -> PersonnelSecurityLevel.CORE;
            default -> null;
        };
    }

    private static String normalizeCodeToken(String token) {
        return token.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static Set<String> buildTokens(String code, String labelZh, List<String> aliases) {
        Set<String> tokens = new LinkedHashSet<>();
        addTokenVariants(tokens, code);
        addTokenVariants(tokens, labelZh);
        if (aliases != null) {
            aliases.forEach(alias -> addTokenVariants(tokens, alias));
        }
        return Collections.unmodifiableSet(tokens);
    }

    private static void addTokenVariants(Set<String> target, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String normalized = normalizeCodeToken(token.trim());
        target.add(normalized);
        if (normalized.contains("_")) {
            target.add(normalized.replace('_', '-'));
        }
        if (normalized.contains("-")) {
            target.add(normalized.replace('-', '_'));
        }
    }
}
