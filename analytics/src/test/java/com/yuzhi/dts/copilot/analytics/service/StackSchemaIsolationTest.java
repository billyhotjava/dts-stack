package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class StackSchemaIsolationTest {
    @Test
    void initializesAndReplaysStackMigrationsWithoutAStudioSchema() throws Exception {
        // The build wrapper supplies a disposable database and overrides inherited credentials.
        assertThat(System.getenv("PG_DB")).isEqualTo("stack_test");
        assertThat(System.getenv("PG_HOST")).isEqualTo("127.0.0.1");
        String url = "jdbc:postgresql://127.0.0.1:" + System.getenv("PG_PORT") + "/stack_test";
        try (Connection connection = DriverManager.getConnection(url,
                System.getenv("PG_USER"), System.getenv("PG_PASSWORD"))) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS copilot_analytics");
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName("copilot_analytics");
            try (Liquibase liquibase = new Liquibase("config/liquibase/master.xml",
                    new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                liquibase.update(new Contexts(), new LabelExpression());
                try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                        "SELECT to_regclass('copilot_analytics.analytics_database'), "
                        + "to_regclass('copilot_analytics.analytics_user'), "
                        + "EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'copilot_ai')")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isNotNull();
                    assertThat(rs.getString(2)).isNotNull();
                    assertThat(rs.getBoolean(3)).isFalse();
                }
            }
        }
    }
}
