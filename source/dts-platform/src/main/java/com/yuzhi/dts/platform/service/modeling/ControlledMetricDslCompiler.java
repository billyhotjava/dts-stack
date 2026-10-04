package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 受控派生指标 DSL 编译器（Sprint-41 / F2，整合自 dts-metrics 的受控建模逻辑）。
 *
 * <p>把无类型/permissive 的指标公式收敛为"受控模式"：只接受函数白名单、默认拒绝、拒绝原始 SQL、
 * 对标识符做方言感知 quote、对字面量做注入防御。它是一个独立、无状态、可单测、可被未来抽取的组件
 * （仅依赖 Jackson 解析 formula JSON），不耦合任何已退役的语义建模实现。
 *
 * <p>设计取向：<b>安全/语义一致</b>而非字节复刻 dts-metrics——dts-metrics 吃字符串表达式、平台吃 JSON formula，
 * 输出形态不同；本编译器把 dts-metrics 的安全纪律施加到平台 formula 模型上。
 */
@Component
public class ControlledMetricDslCompiler {

    /** SQL 方言：标识符引号差异（postgres 双引号 / doris 反引号）+ date_trunc 参数顺序。 */
    public enum SqlDialect {
        POSTGRES,
        DORIS,
    }

    private static final Pattern UNSAFE = Pattern.compile(
        "(?i)(;|--|/\\*|\\*/|\\bselect\\b|\\binsert\\b|\\bupdate\\b|\\bdelete\\b|\\bdrop\\b|\\balter\\b|\\btruncate\\b|\\bmerge\\b|\\bunion\\b|\\bgrant\\b|\\brevoke\\b|\\bexec\\b)"
    );
    private static final Pattern IDENTIFIER_SEGMENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Set<String> GRAINS = Set.of("day", "week", "month", "quarter", "year");

    private final ObjectMapper objectMapper;

    public ControlledMetricDslCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 编译受控派生指标为 SQL 片段。非白名单类型 / 原始 SQL / 注入串 / 非法标识符均抛
     * {@link IllegalArgumentException}（调用方映射 422 {@code unsafe_expression}）。
     */
    public String compile(String formulaType, String formulaJson, SqlDialect dialect) {
        String type = normalizeType(formulaType);
        JsonNode formula = parse(formulaJson);
        return switch (type) {
            case "sum", "count", "count_distinct", "avg", "min", "max" -> simpleAggregation(type, formula, dialect);
            case "ratio" -> ratio(formula, dialect);
            case "count_if", "conditional_count" -> countIf(formula, dialect);
            case "sum_if", "conditional_sum" -> sumIf(formula, dialect);
            case "date_trunc" -> dateTrunc(formula, dialect);
            case "case_when" -> caseWhen(formula, dialect);
            default -> throw reject("受控模式不支持的指标类型: " + formulaType + "（原始 SQL/自定义表达式被禁止）");
        };
    }

    // ---- function compilers ----

    private String simpleAggregation(String type, JsonNode formula, SqlDialect dialect) {
        if ("count".equals(type)) {
            String field = text(formula, "field");
            if (!StringUtils.hasText(field) || "*".equals(field.trim())) {
                return "count(*)";
            }
            return "count(" + quoteIdentifier(field, dialect) + ")";
        }
        String field = quoteIdentifier(requiredField(formula), dialect);
        return switch (type) {
            case "count_distinct" -> "count(distinct " + field + ")";
            case "avg" -> "avg(" + field + ")";
            case "min" -> "min(" + field + ")";
            case "max" -> "max(" + field + ")";
            default -> "sum(" + field + ")";
        };
    }

    private String aggregateNode(JsonNode node, SqlDialect dialect) {
        if (node == null || node.isNull()) {
            throw reject("ratio 缺少 numerator/denominator");
        }
        String type = normalizeType(firstText(node, "aggregation", "type"));
        if ("aggregation".equals(type) || !StringUtils.hasText(type)) {
            type = "sum";
        }
        return switch (type) {
            case "sum", "count", "count_distinct", "avg", "min", "max" -> simpleAggregation(type, node, dialect);
            default -> throw reject("ratio 分子/分母仅支持聚合函数，收到: " + type);
        };
    }

    private String ratio(JsonNode formula, SqlDialect dialect) {
        String numerator = aggregateNode(formula.get("numerator"), dialect);
        String denominator = aggregateNode(formula.get("denominator"), dialect);
        return "case when " + denominator + " = 0 then null else " + numerator + " / " + denominator + " end";
    }

    private String countIf(JsonNode formula, SqlDialect dialect) {
        String condition = condition(formula, dialect);
        boolean distinct = formula.path("distinct").asBoolean(false);
        if (!distinct) {
            return "sum(case when " + condition + " then 1 else 0 end)";
        }
        return "count(distinct case when " + condition + " then " + quoteIdentifier(requiredField(formula), dialect) + " end)";
    }

    private String sumIf(JsonNode formula, SqlDialect dialect) {
        String condition = condition(formula, dialect);
        return "sum(case when " + condition + " then " + quoteIdentifier(requiredField(formula), dialect) + " else 0 end)";
    }

    private String dateTrunc(JsonNode formula, SqlDialect dialect) {
        String grain = normalizeType(required(text(formula, "grain"), "date_trunc grain"));
        if (!GRAINS.contains(grain)) {
            throw reject("date_trunc 粒度仅支持 " + GRAINS + "，收到: " + grain);
        }
        String field = quoteIdentifier(requiredField(formula), dialect);
        return dialect == SqlDialect.DORIS
            ? "date_trunc(" + field + ", '" + grain + "')"
            : "date_trunc('" + grain + "', " + field + ")";
    }

    private String caseWhen(JsonNode formula, SqlDialect dialect) {
        String condition = condition(formula, dialect);
        String thenValue = literal(required(text(formula, "then"), "case_when then"));
        String elseValue = literal(required(text(formula, "else"), "case_when else"));
        return "case when " + condition + " then " + thenValue + " else " + elseValue + " end";
    }

    // ---- condition / identifier / literal ----

    private String condition(JsonNode formula, SqlDialect dialect) {
        JsonNode conditionNode = formula == null ? null : formula.get("condition");
        JsonNode source = conditionNode != null && conditionNode.isObject() ? conditionNode : formula;
        String field = quoteIdentifier(required(text(source, "field"), "condition field"), dialect);
        String operator = operatorSql(defaultText(text(source, "operator"), "="));
        String value = literal(required(text(source, "value"), "condition value"));
        return field + " " + operator + " " + value;
    }

    private String quoteIdentifier(String raw, SqlDialect dialect) {
        String value = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(value)) {
            throw reject("标识符为空");
        }
        String quote = dialect == SqlDialect.DORIS ? "`" : "\"";
        StringBuilder sb = new StringBuilder();
        String[] segments = value.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (!IDENTIFIER_SEGMENT.matcher(segment).matches()) {
                throw reject("非法标识符: " + raw);
            }
            if (i > 0) {
                sb.append('.');
            }
            sb.append(quote).append(segment).append(quote);
        }
        return sb.toString();
    }

    private String operatorSql(String op) {
        return switch (op.trim().toLowerCase(Locale.ROOT)) {
            case "=", "==", "eq" -> "=";
            case "!=", "<>", "ne" -> "<>";
            case ">", "gt" -> ">";
            case ">=", "gte" -> ">=";
            case "<", "lt" -> "<";
            case "<=", "lte" -> "<=";
            default -> throw reject("受控模式不支持的比较运算符: " + op);
        };
    }

    private String literal(String value) {
        String literal = value == null ? "" : value.trim();
        if (literal.matches("-?\\d+(\\.\\d+)?")) {
            return literal;
        }
        if (UNSAFE.matcher(literal).find()) {
            throw reject("字面量含危险内容: " + value);
        }
        return "'" + literal.replace("'", "''") + "'";
    }

    // ---- helpers ----

    private String requiredField(JsonNode formula) {
        return required(text(formula, "field"), "metric field");
    }

    private JsonNode parse(String formulaJson) {
        if (!StringUtils.hasText(formulaJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(formulaJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("指标公式 JSON 不合法: " + ex.getMessage());
        }
    }

    private static String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return type.startsWith("aggregation/") ? type.substring("aggregation/".length()) : type;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String required(String value, String what) {
        if (!StringUtils.hasText(value)) {
            throw reject("缺少必填项: " + what);
        }
        return value.trim();
    }

    private static IllegalArgumentException reject(String message) {
        return new IllegalArgumentException("unsafe_expression: " + message);
    }
}
