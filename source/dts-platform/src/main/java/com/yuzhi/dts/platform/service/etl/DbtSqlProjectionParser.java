package com.yuzhi.dts.platform.service.etl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Shared conservative parser for dbt SELECT projections used by Catalog lineage writers. */
public final class DbtSqlProjectionParser {

    private DbtSqlProjectionParser() {}

    public static Map<String, String> selectExpressionsByAlias(String sql) {
        if (!StringUtils.hasText(sql)) {
            return Map.of();
        }
        String selectClause = extractTopLevelSelectClause(sql);
        if (!StringUtils.hasText(selectClause)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String expression : splitTopLevel(selectClause, ',')) {
            String trimmed = trimToNull(expression);
            if (trimmed == null) {
                continue;
            }
            String alias = resolveSelectAlias(trimmed);
            String key = normalizeColumnName(alias);
            if (StringUtils.hasText(key)) {
                result.putIfAbsent(key, trimmed);
            }
        }
        return result;
    }

    public static boolean expressionReferencesColumn(
        String expression,
        String columnName
    ) {
        return columnReference(expression, columnName).referenced();
    }

    public static ColumnReference columnReference(
        String expression,
        String columnName
    ) {
        String column = trimToNull(columnName);
        if (!StringUtils.hasText(expression) || column == null) {
            return new ColumnReference(Set.of(), false);
        }
        String source = stripTrailingAlias(expression).replaceAll("'(?:''|[^'])*'", " ");
        String quotedColumn = "([\"`\\[]?)" +
            Pattern.quote(column) +
            "([\"`\\]]?)";
        Pattern qualifiedPattern = Pattern.compile(
            "(?i)([\"`\\[]?[A-Za-z_][A-Za-z0-9_]*[\"`\\]]?)\\s*\\.\\s*" + quotedColumn
        );
        Matcher qualified = qualifiedPattern.matcher(source);
        Set<String> qualifiers = new LinkedHashSet<>();
        StringBuffer withoutQualified = new StringBuffer();
        while (qualified.find()) {
            qualifiers.add(normalizeColumnName(unquoteIdentifier(qualified.group(1))));
            qualified.appendReplacement(withoutQualified, " ");
        }
        qualified.appendTail(withoutQualified);
        boolean unqualified = Pattern
            .compile(
                "(?i)(^|[^A-Za-z0-9_\\.])" + quotedColumn + "([^A-Za-z0-9_]|$)"
            )
            .matcher(withoutQualified)
            .find();
        return new ColumnReference(qualifiers, unqualified);
    }

    public static Map<String, String> relationAliases(String sql) {
        if (!StringUtils.hasText(sql)) return Map.of();
        String identifier = "[\"`\\[]?[A-Za-z_][A-Za-z0-9_$]*[\"`\\]]?";
        String relation = identifier + "(?:\\s*\\.\\s*" + identifier + "){0,2}";
        Matcher matcher = Pattern
            .compile(
                "(?is)\\b(?:from|join)\\s+(" + relation + ")(?:\\s+(?:as\\s+)?(" + identifier + "))?"
            )
            .matcher(stripSqlComments(sql));
        Map<String, String> result = new LinkedHashMap<>();
        while (matcher.find()) {
            String relationName = relationLeaf(matcher.group(1));
            String alias = normalizeColumnName(unquoteIdentifier(matcher.group(2)));
            if (!StringUtils.hasText(alias) || isSqlKeyword(alias)) alias = relationName;
            if (StringUtils.hasText(relationName)) {
                result.putIfAbsent(relationName, relationName);
                if (StringUtils.hasText(alias)) result.putIfAbsent(alias, relationName);
            }
        }
        return Map.copyOf(result);
    }

    private static String extractTopLevelSelectClause(String sql) {
        String normalized = stripSqlComments(sql);
        int selectStart = findTopLevelKeyword(normalized, "select", 0);
        if (selectStart < 0) {
            return null;
        }
        int fromStart = findTopLevelKeyword(
            normalized,
            "from",
            selectStart + "select".length()
        );
        if (fromStart < 0 || fromStart <= selectStart) {
            return null;
        }
        return normalized.substring(selectStart + "select".length(), fromStart);
    }

    private static int findTopLevelKeyword(
        String sql,
        String keyword,
        int startIndex
    ) {
        if (!StringUtils.hasText(sql) || !StringUtils.hasText(keyword)) {
            return -1;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int depth = 0;
        char quote = 0;
        for (
            int index = Math.max(0, startIndex);
            index <= sql.length() - keyword.length();
            index++
        ) {
            char ch = sql.charAt(index);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
                continue;
            }
            if (ch == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth != 0) {
                continue;
            }
            if (
                sql.regionMatches(
                    true,
                    index,
                    lowerKeyword,
                    0,
                    lowerKeyword.length()
                ) && isKeywordBoundary(sql, index, lowerKeyword.length())
            ) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isKeywordBoundary(
        String text,
        int start,
        int length
    ) {
        char before = start > 0 ? text.charAt(start - 1) : ' ';
        char after = start + length < text.length()
            ? text.charAt(start + length)
            : ' ';
        return !isIdentifierChar(before) && !isIdentifierChar(after);
    }

    private static List<String> splitTopLevel(String text, char delimiter) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
                continue;
            }
            if (ch == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (ch == delimiter && depth == 0) {
                parts.add(text.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static String resolveSelectAlias(String expression) {
        String trimmed = trimToNull(expression);
        if (trimmed == null) {
            return null;
        }
        Matcher asMatcher = Pattern
            .compile(
                "(?is)\\s+as\\s+([\"`\\[]?[A-Za-z_][A-Za-z0-9_]*[\"`\\]]?)\\s*$"
            )
            .matcher(trimmed);
        if (asMatcher.find()) {
            return unquoteIdentifier(asMatcher.group(1));
        }
        List<String> tokens = splitTopLevel(trimmed, ' ');
        for (int index = tokens.size() - 1; index >= 0; index--) {
            String token = trimToNull(tokens.get(index));
            if (token == null) {
                continue;
            }
            String alias = unquoteIdentifier(token);
            if (
                alias != null &&
                alias.matches("[A-Za-z_][A-Za-z0-9_]*") &&
                !isSqlKeyword(alias)
            ) {
                return alias;
            }
            break;
        }
        String simpleColumn = trimmed.replace("\"", "").replace("`", "");
        int dot = simpleColumn.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < simpleColumn.length()) {
            simpleColumn = simpleColumn.substring(dot + 1);
        }
        simpleColumn = trimToNull(simpleColumn);
        return simpleColumn != null &&
            simpleColumn.matches("[A-Za-z_][A-Za-z0-9_]*")
            ? simpleColumn
            : null;
    }

    private static String stripTrailingAlias(String expression) {
        return expression.replaceFirst(
            "(?is)\\s+as\\s+[\"`\\[]?[A-Za-z_][A-Za-z0-9_]*[\"`\\]]?\\s*$",
            ""
        );
    }

    private static String relationLeaf(String relation) {
        String value = trimToNull(relation);
        if (value == null) return null;
        List<String> parts = List.of(value.split("\\s*\\.\\s*"));
        return normalizeColumnName(unquoteIdentifier(parts.getLast()));
    }

    private static String stripSqlComments(String sql) {
        if (!StringUtils.hasText(sql)) {
            return sql;
        }
        return sql
            .replaceAll("(?m)--.*?$", " ")
            .replaceAll("(?s)/\\*.*?\\*/", " ");
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeColumnName(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String unquoteIdentifier(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if (
            (text.startsWith("\"") && text.endsWith("\"")) ||
            (text.startsWith("`") && text.endsWith("`"))
        ) {
            return text.substring(1, text.length() - 1);
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static boolean isIdentifierChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static boolean isSqlKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "AS", "CASE", "WHEN", "THEN", "ELSE", "END", "NULL", "TRUE", "FALSE", "FROM", "JOIN", "ON", "WHERE", "GROUP", "ORDER", "HAVING", "LIMIT", "UNION", "LEFT", "RIGHT", "FULL", "INNER", "OUTER", "CROSS" -> true;
            default -> false;
        };
    }

    public record ColumnReference(Set<String> qualifiers, boolean unqualified) {
        public ColumnReference {
            qualifiers = Set.copyOf(qualifiers == null ? Set.of() : qualifiers);
        }

        public boolean referenced() {
            return unqualified || !qualifiers.isEmpty();
        }
    }
}
