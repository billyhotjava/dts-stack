package com.yuzhi.dts.platform.service.catalog.lineage;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SqlTableReferenceExtractor {

    /**
     * Extremely lightweight table reference extractor for lineage discovery.
     * <p>
     * Goals:
     * - Work across common dialects with limited dependencies.
     * - Handle quoted identifiers such as "SCHEMA"."TABLE" / `schema`.`table` / [schema].[table].
     * - Ignore string literals and comments.
     * - Avoid treating CTE names (WITH ...) as physical tables.
     * <p>
     * Non-goals:
     * - Full SQL parsing (CTE nesting, complex joins, MERGE, etc.).
    */
    private static final String IDENT_PART = "(?:`[^`]+`|\"[^\"]+\"|\\[[^\\]]+\\]|[\\w$@]+)";
    private static final Pattern IDENT_PART_PATTERN = Pattern.compile(IDENT_PART);
    private static final Pattern TABLE_REF_PATTERN = Pattern.compile(
        "(?i)\\b(from|join|update|into)\\s+(" + IDENT_PART + "(?:\\s*\\.\\s*" + IDENT_PART + "){0,2})"
    );

    public Set<TableRef> extract(String sql) {
        if (!StringUtils.hasText(sql)) {
            return Set.of();
        }
        String normalized = stripCommentsAndStrings(sql);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        Set<String> cteNames = extractTopLevelCteNames(normalized);
        Matcher matcher = TABLE_REF_PATTERN.matcher(normalized);
        Set<TableRef> refs = new LinkedHashSet<>();
        while (matcher.find()) {
            String raw = matcher.group(2);
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String token = raw.trim();
            if (token.startsWith("(")) {
                continue;
            }
            token = token.replaceAll("[,;]$", "");
            TableRef ref = parseTableRef(token);
            if (ref == null) {
                continue;
            }
            if (ref.schema() == null && cteNames.contains(ref.normalizedTable())) {
                continue;
            }
            refs.add(ref);
        }
        return refs;
    }

    private TableRef parseTableRef(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalizedToken = token.trim();
        if (normalizedToken.isEmpty()) {
            return null;
        }
        // Split by '.' between identifier parts (pattern guarantees dots are separators, not part of quoted token).
        String[] parts = normalizedToken.split("\\s*\\.\\s*");
        if (parts.length == 0) {
            return null;
        }
        String table = stripIdentifierQuotes(parts[parts.length - 1]);
        if (!StringUtils.hasText(table)) {
            return null;
        }
        String schema = null;
        if (parts.length >= 2) {
            schema = stripIdentifierQuotes(parts[parts.length - 2]);
        }
        if (schema != null && schema.isBlank()) {
            schema = null;
        }
        return new TableRef(schema, table);
    }

    private String stripCommentsAndStrings(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    out.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (!inSingleQuote) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '\'') {
                if (inSingleQuote) {
                    if (next == '\'') {
                        // escaped ''
                        i++;
                        continue;
                    }
                    inSingleQuote = false;
                } else {
                    inSingleQuote = true;
                }
                out.append(' ');
                continue;
            }

            if (inSingleQuote) {
                out.append(' ');
                continue;
            }

            out.append(c);
        }
        return out.toString();
    }

    private String stripIdentifierQuotes(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ((trimmed.startsWith("`") && trimmed.endsWith("`")) ||
            (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        int at = trimmed.indexOf('@');
        if (at > 0) {
            trimmed = trimmed.substring(0, at);
        }
        String out = trimmed.trim();
        if (out.isEmpty()) {
            return null;
        }
        if (
            "select".equalsIgnoreCase(out) ||
            "values".equalsIgnoreCase(out) ||
            "lateral".equalsIgnoreCase(out)
        ) {
            return null;
        }
        return out;
    }

    private Set<String> extractTopLevelCteNames(String sql) {
        if (!StringUtils.hasText(sql)) {
            return Set.of();
        }
        int withPos = findTopLevelWith(sql);
        if (withPos < 0) {
            return Set.of();
        }
        int pos = withPos + 4; // "with"
        pos = skipWs(sql, pos);
        if (regionMatchesWord(sql, pos, "recursive")) {
            pos = skipWs(sql, pos + "recursive".length());
        }

        Set<String> names = new LinkedHashSet<>();
        while (pos < sql.length()) {
            pos = skipWsAndCommas(sql, pos);
            IdentifierToken id = readIdentifier(sql, pos);
            if (id == null) {
                break;
            }
            String name = stripIdentifierQuotes(id.token());
            if (StringUtils.hasText(name)) {
                names.add(name.trim().toLowerCase(Locale.ROOT));
            }
            pos = id.end();
            pos = skipWs(sql, pos);

            // Optional column list: cte(col1, col2) AS ( ... )
            if (pos < sql.length() && sql.charAt(pos) == '(') {
                pos = skipBalancedParens(sql, pos);
                pos = skipWs(sql, pos);
            }

            if (!regionMatchesWord(sql, pos, "as")) {
                break;
            }
            pos = skipWs(sql, pos + 2);
            if (pos >= sql.length() || sql.charAt(pos) != '(') {
                break;
            }
            pos = skipBalancedParens(sql, pos);
            pos = skipWs(sql, pos);

            if (pos < sql.length() && sql.charAt(pos) == ',') {
                pos++;
                continue;
            }
            break;
        }
        return names;
    }

    private int findTopLevelWith(String sql) {
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth = Math.max(0, depth - 1);
            if (depth != 0) continue;
            if (regionMatchesWord(sql, i, "with")) {
                return i;
            }
        }
        return -1;
    }

    private int skipWs(String sql, int pos) {
        int i = pos;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return i;
    }

    private int skipWsAndCommas(String sql, int pos) {
        int i = pos;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            return i;
        }
        return i;
    }

    private int skipBalancedParens(String sql, int pos) {
        if (pos >= sql.length() || sql.charAt(pos) != '(') {
            return pos;
        }
        int depth = 0;
        int i = pos;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            if (c == ')') {
                depth--;
                if (depth <= 0) {
                    return i + 1;
                }
            }
            i++;
        }
        return i;
    }

    private IdentifierToken readIdentifier(String sql, int pos) {
        if (pos >= sql.length()) {
            return null;
        }
        Matcher m = IDENT_PART_PATTERN.matcher(sql);
        m.region(pos, sql.length());
        if (!m.lookingAt()) {
            return null;
        }
        return new IdentifierToken(m.group(), m.end());
    }

    private boolean regionMatchesWord(String sql, int pos, String word) {
        if (pos < 0 || pos + word.length() > sql.length()) {
            return false;
        }
        if (!sql.regionMatches(true, pos, word, 0, word.length())) {
            return false;
        }
        char before = pos > 0 ? sql.charAt(pos - 1) : ' ';
        char after = pos + word.length() < sql.length() ? sql.charAt(pos + word.length()) : ' ';
        return !isWordChar(before) && !isWordChar(after);
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private record IdentifierToken(String token, int end) {}

    public record TableRef(String schema, String table) {
        public String normalizedSchema() {
            return schema != null ? schema.trim().toLowerCase(Locale.ROOT) : null;
        }

        public String normalizedTable() {
            return table != null ? table.trim().toLowerCase(Locale.ROOT) : null;
        }
    }
}
