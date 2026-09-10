package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** One definition precedence and checksum for save, preview and execution. */
public final class QualityRuleStatements {
    private static final ObjectMapper JSON = new ObjectMapper();
    private QualityRuleStatements() {}

    public static Map<String, String> resolve(String definition) {
        try {
            return resolve(JSON.readValue(definition, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception error) {
            throw new IllegalArgumentException("质量规则定义不是有效的 JSON", error);
        }
    }

    public static Map<String, String> resolve(Map<String, Object> definition) {
        if (definition == null) throw new IllegalArgumentException("质量规则定义不能为空");
        Map<String, String> result = new LinkedHashMap<>();
        Object statements = definition.get("statements");
        if (statements != null) {
            if (!(statements instanceof Map<?, ?> values)) throw new IllegalArgumentException("statements 必须为语句对象");
            values.forEach((key, value) -> {
                if (!(key instanceof String name) || name.isBlank() || !(value instanceof String sql) || sql.isBlank()) {
                    throw new IllegalArgumentException("检测语句名称与 SQL 不能为空");
                }
                result.put(name, sql.trim());
            });
        }
        if (result.isEmpty() && definition.get("sql") instanceof String sql && !sql.isBlank()) result.put("sql", sql.trim());
        if (result.isEmpty()) throw new IllegalArgumentException("质量规则未配置可执行检测语句");
        if (result.size() > 32 || result.values().stream().anyMatch(sql -> sql.length() > 65536)) {
            throw new IllegalArgumentException("检测语句超出允许的数量或长度");
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public static String checksum(Map<String, String> statements) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(statements)));
        } catch (Exception error) { throw new IllegalStateException("质量定义校验和生成失败", error); }
    }
}
