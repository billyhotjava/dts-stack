package com.yuzhi.dts.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditPayloadSanitizerTest {

    @Test
    @SuppressWarnings("unchecked")
    void redactsSensitiveKeysAndSensitiveValuesRecursively() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelCode", "dim_account");
        payload.put(
            "connection",
            Map.of(
                "password",
                "database-password",
                "headers",
                List.of(
                    Map.of("Authorization", "Bearer header-token"),
                    "access_token=inline-token",
                    "Bearer list-token",
                    "safe-value"
                )
            )
        );

        Map<String, Object> sanitized = AuditPayloadSanitizer.sanitize(payload);

        assertThat(sanitized).containsEntry("modelCode", "dim_account");
        Map<String, Object> connection = (Map<String, Object>) sanitized.get("connection");
        assertThat(connection).doesNotContainKey("password");
        assertThat(connection.values()).contains("[REDACTED]");
        List<Object> headers = (List<Object>) connection.get("headers");
        Map<String, Object> authorization = (Map<String, Object>) headers.get(0);
        assertThat(authorization).doesNotContainKey("Authorization");
        assertThat(authorization.values()).containsExactly("[REDACTED]");
        assertThat(headers.subList(1, 4)).containsExactly("[REDACTED]", "[REDACTED]", "safe-value");
    }

    @Test
    void replacesUnsafeKeysWithoutCollisionsAndRedactsOpaqueTokens() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("password=key-secret", "value-secret");
        payload.put("pwd", "short-secret");
        payload.put("中文密码", "中文秘密");
        payload.put("control\nkey", "control-value");
        payload.put("x".repeat(129), "oversized-key-value");
        payload.put("sample", "retry after sk_live_1234567890abcdef failed");

        Map<String, Object> first = AuditPayloadSanitizer.sanitize(payload);
        Map<String, Object> second = AuditPayloadSanitizer.sanitize(payload);

        assertThat(first).isEqualTo(second).hasSize(6);
        assertThat(first.keySet())
            .noneMatch(key -> key.contains("secret") || key.contains("pwd") || key.contains("密码") || key.contains("\n"))
            .filteredOn(key -> key.startsWith("redacted_key_"))
            .hasSize(5)
            .doesNotHaveDuplicates();
        assertThat(first.values()).contains("[REDACTED]");
        assertThat(first).containsEntry("sample", "[REDACTED]");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void preservesDistinctEntriesWhenMapKeysStringifyToTheSameValue() {
        Map<Object, Object> payload = new LinkedHashMap<>();
        payload.put(7, "numeric-key");
        payload.put("7", "string-key");

        Map<String, Object> sanitized = AuditPayloadSanitizer.sanitize((Map) payload);

        assertThat(sanitized).hasSize(2).containsEntry("7", "numeric-key");
        assertThat(sanitized.values()).containsExactlyInAnyOrder("numeric-key", "string-key");
        assertThat(sanitized.keySet()).doesNotHaveDuplicates();
    }

    @Test
    @SuppressWarnings("unchecked")
    void boundsStringsCollectionsDepthAndCycles() {
        List<Object> oversized = new ArrayList<>();
        for (int index = 0; index < 105; index++) {
            oversized.add(index);
        }
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("longText", "x".repeat(2_100));
        payload.put("oversized", oversized);
        payload.put("deep", Map.of("a", Map.of("b", Map.of("c", Map.of("d", Map.of("e", Map.of("f", "hidden")))))));
        payload.put("cyclic", cyclic);

        Map<String, Object> sanitized = AuditPayloadSanitizer.sanitize(payload);

        assertThat((String) sanitized.get("longText"))
            .hasSize(2_059)
            .endsWith("[TRUNCATED]");
        List<Object> boundedList = (List<Object>) sanitized.get("oversized");
        assertThat(boundedList).hasSize(101);
        assertThat(boundedList.getLast()).isEqualTo("[TRUNCATED]");
        assertThat(String.valueOf(sanitized.get("deep"))).contains("[TRUNCATED]").doesNotContain("hidden");
        assertThat(String.valueOf(sanitized.get("cyclic"))).contains("[CYCLE]");
    }
}
