package com.yuzhi.dts.platform.service.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Simple masking helpers used by default access policies.
 * The goal is to provide deterministic, lightweight masking without relying on external engines.
 */
public final class MaskingFunctions {

    private MaskingFunctions() {}

    public static Object apply(Object value, String strategy) {
        if (value == null) {
            return null;
        }
        if (!StringUtils.hasText(strategy)) {
            return value;
        }
        String mode = strategy.trim().toUpperCase(Locale.ROOT);
        if ("NONE".equals(mode)) {
            return value;
        }
        String text = stringify(value);
        return switch (mode) {
            case "PARTIAL" -> maskPartial(text);
            case "HASH" -> hash(text);
            case "TOKENIZE" -> tokenize(text);
            case "CUSTOM" -> hash(text); // treat custom as hash until extended rules are provided
            default -> value;
        };
    }

    private static String stringify(Object value) {
        if (value instanceof CharSequence seq) {
            return seq.toString();
        }
        if (value instanceof Number || value instanceof java.util.Date) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }

    private static String maskPartial(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        String trimmed = input.trim();
        if (trimmed.length() <= 2) {
            return "*".repeat(trimmed.length());
        }
        if (trimmed.contains("@")) {
            return trimmed.replaceAll("(^.).*(@.*$)", "$1***$2");
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() >= 7) {
            return trimmed.replaceAll("(\\d{3})\\d*(\\d{4})", "$1****$2");
        }
        int keep = Math.max(1, trimmed.length() / 4);
        String left = trimmed.substring(0, keep);
        String right = trimmed.substring(trimmed.length() - keep);
        return left + "*".repeat(Math.max(trimmed.length() - keep * 2, 1)) + right;
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法初始化SHA-256摘要算法", e);
        }
    }

    private static String tokenize(String input) {
        UUID token = UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
        return "TK-" + token.toString().replace("-", "").substring(0, 16);
    }
}

