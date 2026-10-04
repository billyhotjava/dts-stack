package com.yuzhi.dts.platform.service.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PgStatementExecutor implements QualityStatementExecutor {

    private static final Pattern FORBIDDEN_IN_QUERY = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|COPY|DO\\s*\\$)\\b",
        Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;

    public PgStatementExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> executeQualityCheck(String sql, int maxRows) {
        // Validate: only SELECT statements allowed for quality checks
        String trimmed = sql.strip();
        if (!trimmed.toUpperCase().startsWith("SELECT")) {
            throw new IllegalArgumentException("Quality check SQL must be a SELECT statement");
        }
        if (FORBIDDEN_IN_QUERY.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Quality check SQL contains forbidden DML/DDL keywords");
        }

        String limited = "SELECT * FROM (" + trimmed + ") _qc LIMIT " + maxRows;
        return jdbcTemplate.queryForList(limited);
    }
}
