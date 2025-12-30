package com.yuzhi.dts.common.security;

import java.util.Locale;
import java.util.Objects;

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
 *   <li>Keycloak user attribute {@code person_security_level} currently uses numeric codes (0/1/2).</li>
 *   <li>Upstream systems might send numeric or Chinese labels for data classification.</li>
 *   <li>Legacy tokens like NON_SECRET/TOP_SECRET are accepted as compatibility aliases.</li>
 * </ul>
 */
public final class SecurityLevelCatalog {

    private SecurityLevelCatalog() {}

    public enum PersonnelSecurityLevel {
        GENERAL(0, "GENERAL", "一般"),
        IMPORTANT(1, "IMPORTANT", "重要"),
        CORE(2, "CORE", "核心");

        private final int number;
        private final String code;
        private final String labelZh;

        PersonnelSecurityLevel(int number, String code, String labelZh) {
            this.number = number;
            this.code = code;
            this.labelZh = labelZh;
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

            // Accept Chinese labels
            if ("一般".equals(token)) return GENERAL;
            if ("重要".equals(token)) return IMPORTANT;
            if ("核心".equals(token)) return CORE;

            // Common aliases / mixed tokens
            String upper = token.toUpperCase(Locale.ROOT);
            if (upper.startsWith("ROLE_")) upper = upper.substring("ROLE_".length());
            upper = upper.replace('-', '_').replace(' ', '_');

            // Compatibility: old NON_SECRET/NONE_SECRET treated as GENERAL
            if ("NON_SECRET".equals(upper) || "NONE_SECRET".equals(upper) || "NS".equals(upper)) return GENERAL;

            // If someone passed data level by mistake, map conservatively
            if ("PUBLIC".equals(upper) || "INTERNAL".equals(upper)) return GENERAL;
            if ("SECRET".equals(upper)) return IMPORTANT;
            if ("CONFIDENTIAL".equals(upper) || "TOP_SECRET".equals(upper) || "DATA_TOP_SECRET".equals(upper)) return CORE;

            return switch (upper) {
                case "GENERAL" -> GENERAL;
                case "IMPORTANT", "IMPORTAN" -> IMPORTANT;
                case "CORE" -> CORE;
                default -> null;
            };
        }

        public static PersonnelSecurityLevel fromNumber(int n) {
            if (n <= 0) return GENERAL;
            if (n == 1) return IMPORTANT;
            return CORE;
        }
    }

    public enum DataSecurityLevel {
        PUBLIC(0, "PUBLIC", "公开"),
        INTERNAL(1, "INTERNAL", "内部"),
        SECRET(2, "SECRET", "秘密"),
        CONFIDENTIAL(3, "CONFIDENTIAL", "机密");

        private final int number;
        private final String code;
        private final String labelZh;

        DataSecurityLevel(int number, String code, String labelZh) {
            this.number = number;
            this.code = code;
            this.labelZh = labelZh;
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

            // Accept Chinese labels
            if ("公开".equals(token)) return PUBLIC;
            if ("内部".equals(token)) return INTERNAL;
            if ("秘密".equals(token)) return SECRET;
            if ("机密".equals(token)) return CONFIDENTIAL;

            String upper = token.toUpperCase(Locale.ROOT);
            if (upper.startsWith("DATA_")) upper = upper.substring("DATA_".length());
            upper = upper.replace('-', '_').replace(' ', '_');

            // Compatibility aliases
            if ("TOP_SECRET".equals(upper) || "DATA_TOP_SECRET".equals(upper)) return CONFIDENTIAL;
            if ("NON_SECRET".equals(upper) || "NONE_SECRET".equals(upper)) return PUBLIC;

            return switch (upper) {
                case "PUBLIC" -> PUBLIC;
                case "INTERNAL", "GENERAL" -> INTERNAL;
                case "SECRET", "IMPORTANT" -> SECRET;
                case "CONFIDENTIAL", "CORE" -> CONFIDENTIAL;
                default -> null;
            };
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

    /**
     * Parse a maximum data level from either:
     * - data level tokens (PUBLIC/INTERNAL/SECRET/CONFIDENTIAL; 0/1/2/3; Chinese labels)
     * - personnel level tokens (GENERAL/IMPORTANT/CORE; 0/1/2; Chinese labels)
     */
    public static DataSecurityLevel parseMaxDataLevel(Object raw) {
        DataSecurityLevel data = DataSecurityLevel.parse(raw);
        if (data != null) return data;
        PersonnelSecurityLevel personnel = PersonnelSecurityLevel.parse(raw);
        if (personnel != null) return maxDataLevelForPersonnel(personnel);
        return null;
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
}

