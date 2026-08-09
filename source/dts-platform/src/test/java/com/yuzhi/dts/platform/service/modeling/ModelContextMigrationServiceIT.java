package com.yuzhi.dts.platform.service.modeling;

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
class ModelContextMigrationServiceIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260810_01_model_spec_business_context_expand.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s87_model_context_[0-9a-f]{32}$");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("modelContextMigrationIT")
        .withUsername("model_context_test")
        .withPassword("model_context_test");

    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private ModelContextMigrationService service;
    private UUID factModelId;
    private UUID applicationModelId;
    private UUID processId;
    private UUID subjectDomainId;

    @BeforeEach
    void setUp() throws Exception {
        schema = "s87_model_context_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        factModelId = UUID.randomUUID();
        applicationModelId = UUID.randomUUID();
        processId = UUID.randomUUID();
        subjectDomainId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID dataMartId = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute(
                """
                create table sprint64_business_process(
                    id uuid primary key, domain_id uuid not null, process_id varchar(64),
                    name varchar(128), confirmed boolean not null
                )
                """
            );
            statement.execute(
                """
                create table modeling_subject_domain(
                    tenant_id varchar(128) not null, id uuid not null, mart_id uuid not null,
                    code varchar(64), name varchar(128), status varchar(16),
                    primary key(tenant_id, id)
                )
                """
            );
            statement.execute(
                """
                create table modeling_model_spec(
                    tenant_id varchar(128) not null, id uuid not null, model_type varchar(32) not null,
                    domain_id uuid, business_activity_ref varchar(256), data_mart_id uuid, status varchar(32),
                    primary key(tenant_id, id)
                )
                """
            );
            statement.execute(
                """
                create table modeling_model_spec_revision(
                    tenant_id varchar(128) not null, model_spec_id uuid not null, revision int not null,
                    model_type varchar(32) not null, domain_id uuid, business_activity_ref varchar(256), data_mart_id uuid,
                    primary key(tenant_id, model_spec_id, revision)
                )
                """
            );
            statement.execute(
                "insert into sprint64_business_process values ('" + processId + "', '" + domainId +
                "', 'budget_execution', '预算执行', true)"
            );
            statement.execute(
                "insert into modeling_subject_domain values ('platform', '" + subjectDomainId + "', '" + dataMartId +
                "', 'budget_subject', '预算主题', 'CURRENT')"
            );
            statement.execute(
                "insert into modeling_model_spec values ('platform', '" + factModelId +
                "', 'FACT', '" + domainId + "', 'budget_execution', null, 'DRAFT')"
            );
            statement.execute(
                "insert into modeling_model_spec_revision values ('platform', '" + factModelId +
                "', 1, 'FACT', '" + domainId + "', 'budget_execution', null)"
            );
            statement.execute(
                "insert into modeling_model_spec values ('platform', '" + applicationModelId +
                "', 'APPLICATION', '" + domainId + "', null, '" + dataMartId + "', 'DRAFT')"
            );
            statement.execute(
                "insert into modeling_model_spec_revision values ('platform', '" + applicationModelId +
                "', 1, 'APPLICATION', '" + domainId + "', null, '" + dataMartId + "')"
            );
        }
        applyMigration();
        DriverManagerDataSource dataSource = dataSourceInSchema();
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new ModelContextMigrationService(jdbc);
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
    void previewApplyAndRollbackUseUniqueExactMatchesForHeadsAndImmutableRevisions() {
        var preview = service.preview(100);

        assertThat(preview.rows()).hasSize(4).allMatch(row -> row.issueCode() == null);
        assertThat(preview.rows()).filteredOn(row -> row.modelSpecId().equals(factModelId))
            .allMatch(row -> processId.equals(row.proposedBusinessProcessId()));
        assertThat(preview.rows()).filteredOn(row -> row.modelSpecId().equals(applicationModelId))
            .allMatch(row -> subjectDomainId.equals(row.proposedSubjectDomainId()));

        var applied = transaction.execute(status -> service.apply(preview.previewHash(), 100, "it-correlation"));
        assertThat(applied.applied()).isEqualTo(4);
        assertThat(jdbc.queryForObject(
            "select business_process_id from modeling_model_spec where id = ?",
            UUID.class,
            factModelId
        )).isEqualTo(processId);
        assertThat(jdbc.queryForObject(
            "select business_process_id from modeling_model_spec_revision where model_spec_id = ? and revision = 1",
            UUID.class,
            factModelId
        )).isEqualTo(processId);
        assertThat(jdbc.queryForObject(
            "select subject_domain_id from modeling_model_spec where id = ?",
            UUID.class,
            applicationModelId
        )).isEqualTo(subjectDomainId);
        assertThat(jdbc.queryForObject(
            "select subject_domain_id from modeling_model_spec_revision where model_spec_id = ? and revision = 1",
            UUID.class,
            applicationModelId
        )).isEqualTo(subjectDomainId);

        var rolledBack = transaction.execute(status -> service.rollback(applied.batchId()));
        assertThat(rolledBack.restored()).isEqualTo(4);
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_model_spec where business_process_id is not null or subject_domain_id is not null",
            Long.class
        )).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_model_spec_revision where business_process_id is not null or subject_domain_id is not null",
            Long.class
        )).isZero();
    }

    private void applyMigration() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.setChangeLogParameter("uuidType", "uuid");
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
