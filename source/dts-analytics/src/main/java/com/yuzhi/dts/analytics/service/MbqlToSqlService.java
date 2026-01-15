package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsField;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MbqlToSqlService {

    private static final Set<String> UNSUPPORTED_KEYS = Set.of(
            "expressions",
            "joins",
            "source-query",
            "limit-by");

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
    public TranslationResult translateSelect(long databaseId, JsonNode mbqlQuery, DatasetQueryService.DatasetConstraints constraints) {
        if (mbqlQuery == null || !mbqlQuery.isObject()) {
            throw new IllegalArgumentException("query must be a map.");
        }

        for (String unsupported : UNSUPPORTED_KEYS) {
            if (mbqlQuery.has(unsupported)) {
                throw new IllegalArgumentException("MBQL query key is not supported yet: " + unsupported);
            }
        }

        JsonNode sourceTableNode = mbqlQuery.get("source-table");
        if (sourceTableNode != null && sourceTableNode.isTextual()) {
            throw new IllegalArgumentException("Only numeric query.source-table is supported.");
        }
        long tableId = mbqlQuery.path("source-table").asLong(0);
        if (tableId <= 0) {
            throw new IllegalArgumentException("query.source-table is required.");
        }

        AnalyticsTable table = tableRepository
                .findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));
        if (table.getDatabaseId() == null || table.getDatabaseId() != databaseId) {
            throw new IllegalArgumentException("Table " + tableId + " does not belong to database " + databaseId);
        }

        Map<Long, AnalyticsField> fieldsById = new HashMap<>();
        for (AnalyticsField field : fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(tableId)) {
            if (field.getId() != null) {
                fieldsById.put(field.getId(), field);
            }
        }

        String engine = databaseRepository
                .findById(databaseId)
                .map(AnalyticsDatabase::getEngine)
                .orElse(null);
        char quote = quoteChar(engine);

        List<String> breakoutColumns = parseBreakoutColumns(mbqlQuery.get("breakout"), fieldsById, quote);
        List<AggregationSpec> aggregations = parseAggregations(mbqlQuery.get("aggregation"), fieldsById, quote);
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
        if (selectParts.isEmpty()) {
            JsonNode fields = mbqlQuery.get("fields");
            if (fields != null && fields.isArray() && fields.size() > 0) {
                for (JsonNode node : fields) {
                    selectParts.add(parseFieldRefColumnName(node, fieldsById, quote));
                }
            }
        }

        String select = selectParts.isEmpty() ? "*" : String.join(", ", selectParts);
        String from = qualifyTable(table.getSchemaName(), table.getName(), quote);
        SqlFragment where = renderWhere(mbqlQuery.get("filter"), fieldsById, quote);
        String groupBy = hasAgg && hasBreakout ? (" GROUP BY " + String.join(", ", breakoutColumns)) : "";
        String orderBy = renderOrderBy(mbqlQuery.get("order-by"), fieldsById, aggregations, quote);

        int requestedLimit = mbqlQuery.path("limit").canConvertToInt() ? mbqlQuery.path("limit").asInt() : 0;
        int limit = constraints != null ? constraints.maxResults() : DatasetQueryService.DatasetConstraints.defaults().maxResults();
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

        String sql = "SELECT %s FROM %s%s%s%s LIMIT %d%s"
                .formatted(select, from, where.sql(), groupBy, orderBy, limit, offset > 0 ? " OFFSET " + offset : "");
        return new TranslationResult(tableId, sql, where.bindings());
    }

    private static SqlFragment renderWhere(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (filter == null || filter.isNull() || filter.isMissingNode()) {
            return SqlFragment.empty();
        }
        SqlFragment rendered = renderFilter(filter, fieldsById, quote);
        if (rendered.sql().isBlank()) {
            return SqlFragment.empty();
        }
        return new SqlFragment(" WHERE " + rendered.sql(), rendered.bindings());
    }

    private static SqlFragment renderFilter(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (filter == null || filter.isNull() || filter.isMissingNode()) {
            return SqlFragment.empty();
        }
        if (!filter.isArray() || filter.size() < 1) {
            throw new IllegalArgumentException("query.filter must be an array.");
        }

        String op = filter.get(0).asText("").toLowerCase();
        return switch (op) {
            case "and" -> renderLogical("AND", filter, fieldsById, quote);
            case "or" -> renderLogical("OR", filter, fieldsById, quote);
            case "not" -> renderNot(filter, fieldsById, quote);
            case "=" -> renderComparison("=", filter, fieldsById, quote);
            case "!=" -> renderComparison("<>", filter, fieldsById, quote);
            case "<" -> renderComparison("<", filter, fieldsById, quote);
            case ">" -> renderComparison(">", filter, fieldsById, quote);
            case "<=" -> renderComparison("<=", filter, fieldsById, quote);
            case ">=" -> renderComparison(">=", filter, fieldsById, quote);
            case "is-null" -> renderNullCheck(filter, fieldsById, quote, true);
            case "not-null" -> renderNullCheck(filter, fieldsById, quote, false);
            case "between" -> renderBetween(filter, fieldsById, quote);
            case "in" -> renderIn(filter, fieldsById, quote);
            case "contains" -> renderLike(filter, fieldsById, quote, "%", "%");
            case "starts-with" -> renderLike(filter, fieldsById, quote, "", "%");
            case "ends-with" -> renderLike(filter, fieldsById, quote, "%", "");
            case "is-empty" -> renderEmptyCheck(filter, fieldsById, quote, true);
            case "not-empty" -> renderEmptyCheck(filter, fieldsById, quote, false);
            default -> throw new IllegalArgumentException("Unsupported filter operator: " + op);
        };
    }

    private static SqlFragment renderLogical(String join, JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (filter.size() < 2) {
            throw new IllegalArgumentException("query.filter " + join.toLowerCase() + " requires at least one clause.");
        }
        List<String> parts = new ArrayList<>();
        List<Object> bindings = new ArrayList<>();
        for (int i = 1; i < filter.size(); i++) {
            SqlFragment child = renderFilter(filter.get(i), fieldsById, quote);
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

    private static SqlFragment renderNot(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter not must be [\"not\", clause].");
        }
        SqlFragment child = renderFilter(filter.get(1), fieldsById, quote);
        if (child.sql().isBlank()) {
            return SqlFragment.empty();
        }
        return new SqlFragment("NOT (" + child.sql() + ")", child.bindings());
    }

    private static SqlFragment renderComparison(String operator, JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (filter.size() != 3) {
            throw new IllegalArgumentException("query.filter comparison must be [\"" + operator + "\", field, value].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById, quote);
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

    private static SqlFragment renderNullCheck(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote, boolean isNull) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter null check must be [\"is-null\"|\"not-null\", field].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById, quote);
        return new SqlFragment(column + (isNull ? " IS NULL" : " IS NOT NULL"), List.of());
    }

    private static SqlFragment renderBetween(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (filter.size() != 4) {
            throw new IllegalArgumentException("query.filter between must be [\"between\", field, min, max].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById, quote);
        Object min = jsonScalarToBinding(requireScalar(filter.get(2), "between min"));
        Object max = jsonScalarToBinding(requireScalar(filter.get(3), "between max"));
        return new SqlFragment(column + " BETWEEN ? AND ?", List.of(min, max));
    }

    private static SqlFragment renderIn(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (filter.size() < 3) {
            throw new IllegalArgumentException("query.filter in must be [\"in\", field, values...].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById, quote);

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

    private static SqlFragment renderLike(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote, String prefix, String suffix) {
        if (filter.size() != 3) {
            throw new IllegalArgumentException("query.filter like must be [\"contains\"|\"starts-with\"|\"ends-with\", field, value].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById, quote);
        JsonNode valueNode = requireScalar(filter.get(2), "like value");
        String value = valueNode.asText();
        return new SqlFragment(column + " LIKE ?", List.of(prefix + value + suffix));
    }

    private static SqlFragment renderEmptyCheck(JsonNode filter, Map<Long, AnalyticsField> fieldsById, char quote, boolean isEmpty) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter is-empty/not-empty must be [\"is-empty\"|\"not-empty\", field].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById, quote);
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

    private static String renderOrderBy(
            JsonNode orderBy, Map<Long, AnalyticsField> fieldsById, List<AggregationSpec> aggregations, char quote) {
        if (orderBy == null || !orderBy.isArray() || orderBy.isEmpty()) {
            return "";
        }

        List<String> clauses = new ArrayList<>();
        for (JsonNode node : orderBy) {
            clauses.add(renderOrderByClause(node, fieldsById, aggregations, quote));
        }

        if (clauses.isEmpty()) {
            return "";
        }
        return " ORDER BY " + String.join(", ", clauses);
    }

    private static String renderOrderByClause(
            JsonNode node, Map<Long, AnalyticsField> fieldsById, List<AggregationSpec> aggregations, char quote) {
        if (node == null || !node.isArray() || node.size() < 2) {
            throw new IllegalArgumentException("query.order-by must be a list of [direction field-ref] pairs.");
        }
        String direction = node.get(0).asText("");
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("query.order-by direction must be asc or desc.");
        }
        String column = parseOrderByTarget(node.get(1), fieldsById, aggregations, quote);
        return column + " " + direction.toUpperCase();
    }

    private static String parseOrderByTarget(
            JsonNode target, Map<Long, AnalyticsField> fieldsById, List<AggregationSpec> aggregations, char quote) {
        if (target != null && target.isArray() && target.size() >= 2) {
            String kind = target.get(0).asText("");
            if ("aggregation".equalsIgnoreCase(kind)) {
                int index = target.get(1).canConvertToInt() ? target.get(1).asInt() : -1;
                if (index < 0 || index >= aggregations.size()) {
                    throw new IllegalArgumentException("Invalid aggregation reference in order-by: " + target);
                }
                return quoteIdentifier(aggregations.get(index).alias(), quote);
            }
        }
        return parseFieldRefColumnName(target, fieldsById, quote);
    }

    private static String parseFieldRefColumnName(JsonNode fieldRef, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (fieldRef == null || !fieldRef.isArray() || fieldRef.size() < 2) {
            throw new IllegalArgumentException("fields must contain field references.");
        }
        String kind = fieldRef.get(0).asText("");
        if (!"field".equalsIgnoreCase(kind)) {
            throw new IllegalArgumentException("Only [\"field\", id, ...] refs are supported in MBQL fields.");
        }
        long fieldId = fieldRef.get(1).asLong(0);
        if (fieldId <= 0) {
            throw new IllegalArgumentException("Invalid field ref: missing id.");
        }
        AnalyticsField field = fieldsById.get(fieldId);
        if (field == null) {
            throw new IllegalArgumentException("Field not found: " + fieldId);
        }
        return quoteIdentifier(field.getName(), quote);
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
        return switch (engine.trim().toLowerCase(java.util.Locale.ROOT)) {
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

    private static List<String> parseBreakoutColumns(JsonNode breakout, Map<Long, AnalyticsField> fieldsById, char quote) {
        if (breakout == null || breakout.isNull() || breakout.isMissingNode()) {
            return List.of();
        }
        if (!breakout.isArray()) {
            throw new IllegalArgumentException("query.breakout must be a list of field refs.");
        }
        List<String> columns = new ArrayList<>();
        for (JsonNode node : breakout) {
            columns.add(parseFieldRefColumnName(node, fieldsById, quote));
        }
        return columns;
    }

    private static List<AggregationSpec> parseAggregations(JsonNode aggregation, Map<Long, AnalyticsField> fieldsById, char quote) {
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
            String expr = switch (op.toLowerCase(java.util.Locale.ROOT)) {
                case "count" -> {
                    if (node.size() == 1) {
                        yield "COUNT(*)";
                    }
                    if (node.size() == 2) {
                        yield "COUNT(" + parseFieldRefColumnName(node.get(1), fieldsById, quote) + ")";
                    }
                    throw new IllegalArgumentException("aggregation count must be [\"count\"] or [\"count\", field].");
                }
                case "sum" -> {
                    if (node.size() != 2) throw new IllegalArgumentException("aggregation sum must be [\"sum\", field].");
                    yield "SUM(" + parseFieldRefColumnName(node.get(1), fieldsById, quote) + ")";
                }
                case "avg" -> {
                    if (node.size() != 2) throw new IllegalArgumentException("aggregation avg must be [\"avg\", field].");
                    yield "AVG(" + parseFieldRefColumnName(node.get(1), fieldsById, quote) + ")";
                }
                case "min" -> {
                    if (node.size() != 2) throw new IllegalArgumentException("aggregation min must be [\"min\", field].");
                    yield "MIN(" + parseFieldRefColumnName(node.get(1), fieldsById, quote) + ")";
                }
                case "max" -> {
                    if (node.size() != 2) throw new IllegalArgumentException("aggregation max must be [\"max\", field].");
                    yield "MAX(" + parseFieldRefColumnName(node.get(1), fieldsById, quote) + ")";
                }
                default -> throw new IllegalArgumentException("Unsupported aggregation operator: " + op);
            };
            result.add(new AggregationSpec(expr, alias, quote));
            idx++;
        }
        return result;
    }

    public record TranslationResult(long sourceTableId, String sql, List<Object> bindings) {}

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
