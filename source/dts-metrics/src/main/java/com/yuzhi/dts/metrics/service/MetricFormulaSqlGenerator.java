package com.yuzhi.dts.metrics.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MetricFormulaSqlGenerator {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final Set<String> AGGREGATIONS = Set.of("sum", "count", "count_distinct", "avg", "max", "min");
    private static final Set<String> OPERATORS = Set.of("=", "!=", "<>", ">", ">=", "<", "<=", "in", "not_in", "is_null", "is_not_null");

    public String render(Object formula) {
        if (!(formula instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Formula must be an object");
        }
        Map<String, Object> map = castMap(raw);
        String type = normalized(required(map, "type"));
        return switch (type) {
            case "aggregation" -> aggregation(map);
            case "conditional_count", "count_if" -> conditionalCount(map);
            case "conditional_sum", "sum_if" -> conditionalSum(map);
            case "ratio" -> ratio(map);
            case "date_trunc" -> dateTrunc(map);
            case "case_when" -> caseWhen(map);
            default -> throw new IllegalArgumentException("Unsupported formula type: " + type);
        };
    }

    private String aggregation(Map<String, Object> map) {
        String aggregation = normalized(required(map, "aggregation"));
        if (!AGGREGATIONS.contains(aggregation)) {
            throw new IllegalArgumentException("Unsupported aggregation: " + aggregation);
        }
        if ("count".equals(aggregation)) {
            String field = optionalIdentifier(map, "field");
            return StringUtils.hasText(field) ? "count(" + field + ")" : "count(*)";
        }
        String field = safeIdentifier(required(map, "field"));
        if ("count_distinct".equals(aggregation)) {
            return "count(distinct " + field + ")";
        }
        if ("sum".equals(aggregation)) {
            return "sum(coalesce(" + field + ", 0))";
        }
        return aggregation + "(" + field + ")";
    }

    private String conditionalCount(Map<String, Object> map) {
        String condition = condition(requiredMap(map, "condition"));
        boolean distinct = Boolean.TRUE.equals(map.get("distinct"));
        String field = optionalIdentifier(map, "field");
        if (distinct || StringUtils.hasText(field)) {
            String target = StringUtils.hasText(field) ? field : "1";
            return "count(" + (distinct ? "distinct " : "") + "case when " + condition + " then " + target + " end)";
        }
        return "sum(case when " + condition + " then 1 else 0 end)";
    }

    private String conditionalSum(Map<String, Object> map) {
        String field = safeIdentifier(required(map, "field"));
        String condition = condition(requiredMap(map, "condition"));
        return "sum(case when " + condition + " then coalesce(" + field + ", 0) else 0 end)";
    }

    private String ratio(Map<String, Object> map) {
        String numerator = render(map.get("numerator"));
        String denominator = render(map.get("denominator"));
        String multiply = multiply(map.get("multiply"));
        return "case when (" + denominator + ") = 0 then null else (" + numerator + ") / (" + denominator + ")" + multiply + " end";
    }

    private String dateTrunc(Map<String, Object> map) {
        String grain = normalized(required(map, "grain"));
        if (!Set.of("day", "week", "month", "quarter", "year").contains(grain)) {
            throw new IllegalArgumentException("Unsupported date_trunc grain: " + grain);
        }
        return "date_trunc('" + grain + "', " + safeIdentifier(required(map, "field")) + ")";
    }

    private String caseWhen(Map<String, Object> map) {
        Object casesRaw = map.get("cases");
        if (!(casesRaw instanceof List<?> cases) || cases.isEmpty()) {
            throw new IllegalArgumentException("case_when.cases must be a non-empty list");
        }
        StringBuilder sql = new StringBuilder("case");
        for (Object item : cases) {
            Map<String, Object> row = asMap(item, "case_when case");
            sql.append(" when ").append(condition(requiredMap(row, "when"))).append(" then ").append(literal(row.get("then")));
        }
        sql.append(" else ").append(literal(map.get("else"))).append(" end");
        return sql.toString();
    }

    private String condition(Map<String, Object> map) {
        String field = safeIdentifier(required(map, "field"));
        String operator = normalized(required(map, "operator"));
        if (!OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Unsupported condition operator: " + operator);
        }
        return switch (operator) {
            case "is_null" -> field + " is null";
            case "is_not_null" -> field + " is not null";
            case "in", "not_in" -> field + ("in".equals(operator) ? " in " : " not in ") + listLiteral(map.get("value"));
            default -> field + " " + operator + " " + literal(map.get("value"));
        };
    }

    private static String listLiteral(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Condition value must be a non-empty list for in/not_in");
        }
        return "(" + list.stream().map(MetricFormulaSqlGenerator::literal).reduce((a, b) -> a + ", " + b).orElse("") + ")";
    }

    private static String literal(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value).replace("'", "''");
        return "'" + text + "'";
    }

    private static String multiply(Object value) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("ratio.multiply must be numeric");
        }
        BigDecimal decimal = new BigDecimal(String.valueOf(number));
        if (BigDecimal.ONE.compareTo(decimal) == 0) {
            return "";
        }
        return " * " + decimal.stripTrailingZeros().toPlainString();
    }

    private static String optionalIdentifier(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return "";
        }
        return safeIdentifier(String.valueOf(value));
    }

    static String safeIdentifier(String value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (!IDENTIFIER.matcher(text).matches()) {
            throw new IllegalArgumentException("Unsafe identifier: " + value);
        }
        return text;
    }

    private static String required(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(value).trim();
    }

    private static Map<String, Object> requiredMap(Map<String, Object> map, String key) {
        return asMap(map.get(key), key);
    }

    private static Map<String, Object> asMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return castMap(raw);
    }

    private static Map<String, Object> castMap(Map<?, ?> raw) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        raw.forEach((key, value) -> map.put(String.valueOf(key), value));
        return map;
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
