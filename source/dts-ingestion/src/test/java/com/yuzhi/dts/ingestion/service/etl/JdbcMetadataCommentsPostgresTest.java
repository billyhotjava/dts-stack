package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcMetadataCommentsPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    @Test
    void firstDiscoveryReadsCommentAndKeepsTablesWithoutComments() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE public.orders (id integer)");
            statement.execute("COMMENT ON TABLE public.orders IS '客户订单'");
            statement.execute("CREATE SCHEMA archive");
            statement.execute("CREATE TABLE archive.orders (id integer)");
        }
        var info = new JdbcMetadataService.JdbcConnectionInfo(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), null, null, Map.of());
        var service = new JdbcMetadataService();
        assertThat(service.listTables(info, "public", "orders", 0))
            .containsExactly(new JdbcMetadataService.TableMeta("public", "orders", "TABLE", "客户订单"));
        assertThat(service.listTables(info, "archive", "orders", 0))
            .containsExactly(new JdbcMetadataService.TableMeta("archive", "orders", "TABLE", null));
    }
}
