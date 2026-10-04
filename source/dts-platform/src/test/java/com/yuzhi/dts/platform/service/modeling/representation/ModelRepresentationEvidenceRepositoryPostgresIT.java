package com.yuzhi.dts.platform.service.modeling.representation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelRepresentationEvidenceRepository;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelRepresentationEvidenceRepositoryPostgresIT {

    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s83_representation_[0-9a-f]{32}$");
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final UUID MODEL_A = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_B = UUID.fromString("83000000-0000-0000-0000-000000000002");
    private static final UUID IMPLEMENTATION_A = UUID.fromString("83000000-0000-0000-0000-000000000011");
    private static final UUID IMPLEMENTATION_B = UUID.fromString("83000000-0000-0000-0000-000000000012");
    private static final String MODEL_CHECKSUM_1 = "1".repeat(64);
    private static final String MODEL_CHECKSUM_2 = "2".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM_1 = "3".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM_2 = "4".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("model_representation_it")
        .withUsername("model_representation_test")
        .withPassword("model_representation_test");

    private String schema;
    private JdbcTemplate jdbc;
    private ModelRepresentationEvidenceRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        schema = "s83_representation_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            createTables(statement);
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        repository = new ModelRepresentationEvidenceRepository(jdbc, new ObjectMapper());
        seed();
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
    void resolvesOnlyArtifactBoundRevisionPairsAndNeverCrossesTenantBoundary() {
        var first = repository
            .findExactImplementation(TENANT_A, MODEL_A, 1, MODEL_CHECKSUM_1, 1)
            .orElseThrow();
        var second = repository
            .findExactImplementation(TENANT_A, MODEL_A, 2, MODEL_CHECKSUM_2, 2)
            .orElseThrow();

        assertThat(first.implementationChecksum()).isEqualTo(IMPLEMENTATION_CHECKSUM_1);
        assertThat(second.implementationChecksum()).isEqualTo(IMPLEMENTATION_CHECKSUM_2);
        assertThat(repository.findExactImplementation(TENANT_A, MODEL_A, 2, MODEL_CHECKSUM_2, 1)).isEmpty();
        assertThat(repository.findExactImplementation(TENANT_A, MODEL_B, 1, MODEL_CHECKSUM_1, 1)).isEmpty();
        assertThat(
            repository.listExactArtifacts(
                TENANT_A,
                MODEL_B,
                1,
                MODEL_CHECKSUM_1,
                1,
                IMPLEMENTATION_CHECKSUM_1,
                true
            )
        ).isEmpty();
        assertThat(
            repository.listExactArtifacts(
                TENANT_A,
                MODEL_A,
                1,
                MODEL_CHECKSUM_1,
                1,
                IMPLEMENTATION_CHECKSUM_1,
                false
            )
        )
            .singleElement()
            .satisfies(artifact -> {
                assertThat(artifact.artifactPath()).isEqualTo("models/orders_v1.sql");
                assertThat(artifact.artifactContent()).isNull();
            });
    }

    private void seed() {
        UUID planA = UUID.randomUUID();
        UUID planB = UUID.randomUUID();
        jdbc.update("insert into modeling_model_spec values (?, ?, ?, 2)", MODEL_A, TENANT_A, planA);
        jdbc.update("insert into modeling_model_spec values (?, ?, ?, 2)", MODEL_B, TENANT_B, planB);
        insertModelRevision(TENANT_A, MODEL_A, 1, MODEL_CHECKSUM_1);
        insertModelRevision(TENANT_A, MODEL_A, 2, MODEL_CHECKSUM_2);
        insertModelRevision(TENANT_B, MODEL_B, 1, MODEL_CHECKSUM_1);
        insertImplementation(TENANT_A, IMPLEMENTATION_A, MODEL_A, 2, IMPLEMENTATION_CHECKSUM_2);
        insertImplementation(TENANT_B, IMPLEMENTATION_B, MODEL_B, 1, IMPLEMENTATION_CHECKSUM_1);
        insertImplementationRevision(TENANT_A, IMPLEMENTATION_A, 1, IMPLEMENTATION_CHECKSUM_1);
        insertImplementationRevision(TENANT_A, IMPLEMENTATION_A, 2, IMPLEMENTATION_CHECKSUM_2);
        insertImplementationRevision(TENANT_B, IMPLEMENTATION_B, 1, IMPLEMENTATION_CHECKSUM_1);
        insertArtifact(MODEL_A, 1, MODEL_CHECKSUM_1, 1, "models/orders_v1.sql");
        insertArtifact(MODEL_A, 2, MODEL_CHECKSUM_2, 2, "models/orders_v2.sql");
        insertArtifact(MODEL_B, 1, MODEL_CHECKSUM_1, 1, "models/private.sql");
    }

    private void insertModelRevision(String tenant, UUID modelId, int revision, String checksum) {
        jdbc.update(
            "insert into modeling_model_spec_revision values (?, ?, ?, ?, 2)",
            tenant,
            modelId,
            revision,
            checksum
        );
    }

    private void insertImplementation(
        String tenant,
        UUID implementationId,
        UUID modelId,
        int revision,
        String checksum
    ) {
        jdbc.update(
            "insert into modeling_model_implementation values (?, ?, ?, 'finance', 'model.finance.orders', ?, ?)",
            implementationId,
            tenant,
            modelId,
            revision,
            checksum
        );
    }

    private void insertImplementationRevision(String tenant, UUID implementationId, int revision, String checksum) {
        jdbc.update(
            "insert into modeling_model_implementation_revision values (?, ?, ?, ?, 'DBT_MANAGED', 'IMPORTED', '[]', '[]', '{}', 'table')",
            tenant,
            implementationId,
            revision,
            checksum
        );
    }

    private void insertArtifact(UUID modelId, int modelRevision, String modelChecksum, int implementationRevision, String path) {
        jdbc.update(
            "insert into modeling_dbt_artifact values (?, ?, ?, ?, ?, 'DBT_MANAGED', 'SQL', ?, ?, 'select 1', 'IMPORTED')",
            UUID.randomUUID(),
            modelId,
            modelRevision,
            modelChecksum,
            implementationRevision,
            path,
            "a".repeat(64)
        );
    }

    private static void createTables(Statement statement) throws Exception {
        statement.execute(
            "create table modeling_model_spec (id uuid, tenant_id varchar(128), plan_id uuid, contract_version int)"
        );
        statement.execute(
            "create table modeling_model_spec_revision (tenant_id varchar(128), model_spec_id uuid, revision int, content_checksum varchar(128), contract_version int)"
        );
        statement.execute(
            "create table modeling_model_implementation (id uuid, tenant_id varchar(128), model_spec_id uuid, project_key varchar(128), dbt_unique_id varchar(256), implementation_revision int, current_implementation_checksum varchar(64))"
        );
        statement.execute(
            "create table modeling_model_implementation_revision (tenant_id varchar(128), implementation_id uuid, revision int, content_checksum varchar(64), ownership varchar(32), input_mode varchar(32), inputs_json jsonb, field_mappings_json jsonb, settings_json jsonb, materialization varchar(64))"
        );
        statement.execute(
            "create table modeling_dbt_artifact (id uuid, model_spec_id uuid, revision int, model_checksum varchar(128), implementation_revision int, ownership varchar(32), artifact_type varchar(32), path text, content_checksum varchar(128), content text, status varchar(32))"
        );
    }

    private Connection openConnection() throws Exception {
        return java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Refusing to mutate an unowned schema");
        }
    }
}
