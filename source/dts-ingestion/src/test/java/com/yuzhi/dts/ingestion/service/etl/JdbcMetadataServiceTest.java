package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JdbcMetadataServiceTest {

    @Test
    void listTablesSurfacesSafeAuthenticationFailure() throws SQLException {
        Driver failingDriver = new AuthenticationFailureDriver();
        DriverManager.registerDriver(failingDriver);
        try {
            JdbcMetadataService service = new JdbcMetadataService();
            JdbcMetadataService.JdbcConnectionInfo info = new JdbcMetadataService.JdbcConnectionInfo(
                "jdbc:dts-auth-failure:test",
                "sensitive-user",
                "sensitive-password",
                null,
                null,
                Map.of()
            );

            assertThatThrownBy(() -> service.listTables(info, null, null, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("数据库认证失败，请检查用户名、密码及来源 IP 授权")
                .hasMessageNotContaining("sensitive-user")
                .hasMessageNotContaining("sensitive-password")
                .hasMessageNotContaining("Access denied");
        } finally {
            DriverManager.deregisterDriver(failingDriver);
        }
    }

    @Test
    void listTablesUsesMySqlDatabaseAsCatalogAndReturnsItAsSchema() throws Exception {
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(
            metadata.getTables(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                any(String[].class)
            )
        ).thenAnswer(invocation -> {
            String catalog = invocation.getArgument(0);
            String schema = invocation.getArgument(1);
            if ("test_db".equals(catalog) && schema == null) {
                return tableRows(new TableRow("test_db", null, "customer", "TABLE"));
            }
            return tableRows(
                new TableRow("test_db", null, "customer", "TABLE"),
                new TableRow("audit_db", null, "event_log", "TABLE")
            );
        });
        Driver driver = new FixedConnectionDriver(connection);
        DriverManager.registerDriver(driver);
        try {
            JdbcMetadataService service = new JdbcMetadataService();
            JdbcMetadataService.JdbcConnectionInfo info = new JdbcMetadataService.JdbcConnectionInfo(
                "jdbc:dts-mysql-catalog:test",
                null,
                null,
                null,
                null,
                Map.of()
            );

            assertThat(service.listTables(info, "test_db", null, 0))
                .containsExactly(new JdbcMetadataService.TableMeta("test_db", "customer", "TABLE"));
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    void listTablesDefaultsMySqlDiscoveryToTheCurrentCatalog() throws Exception {
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("test_db");
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(
            metadata.getTables(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                any(String[].class)
            )
        ).thenAnswer(invocation -> {
            String catalog = invocation.getArgument(0);
            if ("test_db".equals(catalog)) {
                return tableRows(new TableRow("test_db", null, "customer", "TABLE"));
            }
            return tableRows(
                new TableRow("test_db", null, "customer", "TABLE"),
                new TableRow("audit_db", null, "event_log", "TABLE")
            );
        });
        Driver driver = new FixedConnectionDriver(connection);
        DriverManager.registerDriver(driver);
        try {
            JdbcMetadataService service = new JdbcMetadataService();
            JdbcMetadataService.JdbcConnectionInfo info = new JdbcMetadataService.JdbcConnectionInfo(
                "jdbc:dts-mysql-catalog:test",
                null,
                null,
                null,
                null,
                Map.of()
            );

            assertThat(service.listTables(info, null, null, 0))
                .containsExactly(new JdbcMetadataService.TableMeta("test_db", "customer", "TABLE"));
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    private static ResultSet tableRows(TableRow... rows) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        AtomicInteger cursor = new AtomicInteger(-1);
        when(resultSet.next()).thenAnswer(ignored -> cursor.incrementAndGet() < rows.length);
        when(resultSet.getString(any(String.class))).thenAnswer(invocation -> {
            TableRow row = rows[cursor.get()];
            return switch ((String) invocation.getArgument(0)) {
                case "TABLE_CAT" -> row.catalog();
                case "TABLE_SCHEM" -> row.schema();
                case "TABLE_NAME" -> row.name();
                case "TABLE_TYPE" -> row.type();
                default -> null;
            };
        });
        return resultSet;
    }

    private record TableRow(String catalog, String schema, String name, String type) {}

    private static final class FixedConnectionDriver implements Driver {

        private final Connection connection;

        private FixedConnectionDriver(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection connect(String url, Properties info) {
            return acceptsURL(url) ? connection : null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:dts-mysql-catalog:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return java.util.logging.Logger.getGlobal();
        }
    }

    private static final class AuthenticationFailureDriver implements Driver {

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            throw new SQLException(
                "Access denied for user 'sensitive-user' (using password: YES; password=sensitive-password)",
                "28000",
                1045
            );
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:dts-auth-failure:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
