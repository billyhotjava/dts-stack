package com.yuzhi.dts.platform.service.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Strict, canonical rollback command shared by analyze, confirmation, and execute. */
public record RollbackCommand(
    int level,
    String scope,
    Long taskId,
    UUID dataSourceId,
    List<String> tables,
    boolean dryRun
) {

    private static final int MAX_TABLES = 500;
    private static final Set<String> COMMAND_FIELDS = Set.of(
        "level",
        "scope",
        "taskId",
        "dataSourceId",
        "tables",
        "dryRun"
    );
    private static final Set<String> EXECUTE_METADATA_FIELDS = Set.of(
        "confirmationToken",
        "confirmationType",
        "confirmationText",
        "confirmationExpiresAt"
    );

    public static RollbackCommand executionPlan(Map<String, Object> request, boolean executeRequest) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        rejectClientSideEffectOverrides(safe);
        Set<String> allowed = new java.util.HashSet<>(COMMAND_FIELDS);
        if (executeRequest) {
            allowed.addAll(EXECUTE_METADATA_FIELDS);
        }
        for (String field : safe.keySet()) {
            if (!allowed.contains(field)) {
                throw badRequest("不支持的回退请求字段: " + field);
            }
        }

        int level = requireInteger(safe.get("level"), "level");
        if (level < 1 || level > 3) {
            throw badRequest("回退级别仅支持 1、2、3");
        }
        String scope = requireText(safe.get("scope"), "scope").toLowerCase(Locale.ROOT);
        if (!"task".equals(scope) && !"datasource".equals(scope)) {
            throw badRequest("回退范围仅支持 task 或 datasource");
        }

        Long taskId = null;
        UUID dataSourceId = null;
        if ("task".equals(scope)) {
            taskId = requirePositiveLong(safe.get("taskId"), "taskId");
            if (safe.containsKey("dataSourceId")) {
                throw badRequest("task 范围不能同时指定 dataSourceId");
            }
        } else {
            dataSourceId = requireUuid(safe.get("dataSourceId"), "dataSourceId");
            if (safe.containsKey("taskId")) {
                throw badRequest("datasource 范围不能同时指定 taskId");
            }
        }

        List<String> tables = normalizeTables(safe.get("tables"));
        if (level > 1 && !tables.isEmpty()) {
            throw badRequest("tables 子集仅支持一级回退；二、三级回退会作用于任务或数据源的全部表");
        }
        boolean requestedDryRun = requireBoolean(safe, "dryRun", false);
        if (executeRequest && requestedDryRun) {
            throw badRequest("execute 不接受 dryRun=true，请重新执行影响分析");
        }
        return new RollbackCommand(level, scope, taskId, dataSourceId, tables, false);
    }

    public RollbackCommand asAnalysisCommand() {
        return new RollbackCommand(level, scope, taskId, dataSourceId, tables, true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("level", level);
        command.put("scope", scope);
        if (taskId != null) {
            command.put("taskId", taskId);
        }
        if (dataSourceId != null) {
            command.put("dataSourceId", dataSourceId.toString());
        }
        if (!tables.isEmpty()) {
            command.put("tables", tables);
        }
        command.put("dryRun", dryRun);
        return Map.copyOf(command);
    }

    public String canonicalFingerprintSource() {
        StringBuilder canonical = new StringBuilder("rollback-command-v2;");
        appendFingerprintValue(canonical, "level", String.valueOf(level));
        appendFingerprintValue(canonical, "scope", scope);
        appendFingerprintValue(canonical, "taskId", taskId == null ? "" : taskId.toString());
        appendFingerprintValue(
            canonical,
            "dataSourceId",
            dataSourceId == null ? "" : dataSourceId.toString()
        );
        appendFingerprintValue(canonical, "tableCount", String.valueOf(tables.size()));
        tables.forEach(table -> appendFingerprintValue(canonical, "table", table));
        appendFingerprintValue(canonical, "dryRun", String.valueOf(dryRun));
        return canonical.toString();
    }

    private static void appendFingerprintValue(StringBuilder canonical, String name, String value) {
        String safeValue = value == null ? "" : value;
        canonical
            .append(name)
            .append('=')
            .append(safeValue.length())
            .append(':')
            .append(safeValue)
            .append(';');
    }

    private static List<String> normalizeTables(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof Iterable<?> values)) {
            throw badRequest("tables 必须是字符串数组");
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (Object item : values) {
            if (!(item instanceof String table) || !StringUtils.hasText(table)) {
                throw badRequest("tables 只能包含非空字符串");
            }
            normalized.add(table.trim());
            if (normalized.size() > MAX_TABLES) {
                throw badRequest("单次回退最多允许 " + MAX_TABLES + " 张表");
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static int requireInteger(Object value, String field) {
        long parsed = requireIntegralNumber(value, field);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw badRequest(field + " 超出允许范围");
        }
        return (int) parsed;
    }

    private static Long requirePositiveLong(Object value, String field) {
        long parsed = requireIntegralNumber(value, field);
        if (parsed <= 0) {
            throw badRequest(field + " 必须是正整数");
        }
        return parsed;
    }

    private static long requireIntegralNumber(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw badRequest(field + " 必须是整数");
        }
        double doubleValue = number.doubleValue();
        long longValue = number.longValue();
        if (!Double.isFinite(doubleValue) || doubleValue != (double) longValue) {
            throw badRequest(field + " 必须是整数");
        }
        return longValue;
    }

    private static boolean requireBoolean(Map<String, Object> request, String field, boolean defaultValue) {
        if (!request.containsKey(field)) {
            return defaultValue;
        }
        Object value = request.get(field);
        if (!(value instanceof Boolean bool)) {
            throw badRequest(field + " 必须是 boolean");
        }
        return bool;
    }

    private static String requireText(Object value, String field) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            throw badRequest(field + " 必须是非空字符串");
        }
        return text.trim();
    }

    private static UUID requireUuid(Object value, String field) {
        String text = requireText(value, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw badRequest(field + " 必须是 UUID");
        }
    }

    private static void rejectClientSideEffectOverrides(Map<String, Object> request) {
        if (request.containsKey("sourceDataSourceId") || request.containsKey("models")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "回退副作用范围不可由客户端指定，请重新分析影响范围");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
