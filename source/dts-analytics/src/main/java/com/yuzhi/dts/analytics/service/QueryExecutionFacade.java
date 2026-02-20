package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class QueryExecutionFacade {
    private static final int MAX_SQL_LENGTH = 100_000;
    private static final List<String> DANGEROUS_SQL_KEYWORDS = List.of(
            " insert ",
            " update ",
            " delete ",
            " drop ",
            " truncate ",
            " alter ",
            " create ",
            " merge ",
            " grant ",
            " revoke ",
            " call ",
            " execute ");

    private final DatasetQueryService datasetQueryService;
    private final MbqlToSqlService mbqlToSqlService;
    private final NativeQueryTemplateService nativeQueryTemplateService;
    private final ScreenComplianceService screenComplianceService;

    public QueryExecutionFacade(
            DatasetQueryService datasetQueryService,
            MbqlToSqlService mbqlToSqlService,
            NativeQueryTemplateService nativeQueryTemplateService,
            ScreenComplianceService screenComplianceService) {
        this.datasetQueryService = datasetQueryService;
        this.mbqlToSqlService = mbqlToSqlService;
        this.nativeQueryTemplateService = nativeQueryTemplateService;
        this.screenComplianceService = screenComplianceService;
    }

    public PreparedQuery prepare(
            JsonNode datasetQuery,
            JsonNode requestBody,
            JsonNode mbqlOverride,
            DatasetQueryService.DatasetConstraints constraints) {
        if (datasetQuery == null || !datasetQuery.isObject()) {
            throw new IllegalArgumentException("Invalid saved dataset_query");
        }

        DatasetQueryService.DatasetConstraints safeConstraints =
                constraints == null ? DatasetQueryService.DatasetConstraints.defaults() : constraints;

        String type = datasetQuery.path("type").asText(null);
        if (type == null || type.isBlank()) {
            if (datasetQuery.has("native")) {
                type = "native";
            } else if (datasetQuery.has("query")) {
                type = "query";
            }
        }
        long databaseId = datasetQuery.path("database").asLong(0);
        if (databaseId <= 0) {
            throw new IllegalArgumentException("dataset_query.database is required");
        }

        if ("native".equalsIgnoreCase(type)) {
            String sql = datasetQuery.path("native").path("query").asText(null);
            if (sql == null || sql.isBlank()) {
                throw new IllegalArgumentException("dataset_query.native.query is required");
            }

            List<Object> bindings = List.of();
            JsonNode parametersNode = requestBody == null ? null : requestBody.get("parameters");
            if (parametersNode != null && !parametersNode.isNull() && !parametersNode.isMissingNode()) {
                nativeQueryTemplateService.validateParameterWhitelist(sql, parametersNode);
                if (sql.contains("{{") || sql.contains("${")) {
                    NativeQueryTemplateService.RenderedQuery rendered =
                            nativeQueryTemplateService.render(sql, parametersNode);
                    sql = rendered.sql();
                    bindings = rendered.bindings();
                }
            }
            validateNativeSql(sql);
            return new PreparedQuery(databaseId, "native", sql, bindings, null, safeConstraints);
        }

        if ("query".equalsIgnoreCase(type)) {
            JsonNode mbql = mbqlOverride != null ? mbqlOverride : datasetQuery.get("query");
            MbqlToSqlService.TranslationResult translated =
                    mbqlToSqlService.translateSelect(databaseId, mbql, safeConstraints);
            return new PreparedQuery(
                    databaseId,
                    "query",
                    translated.sql(),
                    translated.bindings(),
                    mbql,
                    safeConstraints);
        }

        throw new IllegalArgumentException("Only native and query (MBQL) queries are supported");
    }

    private static void validateNativeSql(String sql) {
        String normalized = normalizeSql(sql);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("SQL is empty after normalization");
        }
        if (normalized.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException("SQL is too long");
        }
        if (!isReadOnlySql(normalized)) {
            throw new IllegalArgumentException("Only SELECT/WITH read-only SQL is allowed");
        }
        if (hasMultipleStatements(normalized)) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }
        for (String keyword : DANGEROUS_SQL_KEYWORDS) {
            if (normalized.contains(keyword)) {
                throw new IllegalArgumentException("Dangerous SQL statement is blocked");
            }
        }
    }

    private static String normalizeSql(String sql) {
        String normalized = stripSqlLiteralsAndComments(sql).trim().toLowerCase(Locale.ROOT);
        // Normalize whitespace and keep boundary spaces for safer keyword contains checks.
        normalized = normalized.replaceAll("\\s+", " ");
        return " " + normalized + " ";
    }

    /**
     * Remove SQL comments and string/identifier literals before security keyword checks.
     * This avoids false positives such as "drop" inside a text literal.
     */
    private static String stripSqlLiteralsAndComments(String sql) {
        String text = sql == null ? "" : sql;
        StringBuilder out = new StringBuilder(text.length());
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                    out.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i += 1;
                    out.append(' ');
                }
                continue;
            }
            if (inSingleQuote) {
                if (ch == '\'' && next == '\'') {
                    i += 1;
                    continue;
                }
                if (ch == '\'') {
                    inSingleQuote = false;
                    out.append(' ');
                }
                continue;
            }
            if (inDoubleQuote) {
                if (ch == '"' && next == '"') {
                    i += 1;
                    continue;
                }
                if (ch == '"') {
                    inDoubleQuote = false;
                    out.append(' ');
                }
                continue;
            }

            if (ch == '-' && next == '-') {
                inLineComment = true;
                i += 1;
                out.append(' ');
                continue;
            }
            if (ch == '/' && next == '*') {
                inBlockComment = true;
                i += 1;
                out.append(' ');
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = true;
                out.append(' ');
                continue;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                out.append(' ');
                continue;
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static boolean isReadOnlySql(String normalized) {
        // normalized has boundary spaces from normalizeSql.
        return normalized.startsWith(" select ") || normalized.startsWith(" with ");
    }

    private static boolean hasMultipleStatements(String normalized) {
        // Allow at most one trailing semicolon.
        int first = normalized.indexOf(';');
        if (first < 0) {
            return false;
        }
        int last = normalized.lastIndexOf(';');
        if (first != last) {
            return true;
        }
        String tail = normalized.substring(first + 1).trim();
        return !tail.isEmpty();
    }

    public DatasetQueryService.DatasetResult executeRaw(PreparedQuery prepared) throws SQLException {
        return datasetQueryService.runNative(
                prepared.databaseId(), prepared.sql(), prepared.constraints(), prepared.bindings());
    }

    public DatasetQueryService.DatasetResult executeWithCompliance(PreparedQuery prepared) throws SQLException {
        return screenComplianceService.applyMasking(executeRaw(prepared));
    }

    public DatasetQueryService.DatasetResult applyCompliance(DatasetQueryService.DatasetResult result) {
        return screenComplianceService.applyMasking(result);
    }

    public record PreparedQuery(
            long databaseId,
            String type,
            String sql,
            List<Object> bindings,
            JsonNode mbql,
            DatasetQueryService.DatasetConstraints constraints) {}
}
