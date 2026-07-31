package com.yuzhi.dts.platform.service.audit;

import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounds and redacts audit evidence before it is hashed or persisted. */
final class AuditPayloadSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final String TRUNCATED = "[TRUNCATED]";
    private static final String CYCLE = "[CYCLE]";
    private static final int MAX_DEPTH = 6;
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_NODES = 1_000;
    private static final int MAX_STRING_LENGTH = 2048;
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
        "password",
        "passwd",
        "secret",
        "token",
        "authorization",
        "cookie",
        "credential",
        "privatekey",
        "accesskey",
        "apikey"
    );

    private AuditPayloadSanitizer() {}

    static Map<String, Object> sanitize(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        Object sanitized = sanitizeValue(body, 0, new IdentityHashMap<>(), new Budget(MAX_NODES));
        if (sanitized instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        throw new IllegalArgumentException("audit body must be an object");
    }

    private static Object sanitizeValue(Object value, int depth, IdentityHashMap<Object, Boolean> seen, Budget budget) {
        if (!budget.consume()) {
            return TRUNCATED;
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            return bounded(String.valueOf(value));
        }
        if (depth >= MAX_DEPTH) {
            return TRUNCATED;
        }
        if (value instanceof Map<?, ?> map) {
            if (seen.put(value, Boolean.TRUE) != null) {
                return CYCLE;
            }
            try {
                Map<String, Object> result = new LinkedHashMap<>();
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (count++ >= MAX_ENTRIES) {
                        result.put("_auditTruncated", true);
                        break;
                    }
                    String key = bounded(String.valueOf(entry.getKey()));
                    result.put(
                        key,
                        isSensitiveKey(key) ? REDACTED : sanitizeValue(entry.getValue(), depth + 1, seen, budget)
                    );
                }
                return result;
            } finally {
                seen.remove(value);
            }
        }
        if (value instanceof Collection<?> collection) {
            if (seen.put(value, Boolean.TRUE) != null) {
                return CYCLE;
            }
            try {
                List<Object> result = new ArrayList<>(Math.min(collection.size(), MAX_ENTRIES));
                int count = 0;
                for (Object item : collection) {
                    if (count++ >= MAX_ENTRIES) {
                        result.add(TRUNCATED);
                        break;
                    }
                    result.add(sanitizeValue(item, depth + 1, seen, budget));
                }
                return result;
            } finally {
                seen.remove(value);
            }
        }
        if (value.getClass().isArray()) {
            int length = Math.min(Array.getLength(value), MAX_ENTRIES);
            List<Object> result = new ArrayList<>(length + 1);
            for (int index = 0; index < length; index++) {
                result.add(sanitizeValue(Array.get(value, index), depth + 1, seen, budget));
            }
            if (Array.getLength(value) > MAX_ENTRIES) {
                result.add(TRUNCATED);
            }
            return result;
        }
        return bounded(String.valueOf(value));
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private static String bounded(String value) {
        if (value == null || value.length() <= MAX_STRING_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_STRING_LENGTH) + TRUNCATED;
    }

    private static final class Budget {

        private int remaining;

        private Budget(int remaining) {
            this.remaining = remaining;
        }

        private boolean consume() {
            return remaining-- > 0;
        }
    }
}
