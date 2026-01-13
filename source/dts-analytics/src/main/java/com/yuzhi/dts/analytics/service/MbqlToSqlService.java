package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.domain.AnalyticsField;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
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
            "aggregation",
            "breakout",
            "expressions",
            "joins",
            "source-query",
            "limit-by");

    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;

    public MbqlToSqlService(AnalyticsTableRepository tableRepository, AnalyticsFieldRepository fieldRepository) {
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

        String select = "*";
        JsonNode fields = mbqlQuery.get("fields");
        if (fields != null && fields.isArray() && fields.size() > 0) {
            List<String> projected = new ArrayList<>();
            for (JsonNode node : fields) {
                projected.add(parseFieldRefColumnName(node, fieldsById));
            }
            select = String.join(", ", projected);
        }

        String from = qualifyTable(table.getSchemaName(), table.getName());
        SqlFragment where = renderWhere(mbqlQuery.get("filter"), fieldsById);
        String orderBy = renderOrderBy(mbqlQuery.get("order-by"), fieldsById);

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

        String sql = "SELECT %s FROM %s%s%s LIMIT %d%s"
                .formatted(select, from, where.sql(), orderBy, limit, offset > 0 ? " OFFSET " + offset : "");
        return new TranslationResult(tableId, sql, where.bindings());
    }

    private static SqlFragment renderWhere(JsonNode filter, Map<Long, AnalyticsField> fieldsById) {
        if (filter == null || filter.isNull() || filter.isMissingNode()) {
            return SqlFragment.empty();
        }
        SqlFragment rendered = renderFilter(filter, fieldsById);
        if (rendered.sql().isBlank()) {
            return SqlFragment.empty();
        }
        return new SqlFragment(" WHERE " + rendered.sql(), rendered.bindings());
    }

    private static SqlFragment renderFilter(JsonNode filter, Map<Long, AnalyticsField> fieldsById) {
        if (filter == null || filter.isNull() || filter.isMissingNode()) {
            return SqlFragment.empty();
        }
        if (!filter.isArray() || filter.size() < 1) {
            throw new IllegalArgumentException("query.filter must be an array.");
        }

        String op = filter.get(0).asText("").toLowerCase();
        return switch (op) {
            case "and" -> renderLogical("AND", filter, fieldsById);
            case "or" -> renderLogical("OR", filter, fieldsById);
            case "not" -> renderNot(filter, fieldsById);
            case "=" -> renderComparison("=", filter, fieldsById);
            case "!=" -> renderComparison("<>", filter, fieldsById);
            case "<" -> renderComparison("<", filter, fieldsById);
            case ">" -> renderComparison(">", filter, fieldsById);
            case "<=" -> renderComparison("<=", filter, fieldsById);
            case ">=" -> renderComparison(">=", filter, fieldsById);
            case "is-null" -> renderNullCheck(filter, fieldsById, true);
            case "not-null" -> renderNullCheck(filter, fieldsById, false);
            case "between" -> renderBetween(filter, fieldsById);
            case "in" -> renderIn(filter, fieldsById);
            case "contains" -> renderLike(filter, fieldsById, "%", "%");
            case "starts-with" -> renderLike(filter, fieldsById, "", "%");
            case "ends-with" -> renderLike(filter, fieldsById, "%", "");
            case "is-empty" -> renderEmptyCheck(filter, fieldsById, true);
            case "not-empty" -> renderEmptyCheck(filter, fieldsById, false);
            default -> throw new IllegalArgumentException("Unsupported filter operator: " + op);
        };
    }

    private static SqlFragment renderLogical(String join, JsonNode filter, Map<Long, AnalyticsField> fieldsById) {
        if (filter.size() < 2) {
            throw new IllegalArgumentException("query.filter " + join.toLowerCase() + " requires at least one clause.");
        }
        List<String> parts = new ArrayList<>();
        List<Object> bindings = new ArrayList<>();
        for (int i = 1; i < filter.size(); i++) {
            SqlFragment child = renderFilter(filter.get(i), fieldsById);
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

    private static SqlFragment renderNot(JsonNode filter, Map<Long, AnalyticsField> fieldsById) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter not must be [\"not\", clause].");
        }
        SqlFragment child = renderFilter(filter.get(1), fieldsById);
        if (child.sql().isBlank()) {
            return SqlFragment.empty();
        }
        return new SqlFragment("NOT (" + child.sql() + ")", child.bindings());
    }

    private static SqlFragment renderComparison(String operator, JsonNode filter, Map<Long, AnalyticsField> fieldsById) {
        if (filter.size() != 3) {
            throw new IllegalArgumentException("query.filter comparison must be [\"" + operator + "\", field, value].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById);
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

    private static SqlFragment renderNullCheck(JsonNode filter, Map<Long, AnalyticsField> fieldsById, boolean isNull) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter null check must be [\"is-null\"|\"not-null\", field].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById);
        return new SqlFragment(column + (isNull ? " IS NULL" : " IS NOT NULL"), List.of());
    }

    private static SqlFragment renderBetween(JsonNode filter, Map<Long, AnalyticsField> fieldsById) {
        if (filter.size() != 4) {
            throw new IllegalArgumentException("query.filter between must be [\"between\", field, min, max].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById);
        Object min = jsonScalarToBinding(requireScalar(filter.get(2), "between min"));
        Object max = jsonScalarToBinding(requireScalar(filter.get(3), "between max"));
        return new SqlFragment(column + " BETWEEN ? AND ?", List.of(min, max));
    }

    private static SqlFragment renderIn(JsonNode filter, Map<Long, AnalyticsField> fieldsById) {
        if (filter.size() < 3) {
            throw new IllegalArgumentException("query.filter in must be [\"in\", field, values...].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById);

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

    private static SqlFragment renderLike(JsonNode filter, Map<Long, AnalyticsField> fieldsById, String prefix, String suffix) {
        if (filter.size() != 3) {
            throw new IllegalArgumentException("query.filter like must be [\"contains\"|\"starts-with\"|\"ends-with\", field, value].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById);
        JsonNode valueNode = requireScalar(filter.get(2), "like value");
        String value = valueNode.asText();
        return new SqlFragment(column + " LIKE ?", List.of(prefix + value + suffix));
    }

    private static SqlFragment renderEmptyCheck(JsonNode filter, Map<Long, AnalyticsField> fieldsById, boolean isEmpty) {
        if (filter.size() != 2) {
            throw new IllegalArgumentException("query.filter is-empty/not-empty must be [\"is-empty\"|\"not-empty\", field].");
        }
        String column = parseFieldRefColumnName(filter.get(1), fieldsById);
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

    private static String renderOrderBy(JsonNode orderBy, Map<Long, AnalyticsField> fieldsById) {
        if (orderBy == null || !orderBy.isArray() || orderBy.isEmpty()) {
            return "";
        }

        List<String> clauses = new ArrayList<>();
        for (JsonNode node : orderBy) {
            clauses.add(renderOrderByClause(node, fieldsById));
        }

        if (clauses.isEmpty()) {
            return "";
        }
        return " ORDER BY " + String.join(", ", clauses);
    }

    private static String renderOrderByClause(JsonNode node, Map<Long, AnalyticsField> fieldsById) {
        if (node == null || !node.isArray() || node.size() < 2) {
            throw new IllegalArgumentException("query.order-by must be a list of [direction field-ref] pairs.");
        }
        String direction = node.get(0).asText("");
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("query.order-by direction must be asc or desc.");
        }
        String column = parseFieldRefColumnName(node.get(1), fieldsById);
        return column + " " + direction.toUpperCase();
    }

    private static String parseFieldRefColumnName(JsonNode fieldRef, Map<Long, AnalyticsField> fieldsById) {
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
        return quoteIdentifier(field.getName());
    }

    private static String qualifyTable(String schema, String name) {
        String tableName = quoteIdentifier(name);
        if (schema == null || schema.isBlank()) {
            return tableName;
        }
        return quoteIdentifier(schema) + "." + tableName;
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Identifier cannot be null");
        }
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public record TranslationResult(long sourceTableId, String sql, List<Object> bindings) {}

    private record SqlFragment(String sql, List<Object> bindings) {

        static SqlFragment empty() {
            return new SqlFragment("", List.of());
        }
    }
}
