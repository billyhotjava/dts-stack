package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String SQL_IDENTIFIER =
            "(?:[A-Za-z_][A-Za-z0-9_$]*|\"(?:[^\"]|\"\")+\")"
                    + "(?:\\s*\\.\\s*(?:[A-Za-z_][A-Za-z0-9_$]*|\"(?:[^\"]|\"\")+\"))*";
    private static final Pattern POSTGRES_TO_DATE_SIMPLE_IDENTIFIER_PATTERN = Pattern.compile(
            "(?i)\\bto_date\\s*\\(\\s*(" + SQL_IDENTIFIER + ")\\s*,\\s*'(?:[^']|'')*'\\s*\\)");
    private static final Pattern POSTGRES_TO_CHAR_DATE_FORMAT_PATTERN = Pattern.compile(
            "(?i)\\bto_char\\s*\\(\\s*([^,]+?)\\s*,\\s*'(YYYY-MM-DD|YYYY-MM)'\\s*\\)");
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
    private final FederatedNativeSqlQualifier federatedNativeSqlQualifier;
    private final FinanceCaliberGuardrail financeCaliberGuardrail;

    @FunctionalInterface
    public interface ExecutionAttemptListener {
        void onAttempt(ExecutionAttempt attempt);
    }

    public QueryExecutionFacade(
            DatasetQueryService datasetQueryService,
            MbqlToSqlService mbqlToSqlService,
            NativeQueryTemplateService nativeQueryTemplateService,
            ScreenComplianceService screenComplianceService) {
        this(
                datasetQueryService,
                mbqlToSqlService,
                nativeQueryTemplateService,
                screenComplianceService,
                FederatedNativeSqlQualifier.noop());
    }

    @Autowired
    public QueryExecutionFacade(
            DatasetQueryService datasetQueryService,
            MbqlToSqlService mbqlToSqlService,
            NativeQueryTemplateService nativeQueryTemplateService,
            ScreenComplianceService screenComplianceService,
            FederatedNativeSqlQualifier federatedNativeSqlQualifier) {
        this(
                datasetQueryService,
                mbqlToSqlService,
                nativeQueryTemplateService,
                screenComplianceService,
                federatedNativeSqlQualifier,
                new FinanceCaliberGuardrail());
    }

    private QueryExecutionFacade(
            DatasetQueryService datasetQueryService,
            MbqlToSqlService mbqlToSqlService,
            NativeQueryTemplateService nativeQueryTemplateService,
            ScreenComplianceService screenComplianceService,
            FederatedNativeSqlQualifier federatedNativeSqlQualifier,
            FinanceCaliberGuardrail financeCaliberGuardrail) {
        this.datasetQueryService = datasetQueryService;
        this.mbqlToSqlService = mbqlToSqlService;
        this.nativeQueryTemplateService = nativeQueryTemplateService;
        this.screenComplianceService = screenComplianceService;
        this.federatedNativeSqlQualifier =
                federatedNativeSqlQualifier == null ? FederatedNativeSqlQualifier.noop() : federatedNativeSqlQualifier;
        this.financeCaliberGuardrail =
                financeCaliberGuardrail == null ? new FinanceCaliberGuardrail() : financeCaliberGuardrail;
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
            sql = federatedNativeSqlQualifier.qualify(databaseId, sql);
            validateNativeSql(sql);
            financeCaliberGuardrail.assertAllowed(sql);
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
        int retries =
                prepared != null && "native".equalsIgnoreCase(prepared.type()) ? DEFAULT_NATIVE_AUTOFIX_RETRIES : 0;
        PreparedQuery current = prepared;
        SQLException lastError = null;

        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return executeRawOnce(current);
            } catch (SQLException ex) {
                lastError = ex;
                if (attempt >= retries) {
                    throw ex;
                }
                PreparedQuery rewritten = tryAutoFixRetry(current, ex);
                if (rewritten == null) {
                    throw ex;
                }
                log.warn(
                        "[analytics] raw auto-fix retry {}/{} on db={}, reason={}, sql={}",
                        attempt + 1,
                        retries,
                        current.databaseId(),
                        rootCauseMessage(ex),
                        current.sql());
                current = rewritten;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new SQLException("Query execution failed");
    }

    private DatasetQueryService.DatasetResult executeRawOnce(PreparedQuery prepared) throws SQLException {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared query is required");
        }
        if ("native".equalsIgnoreCase(prepared.type())) {
            validateNativeSql(prepared.sql());
        }
        financeCaliberGuardrail.assertAllowed(prepared.sql());
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
                DatasetQueryService.DatasetResult result = screenComplianceService.applyMasking(executeRawOnce(current));
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
        if (!isRetryableSqlAutoFixError(message)) {
            return null;
        }

        String rewrittenSql = normalizeSqlForAutoFix(
                sql,
                isPostgresCastSyntaxError(message),
                isTrinoToCharDateFormatError(message));
        if (rewrittenSql.equals(sql)) {
            return null;
        }
        try {
            validateNativeSql(rewrittenSql);
            financeCaliberGuardrail.assertAllowed(rewrittenSql);
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

    private static boolean isRetryableSqlAutoFixError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        return isRetryableSqlSyntaxError(errorMessage)
                || isPostgresToDateDateArgumentError(errorMessage)
                || isTrinoToCharDateFormatError(errorMessage);
    }

    private static boolean isRetryableSqlSyntaxError(String errorMessage) {
        return errorMessage.contains("syntax error")
                || errorMessage.contains("parse error")
                || errorMessage.contains("mismatched input")
                || errorMessage.contains("at or near")
                || errorMessage.contains("unterminated");
    }

    private static boolean isPostgresToDateDateArgumentError(String errorMessage) {
        return errorMessage.contains("function to_date(date, unknown) does not exist")
                || errorMessage.contains("function to_date(date, text) does not exist");
    }

    private static String normalizeSqlForAutoFix(String sql) {
        return normalizeSqlForAutoFix(sql, false);
    }

    private static boolean isPostgresCastSyntaxError(String errorMessage) {
        return errorMessage.contains("mismatched input ':'")
                || errorMessage.contains("at or near \":\"")
                || errorMessage.contains("syntax error at or near ':'")
                || errorMessage.contains("syntax error at or near \":\"");
    }

    private static String normalizeSqlForAutoFix(String sql, boolean rewritePostgresCasts) {
        return normalizeSqlForAutoFix(sql, rewritePostgresCasts, false);
    }

    private static boolean isTrinoToCharDateFormatError(String errorMessage) {
        return errorMessage.contains("failed to tokenize string [y]")
                || (errorMessage.contains("to_char") && errorMessage.contains("yyyy"));
    }

    private static String normalizeSqlForAutoFix(
            String sql,
            boolean rewritePostgresCasts,
            boolean rewritePostgresToCharDateFormats) {
        String out = sql
                .replace('，', ',')
                .replace('；', ';')
                .replace('（', '(')
                .replace('）', ')');
        out = rewritePostgresToDateDateArgument(out);
        if (rewritePostgresCasts) {
            out = rewritePostgresCastSyntax(out);
            out = rewriteVarcharNumericAggregates(out);
        }
        if (rewritePostgresToCharDateFormats) {
            out = rewritePostgresToCharDateFormats(out);
        }
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

    private static String rewritePostgresToDateDateArgument(String sql) {
        Matcher matcher = POSTGRES_TO_DATE_SIMPLE_IDENTIFIER_PATTERN.matcher(sql);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "::date"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String rewritePostgresToCharDateFormats(String sql) {
        Matcher matcher = POSTGRES_TO_CHAR_DATE_FORMAT_PATTERN.matcher(sql);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String postgresFormat = matcher.group(2).toUpperCase(Locale.ROOT);
            String trinoFormat = "YYYY-MM-DD".equals(postgresFormat) ? "%Y-%m-%d" : "%Y-%m";
            matcher.appendReplacement(
                    out,
                    Matcher.quoteReplacement(
                            "date_format(CAST(" + expression + " AS timestamp), '" + trinoFormat + "')"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String rewriteVarcharNumericAggregates(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length() + 64);
        int cursor = 0;
        while (cursor < sql.length()) {
            int aggregateStart = findNextNumericAggregate(sql, cursor);
            if (aggregateStart < 0) {
                out.append(sql.substring(cursor));
                break;
            }
            int nameEnd = skipAggregateName(sql, aggregateStart);
            int open = skipWhitespaceRight(sql, nameEnd);
            int close = findMatchingCloseParen(sql, open);
            if (open >= sql.length() || sql.charAt(open) != '(' || close < 0) {
                out.append(sql, cursor, aggregateStart + 1);
                cursor = aggregateStart + 1;
                continue;
            }
            String aggregateName = sql.substring(aggregateStart, nameEnd).toUpperCase(Locale.ROOT);
            String argument = sql.substring(open + 1, close).trim();
            out.append(sql, cursor, aggregateStart);
            if (shouldWrapNumericAggregateArgument(argument)) {
                out.append(aggregateName)
                        .append("(TRY_CAST(")
                        .append(argument)
                        .append(" AS DOUBLE))");
            } else {
                out.append(sql, aggregateStart, close + 1);
            }
            cursor = close + 1;
        }
        return out.toString();
    }

    private static int findNextNumericAggregate(String sql, int start) {
        for (int i = Math.max(start, 0); i < sql.length(); i++) {
            if (matchesAggregateName(sql, i, "sum") || matchesAggregateName(sql, i, "avg")) {
                int nameEnd = skipAggregateName(sql, i);
                int open = skipWhitespaceRight(sql, nameEnd);
                if (open < sql.length() && sql.charAt(open) == '(') {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean matchesAggregateName(String sql, int index, String name) {
        int end = index + name.length();
        if (end > sql.length() || !sql.regionMatches(true, index, name, 0, name.length())) {
            return false;
        }
        if (index > 0 && isIdentifierPart(sql.charAt(index - 1))) {
            return false;
        }
        return end >= sql.length() || !isIdentifierPart(sql.charAt(end));
    }

    private static int skipAggregateName(String sql, int start) {
        int i = start;
        while (i < sql.length() && isIdentifierPart(sql.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean shouldWrapNumericAggregateArgument(String argument) {
        if (argument == null || argument.isBlank()) {
            return false;
        }
        String normalized = argument.trim().toLowerCase(Locale.ROOT);
        return !("*".equals(normalized)
                || normalized.startsWith("try_cast(")
                || normalized.startsWith("cast("));
    }

    private static String rewritePostgresCastSyntax(String sql) {
        if (sql == null || sql.isBlank() || !sql.contains("::")) {
            return sql;
        }
        String current = sql;
        int searchFrom = 0;
        while (searchFrom < current.length()) {
            int castIndex = findNextPostgresCast(current, searchFrom);
            if (castIndex < 0) {
                break;
            }
            int exprStart = findCastExpressionStart(current, castIndex);
            int typeStart = skipWhitespaceRight(current, castIndex + 2);
            int typeEnd = findCastTypeEnd(current, typeStart);
            if (exprStart < 0 || typeEnd <= typeStart) {
                searchFrom = castIndex + 2;
                continue;
            }
            String expression = current.substring(exprStart, castIndex).trim();
            String targetType = normalizePostgresCastTargetType(current.substring(typeStart, typeEnd).trim());
            if (expression.isBlank() || targetType.isBlank()) {
                searchFrom = castIndex + 2;
                continue;
            }
            String replacement = rewriteNumericAggregateCast(expression, targetType);
            if (replacement == null) {
                replacement = "CAST(" + expression + " AS " + targetType + ")";
            }
            current = current.substring(0, exprStart) + replacement + current.substring(typeEnd);
            searchFrom = exprStart + replacement.length();
        }
        return current;
    }

    private static String rewriteNumericAggregateCast(String expression, String targetType) {
        if (!"DOUBLE".equalsIgnoreCase(targetType) || expression == null) {
            return null;
        }
        String trimmed = expression.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String aggregate;
        if (lower.startsWith("sum(") && trimmed.endsWith(")")) {
            aggregate = "SUM";
        } else if (lower.startsWith("avg(") && trimmed.endsWith(")")) {
            aggregate = "AVG";
        } else {
            return null;
        }
        int open = trimmed.indexOf('(');
        int close = findMatchingCloseParen(trimmed, open);
        if (close != trimmed.length() - 1) {
            return null;
        }
        String argument = trimmed.substring(open + 1, close).trim();
        if (argument.isBlank()) {
            return null;
        }
        return aggregate + "(TRY_CAST(" + argument + " AS DOUBLE))";
    }

    private static String normalizePostgresCastTargetType(String rawType) {
        if (rawType == null) {
            return "";
        }
        String type = rawType.trim();
        String lower = type.toLowerCase(Locale.ROOT);
        if ("numeric".equals(lower)) {
            return "DOUBLE";
        }
        if (lower.startsWith("numeric(")) {
            return "DECIMAL" + type.substring("numeric".length());
        }
        return type;
    }

    private static int findNextPostgresCast(String sql, int start) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = Math.max(start, 0); i + 1 < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = sql.charAt(i + 1);
            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingleQuote) {
                if (ch == '\'' && next == '\'') {
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (ch == '"' && next == '"') {
                    i++;
                    continue;
                }
                if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (ch == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (ch == ':' && next == ':') {
                return i;
            }
        }
        return -1;
    }

    private static int findCastExpressionStart(String sql, int castIndex) {
        int end = skipWhitespaceLeft(sql, castIndex - 1);
        if (end < 0) {
            return -1;
        }
        char ch = sql.charAt(end);
        if (ch == '?') {
            return end;
        }
        if (ch == ')') {
            int open = findMatchingOpenParen(sql, end);
            if (open < 0) {
                return -1;
            }
            return includeFunctionName(sql, open);
        }
        if (ch == '"') {
            int quoteStart = findQuotedIdentifierStart(sql, end);
            return quoteStart < 0 ? -1 : includeIdentifierQualifier(sql, quoteStart);
        }
        if (isBareIdentifierChar(ch)) {
            int start = end;
            while (start > 0 && isBareIdentifierChar(sql.charAt(start - 1))) {
                start--;
            }
            return start;
        }
        return -1;
    }

    private static int findCastTypeEnd(String sql, int start) {
        if (start < 0 || start >= sql.length() || !isIdentifierStart(sql.charAt(start))) {
            return -1;
        }
        int end = start + 1;
        while (end < sql.length() && isIdentifierPart(sql.charAt(end))) {
            end++;
        }
        int next = skipWhitespaceRight(sql, end);
        if (next < sql.length() && sql.charAt(next) == '(') {
            int close = findMatchingCloseParen(sql, next);
            if (close > next) {
                end = close + 1;
            }
        }
        return end;
    }

    private static int includeFunctionName(String sql, int openParen) {
        int end = skipWhitespaceLeft(sql, openParen - 1);
        if (end < 0 || !isIdentifierPart(sql.charAt(end))) {
            return openParen;
        }
        int start = end;
        while (start > 0 && isIdentifierPart(sql.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    private static int includeIdentifierQualifier(String sql, int identifierStart) {
        int start = identifierStart;
        while (true) {
            int dot = skipWhitespaceLeft(sql, start - 1);
            if (dot < 0 || sql.charAt(dot) != '.') {
                return start;
            }
            int leftEnd = skipWhitespaceLeft(sql, dot - 1);
            if (leftEnd < 0) {
                return start;
            }
            int leftStart;
            if (sql.charAt(leftEnd) == '"') {
                leftStart = findQuotedIdentifierStart(sql, leftEnd);
            } else if (isIdentifierPart(sql.charAt(leftEnd))) {
                leftStart = leftEnd;
                while (leftStart > 0 && isIdentifierPart(sql.charAt(leftStart - 1))) {
                    leftStart--;
                }
            } else {
                return start;
            }
            if (leftStart < 0) {
                return start;
            }
            start = leftStart;
        }
    }

    private static int findQuotedIdentifierStart(String sql, int quoteEnd) {
        for (int i = quoteEnd - 1; i >= 0; i--) {
            if (sql.charAt(i) == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingOpenParen(String sql, int closeParen) {
        int depth = 0;
        for (int i = closeParen; i >= 0; i--) {
            char ch = sql.charAt(i);
            if (ch == ')') {
                depth++;
            } else if (ch == '(') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingCloseParen(String sql, int openParen) {
        int depth = 0;
        for (int i = openParen; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespaceLeft(String sql, int index) {
        int i = index;
        while (i >= 0 && Character.isWhitespace(sql.charAt(i))) {
            i--;
        }
        return i;
    }

    private static int skipWhitespaceRight(String sql, int index) {
        int i = Math.max(index, 0);
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isBareIdentifierChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '.';
    }

    private static boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
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
