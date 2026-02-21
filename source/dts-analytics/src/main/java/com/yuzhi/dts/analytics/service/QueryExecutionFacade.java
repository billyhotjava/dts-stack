package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QueryExecutionFacade {
    private static final Logger log = LoggerFactory.getLogger(QueryExecutionFacade.class);
    private static final int MAX_SQL_LENGTH = 100_000;
    private static final int DEFAULT_NATIVE_AUTOFIX_RETRIES = 1;
    private static final Pattern TEMPLATE_TAG_PATTERN = Pattern.compile("\\{\\{\\s*[^}]+\\s*\\}\\}|\\$\\{\\s*[^}]+\\s*\\}");
    private static final Pattern BROKEN_COMMA_BEFORE_CLAUSE_PATTERN =
            Pattern.compile(
                    ",\\s*(from|where|group\\s+by|order\\s+by|having|limit|offset|union|join)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DUPLICATE_COMMA_PATTERN = Pattern.compile(",\\s*,+");
    private static final Pattern COMMA_BEFORE_CLOSE_PAREN_PATTERN = Pattern.compile(",\\s*\\)");
    private static final Pattern TRAILING_SQL_DELIMITER_PATTERN = Pattern.compile("\\s*[;；]+\\s*$");
    private static final Pattern TRAILING_COMMA_PATTERN = Pattern.compile(",\\s*$");
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

    @FunctionalInterface
    public interface ExecutionAttemptListener {
        void onAttempt(ExecutionAttempt attempt);
    }

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
            if (hasUnresolvedTemplateTag(sql)) {
                throw new IllegalArgumentException("Missing required SQL template parameters");
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
        NativeSqlSafety safety = checkNativeSqlSafety(sql);
        String normalized = safety.normalizedSql();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("SQL is empty after normalization");
        }
        if (safety.tooLong()) {
            throw new IllegalArgumentException("SQL is too long");
        }
        if (!safety.readOnlySql()) {
            throw new IllegalArgumentException("Only SELECT/WITH read-only SQL is allowed");
        }
        if (safety.multipleStatements()) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }
        if (safety.dangerousKeywordMatched()) {
            throw new IllegalArgumentException("Dangerous SQL statement is blocked");
        }
    }

    static NativeSqlSafety checkNativeSqlSafety(String sql) {
        String normalized = normalizeSql(sql);
        if (normalized.isBlank()) {
            return new NativeSqlSafety(normalized, false, false, false, false);
        }
        if (normalized.length() > MAX_SQL_LENGTH) {
            return new NativeSqlSafety(normalized, true, false, false, false);
        }
        if (!isReadOnlySql(normalized)) {
            return new NativeSqlSafety(normalized, false, false, false, false);
        }
        if (hasMultipleStatements(normalized)) {
            return new NativeSqlSafety(normalized, false, true, true, false);
        }
        for (String keyword : DANGEROUS_SQL_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return new NativeSqlSafety(normalized, false, true, false, true);
            }
        }
        return new NativeSqlSafety(normalized, false, true, false, false);
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

    private static boolean hasUnresolvedTemplateTag(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        return TEMPLATE_TAG_PATTERN.matcher(sql).find();
    }

    public DatasetQueryService.DatasetResult executeRaw(PreparedQuery prepared) throws SQLException {
        return datasetQueryService.runNative(
                prepared.databaseId(), prepared.sql(), prepared.constraints(), prepared.bindings());
    }

    public DatasetQueryService.DatasetResult executeWithCompliance(PreparedQuery prepared) throws SQLException {
        int retries = "native".equalsIgnoreCase(prepared.type()) ? DEFAULT_NATIVE_AUTOFIX_RETRIES : 0;
        return executeWithCompliance(prepared, retries, null);
    }

    public DatasetQueryService.DatasetResult executeWithCompliance(
            PreparedQuery prepared, ExecutionAttemptListener listener) throws SQLException {
        int retries = "native".equalsIgnoreCase(prepared.type()) ? DEFAULT_NATIVE_AUTOFIX_RETRIES : 0;
        return executeWithCompliance(prepared, retries, listener);
    }

    public ExecutionOutcome executeWithComplianceOutcome(PreparedQuery prepared) throws SQLException {
        List<ExecutionAttempt> attempts = new ArrayList<>();
        DatasetQueryService.DatasetResult result = executeWithCompliance(prepared, attempts::add);
        return new ExecutionOutcome(result, List.copyOf(attempts));
    }

    DatasetQueryService.DatasetResult executeWithCompliance(PreparedQuery prepared, int maxAutoFixRetries)
            throws SQLException {
        return executeWithCompliance(prepared, maxAutoFixRetries, null);
    }

    DatasetQueryService.DatasetResult executeWithCompliance(
            PreparedQuery prepared, int maxAutoFixRetries, ExecutionAttemptListener listener)
            throws SQLException {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared query is required");
        }
        int retries = Math.max(0, maxAutoFixRetries);
        PreparedQuery current = prepared;
        SQLException lastError = null;
        ExecutionAttemptListener safeListener = listener == null ? attempt -> {} : listener;
        boolean currentFromAutoFix = false;

        for (int attempt = 0; attempt <= retries; attempt++) {
            long attemptStartedNanos = System.nanoTime();
            try {
                DatasetQueryService.DatasetResult result = screenComplianceService.applyMasking(executeRaw(current));
                safeListener.onAttempt(new ExecutionAttempt(
                        attempt + 1,
                        current.sql(),
                        true,
                        currentFromAutoFix,
                        null,
                        null,
                        null,
                        false,
                        (System.nanoTime() - attemptStartedNanos) / 1_000_000));
                return result;
            } catch (SQLException ex) {
                lastError = ex;
                String errorMessage = rootCauseMessage(ex);
                String errorCategory = classifySqlErrorCategory(errorMessage);
                if (attempt >= retries) {
                    safeListener.onAttempt(new ExecutionAttempt(
                            attempt + 1,
                            current.sql(),
                            false,
                            currentFromAutoFix,
                            errorCategory,
                            errorMessage,
                            null,
                            false,
                            (System.nanoTime() - attemptStartedNanos) / 1_000_000));
                    throw ex;
                }
                PreparedQuery rewritten = tryAutoFixRetry(current, ex);
                safeListener.onAttempt(new ExecutionAttempt(
                        attempt + 1,
                        current.sql(),
                        false,
                        currentFromAutoFix,
                        errorCategory,
                        errorMessage,
                        rewritten == null ? null : rewritten.sql(),
                        rewritten != null,
                        (System.nanoTime() - attemptStartedNanos) / 1_000_000));
                if (rewritten == null) {
                    throw ex;
                }
                log.warn(
                        "[analytics] auto-fix retry {}/{} on db={}, reason={}, sql={}",
                        attempt + 1,
                        retries,
                        current.databaseId(),
                        rootCauseMessage(ex),
                        current.sql());
                current = rewritten;
                currentFromAutoFix = true;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new SQLException("Query execution failed");
    }

    public DatasetQueryService.DatasetResult applyCompliance(DatasetQueryService.DatasetResult result) {
        return screenComplianceService.applyMasking(result);
    }

    private PreparedQuery tryAutoFixRetry(PreparedQuery prepared, SQLException error) {
        if (prepared == null || !"native".equalsIgnoreCase(prepared.type())) {
            return null;
        }
        String sql = prepared.sql();
        if (sql == null || sql.isBlank()) {
            return null;
        }
        String message = rootCauseMessage(error).toLowerCase(Locale.ROOT);
        if (!isRetryableSqlSyntaxError(message)) {
            return null;
        }

        String rewrittenSql = normalizeSqlForAutoFix(sql);
        if (rewrittenSql.equals(sql)) {
            return null;
        }
        try {
            validateNativeSql(rewrittenSql);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return new PreparedQuery(
                prepared.databaseId(),
                prepared.type(),
                rewrittenSql,
                prepared.bindings(),
                prepared.mbql(),
                prepared.constraints());
    }

    private static boolean isRetryableSqlSyntaxError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        return errorMessage.contains("syntax error")
                || errorMessage.contains("parse error")
                || errorMessage.contains("mismatched input")
                || errorMessage.contains("at or near")
                || errorMessage.contains("unterminated");
    }

    private static String normalizeSqlForAutoFix(String sql) {
        String out = sql
                .replace('，', ',')
                .replace('；', ';')
                .replace('（', '(')
                .replace('）', ')');
        String previous;
        do {
            previous = out;
            out = BROKEN_COMMA_BEFORE_CLAUSE_PATTERN.matcher(out).replaceAll(" $1");
            out = DUPLICATE_COMMA_PATTERN.matcher(out).replaceAll(", ");
            out = COMMA_BEFORE_CLOSE_PAREN_PATTERN.matcher(out).replaceAll(")");
        } while (!out.equals(previous));
        out = TRAILING_COMMA_PATTERN.matcher(out).replaceAll("");
        out = TRAILING_SQL_DELIMITER_PATTERN.matcher(out).replaceAll("");
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    private static String rootCauseMessage(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message;
    }

    private static String classifySqlErrorCategory(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "runtime";
        }
        String message = errorMessage.toLowerCase(Locale.ROOT);
        if (message.contains("syntax error")
                || message.contains("parse error")
                || message.contains("mismatched input")
                || message.contains("at or near")
                || message.contains("unterminated")) {
            return "syntax";
        }
        if (message.contains("permission")
                || message.contains("not authorized")
                || message.contains("access denied")
                || message.contains("forbidden")) {
            return "permission";
        }
        if (message.contains("relation")
                || message.contains("column")
                || message.contains("table")
                || message.contains("schema")
                || message.contains("does not exist")
                || message.contains("unknown")) {
            return "schema";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "timeout";
        }
        if (message.contains("connection refused")
                || message.contains("connect")
                || message.contains("connection")
                || message.contains("unreachable")
                || message.contains("refused")) {
            return "connection";
        }
        return "runtime";
    }

    public record PreparedQuery(
            long databaseId,
            String type,
            String sql,
            List<Object> bindings,
            JsonNode mbql,
            DatasetQueryService.DatasetConstraints constraints) {}

    public record ExecutionAttempt(
            int attemptNo,
            String sql,
            boolean success,
            boolean fromAutoFix,
            String errorCategory,
            String errorMessage,
            String rewrittenSql,
            boolean retryPlanned,
            long durationMs) {}

    public record ExecutionOutcome(DatasetQueryService.DatasetResult result, List<ExecutionAttempt> attempts) {}

    record NativeSqlSafety(
            String normalizedSql,
            boolean tooLong,
            boolean readOnlySql,
            boolean multipleStatements,
            boolean dangerousKeywordMatched) {}
}
