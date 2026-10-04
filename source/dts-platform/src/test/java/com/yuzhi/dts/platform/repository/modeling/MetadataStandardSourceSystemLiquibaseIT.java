package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MetadataStandardSourceSystemLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260729_01_metadata_standard_source_system_nullable.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("metadata_standard_source_it")
            .withUsername("metadata_standard_source_test")
            .withPassword("metadata_standard_source_test");

    @Test
    void migrationAllowsBlankPackageSourceAndBlocksUnsafeRollback() throws Exception {
        execute("create table metadata_standard (id uuid primary key, source_system varchar(64) not null)");

        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = connection()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                    execute("insert into metadata_standard (id, source_system) values (gen_random_uuid(), null)");

                    assertThatThrownBy(() ->
                        liquibase.rollback(1, new Contexts(), new LabelExpression())
                    )
                        .hasStackTraceContaining("ROLLBACK_BLOCKED_METADATA_STANDARD_NULL_SOURCE_SYSTEM");

                    execute("delete from metadata_standard where source_system is null");
                    liquibase.rollback(1, new Contexts(), new LabelExpression());

                    assertThatThrownBy(() ->
                        execute("insert into metadata_standard (id, source_system) values (gen_random_uuid(), null)")
                    )
                        .isInstanceOf(SQLException.class);
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private static Connection connection() throws SQLException {
        Connection connection = POSTGRES.createConnection("");
        connection.setAutoCommit(true);
        return connection;
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
