package com.yuzhi.dts.platform.service.governance;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SQL 模板渲染引擎：将包含 {{param}} 占位符的 SQL 模板渲染为可执行 SQL，带安全校验。
 *
 * 占位符格式：{{paramName}} 或 {{paramName:modifier}}
 */
@Component
public class SqlTemplateRenderer {

    // 标识符白名单正则（表名、列名只允许字母数字下划线点）
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    // 模板占位符正则
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)(?::(\\w+))?\\}\\}");

    /**
     * 渲染 SQL 模板
     * @param sqlTemplate SQL 模板字符串
     * @param params      用户填写的参数
     * @param paramSchema 参数 schema 定义（List of Map，每个 Map 包含 name/type/required）
     * @return 渲染后的 SQL
     * @throws IllegalArgumentException 参数校验失败
     */
    public String render(String sqlTemplate, Map<String, Object> params,
                         List<Map<String, Object>> paramSchema) {
        if (!StringUtils.hasText(sqlTemplate)) {
            throw new IllegalArgumentException("SQL 模板不能为空");
        }
        Map<String, Object> safeParams = params != null ? params : Collections.emptyMap();
        List<Map<String, Object>> schema = paramSchema != null ? paramSchema : Collections.emptyList();

        // 1. 校验必填参数
        for (Map<String, Object> def : schema) {
            String name = String.valueOf(def.get("name"));
            boolean required = Boolean.TRUE.equals(def.get("required"));
            if (required && !StringUtils.hasText(toString(safeParams.get(name)))) {
                String label = toString(def.getOrDefault("label", name));
                throw new IllegalArgumentException("参数 [" + label + "] 不能为空");
            }
        }

        // 2. 按类型校验并构建类型映射
        Map<String, String> typeMap = new HashMap<>();
        for (Map<String, Object> def : schema) {
            typeMap.put(String.valueOf(def.get("name")), toString(def.getOrDefault("type", "text")));
        }

        // 3. 替换占位符
        Matcher matcher = PLACEHOLDER.matcher(sqlTemplate);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String modifier = matcher.group(2); // 可能为 null
            String rawValue = toString(safeParams.get(paramName));
            String paramType = typeMap.getOrDefault(paramName, "text");
            String replacement = renderParam(paramName, rawValue, paramType, modifier);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String renderParam(String name, String value, String type, String modifier) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return switch (type) {
            case "table_select" -> {
                validateIdentifier(name, value);
                yield value;
            }
            case "column_select" -> {
                validateIdentifier(name, value);
                yield value;
            }
            case "number" -> {
                validateNumber(name, value);
                yield value;
            }
            case "text_array", "csv" -> renderCsv(value);
            case "sql_editor" -> value; // 自定义 SQL 直接透传
            default -> {
                // text 类型：如果 modifier 是 csv，做 CSV 渲染
                if ("csv".equals(modifier)) {
                    yield renderCsv(value);
                }
                // 普通文本直接替换（用于正则、描述等）
                yield value;
            }
        };
    }

    // 标识符校验（表名/列名）
    private void validateIdentifier(String paramName, String value) {
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "参数 [" + paramName + "] 包含非法字符: " + value + "（只允许字母、数字、下划线和点）");
        }
    }

    // 数值校验
    private void validateNumber(String paramName, String value) {
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 [" + paramName + "] 必须是数字: " + value);
        }
    }

    // CSV 渲染：逗号分隔的值列表 → 'val1','val2','val3'
    private String renderCsv(String value) {
        String[] parts = value.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("'").append(escapeSql(parts[i].trim())).append("'");
        }
        return sb.toString();
    }

    // SQL 字符串转义（防注入）
    private String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
