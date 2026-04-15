package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.service.sql.JdbcSqlExecutor.ExecutionResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

/**
 * Integration tests for {@link JdbcSqlExecutor}.
 * All tests run against the Testcontainers PostgreSQL instance that backs the integration-test
 * Spring context (same DB the JPA tests use).
 */
@IntegrationTest
class JdbcSqlExecutorIT {

    @Autowired
    private JdbcSqlExecutor executor;

    @Autowired
    private DataSource dataSource;           // TC-backed datasource wired by EmbeddedSQL

    @Autowired
    private DataSourceProperties dataSourceProperties;

    private InfraDataSource ds;

    @BeforeEach
    void setUp() {
        ds = new InfraDataSource();
        ds.setName("tc-test");
        ds.setType("POSTGRESQL");
        ds.setJdbcUrl(dataSourceProperties.getUrl());
        ds.setUsername(dataSourceProperties.getUsername());
        // secureProps intentionally null — resolvePassword returns null → DriverManager picks up the props
        // We set password via a separate holder approach: store it in a test-only plaintext field.
        // InfraDataSource.secureProps is encrypted by InfraSecretService — in tests we have no key,
        // so secureProps==null → readSecrets() returns empty map → password==null.
        // We therefore have to embed the password in the JDBC URL or use the Properties approach.
        // Simplest: override jdbcUrl to include the credentials inline for TC.
        String url = dataSourceProperties.getUrl();
        String user = dataSourceProperties.getUsername();
        String pass = dataSourceProperties.getPassword();
        if (pass != null && !pass.isBlank()) {
            // Append credentials as query params if not already present
            if (!url.contains("user=") && !url.contains("password=")) {
                url = url + (url.contains("?") ? "&" : "?") + "user=" + user + "&password=" + pass;
            }
        }
        ds.setJdbcUrl(url);
    }

    // ------------------------------------------------------------------
    // 1. execute_returnsRowsFromRealSelect
    // ------------------------------------------------------------------

    @Test
    void execute_returnsRowsFromRealSelect() throws Exception {
        String tableName = "jdbcit_basic_" + randomSuffix();
        createAndPopulate(tableName, 3);
        try {
            // Pass null executionId to skip JPA chunk persistence (avoids Hibernate jsonb mapping in test env)
            ExecutionResult result = executor.execute(ds, "SELECT * FROM " + tableName, null, 10_000, null, new AtomicBoolean(false));

            assertThat(result.rowCount()).isEqualTo(3);
            assertThat(result.columns()).hasSize(2);
            assertThat(result.columns().get(0).name()).isEqualTo("id");
            assertThat(result.columns().get(1).name()).isEqualTo("val");
            assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(0);
        } finally {
            dropTable(tableName);
        }
    }

    // ------------------------------------------------------------------
    // 2. execute_respectsRowLimit
    // ------------------------------------------------------------------

    @Test
    void execute_respectsRowLimit() throws Exception {
        String tableName = "jdbcit_limit_" + randomSuffix();
        createAndPopulate(tableName, 5);
        try {
            // Pass null executionId to skip JPA chunk persistence (avoids Hibernate jsonb mapping in test env)
            ExecutionResult result = executor.execute(ds, "SELECT * FROM " + tableName + " ORDER BY id", null, 3, null, new AtomicBoolean(false));

            assertThat(result.rowCount()).isEqualTo(3);
            assertThat(result.truncated()).isTrue();
        } finally {
            dropTable(tableName);
        }
    }

    // ------------------------------------------------------------------
    // 3. execute_cancelStopsEarly
    // ------------------------------------------------------------------

    @Test
    void execute_cancelStopsEarly() throws Exception {
        // We can't reliably create a truly long-running query in TC, so we set the cancel flag
        // immediately before calling execute — the implementation checks cancelFlag before reading each row.
        String tableName = "jdbcit_cancel_" + randomSuffix();
        createAndPopulate(tableName, 100);
        try {
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            // Set cancel immediately
            cancelFlag.set(true);
            // null executionId to skip JPA chunk persistence
            ExecutionResult result = executor.execute(ds, "SELECT * FROM " + tableName, null, 10_000, null, cancelFlag);

            // With cancel=true from the start, 0 rows should be read
            assertThat(result.rowCount()).isEqualTo(0);
        } finally {
            dropTable(tableName);
        }
    }

    // ------------------------------------------------------------------
    // 4. explain_returnsPlanText
    // ------------------------------------------------------------------

    @Test
    void explain_returnsPlanText() throws Exception {
        String planText = executor.explain(ds, "SELECT 1");

        assertThat(planText).isNotBlank();
        // PostgreSQL EXPLAIN (FORMAT JSON) returns JSON starting with "[" or "QUERY PLAN" text
        assertThat(planText.length()).isGreaterThan(5);
    }

    // ------------------------------------------------------------------
    // 5. getConnection_withBadUrl_throwsMeaningful
    // ------------------------------------------------------------------

    @Test
    void getConnection_withBadUrl_throwsMeaningful() {
        InfraDataSource bad = new InfraDataSource();
        bad.setName("bad-ds");
        bad.setType("POSTGRESQL");
        bad.setJdbcUrl("jdbc:postgresql://no-such-host-xyz:9999/doesnotexist?connectTimeout=2");
        bad.setUsername("nobody");

        assertThatThrownBy(() -> executor.getConnection(bad))
            .isInstanceOf(SQLException.class)
            .message().contains("no-such-host-xyz");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void createAndPopulate(String tableName, int rows) throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (id INT PRIMARY KEY, val VARCHAR(50))");
            for (int i = 1; i <= rows; i++) {
                stmt.execute("INSERT INTO " + tableName + " VALUES (" + i + ", 'row_" + i + "')");
            }
        }
    }

    private void dropTable(String tableName) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
        } catch (Exception ignored) {}
    }

    private String randomSuffix() {
        return Integer.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextInt(0xFFFF));
    }
}
