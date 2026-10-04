package com.yuzhi.dts.common.audit;

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
import java.util.regex.Pattern;

/**
 * Shared fail-safe policy for bounding and redacting audit evidence before hashing, transport,
 * or persistence.
 */
public final class AuditPayloadSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final String REDACTED_KEY_PREFIX = "redacted_key_";
    private static final String TRUNCATED = "[TRUNCATED]";
    private static final String CYCLE = "[CYCLE]";
    private static final int MAX_DEPTH = 6;
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_NODES = 1_000;
    private static final int MAX_STRING_LENGTH = 2_048;
    private static final int MAX_KEY_LENGTH = 128;
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
        "password",
        "passwd",
        "pwd",
        "passphrase",
        "secret",
        "token",
        "authorization",
        "cookie",
        "credential",
        "privatekey",
        "accesskey",
        "apikey"
    );
    private static final Set<String> SENSITIVE_UNICODE_KEY_PARTS = Set.of("密码", "口令", "密钥", "令牌");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
        "(?i)(?:^|[\\s?&;,{}])['\"]?(?:password|passwd|pwd|passphrase|密码|secret|token|authorization|cookie|credential|private[_-]?key|access[_-]?(?:key|token)|api[_-]?key)['\"]?\\s*[:=]\\s*['\"]?[^\\s,;}&]+"
    );
    private static final Pattern AUTHORIZATION_VALUE = Pattern.compile("(?i)^(?:bearer|basic|digest|apikey)\\s+\\S+");
    private static final Pattern CREDENTIAL_URI = Pattern.compile(
        "(?i)(?:[a-z][a-z0-9+.-]*:)+//[^\\s/:@]+:[^\\s@/]+@"
    );
    private static final Pattern PRIVATE_KEY_VALUE = Pattern.compile("-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----");
    private static final Pattern JWT_FRAGMENT = Pattern.compile(
        "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+(?![A-Za-z0-9_-])"
    );
    private static final Pattern OPAQUE_TOKEN_FRAGMENT = Pattern.compile(
        "(?i)(?<![A-Za-z0-9])(?:(?:sk|pk)_(?:live|test)_[A-Za-z0-9._-]{8,}|(?:sk|api|token|pat|gh[pousr]|glpat|xox[baprs])[-_][A-Za-z0-9._-]{12,}|AKIA[0-9A-Z]{16})(?![A-Za-z0-9])"
    );
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cntrl}]");

    private AuditPayloadSanitizer() {}

    public static Map<String, Object> sanitize(Map<String, ?> body) {
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
            String text = String.valueOf(value);
            return isSensitiveValue(text) ? REDACTED : bounded(text);
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
                    String rawKey = String.valueOf(entry.getKey());
                    boolean sensitiveKey = isSensitiveKey(rawKey) || isSensitiveValue(rawKey);
                    String key = sanitizeMapKey(rawKey, sensitiveKey, result);
                    result.put(
                        key,
                        sensitiveKey ? REDACTED : sanitizeValue(entry.getValue(), depth + 1, seen, budget)
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
                List<Object> result = new ArrayList<>(Math.min(collection.size(), MAX_ENTRIES) + 1);
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
            if (seen.put(value, Boolean.TRUE) != null) {
                return CYCLE;
            }
            try {
                int sourceLength = Array.getLength(value);
                int length = Math.min(sourceLength, MAX_ENTRIES);
                List<Object> result = new ArrayList<>(length + 1);
                for (int index = 0; index < length; index++) {
                    result.add(sanitizeValue(Array.get(value, index), depth + 1, seen, budget));
                }
                if (sourceLength > MAX_ENTRIES) {
                    result.add(TRUNCATED);
                }
                return result;
            } finally {
                seen.remove(value);
            }
        }
        String text = String.valueOf(value);
        return isSensitiveValue(text) ? REDACTED : bounded(text);
    }

    private static boolean isSensitiveKey(String key) {
        String lowered = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_UNICODE_KEY_PARTS.stream().anyMatch(lowered::contains)) {
            return true;
        }
        String normalized = lowered.replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private static String sanitizeMapKey(String rawKey, boolean sensitive, Map<String, Object> existing) {
        boolean suspicious = sensitive ||
            rawKey.isBlank() ||
            rawKey.length() > MAX_KEY_LENGTH ||
            CONTROL_CHARACTER.matcher(rawKey).find() ||
            rawKey.startsWith(REDACTED_KEY_PREFIX);
        String candidate = suspicious ? nextRedactedKey(existing) : rawKey;
        if (!existing.containsKey(candidate)) {
            return candidate;
        }
        return nextRedactedKey(existing);
    }

    private static String nextRedactedKey(Map<String, Object> existing) {
        int sequence = existing.size() + 1;
        String candidate;
        do {
            candidate = REDACTED_KEY_PREFIX + sequence++;
        } while (existing.containsKey(candidate));
        return candidate;
    }

    private static boolean isSensitiveValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return (
            SENSITIVE_ASSIGNMENT.matcher(trimmed).find() ||
            AUTHORIZATION_VALUE.matcher(trimmed).find() ||
            CREDENTIAL_URI.matcher(trimmed).find() ||
            PRIVATE_KEY_VALUE.matcher(trimmed).find() ||
            JWT_FRAGMENT.matcher(trimmed).find() ||
            OPAQUE_TOKEN_FRAGMENT.matcher(trimmed).find()
        );
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
