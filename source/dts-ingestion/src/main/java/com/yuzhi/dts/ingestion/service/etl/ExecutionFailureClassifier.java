package com.yuzhi.dts.ingestion.service.etl;

import java.util.Locale;
import org.springframework.util.StringUtils;

public final class ExecutionFailureClassifier {

    public static final String CATEGORY_CONNECTION = "CONNECTION_ERROR";
    public static final String CATEGORY_PERMISSION = "PERMISSION_ERROR";
    public static final String CATEGORY_DDL = "DDL_ERROR";
    public static final String CATEGORY_DML = "DML_ERROR";
    public static final String CATEGORY_DATA_QUALITY = "DATA_QUALITY_ERROR";
    public static final String CATEGORY_GOVERNANCE_LIMIT = "GOVERNANCE_LIMIT";
    public static final String CATEGORY_GOVERNANCE_QUEUE_TIMEOUT = "GOVERNANCE_QUEUE_TIMEOUT";
    public static final String CATEGORY_RUNTIME = "RUNTIME_ERROR";

    private ExecutionFailureClassifier() {}

    public static String classify(String errorMessage) {
        String text = normalize(errorMessage);
        if (!StringUtils.hasText(text)) {
            return CATEGORY_RUNTIME;
        }

        String apiRuntimeCategory = classifyApiRuntime(text);
        if (apiRuntimeCategory != null) {
            return apiRuntimeCategory;
        }

        // Governance rules (highest priority — user-actionable)
        if (containsAny(text, "治理队列等待超时", "governance queue wait timeout")) {
            return CATEGORY_GOVERNANCE_QUEUE_TIMEOUT;
        }
        if (containsAny(text, "并发已达上限", "不在允许执行窗口内", "任务仍在运行中",
            "governance rejected", "concurrency limit reached", "execution window")) {
            return CATEGORY_GOVERNANCE_LIMIT;
        }

        // Airflow/runtime errors (before connection checks — "connection refused" in Airflow logs
        // should not be classified as CONNECTION if it's clearly an Airflow DAG issue)
        if (containsAny(text, "airflowtimetableinvalid", "dag 导入失败", "dag import failed",
            "airflow_dag_not_ready_timeout", "iterator expression", "dag 未就绪",
            "airflow 熔断中", "airflow 触发失败")) {
            return CATEGORY_RUNTIME;
        }

        // Network/connection errors
        if (containsAny(text, "connection refused", "connect timed out", "unknown host",
            "no route to host", "connection reset", "i/o error", "broken pipe",
            "ehostunreach", "econnrefused", "econnaborted")) {
            return CATEGORY_CONNECTION;
        }

        // Permission errors
        if (containsAny(text, "access denied", "permission denied", "not authorized",
            "authentication failed", "login failed", "password authentication failed")) {
            return CATEGORY_PERMISSION;
        }

        // DDL errors (check before DML — "does not exist" is a DDL issue, not DML)
        if (containsAny(text, "syntax error", "create table", "alter table", "drop table",
            "relation \"", "does not exist", "already exists", "cannot drop")) {
            return CATEGORY_DDL;
        }

        // DML errors
        if (containsAny(text, "duplicate key", "violates unique", "violates foreign key",
            "violates check constraint", "violates not-null", "on conflict",
            "deadlock detected", "lock timeout")) {
            return CATEGORY_DML;
        }

        // Data quality errors (narrowed keywords to avoid false positives)
        if (containsAny(text, "type mismatch", "data truncation", "null value in column",
            "invalid input syntax", "numeric value out of range",
            "date/time field value out of range", "invalid date format", "character varying")) {
            return CATEGORY_DATA_QUALITY;
        }

        return CATEGORY_RUNTIME;
    }

    public static String advice(String category) {
        if (!StringUtils.hasText(category)) {
            return "请查看任务日志并重试。";
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case CATEGORY_CONNECTION -> "请检查网络连通性、JDBC 地址与端口是否可达。";
            case CATEGORY_PERMISSION -> "请检查数据源账号权限与平台授权配置。";
            case CATEGORY_DDL -> "请检查目标库 schema、表名与建表/删表语句是否正确。";
            case CATEGORY_DML -> "请检查主键冲突、约束定义与写入模式配置。";
            case CATEGORY_DATA_QUALITY -> "请检查源数据字段类型、空值与格式是否符合目标表定义。";
            case CATEGORY_GOVERNANCE_LIMIT -> "触发治理策略限制，请调整并发上限、执行窗口或降低调度频率。";
            case CATEGORY_GOVERNANCE_QUEUE_TIMEOUT -> "治理队列等待超时，请扩容执行资源或提升并发配额。";
            default -> "请查看节点日志定位异常并重试，必要时执行整批重跑。";
        };
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String classifyApiRuntime(String text) {
        if (!containsAny(text, "api_runtime_")) {
            return null;
        }
        if (containsAny(text, "api_runtime_auth")) {
            return CATEGORY_PERMISSION;
        }
        if (containsAny(text, "api_runtime_rate_limit", "api_runtime_network", "api_runtime_server")) {
            return CATEGORY_CONNECTION;
        }
        if (containsAny(text, "api_runtime_blocked_url", "api_runtime_config", "api_runtime_redirect", "api_runtime_pagination")) {
            return CATEGORY_GOVERNANCE_LIMIT;
        }
        if (containsAny(text, "api_runtime_schema", "api_runtime_response_too_large", "api_runtime_response_parse",
            "api_runtime_cursor", "api_runtime_client")) {
            return CATEGORY_DATA_QUALITY;
        }
        return CATEGORY_RUNTIME;
    }

    private static boolean containsAny(String text, String... markers) {
        if (!StringUtils.hasText(text) || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (StringUtils.hasText(marker) && text.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
