package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewAuditOutcome;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewAuditFacts;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedRun;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelSpecImportPreviewCommitServicePostgresIT {

    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s83_preview_atomic_[0-9a-f]{32}$");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("model_preview_atomic_it")
        .withUsername("model_preview_atomic_test")
        .withPassword("model_preview_atomic_test");

    private String schema;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        schema = "s83_preview_atomic_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            createTables(statement);
        }
        dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        objectMapper = new ObjectMapper();
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
    void strictAuditFailureRollsBackThePersistedPreview() throws Exception {
        AuditService audit = mock(AuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).auditActionStrict(any(), any(), any(), any());
        ModelSpecImportPreviewCommitPort commit = transactionalCommitPort(audit);
        PersistedRun run = run(UUID.randomUUID());

        assertThatThrownBy(() -> commit.commit(run, List.of(), facts(run)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");

        assertThat(jdbc.queryForObject("select count(*) from modeling_model_spec_import_run", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from modeling_model_spec_import_run_item", Integer.class)).isZero();
    }

    @Test
    void successfulStrictAuditCommitsThePreview() throws Exception {
        PersistedRun run = run(UUID.randomUUID());

        transactionalCommitPort(mock(AuditService.class)).commit(run, List.of(), facts(run));

        assertThat(jdbc.queryForObject("select count(*) from modeling_model_spec_import_run", Integer.class)).isOne();
    }

    private ModelSpecImportPreviewCommitPort transactionalCommitPort(AuditService audit) {
        ModelSpecImportPreviewRepository repository = new ModelSpecImportPreviewRepository(jdbc, objectMapper);
        ModelSpecImportPreviewCommitService target = new ModelSpecImportPreviewCommitService(repository, audit);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(ModelSpecImportPreviewCommitPort.class);
        proxyFactory.addAdvice(
            new TransactionInterceptor(
                new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()
            )
        );
        return (ModelSpecImportPreviewCommitPort) proxyFactory.getProxy();
    }

    private PersistedRun run(UUID runId) throws Exception {
        var modelPackage = ModelPackageFixtures.validPackage();
        ModelSpecImportApplyPayloadCodec codec = new ModelSpecImportApplyPayloadCodec(objectMapper);
        var applyPayload = codec.sanitize(modelPackage);
        String applyPlanJson =
            "{\"runId\":\"" +
            runId +
            "\",\"packageChecksum\":\"" +
            modelPackage.packageChecksum() +
            "\",\"applyPayloadChecksum\":\"" +
            applyPayload.checksum() +
            "\",\"topology\":[],\"candidates\":[]}";
        return new PersistedRun(
            runId,
            "tenant-a",
            UUID.randomUUID(),
            modelPackage.packageChecksum(),
            modelPackage.schemaVersion(),
            objectMapper.writeValueAsString(modelPackage),
            "{}",
            "{}",
            applyPayload.json(),
            applyPayload.checksum(),
            applyPlanJson,
            codec.checksum(objectMapper.readTree(applyPlanJson)),
            "b".repeat(64),
            RunStatus.PREVIEWED,
            objectMapper.writeValueAsString(new PreviewSummary(1, 1, 0, 1, 0, 0, 0)),
            Instant.now().plusSeconds(600),
            "actor-a",
            Instant.now()
        );
    }

    private static PreviewAuditFacts facts(PersistedRun run) {
        return new PreviewAuditFacts(
            run.id(),
            run.tenantId(),
            run.planId(),
            run.actorId(),
            run.previewHash(),
            run.packageChecksum(),
            new PreviewSummary(1, 1, 0, 1, 0, 0, 0),
            PreviewAuditOutcome.SUCCESS,
            List.of(),
            "request-correlation"
        );
    }

    private static void createTables(Statement statement) throws Exception {
        statement.execute(
            """
            create table modeling_model_spec_import_run (
                id uuid primary key, tenant_id varchar(128), plan_id uuid, package_checksum varchar(64),
                package_schema_version varchar(64), package_json jsonb, request_json jsonb,
                context_snapshot_json jsonb, apply_payload_json jsonb, apply_payload_checksum varchar(64),
                apply_plan_json jsonb, apply_plan_checksum varchar(64), preview_hash varchar(64), status varchar(32),
                summary_json jsonb, expires_at timestamp, created_by varchar(128), created_date timestamp,
                last_modified_by varchar(128), last_modified_date timestamp, payload_redacted_at timestamp
            )
            """
        );
        statement.execute(
            """
            create table modeling_model_spec_import_run_item (
                id uuid primary key, run_id uuid, seq int, dbt_unique_id varchar(256), node_type varchar(64),
                canonical boolean, action varchar(32), conversion_mode varchar(32), package_checksum varchar(64),
                model_spec_checksum varchar(64), current_model_spec_id uuid, current_revision int,
                proposed_model_spec_json jsonb, proposed_implementation_json jsonb, dependency_json jsonb,
                source_snapshot_json jsonb, issues_json jsonb, created_date timestamp, last_modified_date timestamp
            )
            """
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
