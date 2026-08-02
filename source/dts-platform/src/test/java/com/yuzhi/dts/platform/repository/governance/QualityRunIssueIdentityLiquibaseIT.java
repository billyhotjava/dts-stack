package com.yuzhi.dts.platform.repository.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
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
class QualityRunIssueIdentityLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_12_quality_run_issue_identity.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("quality_run_issue_identity_it")
        .withUsername("quality_issue_test")
        .withPassword("quality_issue_test");

    @Test
    void migrationRejectsDuplicateQualityRunIdentityAndAllowsOtherSources() throws Exception {
        execute(
            "create table gov_issue_ticket (id uuid primary key, source_type varchar(32), source_ref_id uuid, status varchar(32))"
        );

        applyChangelog();
        UUID sourceId = UUID.randomUUID();
        insert("QUALITY_RUN", sourceId);

        assertThatThrownBy(() -> insert("quality_run", sourceId)).isInstanceOf(SQLException.class);
        insert("QUALITY_TASK", sourceId);
        insert("QUALITY_TASK", sourceId);
    }

    private static void applyChangelog() throws Exception {
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
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private static void insert(String sourceType, UUID sourceId) throws SQLException {
        execute(
            "insert into gov_issue_ticket (id, source_type, source_ref_id, status) values ('" +
            UUID.randomUUID() +
            "', '" +
            sourceType +
            "', '" +
            sourceId +
            "', 'OPEN')"
        );
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
