package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.domain.AnalyticsField;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
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
            "filter",
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

        String sql = "SELECT %s FROM %s%s LIMIT %d%s"
                .formatted(select, from, orderBy, limit, offset > 0 ? " OFFSET " + offset : "");
        return new TranslationResult(tableId, sql);
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

    public record TranslationResult(long sourceTableId, String sql) {}
}
