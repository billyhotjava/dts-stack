package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IngestionDirectDbtRetirementPostgresMigrationIT {

    private static final String CHANGELOG = "config/liquibase/ingestion-direct-dbt-retirement-postgres-it.xml";
    private static final String ARCHIVE_CHANGELOG = "config/liquibase/ingestion-legacy-dbt-selector-archive-postgres-it.xml";
    private static final String BLOCKED = "INGESTION_DIRECT_DBT_RETIREMENT_BLOCKED";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("ingestion_direct_dbt_retirement_it")
        .withUsername("ingestion_direct_dbt_retirement_test")
        .withPassword("ingestion_direct_dbt_retirement_test");

    @BeforeEach
    void resetSchema() throws SQLException {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    }

    @Test
    void migrationDropsBothEmptySelectorColumns() throws Exception {
        applyFixture();
        execute("INSERT INTO ingestion_task(id,dbt_model_selector,dbt_dag_selector) VALUES (1,NULL,NULL),(2,'   ','')");

        applyAll();

        assertThat(queryLong("""
            SELECT count(*) FROM information_schema.columns
             WHERE table_schema=current_schema()
               AND table_name='ingestion_task'
               AND column_name IN ('dbt_model_selector','dbt_dag_selector')
            """))
            .isZero();
    }

    @Test
    void archivePreludePreservesOnlyConfirmedLegacyTabSelectorsBeforeRetirement() throws Exception {
        applyFixture(ARCHIVE_CHANGELOG);
        execute(
            "INSERT INTO ingestion_task(id,dbt_model_selector,dbt_dag_selector) VALUES " +
            "(11,NULL,'tab:findemo1'),(12,NULL,'tab:findemo1'),(14,NULL,'tab:findemo1')," +
            "(18,NULL,'tab:findemo1'),(24,NULL,'tab:findemo1'),(26,NULL,'tab:biadmin')," +
            "(27,'   ',''),(28,NULL,NULL)"
        );

        applyAll(ARCHIVE_CHANGELOG);

        assertThat(queryLong("SELECT count(*) FROM ingestion_task_legacy_dbt_selector_archive")).isEqualTo(6L);
        assertThat(queryString(
            "SELECT coalesce(dbt_model_selector, '<null>') || '|' || dbt_dag_selector " +
            "FROM ingestion_task_legacy_dbt_selector_archive WHERE task_id=11"
        )).isEqualTo("<null>|tab:findemo1");
        assertThat(queryString(
            "SELECT coalesce(dbt_model_selector, '<null>') || '|' || dbt_dag_selector " +
            "FROM ingestion_task_legacy_dbt_selector_archive WHERE task_id=26"
        )).isEqualTo("<null>|tab:biadmin");
        assertThat(queryString(
            "SELECT archived_by FROM ingestion_task_legacy_dbt_selector_archive WHERE task_id=11"
        )).isEqualTo("direct-dbt-retirement");
        assertThat(queryLong("""
            SELECT count(*) FROM information_schema.columns
             WHERE table_schema=current_schema()
               AND table_name='ingestion_task'
               AND column_name IN ('dbt_model_selector','dbt_dag_selector')
            """)).isZero();
    }

    @Test
    void archivePreludeLeavesUnknownTabSelectorForRetirementToReject() throws Exception {
        applyFixture(ARCHIVE_CHANGELOG);
        execute("INSERT INTO ingestion_task(id,dbt_dag_selector) VALUES (99,'tab:unknown')");

        assertThatThrownBy(() -> applyAll(ARCHIVE_CHANGELOG))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining(BLOCKED);

        assertThat(queryLong("SELECT count(*) FROM ingestion_task_legacy_dbt_selector_archive")).isZero();
        assertThat(queryString("SELECT dbt_dag_selector FROM ingestion_task WHERE id=99")).isEqualTo("tab:unknown");
    }

    @Test
    void archivePreludeLeavesModelSelectorForRetirementToReject() throws Exception {
        applyFixture(ARCHIVE_CHANGELOG);
        execute(
            "INSERT INTO ingestion_task(id,dbt_model_selector,dbt_dag_selector) " +
            "VALUES (11,'model:legacy_orders','tab:findemo1')"
        );

        assertThatThrownBy(() -> applyAll(ARCHIVE_CHANGELOG))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining(BLOCKED);

        assertThat(queryLong("SELECT count(*) FROM ingestion_task_legacy_dbt_selector_archive")).isZero();
        assertThat(queryString("SELECT dbt_model_selector FROM ingestion_task WHERE id=11")).isEqualTo("model:legacy_orders");
    }

    @Test
    void archivePreludeRollbackRestoresConfirmedSelectors() throws Exception {
        applyFixture(ARCHIVE_CHANGELOG);
        execute("INSERT INTO ingestion_task(id,dbt_dag_selector) VALUES (11,'tab:findemo1'),(26,'tab:biadmin')");
        applyNext(ARCHIVE_CHANGELOG);

        assertThat(queryLong("SELECT count(*) FROM ingestion_task_legacy_dbt_selector_archive")).isEqualTo(2L);
        assertThat(queryLong("SELECT count(*) FROM ingestion_task WHERE dbt_dag_selector IS NOT NULL")).isZero();

        rollbackNext(ARCHIVE_CHANGELOG);

        assertThat(queryString("SELECT dbt_dag_selector FROM ingestion_task WHERE id=11")).isEqualTo("tab:findemo1");
        assertThat(queryString("SELECT dbt_dag_selector FROM ingestion_task WHERE id=26")).isEqualTo("tab:biadmin");
        assertThat(queryLong("SELECT count(*) FROM information_schema.tables WHERE table_name='ingestion_task_legacy_dbt_selector_archive'")).isZero();
    }

    @Test
    void archivePreludeRollbackRejectsMissingTask() throws Exception {
        applyFixture(ARCHIVE_CHANGELOG);
        execute("INSERT INTO ingestion_task(id,dbt_dag_selector) VALUES (11,'tab:findemo1')");
        applyNext(ARCHIVE_CHANGELOG);
        execute("DELETE FROM ingestion_task WHERE id=11");

        assertThatThrownBy(() -> rollbackNext(ARCHIVE_CHANGELOG))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining("ROLLBACK_BLOCKED_INGESTION_DIRECT_DBT_ARCHIVE_TASK_MISSING");

        assertThat(queryLong("SELECT count(*) FROM ingestion_task_legacy_dbt_selector_archive")).isEqualTo(1L);
    }

    @Test
    void archivePreludeRollbackRejectsSelectorDrift() throws Exception {
        applyFixture(ARCHIVE_CHANGELOG);
        execute("INSERT INTO ingestion_task(id,dbt_dag_selector) VALUES (11,'tab:findemo1')");
        applyNext(ARCHIVE_CHANGELOG);
        execute("UPDATE ingestion_task SET dbt_dag_selector='tab:new-value' WHERE id=11");

        assertThatThrownBy(() -> rollbackNext(ARCHIVE_CHANGELOG))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining("ROLLBACK_BLOCKED_INGESTION_DIRECT_DBT_ARCHIVE_SELECTOR_DRIFT");

        assertThat(queryString("SELECT dbt_dag_selector FROM ingestion_task WHERE id=11")).isEqualTo("tab:new-value");
        assertThat(queryLong("SELECT count(*) FROM ingestion_task_legacy_dbt_selector_archive")).isEqualTo(1L);
    }

    @Test
    void migrationRejectsNonEmptyModelSelector() throws Exception {
        assertBlockedAfter("INSERT INTO ingestion_task(id,dbt_model_selector) VALUES (1,'model.orders')");
    }

    @Test
    void migrationRejectsNonEmptyDagSelector() throws Exception {
        assertBlockedAfter("INSERT INTO ingestion_task(id,dbt_dag_selector) VALUES (1,'tag:finance')");
    }

    @Test
    void migrationRejectsMissingSelectorColumn() throws Exception {
        assertBlockedAfter("ALTER TABLE ingestion_task DROP COLUMN dbt_dag_selector");
    }

    @Test
    void migrationRejectsSelectorIndexDependency() throws Exception {
        assertBlockedAfter("CREATE INDEX idx_ingestion_task_dbt_model_selector ON ingestion_task(dbt_model_selector)");
    }

    @Test
    void migrationRejectsSelectorConstraintDependency() throws Exception {
        assertBlockedAfter("""
            ALTER TABLE ingestion_task ADD CONSTRAINT ck_ingestion_task_dbt_selector
            CHECK (dbt_model_selector IS NULL OR length(dbt_model_selector) > 0)
            """);
    }

    @Test
    void migrationRejectsSelectorViewDependency() throws Exception {
        assertBlockedAfter("CREATE VIEW ingestion_task_selector_view AS SELECT dbt_dag_selector FROM ingestion_task");
    }

    @Test
    void migrationIsForwardOnly() throws Exception {
        applyAll();

        assertThatThrownBy(() -> withLiquibase(liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression())))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining("ROLLBACK_BLOCKED_INGESTION_DIRECT_DBT_RETIREMENT_FORWARD_ONLY");
    }

    private void assertBlockedAfter(String fixtureSql) throws Exception {
        applyFixture();
        execute(fixtureSql);

        assertThatThrownBy(this::applyAll)
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining(BLOCKED);
    }

    private void applyFixture() throws Exception {
        applyFixture(CHANGELOG);
    }

    private void applyFixture(String changelog) throws Exception {
        withLiquibase(changelog, liquibase -> liquibase.update(1, new Contexts(), new LabelExpression()));
    }

    private void applyAll() throws Exception {
        applyAll(CHANGELOG);
    }

    private void applyAll(String changelog) throws Exception {
        withLiquibase(changelog, liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
    }

    private void applyNext(String changelog) throws Exception {
        withLiquibase(changelog, liquibase -> liquibase.update(1, new Contexts(), new LabelExpression()));
    }

    private void rollbackNext(String changelog) throws Exception {
        withLiquibase(changelog, liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
    }

    private void withLiquibase(LiquibaseWork work) throws Exception {
        withLiquibase(CHANGELOG, work);
    }

    private void withLiquibase(String changelog, LiquibaseWork work) throws Exception {
        try (ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor(); Connection connection = connection()) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                    work.run(liquibase);
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    @FunctionalInterface
    private interface LiquibaseWork {
        void run(Liquibase liquibase) throws Exception;
    }
}
