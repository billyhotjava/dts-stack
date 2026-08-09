package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Pattern;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IndicatorContextMigrationServiceIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260810_03_indicator_business_context_expand.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s87_indicator_context_[0-9a-f]{32}$");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("indicatorContextMigrationIT")
        .withUsername("indicator_context_test")
        .withPassword("indicator_context_test");

    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private IndicatorContextMigrationService service;
    private UUID indicatorId;
    private UUID categoryId;
    private UUID domainId;

    @BeforeEach
    void setUp() throws Exception {
        schema = "s87_indicator_context_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        categoryId = UUID.randomUUID();
        domainId = UUID.randomUUID();
        indicatorId = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute(
                """
                create table catalog_domain(
                    id uuid primary key, code varchar(64), name varchar(128), parent_id uuid,
                    lifecycle_status varchar(16)
                )
                """
            );
            statement.execute(
                """
                create table sprint64_business_process(
                    id uuid primary key, domain_id uuid not null, confirmed boolean not null
                )
                """
            );
            statement.execute(
                """
                create table gov_indicator_definition(
                    id uuid primary key, category varchar(128), domain varchar(32), is_derived boolean,
                    dataset_id varchar(64), last_modified_by varchar(50), last_modified_date timestamp
                )
                """
            );
            statement.execute("insert into catalog_domain values ('" + categoryId + "', 'SALES', '销售', null, 'ACTIVE')");
            statement.execute("insert into catalog_domain values ('" + domainId + "', 'RETAIL', '零售', '" + categoryId + "', 'ACTIVE')");
            statement.execute("insert into sprint64_business_process values ('" + processId + "', '" + domainId + "', true)");
            statement.execute(
                "insert into gov_indicator_definition values ('" + indicatorId + "', 'SALES', 'RETAIL', false, 'legacy-dataset', 'seed', current_timestamp)"
            );
        }
        applyMigration();
        DriverManagerDataSource dataSource = dataSourceInSchema();
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new IndicatorContextMigrationService(jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) return;
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void previewApplyAndRollbackUseTheSameEvidenceWithoutGuessingMissingProcessOrSource() {
        var preview = service.preview(100);

        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.proposedBusinessCategoryId()).isEqualTo(categoryId);
            assertThat(row.proposedDataDomainId()).isEqualTo(domainId);
            assertThat(row.proposedMetricType()).isEqualTo("ATOMIC");
            assertThat(row.issueCodes()).isEmpty();
        });

        var applied = transaction.execute(status -> service.apply(preview.previewHash(), 100));
        assertThat(applied.applied()).isEqualTo(1);
        assertThat(jdbc.queryForMap(
            "select business_category_id, data_domain_id, metric_type, business_process_id, source_refs from gov_indicator_definition where id = ?",
            indicatorId
        ))
            .containsEntry("business_category_id", categoryId)
            .containsEntry("data_domain_id", domainId)
            .containsEntry("metric_type", "ATOMIC")
            .containsEntry("business_process_id", null)
            .containsEntry("source_refs", null);

        var rolledBack = transaction.execute(status -> service.rollback(applied.batchId()));
        assertThat(rolledBack.restored()).isEqualTo(1);
        assertThat(jdbc.queryForMap(
            "select business_category_id, data_domain_id, metric_type from gov_indicator_definition where id = ?",
            indicatorId
        ))
            .containsEntry("business_category_id", null)
            .containsEntry("data_domain_id", null)
            .containsEntry("metric_type", null);
    }

    private void applyMigration() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private DriverManagerDataSource dataSourceInSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        dataSource.setUrl(POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("refusing to mutate unowned schema: " + schema);
        }
    }
}
