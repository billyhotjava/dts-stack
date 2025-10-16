package com.yuzhi.dts.platform.service.infra;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Helper utilities to extract column level comments from Hive/Inceptor metadata.
 * Provides a best-effort fallback by parsing {@code SHOW CREATE TABLE} output when
 * {@code DESCRIBE} does not emit comment text (observed on some Inceptor builds).
 */
public final class HiveColumnCommentResolver {

    private static final Logger LOG = LoggerFactory.getLogger(HiveColumnCommentResolver.class);

    /**
     * Matches column definitions with inline {@code COMMENT '<text>'} clauses.
     * Groups: {@code name} = column identifier (without backticks), {@code comment} = raw comment text.
     */
    private static final Pattern COLUMN_COMMENT_PATTERN = Pattern.compile(
        "(?i)^[`\"]?(?<name>[a-z0-9_]+)[`\"]?\\s+[^,]*?\\bcomment\\s+'(?<comment>(?:''|[^'])*)'",
        Pattern.CASE_INSENSITIVE
    );

    private HiveColumnCommentResolver() {}

    /**
     * Execute {@code SHOW CREATE TABLE} and parse column comments for the given table.
     * Returns an empty map on failure.
     */
    public static Map<String, String> fetchColumnComments(Connection connection, String tableName, int statementTimeoutSeconds) {
        if (connection == null || !StringUtils.hasText(tableName)) {
            return Collections.emptyMap();
        }
        String sanitized = tableName.replace("`", "``");
        String sql = "SHOW CREATE TABLE `" + sanitized + "`";
        try (Statement stmt = connection.createStatement()) {
            try {
                stmt.setQueryTimeout(Math.max(1, statementTimeoutSeconds));
            } catch (Throwable ignored) {}
            try (ResultSet rs = stmt.executeQuery(sql)) {
                StringBuilder ddl = new StringBuilder();
                while (rs.next()) {
                    String line = rs.getString(1);
                    if (line != null) {
                        ddl.append(line).append('\n');
                    }
                }
                return parseColumnComments(ddl.toString());
            }
        } catch (SQLException ex) {
            LOG.debug("SHOW CREATE TABLE {} failed while resolving comments: {}", tableName, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Parse column comments from a Hive {@code SHOW CREATE TABLE} statement.
     */
    public static Map<String, String> parseColumnComments(String ddl) {
        if (!StringUtils.hasText(ddl)) {
            return Collections.emptyMap();
        }
        Map<String, String> comments = new HashMap<>();
        for (String rawLine : ddl.split("\\R")) {
            String line = rawLine.stripLeading();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            // Stop once we reached table properties / partition / closing parentheses.
            if (line.startsWith(")") || line.regionMatches(true, 0, "PARTITIONED", 0, "PARTITIONED".length())) {
                break;
            }
            Matcher matcher = COLUMN_COMMENT_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String name = matcher.group("name");
            String comment = matcher.group("comment");
            if (!StringUtils.hasText(name) || comment == null) {
                continue;
            }
            String normalized = normalizeComment(comment);
            if (normalized != null) {
                comments.put(name.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return comments;
    }

    /**
     * Normalize comment text by trimming and un-escaping doubled single quotes.
     */
    public static String normalizeComment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("null".equalsIgnoreCase(trimmed) || "\\N".equalsIgnoreCase(trimmed) || "\\n".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed.replace("''", "'");
    }
}
