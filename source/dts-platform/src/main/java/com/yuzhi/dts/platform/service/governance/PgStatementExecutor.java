package com.yuzhi.dts.platform.service.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PgStatementExecutor implements QualityStatementExecutor {

    private final JdbcTemplate jdbcTemplate;

    public PgStatementExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> executeQualityCheck(String sql, int maxRows) {
        String limited = "SELECT * FROM (" + sql + ") _qc LIMIT " + maxRows;
        return jdbcTemplate.queryForList(limited);
    }
}
