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

    private static final Pattern TABLE_REF_PATTERN = Pattern.compile(
        "(?i)\\b(from|join|update|into)\\s+([`\"\\[]?[\\w$.]+[`\"\\]]?)"
    );

    public Set<TableRef> extract(String sql) {
        if (!StringUtils.hasText(sql)) {
            return Set.of();
        }
        String normalized = stripCommentsAndStrings(sql);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
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
            token = stripIdentifierQuotes(token);
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String[] parts = token.split("\\.");
            if (parts.length == 0) {
                continue;
            }
            String table = stripIdentifierQuotes(parts[parts.length - 1]);
            if (!StringUtils.hasText(table)) {
                continue;
            }
            String schema = null;
            if (parts.length >= 2) {
                schema = stripIdentifierQuotes(parts[parts.length - 2]);
            }
            if (schema != null && schema.isBlank()) {
                schema = null;
            }
            refs.add(new TableRef(schema, table));
        }
        return refs;
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
        String out = trimmed.trim();
        if (out.isEmpty()) {
            return null;
        }
        if ("select".equalsIgnoreCase(out) || "values".equalsIgnoreCase(out)) {
            return null;
        }
        return out;
    }

    public record TableRef(String schema, String table) {
        public String normalizedSchema() {
            return schema != null ? schema.trim().toLowerCase(Locale.ROOT) : null;
        }

        public String normalizedTable() {
            return table != null ? table.trim().toLowerCase(Locale.ROOT) : null;
        }
    }
}

