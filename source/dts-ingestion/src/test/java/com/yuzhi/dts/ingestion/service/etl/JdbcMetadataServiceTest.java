package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.Properties;
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
