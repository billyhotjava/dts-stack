package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsField;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MbqlToSqlService {

    private static final Set<String> UNSUPPORTED_KEYS = Set.of("limit-by");

    private final AnalyticsDatabaseRepository databaseRepository;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;

    public MbqlToSqlService(
            AnalyticsDatabaseRepository databaseRepository,
            AnalyticsTableRepository tableRepository,
            AnalyticsFieldRepository fieldRepository) {
        this.databaseRepository = databaseRepository;
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
    }

    @Transactional(readOnly = true)
    public TranslationResult translateSelect(long databaseId, JsonNode mbqlQuery,
            DatasetQueryService.DatasetConstraints constraints) {
        if (mbqlQuery == null || !mbqlQuery.isObject()) {
            throw new IllegalArgumentException("query must be a map.");
        }

        for (String unsupported : UNSUPPORTED_KEYS) {
            if (mbqlQuery.has(unsupported)) {
                throw new IllegalArgumentException("MBQL query key is not supported yet: " + unsupported);
            }
        }

        String engine = databaseRepository.findById(databaseId).map(AnalyticsDatabase::getEngine).orElse(null);
        char quote = quoteChar(engine);

        // Build query context with expressions, joins, and source info
        QueryContext ctx = buildQueryContext(databaseId, mbqlQuery, quote);

        List<String> breakoutColumns = parseBreakoutColumns(mbqlQuery.get("breakout"), ctx, quote);
        List<AggregationSpec> aggregations = parseAggregations(mbqlQuery.get("aggregation"), ctx, quote);
        boolean hasBreakout = !breakoutColumns.isEmpty();
        boolean hasAgg = !aggregations.isEmpty();

        List<String> selectParts = new ArrayList<>();
        if (hasBreakout) {
            selectParts.addAll(breakoutColumns);
        }
        if (hasAgg) {
            for (AggregationSpec agg : aggregations) {
                selectParts.add(agg.sqlWithAlias());
            }
        }
        // Add expression columns if selecting expressions
        if (selectParts.isEmpty()) {
            JsonNode fields = mbqlQuery.get("fields");
            if (fields != null && fields.isArray() && !fields.isEmpty()) {
                for (JsonNode node : fields) {
                    selectParts.add(parseFieldRef(node, ctx, quote));
                }
            }
        }

        String select = selectParts.isEmpty() ? "*" : String.join(", ", selectParts);
        String from = ctx.fromClause;
        SqlFragment where = renderWhere(mbqlQuery.get("filter"), ctx, quote);
        String groupBy = hasAgg && hasBreakout ? (" GROUP BY " + String.join(", ", breakoutColumns)) : "";
        String orderBy = renderOrderBy(mbqlQuery.get("order-by"), ctx, aggregations, quote);

        int requestedLimit = mbqlQuery.path("limit").canConvertToInt() ? mbqlQuery.path("limit").asInt() : 0;
        int limit = constraints != null ? constraints.maxResults()
                : DatasetQueryService.DatasetConstraints.defaults().maxResults();
        if (requestedLimit > 0) {
            limit = Math.min(limit, requestedLimit);
        }
        if (limit <= 0) {
            limit = 2000;
        }

        int offset = 0;
        JsonNode page = mbqlQuery.get("page");
        if (page != null && page.isObject()) {
            int items = page.path("items").canConvertToInt() ? page.path("items").asInt() : 0;
            int pageIndex = page.path("page").canConvertToInt() ? page.path("page").asInt() : 0;
            if (items > 0 && requestedLimit <= 0) {
                limit = Math.min(limit, items);
            }
            if (items > 0 && pageIndex > 1) {
                offset = (pageIndex - 1) * items;
            }
        }

        // Combine all bindings
        List<Object> allBindings = new ArrayList<>();
        allBindings.addAll(ctx.sourceQueryBindings);
        allBindings.addAll(ctx.joinBindings);
        allBindings.addAll(where.bindings());

        String sql = "SELECT %s FROM %s%s%s%s LIMIT %d%s"
                .formatted(select, from, where.sql(), groupBy, orderBy, limit, offset > 0 ? " OFFSET " + offset : "");
        return new TranslationResult(ctx.primaryTableId, sql, allBindings);
    }

    /**
     * Builds query context including source table/query, joins, and expressions.
     */
    private QueryContext buildQueryContext(long databaseId, JsonNode mbqlQuery, char quote) {
        QueryContext ctx = new QueryContext();
        ctx.databaseId = databaseId;

        // Check for source-query (nested query) first
        JsonNode sourceQuery = mbqlQuery.get("source-query");
        if (sourceQuery != null && sourceQuery.isObject()) {
            // Recursively translate the nested query
            TranslationResult nested = translateSelect(databaseId, sourceQuery, null);
            ctx.primaryTableId = nested.sourceTableId();
            ctx.fromClause = "(" + nested.sql() + ") AS " + quoteIdentifier("source", quote);
            ctx.sourceQueryBindings.addAll(nested.bindings());
            ctx.isSubquery = true;
            // For subqueries, we need to track available columns from the nested query
            // For now, allow pass-through of field references
        } else {
            // Regular source-table
            JsonNode sourceTableNode = mbqlQuery.get("source-table");
            if (sourceTableNode != null && sourceTableNode.isTextual()) {
                throw new IllegalArgumentException("Only numeric query.source-table is supported.");
            }
            long tableId = mbqlQuery.path("source-table").asLong(0);
            if (tableId <= 0) {
                throw new IllegalArgumentException("query.source-table is required.");
            }

            AnalyticsTable table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));
            if (table.getDatabaseId() == null || table.getDatabaseId() != databaseId) {
                throw new IllegalArgumentException("Table " + tableId + " does not belong to database " + databaseId);
            }

            ctx.primaryTableId = tableId;
            ctx.primaryTableAlias = "t0";
            ctx.fromClause = qualifyTable(table.getSchemaName(), table.getName(), quote) + " AS "
                    + quoteIdentifier(ctx.primaryTableAlias, quote);

            // Load fields for primary table
            for (AnalyticsField field : fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(tableId)) {
                if (field.getId() != null) {
                    ctx.fieldsById.put(field.getId(), field);
                    ctx.fieldTableAlias.put(field.getId(), ctx.primaryTableAlias);
                }
            }
        }

        // Parse expressions
        JsonNode expressions = mbqlQuery.get("expressions");
        if (expressions != null && expressions.isObject()) {
            parseExpressions(expressions, ctx, quote);
        }

        // Parse joins
        JsonNode joins = mbqlQuery.get("joins");
        if (joins != null && joins.isArray() && !joins.isEmpty()) {
            parseJoins(joins, ctx, quote);
        }

        return ctx;
    }

    /**
     * Parse MBQL expressions (custom calculated fields).
     * Format: {"expression-name": ["operator", arg1, arg2, ...]}
     */
    private void parseExpressions(JsonNode expressions, QueryContext ctx, char quote) {
        Iterator<String> names = expressions.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode expr = expressions.get(name);
            String sqlExpr = renderExpression(expr, ctx, quote);
            ctx.expressions.put(name, sqlExpr);
        }
    }

    /**
     * Render an expression to SQL.
     */
    private String renderExpression(JsonNode expr, QueryContext ctx, char quote) {
        if (expr == null || expr.isNull()) {
            return "NULL";
        }
        if (expr.isNumber()) {
            return expr.asText();
        }
        if (expr.isTextual()) {
            // String literal
            return "'" + expr.asText().replace("'", "''") + "'";
        }
        if (!expr.isArray() || expr.isEmpty()) {
            throw new IllegalArgumentException("Expression must be an array: " + expr);
        }

        String op = expr.get(0).asText("").toLowerCase(Locale.ROOT);
        return switch (op) {
            // Arithmetic operators
            case "+" -> renderBinaryOp("+", expr, ctx, quote);
            case "-" -> renderBinaryOp("-", expr, ctx, quote);
            case "*" -> renderBinaryOp("*", expr, ctx, quote);
            case "/" -> renderBinaryOp("/", expr, ctx, quote);

            // Comparison (for CASE expressions)
            case "=" -> renderBinaryOp("=", expr, ctx, quote);
            case "!=" -> renderBinaryOp("<>", expr, ctx, quote);
            case "<" -> renderBinaryOp("<", expr, ctx, quote);
            case ">" -> renderBinaryOp(">", expr, ctx, quote);
            case "<=" -> renderBinaryOp("<=", expr, ctx, quote);
            case ">=" -> renderBinaryOp(">=", expr, ctx, quote);

            // Logical
            case "and" -> renderLogicalExpr("AND", expr, ctx, quote);
            case "or" -> renderLogicalExpr("OR", expr, ctx, quote);
            case "not" -> "NOT (" + renderExpression(expr.get(1), ctx, quote) + ")";

            // Conditional
            case "case" -> renderCaseExpression(expr, ctx, quote);
            case "coalesce" -> renderFunctionExpr("COALESCE", expr, ctx, quote);
            case "if" -> renderIfExpression(expr, ctx, quote);

            // String functions
            case "concat" -> renderFunctionExpr("CONCAT", expr, ctx, quote);
            case "substring" -> renderSubstring(expr, ctx, quote);
            case "upper" -> renderFunctionExpr("UPPER", expr, ctx, quote);
            case "lower" -> renderFunctionExpr("LOWER", expr, ctx, quote);
            case "trim" -> renderFunctionExpr("TRIM", expr, ctx, quote);
            case "ltrim" -> renderFunctionExpr("LTRIM", expr, ctx, quote);
            case "rtrim" -> renderFunctionExpr("RTRIM", expr, ctx, quote);
            case "length" -> renderFunctionExpr("LENGTH", expr, ctx, quote);
            case "replace" -> renderFunctionExpr("REPLACE", expr, ctx, quote);

            // Numeric functions
            case "abs" -> renderFunctionExpr("ABS", expr, ctx, quote);
            case "ceil" -> renderFunctionExpr("CEIL", expr, ctx, quote);
            case "floor" -> renderFunctionExpr("FLOOR", expr, ctx, quote);
            case "round" -> renderFunctionExpr("ROUND", expr, ctx, quote);
            case "power" -> renderFunctionExpr("POWER", expr, ctx, quote);
            case "sqrt" -> renderFunctionExpr("SQRT", expr, ctx, quote);
            case "exp" -> renderFunctionExpr("EXP", expr, ctx, quote);
            case "log" -> renderFunctionExpr("LOG", expr, ctx, quote);

            // Date/Time functions
            case "now" -> "NOW()";
            case "current-date" -> "CURRENT_DATE";
            case "current-timestamp" -> "CURRENT_TIMESTAMP";
            case "datetime-add" -> renderDatetimeAdd(expr, ctx, quote);
            case "datetime-subtract" -> renderDatetimeSubtract(expr, ctx, quote);
            case "get-year" -> renderExtract("YEAR", expr, ctx, quote);
            case "get-month" -> renderExtract("MONTH", expr, ctx, quote);
            case "get-day" -> renderExtract("DAY", expr, ctx, quote);
            case "get-hour" -> renderExtract("HOUR", expr, ctx, quote);
            case "get-minute" -> renderExtract("MINUTE", expr, ctx, quote);
            case "get-second" -> renderExtract("SECOND", expr, ctx, quote);
            case "get-quarter" -> renderExtract("QUARTER", expr, ctx, quote);
            case "get-day-of-week" -> renderExtract("DOW", expr, ctx, quote);

            // Null handling
            case "is-null" -> "(" + renderExpression(expr.get(1), ctx, quote) + " IS NULL)";
            case "not-null" -> "(" + renderExpression(expr.get(1), ctx, quote) + " IS NOT NULL)";

            // Field reference
            case "field" -> parseFieldRef(expr, ctx, quote);

            // Type casting
            case "cast" -> renderCast(expr, ctx, quote);

            default -> throw new IllegalArgumentException("Unsupported expression operator: " + op);
        };
    }

    private String renderBinaryOp(String op, JsonNode expr, QueryContext ctx, char quote) {
        if (expr.size() < 3) {
            throw new IllegalArgumentException("Binary operator requires 2 arguments: " + expr);
        }
        String left = renderExpression(expr.get(1), ctx, quote);
        String right = renderExpression(expr.get(2), ctx, quote);
        return "(" + left + " " + op + " " + right + ")";
    }

    private String renderLogicalExpr(String op, JsonNode expr, QueryContext ctx, char quote) {
        List<String> parts = new ArrayList<>();
        for (int i = 1; i < expr.size(); i++) {
            parts.add("(" + renderExpression(expr.get(i), ctx, quote) + ")");
        }
        return "(" + String.join(" " + op + " ", parts) + ")";
    }

    private String renderFunctionExpr(String funcName, JsonNode expr, QueryContext ctx, char quote) {
        List<String> args = new ArrayList<>();
        for (int i = 1; i < expr.size(); i++) {
            args.add(renderExpression(expr.get(i), ctx, quote));
        }
        return funcName + "(" + String.join(", ", args) + ")";
    }

    private String renderCaseExpression(JsonNode expr, QueryContext ctx, char quote) {
        // ["case", [[condition1, result1], [condition2, result2], ...], default]
        StringBuilder sb = new StringBuilder("CASE");
        JsonNode cases = expr.get(1);
        if (cases != null && cases.isArray()) {
            for (JsonNode c : cases) {
                if (c.isArray() && c.size() >= 2) {
                    String cond = renderExpression(c.get(0), ctx, quote);
                    String result = renderExpression(c.get(1), ctx, quote);
                    sb.append(" WHEN ").append(cond).append(" THEN ").append(result);
                }
            }
        }
        if (expr.size() > 2) {
            String defaultVal = renderExpression(expr.get(2), ctx, quote);
            sb.append(" ELSE ").append(defaultVal);
        }
        sb.append(" END");
        return sb.toString();
    }

    private String renderIfExpression(JsonNode expr, QueryContext ctx, char quote) {
        // ["if", condition, then-value, else-value]
        if (expr.size() < 4) {
            throw new IllegalArgumentException("if expression requires condition, then, else: " + expr);
        }
        String cond = renderExpression(expr.get(1), ctx, quote);
        String thenVal = renderExpression(expr.get(2), ctx, quote);
        String elseVal = renderExpression(expr.get(3), ctx, quote);
        return "CASE WHEN " + cond + " THEN " + thenVal + " ELSE " + elseVal + " END";
    }

    private String renderSubstring(JsonNode expr, QueryContext ctx, char quote) {
        // ["substring", field, start, length]
        String field = renderExpression(expr.get(1), ctx, quote);
        String start = expr.size() > 2 ? expr.get(2).asText("1") : "1";
        String length = expr.size() > 3 ? expr.get(3).asText() : null;
        if (length != null) {
            return "SUBSTRING(" + field + " FROM " + start + " FOR " + length + ")";
        }
        return "SUBSTRING(" + field + " FROM " + start + ")";
    }

    private String renderDatetimeAdd(JsonNode expr, QueryContext ctx, char quote) {
        // ["datetime-add", field, amount, unit]
        String field = renderExpression(expr.get(1), ctx, quote);
        String amount = expr.get(2).asText("0");
        String unit = expr.size() > 3 ? expr.get(3).asText("day").toUpperCase(Locale.ROOT) : "DAY";
        return "(" + field + " + INTERVAL '" + amount + "' " + unit + ")";
    }

    private String renderDatetimeSubtract(JsonNode expr, QueryContext ctx, char quote) {
        // ["datetime-subtract", field, amount, unit]
        String field = renderExpression(expr.get(1), ctx, quote);
        String amount = expr.get(2).asText("0");
        String unit = expr.size() > 3 ? expr.get(3).asText("day").toUpperCase(Locale.ROOT) : "DAY";
        return "(" + field + " - INTERVAL '" + amount + "' " + unit + ")";
    }

    private String renderExtract(String part, JsonNode expr, QueryContext ctx, char quote) {
        String field = renderExpression(expr.get(1), ctx, quote);
        return "EXTRACT(" + part + " FROM " + field + ")";
    }

    private String renderCast(JsonNode expr, QueryContext ctx, char quote) {
        // ["cast", value, type]
        String value = renderExpression(expr.get(1), ctx, quote);
        String type = expr.get(2).asText("TEXT").toUpperCase(Locale.ROOT);
        return "CAST(" + value + " AS " + type + ")";
    }

    /**
     * Parse MBQL joins.
     * Format: [{"source-table": id, "condition": [...], "alias": "...", "strategy":
     * "left-join"}]
     */
    private void parseJoins(JsonNode joins, QueryContext ctx, char quote) {
        int joinIndex = 1;
        for (JsonNode join : joins) {
            if (!join.isObject())
                continue;

            long joinTableId = join.path("source-table").asLong(0);
            if (joinTableId <= 0) {
                throw new IllegalArgumentException("Join requires source-table");
            }

            AnalyticsTable joinTable = tableRepository.findById(joinTableId)
                    .orElseThrow(() -> new IllegalArgumentException("Join table not found: " + joinTableId));

            String alias = join.has("alias") ? join.get("alias").asText() : "t" + joinIndex;
            String strategy = join.path("strategy").asText("left-join").toLowerCase(Locale.ROOT);

            String joinType = switch (strategy) {
                case "inner-join" -> "INNER JOIN";
                case "right-join" -> "RIGHT JOIN";
                case "full-join" -> "FULL OUTER JOIN";
                default -> "LEFT JOIN";
            };

            String joinTableSql = qualifyTable(joinTable.getSchemaName(), joinTable.getName(), quote) + " AS "
                    + quoteIdentifier(alias, quote);

            // Load fields for joined table
            for (AnalyticsField field : fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(joinTableId)) {
                if (field.getId() != null) {
                    ctx.fieldsById.put(field.getId(), field);
                    ctx.fieldTableAlias.put(field.getId(), alias);
                }
            }

            // Parse join condition
            JsonNode condition = join.get("condition");
            SqlFragment conditionSql = renderJoinCondition(condition, ctx, quote);

            ctx.fromClause += " " + joinType + " " + joinTableSql + " ON " + conditionSql.sql();
            ctx.joinBindings.addAll(conditionSql.bindings());

            joinIndex++;
        }
    }

    /**
     * Render join condition.
     */
    private SqlFragment renderJoinCondition(JsonNode condition, QueryContext ctx, char quote) {
        if (condition == null || !condition.isArray() || condition.isEmpty()) {
            throw new IllegalArgumentException("Join condition is required");
        }

        String op = condition.get(0).asText("").toLowerCase(Locale.ROOT);
        return switch (op) {
            case "=" -> {
                String left = parseFieldRef(condition.get(1), ctx, quote);
                String right = parseFieldRef(condition.get(2), ctx, quote);
                yield new SqlFragment(left + " = " + right, List.of());
            }
            case "and" -> {
                List<String> parts = new ArrayList<>();
                List<Object> bindings = new ArrayList<>();
                for (int i = 1; i < condition.size(); i++) {
                    SqlFragment part = renderJoinCondition(condition.get(i), ctx, quote);
                    parts.add("(" + part.sql() + ")");
                    bindings.addAll(part.bindings());
                }
                yield new SqlFragment(String.join(" AND ", parts), bindings);
            }
            case "or" -> {
                List<String> parts = new ArrayList<>();
                List<Object> bindings = new ArrayList<>();
                for (int i = 1; i < condition.size(); i++) {
                    SqlFragment part = renderJoinCondition(condition.get(i), ctx, quote);
                    parts.add("(" + part.sql() + ")");
                    bindings.addAll(part.bindings());
                }
                yield new SqlFragment("(" + String.join(" OR ", parts) + ")", bindings);
            }
            default -> throw new IllegalArgumentException("Unsupported join condition operator: " + op);
        };
    }

    /**
     * Parse a field reference, supporting expressions, joined tables, and regular
     * fields.
     */
    private String parseFieldRef(JsonNode fieldRef, QueryContext ctx, char quote) {
        if (fieldRef == null || !fieldRef.isArray() || fieldRef.size() < 2) {
            throw new IllegalArgumentException("Invalid field reference: " + fieldRef);
        }

        String kind = fieldRef.get(0).asText("");
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "field" -> {
                JsonNode idNode = fieldRef.get(1);
                if (idNode.isTextual()) {
                    // Field by name (for subqueries)
                    yield quoteIdentifier(idNode.asText(), quote);
                }
                long fieldId = idNode.asLong(0);
                if (fieldId <= 0) {
                    throw new IllegalArgumentException("Invalid field id: " + fieldRef);
                }
                AnalyticsField field = ctx.fieldsById.get(fieldId);
                if (field == null) {
                    throw new IllegalArgumentException("Field not found: " + fieldId);
                }
                String alias = ctx.fieldTableAlias.get(fieldId);
                if (alias != null && !ctx.isSubquery) {
                    yield quoteIdentifier(alias, quote) + "." + quoteIdentifier(field.getName(), quote);
                }
                yield quoteIdentifier(field.getName(), quote);
            }
            case "expression" -> {
                String exprName = fieldRef.get(1).asText();
                String exprSql = ctx.expressions.get(exprName);
                if (exprSql == null) {
                    throw new IllegalArgumentException("Expression not found: " + exprName);
                }
                yield exprSql;
            }
            case "aggregation" -> {
                // Aggregation reference - will be handled by order-by parsing
                throw new IllegalArgumentException("Aggregation references should be handled by order-by");
            }
            default -> throw new IllegalArgumentException("Unsupported field reference kind: " + kind);
        };
    }

    /**
     * Parse a field reference for numeric aggregations (SUM, AVG, STDDEV, VAR,
     * MEDIAN).
     * If the field is not a numeric type, wraps it with CAST(... AS NUMERIC).
     */
    private String parseNumericFieldRef(JsonNode fieldRef, QueryContext ctx, char quote) {
        if (fieldRef == null || !fieldRef.isArray() || fieldRef.size() < 2) {
            throw new IllegalArgumentException("Invalid field reference: " + fieldRef);
        }

        String kind = fieldRef.get(0).asText("");
        if (!"field".equalsIgnoreCase(kind)) {
            // For expressions, just use the regular parsing
            return parseFieldRef(fieldRef, ctx, quote);
        }

        JsonNode idNode = fieldRef.get(1);
        if (idNode.isTextual()) {
            // Field by name (for subqueries) - assume it needs casting for safety
            String fieldName = quoteIdentifier(idNode.asText(), quote);
            return "CAST(" + fieldName + " AS NUMERIC)";
        }

        long fieldId = idNode.asLong(0);
        if (fieldId <= 0) {
            throw new IllegalArgumentException("Invalid field id: " + fieldRef);
        }

        AnalyticsField field = ctx.fieldsById.get(fieldId);
        if (field == null) {
            throw new IllegalArgumentException("Field not found: " + fieldId);
        }

        String alias = ctx.fieldTableAlias.get(fieldId);
        String fieldSql;
        if (alias != null && !ctx.isSubquery) {
            fieldSql = quoteIdentifier(alias, quote) + "." + quoteIdentifier(field.getName(), quote);
        } else {
            fieldSql = quoteIdentifier(field.getName(), quote);
        }

        // Check if the field is numeric, if not wrap with CAST
        String baseType = field.getBaseType();
        if (isNumericType(baseType)) {
            return fieldSql;
        }
        // Wrap non-numeric fields with CAST to NUMERIC
        return "CAST(" + fieldSql + " AS NUMERIC)";
    }

    /**
     * Check if a base type is numeric.
     */
    private static boolean isNumericType(String baseType) {
        if (baseType == null) {
            return false;
        }
        return "type/Integer".equals(baseType)
                || "type/BigInteger".equals(baseType)
                || "type/Float".equals(baseType)
                || "type/Decimal".equals(baseType)
                || "type/Number".equals(baseType);
    }

    private SqlFragment renderWhere(JsonNode filter, QueryContext ctx, char quote) {
        if (filter == null || filter.isNull() || filter.isMissingNode()) {
            return SqlFragment.empty();
        }
        SqlFragment rendered = renderFilter(filter, ctx, quote);
        if (rendered.sql().isBlank()) {
            return SqlFragment.empty();
        }
        return new SqlFragment(" WHERE " + rendered.sql(), rendered.bindings());
    }

    private SqlFragment renderFilter(JsonNode filter, QueryContext ctx, char quote) {
        if (filter == null || filter.isNull() || filter.isMissingNode()) {
            return SqlFragment.empty();
        }
        if (!filter.isArray() || filter.size() < 1) {
            throw new IllegalArgumentException("query.filter must be an array.");
        }

        String op = filter.get(0).asText("").toLowerCase(Locale.ROOT);
        return switch (op) {
            case "and" -> renderLogical("AND", filter, ctx, quote);
            case "or" -> renderLogical("OR", filter, ctx, quote);
            case "not" -> renderNot(filter, ctx, quote);
            case "=" -> renderComparison("=", filter, ctx, quote);
            case "!=" -> renderComparison("<>", filter, ctx, quote);
            case "<" -> renderComparison("<", filter, ctx, quote);
            case ">" -> renderComparison(">", filter, ctx, quote);
            case "<=" -> renderComparison("<=", filter, ctx, quote);
            case ">=" -> renderComparison(">=", filter, ctx, quote);
            case "is-null" -> renderNullCheck(filter, ctx, quote, true);
            case "not-null" -> renderNullCheck(filter, ctx, quote, false);
            case "between" -> renderBetween(filter, ctx, quote);
            case "in" -> renderIn(filter, ctx, quote);
            case "contains" -> renderLike(filter, ctx, quote, "%", "%");
            case "starts-with" -> renderLike(filter, ctx, quote, "", "%");
            case "ends-with" -> renderLike(filter, ctx, quote, "%", "");
            case "is-empty" -> renderEmptyCheck(filter, ctx, quote, true);
            case "not-empty" -> renderEmptyCheck(filter, ctx, quote, false);
            default -> throw new IllegalArgumentException("Unsupported filter operator: " + op);
        };
    }

    private SqlFragment renderLogical(String join, JsonNode filter, QueryContext ctx, char quote) {
        if (filter.size() < 2) {
            throw new IllegalArgumentException(
                    "query.filter " + join.toLowerCase(Locale.ROOT) + " requires at least one clause.");
        }
        List<String> parts = new ArrayList<>();
        List<Object> bindings = new ArrayList<>();
        for (int i = 1; i < filter.size(); i++) {
            SqlFragment child = renderFilter(filter.get(i), ctx, quote);
            if (child.sql().isBlank()) {
                continue;
            }
            parts.add("(" + child.sql() + ")");
            bindings.addAll(child.bindings());
        }
        if (parts.isEmpty()) {
            return SqlFragment.empty();
        }
        return new SqlFragment(String.join(" " + join + " ", parts), bindings);
    }

    private SqlFragment renderNot(JsonNode filter, QueryContext ctx, char quote) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter not must be [\"not\", clause].");
        }
        SqlFragment child = renderFilter(filter.get(1), ctx, quote);
        if (child.sql().isBlank()) {
            return SqlFragment.empty();
        }
        return new SqlFragment("NOT (" + child.sql() + ")", child.bindings());
    }

    private SqlFragment renderComparison(String operator, JsonNode filter, QueryContext ctx, char quote) {
        if (filter.size() != 3) {
            throw new IllegalArgumentException("query.filter comparison must be [\"" + operator + "\", field, value].");
        }
        String column = parseFieldRef(filter.get(1), ctx, quote);
        JsonNode valueNode = filter.get(2);
        if (valueNode == null || valueNode.isNull()) {
            if ("=".equals(operator)) {
                return new SqlFragment(column + " IS NULL", List.of());
            }
            if ("<>".equals(operator)) {
                return new SqlFragment(column + " IS NOT NULL", List.of());
            }
            throw new IllegalArgumentException("query.filter " + operator + " does not support null value.");
        }
        if (valueNode.isArray() || valueNode.isObject()) {
            throw new IllegalArgumentException("query.filter " + operator + " value must be a scalar.");
        }
        Object binding = jsonScalarToBinding(valueNode);
        return new SqlFragment(column + " " + operator + " ?", List.of(binding));
    }

    private SqlFragment renderNullCheck(JsonNode filter, QueryContext ctx, char quote, boolean isNull) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter null check must be [\"is-null\"|\"not-null\", field].");
        }
        String column = parseFieldRef(filter.get(1), ctx, quote);
        return new SqlFragment(column + (isNull ? " IS NULL" : " IS NOT NULL"), List.of());
    }

    private SqlFragment renderBetween(JsonNode filter, QueryContext ctx, char quote) {
        if (filter.size() != 4) {
            throw new IllegalArgumentException("query.filter between must be [\"between\", field, min, max].");
        }
        String column = parseFieldRef(filter.get(1), ctx, quote);
        Object min = jsonScalarToBinding(requireScalar(filter.get(2), "between min"));
        Object max = jsonScalarToBinding(requireScalar(filter.get(3), "between max"));
        return new SqlFragment(column + " BETWEEN ? AND ?", List.of(min, max));
    }

    private SqlFragment renderIn(JsonNode filter, QueryContext ctx, char quote) {
        if (filter.size() < 3) {
            throw new IllegalArgumentException("query.filter in must be [\"in\", field, values...].");
        }
        String column = parseFieldRef(filter.get(1), ctx, quote);

        List<JsonNode> values = new ArrayList<>();
        JsonNode third = filter.get(2);
        if (third != null && third.isArray()) {
            for (JsonNode v : third) {
                values.add(v);
            }
        } else {
            for (int i = 2; i < filter.size(); i++) {
                values.add(filter.get(i));
            }
        }

        List<Object> bindings = new ArrayList<>();
        boolean hasNull = false;
        for (JsonNode value : values) {
            if (value == null || value.isNull()) {
                hasNull = true;
                continue;
            }
            bindings.add(jsonScalarToBinding(requireScalar(value, "in value")));
        }

        List<String> disjuncts = new ArrayList<>();
        if (!bindings.isEmpty()) {
            String placeholders = String.join(", ", java.util.Collections.nCopies(bindings.size(), "?"));
            disjuncts.add(column + " IN (" + placeholders + ")");
        }
        if (hasNull) {
            disjuncts.add(column + " IS NULL");
        }
        if (disjuncts.isEmpty()) {
            return new SqlFragment("1=0", List.of());
        }

        String sql = disjuncts.size() == 1 ? disjuncts.get(0) : "(" + String.join(" OR ", disjuncts) + ")";
        return new SqlFragment(sql, bindings);
    }

    private SqlFragment renderLike(JsonNode filter, QueryContext ctx, char quote, String prefix, String suffix) {
        if (filter.size() != 3) {
            throw new IllegalArgumentException(
                    "query.filter like must be [\"contains\"|\"starts-with\"|\"ends-with\", field, value].");
        }
        String column = parseFieldRef(filter.get(1), ctx, quote);
        JsonNode valueNode = requireScalar(filter.get(2), "like value");
        String value = valueNode.asText();
        return new SqlFragment(column + " LIKE ?", List.of(prefix + value + suffix));
    }

    private SqlFragment renderEmptyCheck(JsonNode filter, QueryContext ctx, char quote, boolean isEmpty) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException(
                    "query.filter is-empty/not-empty must be [\"is-empty\"|\"not-empty\", field].");
        }
        String column = parseFieldRef(filter.get(1), ctx, quote);
        String sql = "(%s IS NULL OR %s = '')".formatted(column, column);
        if (isEmpty) {
            return new SqlFragment(sql, List.of());
        }
        return new SqlFragment("NOT " + sql, List.of());
    }

    private static JsonNode requireScalar(JsonNode node, String label) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("query.filter " + label + " cannot be null.");
        }
        if (node.isArray() || node.isObject()) {
            throw new IllegalArgumentException("query.filter " + label + " must be a scalar.");
        }
        return node;
    }

    private static Object jsonScalarToBinding(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToLong() ? node.asLong() : node.longValue();
        }
        if (node.isNumber()) {
            BigDecimal decimal = node.decimalValue();
            return decimal;
        }
        return node.asText();
    }

    private String renderOrderBy(JsonNode orderBy, QueryContext ctx, List<AggregationSpec> aggregations, char quote) {
        if (orderBy == null || !orderBy.isArray() || orderBy.isEmpty()) {
            return "";
        }

        List<String> clauses = new ArrayList<>();
        for (JsonNode node : orderBy) {
            clauses.add(renderOrderByClause(node, ctx, aggregations, quote));
        }

        if (clauses.isEmpty()) {
            return "";
        }
        return " ORDER BY " + String.join(", ", clauses);
    }

    private String renderOrderByClause(JsonNode node, QueryContext ctx, List<AggregationSpec> aggregations,
            char quote) {
        if (node == null || !node.isArray() || node.size() < 2) {
            throw new IllegalArgumentException("query.order-by must be a list of [direction field-ref] pairs.");
        }
        String direction = node.get(0).asText("");
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("query.order-by direction must be asc or desc.");
        }
        String column = parseOrderByTarget(node.get(1), ctx, aggregations, quote);
        return column + " " + direction.toUpperCase(Locale.ROOT);
    }

    private String parseOrderByTarget(JsonNode target, QueryContext ctx, List<AggregationSpec> aggregations,
            char quote) {
        if (target != null && target.isArray() && target.size() >= 2) {
            String kind = target.get(0).asText("");
            if ("aggregation".equalsIgnoreCase(kind)) {
                int index = target.get(1).canConvertToInt() ? target.get(1).asInt() : -1;
                if (index < 0 || index >= aggregations.size()) {
                    throw new IllegalArgumentException("Invalid aggregation reference in order-by: " + target);
                }
                return quoteIdentifier(aggregations.get(index).alias(), quote);
            }
            if ("expression".equalsIgnoreCase(kind)) {
                String exprName = target.get(1).asText();
                String exprSql = ctx.expressions.get(exprName);
                if (exprSql == null) {
                    throw new IllegalArgumentException("Expression not found in order-by: " + exprName);
                }
                return exprSql;
            }
        }
        return parseFieldRef(target, ctx, quote);
    }

    private static String qualifyTable(String schema, String name, char quote) {
        String tableName = quoteIdentifier(name, quote);
        if (schema == null || schema.isBlank()) {
            return tableName;
        }
        return quoteIdentifier(schema, quote) + "." + tableName;
    }

    private static char quoteChar(String engine) {
        if (engine == null) {
            return '"';
        }
        return switch (engine.trim().toLowerCase(Locale.ROOT)) {
            case "mysql" -> '`';
            default -> '"';
        };
    }

    private static String quoteIdentifier(String identifier, char quote) {
        if (identifier == null) {
            throw new IllegalArgumentException("Identifier cannot be null");
        }
        String q = String.valueOf(quote);
        String escaped = identifier.replace(q, q + q);
        return quote + escaped + quote;
    }

    private List<String> parseBreakoutColumns(JsonNode breakout, QueryContext ctx, char quote) {
        if (breakout == null || breakout.isNull() || breakout.isMissingNode()) {
            return List.of();
        }
        if (!breakout.isArray()) {
            throw new IllegalArgumentException("query.breakout must be a list of field refs.");
        }
        List<String> columns = new ArrayList<>();
        for (JsonNode node : breakout) {
            columns.add(parseFieldRef(node, ctx, quote));
        }
        return columns;
    }

    private List<AggregationSpec> parseAggregations(JsonNode aggregation, QueryContext ctx, char quote) {
        if (aggregation == null || aggregation.isNull() || aggregation.isMissingNode()) {
            return List.of();
        }
        if (!aggregation.isArray()) {
            throw new IllegalArgumentException("query.aggregation must be a list.");
        }
        List<AggregationSpec> result = new ArrayList<>();
        int idx = 0;
        for (JsonNode node : aggregation) {
            if (node == null || !node.isArray() || node.isEmpty()) {
                throw new IllegalArgumentException("query.aggregation items must be an array.");
            }
            String op = node.get(0).asText("");
            String alias = "metric_" + idx;
            String expr = switch (op.toLowerCase(Locale.ROOT)) {
                case "count" -> {
                    if (node.size() == 1) {
                        yield "COUNT(*)";
                    }
                    if (node.size() == 2) {
                        yield "COUNT(" + parseFieldRef(node.get(1), ctx, quote) + ")";
                    }
                    throw new IllegalArgumentException("aggregation count must be [\"count\"] or [\"count\", field].");
                }
                case "sum" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation sum must be [\"sum\", field].");
                    String sumField = parseNumericFieldRef(node.get(1), ctx, quote);
                    yield "SUM(" + sumField + ")";
                }
                case "avg" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation avg must be [\"avg\", field].");
                    String avgField = parseNumericFieldRef(node.get(1), ctx, quote);
                    yield "AVG(" + avgField + ")";
                }
                case "min" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation min must be [\"min\", field].");
                    yield "MIN(" + parseFieldRef(node.get(1), ctx, quote) + ")";
                }
                case "max" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation max must be [\"max\", field].");
                    yield "MAX(" + parseFieldRef(node.get(1), ctx, quote) + ")";
                }
                case "distinct" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation distinct must be [\"distinct\", field].");
                    yield "COUNT(DISTINCT " + parseFieldRef(node.get(1), ctx, quote) + ")";
                }
                case "stddev" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation stddev must be [\"stddev\", field].");
                    String stddevField = parseNumericFieldRef(node.get(1), ctx, quote);
                    yield "STDDEV(" + stddevField + ")";
                }
                case "var" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation var must be [\"var\", field].");
                    String varField = parseNumericFieldRef(node.get(1), ctx, quote);
                    yield "VARIANCE(" + varField + ")";
                }
                case "median" -> {
                    if (node.size() != 2)
                        throw new IllegalArgumentException("aggregation median must be [\"median\", field].");
                    String medianField = parseNumericFieldRef(node.get(1), ctx, quote);
                    // PostgreSQL specific - use percentile_cont for median
                    yield "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + medianField + ")";
                }
                default -> throw new IllegalArgumentException("Unsupported aggregation operator: " + op);
            };
            result.add(new AggregationSpec(expr, alias, quote));
            idx++;
        }
        return result;
    }

    /**
     * Query context that holds all the information needed during MBQL translation.
     */
    private static class QueryContext {
        long databaseId;
        long primaryTableId;
        String primaryTableAlias;
        String fromClause;
        boolean isSubquery;
        Map<Long, AnalyticsField> fieldsById = new HashMap<>();
        Map<Long, String> fieldTableAlias = new HashMap<>();
        Map<String, String> expressions = new LinkedHashMap<>();
        List<Object> sourceQueryBindings = new ArrayList<>();
        List<Object> joinBindings = new ArrayList<>();
    }

    public record TranslationResult(long sourceTableId, String sql, List<Object> bindings) {
    }

    private record AggregationSpec(String sql, String alias, char quote) {
        String sqlWithAlias() {
            return sql + " AS " + quoteIdentifier(alias, quote);
        }
    }

    private record SqlFragment(String sql, List<Object> bindings) {

        static SqlFragment empty() {
            return new SqlFragment("", List.of());
        }
    }
}
