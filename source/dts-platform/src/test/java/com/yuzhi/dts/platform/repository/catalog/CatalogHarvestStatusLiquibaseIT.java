package com.yuzhi.dts.platform.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
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
class CatalogHarvestStatusLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260728_08_catalog_dataset_harvest_status.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("catalog_harvest_status_it")
            .withUsername("catalog_harvest_status_test")
            .withPassword("catalog_harvest_status_test");

    @Test
    void migrationProtectsClaimsAndRollbackPreservesLaterGovernanceEdits() throws Exception {
        UUID datasetId = UUID.randomUUID();
        UUID harvestOnlyDatasetId = UUID.randomUUID();
        UUID extensionId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(
                "create table catalog_dataset (id uuid primary key, lifecycle_status varchar(32))"
            );
            statement.execute(
                "create table catalog_asset_extension (" +
                "id uuid primary key, legacy_dataset_id uuid, lifecycle_status varchar(32))"
            );
            statement.execute(
                "create table catalog_asset_mapping (id uuid primary key, legacy_dataset_id uuid)"
            );
            statement.execute(
                "insert into catalog_dataset values ('" + datasetId + "', 'SYNCED')"
            );
            statement.execute(
                "insert into catalog_dataset values ('" + harvestOnlyDatasetId + "', 'SYNCED')"
            );
            statement.execute(
                "insert into catalog_asset_extension values ('" +
                extensionId +
                "', '" +
                datasetId +
                "', 'STALE')"
            );
            statement.execute(
                "insert into catalog_asset_mapping values ('" +
                mappingId +
                "', '" +
                datasetId +
                "')"
            );
        }

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

                    assertThat(queryText("select lifecycle_status from catalog_dataset where id = '" + datasetId + "'"))
                        .isEqualTo("PENDING_GOVERNANCE");
                    assertThat(queryText("select harvest_status from catalog_dataset where id = '" + datasetId + "'"))
                        .isEqualTo("SYNCED");
                    assertThat(
                        queryText(
                            "select lifecycle_status from catalog_asset_extension where id = '" +
                            extensionId +
                            "'"
                        )
                    )
                        .isEqualTo("PENDING_GOVERNANCE");

                    assertThatThrownBy(() ->
                        execute(
                            "insert into catalog_asset_mapping values ('" +
                            UUID.randomUUID() +
                            "', '" +
                            datasetId +
                            "')"
                        )
                    )
                        .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() ->
                        execute(
                            "insert into catalog_asset_extension values ('" +
                            UUID.randomUUID() +
                            "', '" +
                            datasetId +
                            "', 'ACTIVE')"
                        )
                    )
                        .isInstanceOf(SQLException.class);

                    execute(
                        "update catalog_dataset set lifecycle_status = 'ACTIVE' where id = '" +
                        datasetId +
                        "'"
                    );
                    execute(
                        "update catalog_dataset set harvest_status = 'STALE' where id = '" +
                        harvestOnlyDatasetId +
                        "'"
                    );
                    execute(
                        "update catalog_asset_extension set lifecycle_status = 'ARCHIVED' where id = '" +
                        extensionId +
                        "'"
                    );

                    liquibase.rollback(1, new Contexts(), new LabelExpression());

                    assertThat(queryText("select lifecycle_status from catalog_dataset where id = '" + datasetId + "'"))
                        .isEqualTo("ACTIVE");
                    assertThat(
                        queryText(
                            "select lifecycle_status from catalog_dataset where id = '" +
                            harvestOnlyDatasetId +
                            "'"
                        )
                    )
                        .isEqualTo("STALE");
                    assertThat(
                        queryText(
                            "select lifecycle_status from catalog_asset_extension where id = '" +
                            extensionId +
                            "'"
                        )
                    )
                        .isEqualTo("ARCHIVED");
                    assertThat(columnExists("catalog_dataset", "harvest_status")).isFalse();

                    execute(
                        "insert into catalog_asset_mapping values ('" +
                        UUID.randomUUID() +
                        "', '" +
                        datasetId +
                        "')"
                    );
                    execute(
                        "insert into catalog_asset_extension values ('" +
                        UUID.randomUUID() +
                        "', '" +
                        datasetId +
                        "', 'ACTIVE')"
                    );
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        connection.setAutoCommit(true);
        return connection;
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String queryText(String sql) throws SQLException {
        try (
            Connection connection = connection();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql)
        ) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        return "1".equals(
            queryText(
                "select count(*) from information_schema.columns where table_schema = 'public' " +
                "and table_name = '" +
                table +
                "' and column_name = '" +
                column +
                "'"
            )
        );
    }
}
