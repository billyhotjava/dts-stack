package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcDetailsResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private PlatformInfraClient platformInfraClient;

    @Test
    void resolvesJdbcDetailsFromManagedDataSourceId() throws Exception {
        JdbcDetailsResolver resolver = new JdbcDetailsResolver(platformInfraClient, "dts-pg", new MockEnvironment());
        when(platformInfraClient.fetchDataSourceDetail(42L)).thenReturn(new PlatformInfraClient.DataSourceDetail(
                "42",
                "园林业务库",
                "postgres",
                "jdbc:postgresql://db.internal:5432/garden",
                "readonly",
                "业务库",
                null,
                Map.of(),
                Map.of("password", "secret-pass"),
                "ACTIVE",
                null));

        JdbcDetailsResolver.JdbcDetails details = resolver.resolve(
                "postgres",
                MAPPER.readTree("""
                        {
                          "dataSourceId": 42
                        }
                        """));

        assertThat(details.jdbcUrl()).isEqualTo("jdbc:postgresql://db.internal:5432/garden");
        assertThat(details.username()).isEqualTo("readonly");
        assertThat(details.password()).isEqualTo("secret-pass");
    }

    @Test
    void resolvesMysqlJdbcDetailsFromDatabaseNameAlias() throws Exception {
        JdbcDetailsResolver resolver = new JdbcDetailsResolver(platformInfraClient, "dts-pg", new MockEnvironment());

        JdbcDetailsResolver.JdbcDetails details = resolver.resolve(
                "mysql",
                MAPPER.readTree("""
                        {
                          "host": "127.0.0.1",
                          "port": 3306,
                          "databaseName": "demo",
                          "username": "readonly",
                          "password": "secret-pass"
                        }
                        """));

        assertThat(details.jdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:3306/demo");
        assertThat(details.username()).isEqualTo("readonly");
        assertThat(details.password()).isEqualTo("secret-pass");
    }
}
