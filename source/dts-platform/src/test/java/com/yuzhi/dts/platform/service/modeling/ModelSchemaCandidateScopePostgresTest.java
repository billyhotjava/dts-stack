package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelSchemaCandidateScopePostgresTest {
    private static final String CHANGELOG = "config/liquibase/changelog/20260908_01_model_schema_candidate_scope.xml";
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    @Test
    void upgradesWithoutChangingHistoryPreservesOrdinaryUniquenessAndGuardsRollback() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("""
            create table modeling_model_release_candidate (
                id int primary key, tenant_id text not null, plan_id int not null, origin varchar(32) not null,
                status text not null, execution_target_key text, adapter text, profile_key text, target_name text,
                constraint ck_model_release_candidate_materialization_context check (origin in ('SINGLE_MODEL_INTENT','BATCH_WORKBENCH'))
            );
            create unique index uk_model_release_candidate_active_plan on modeling_model_release_candidate(tenant_id, plan_id)
                where status not in ('REJECTED','ROLLED_BACK','STALE','CANCELLED','PUBLISHED');
            create table modeling_model_release_candidate_entry (active_claim_key text);
            create unique index uk_model_release_candidate_entry_active_claim on modeling_model_release_candidate_entry(active_claim_key);
            insert into modeling_model_release_candidate (id,tenant_id,plan_id,origin,status) values (1,'tenant',1,'BATCH_WORKBENCH','BUILT');
            """);
        migrate(false);
        assertThat(jdbc.queryForObject("select status from modeling_model_release_candidate where id=1", String.class)).isEqualTo("BUILT");
        migrate(true);
        migrate(false);
        jdbc.update("insert into modeling_model_release_candidate (id,tenant_id,plan_id,origin,status) values (2,'tenant',1,'SCHEMA_ONLY_INTENT','BUILT'),(3,'tenant',1,'SCHEMA_ONLY_INTENT','BUILT')");
        assertThatThrownBy(() -> jdbc.update("insert into modeling_model_release_candidate (id,tenant_id,plan_id,origin,status) values (4,'tenant',1,'SINGLE_MODEL_INTENT','DRAFT')"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into modeling_model_release_candidate (id,tenant_id,plan_id,origin,status) values (5,'tenant',2,'UNRECOGNIZED','DRAFT')"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("insert into modeling_model_release_candidate_entry values ('model-claim')");
        assertThatThrownBy(() -> jdbc.update("insert into modeling_model_release_candidate_entry values ('model-claim')"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> migrate(true)).hasStackTraceContaining("ROLLBACK_BLOCKED_SCHEMA_CANDIDATE_HISTORY_EXISTS");
        assertThat(jdbc.queryForObject("select count(*) from modeling_model_release_candidate", Integer.class)).isEqualTo(3);
    }

    private void migrate(boolean rollback) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(),
                 DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            if (rollback) liquibase.rollback(1, new Contexts(), new LabelExpression());
            else liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    @Test
    void compiledSchemaPlanDoesNotRequireAnImplementationForLogicalUpstream() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("""
            create table modeling_model_spec (id uuid, tenant_id text, plan_id uuid, revision int, contract_version int, status text);
            create table modeling_model_spec_revision (model_spec_id uuid, tenant_id text, revision int, contract_version int, content_checksum text, snapshot_json jsonb);
            create table modeling_model_implementation (id uuid, tenant_id text, model_spec_id uuid, plan_id uuid, model_revision int,
                model_checksum text, implementation_revision int, current_implementation_checksum text, dbt_unique_id text,
                status text, ownership text, project_key text, input_mode text, inputs_json jsonb, field_mappings_json jsonb,
                settings_json jsonb, materialization text);
            """);
        var root = java.util.UUID.randomUUID();
        var upstream = java.util.UUID.randomUUID();
        var plan = java.util.UUID.randomUUID();
        String checksum = "a".repeat(64);
        String snapshot = "{\"dependsOn\":[{\"modelSpecId\":\"" + upstream + "\",\"revision\":1}]}";
        for (var id : java.util.List.of(root, upstream)) {
            jdbc.update("insert into modeling_model_spec values (?,'default',?,1,2,'DRAFT')", id, plan);
            jdbc.update("insert into modeling_model_spec_revision values (?,'default',1,2,?,?::jsonb)", id, checksum, id.equals(root) ? snapshot : "{}");
        }
        jdbc.update("""
            insert into modeling_model_implementation values (?,'default',?,?,1,?,1,?,'model.test.root','ACTIVE','DBT_MANAGED',
                'test','GENERATED','[{"generatorType":"DBT","config":{"buildMode":"SCHEMA_ONLY"}}]'::jsonb,'[]'::jsonb,'{}'::jsonb,'table')
            """, java.util.UUID.randomUUID(), root, plan, checksum, checksum);
        var codec = org.mockito.Mockito.mock(ModelSpecSnapshotCodec.class);
        for (var id : java.util.List.of(root, upstream)) {
            var model = org.mockito.Mockito.mock(ModelSpecContract.ModelSpecView.class);
            org.mockito.Mockito.when(model.id()).thenReturn(id);
            org.mockito.Mockito.when(model.revision()).thenReturn(1);
            org.mockito.Mockito.when(model.checksum()).thenReturn(checksum);
            org.mockito.Mockito.when(model.sourceRefs()).thenReturn(java.util.List.of());
            // PostgreSQL normalizes JSON whitespace; identity is determined by the stored dependency key.
            org.mockito.Mockito.when(codec.readView(org.mockito.ArgumentMatchers.argThat(value -> value != null && value.contains("dependsOn") == id.equals(root))))
                .thenReturn(model);
        }
        org.mockito.Mockito.when(codec.matchesStoredContentChecksum(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(checksum))).thenReturn(true);
        var adapter = new com.yuzhi.dts.platform.repository.modeling.ModelImplementationDependencyReadAdapter(jdbc, codec, new com.fasterxml.jackson.databind.ObjectMapper());
        var facts = adapter.readPlanFacts("default", plan, java.util.List.of(root));
        assertThat(facts.dependencies().models()).extracting(fact -> fact.model().id()).containsExactly(root);
        assertThat(ModelSchemaOnlySupport.isSchemaOnly(facts.implementations().get(root))).isTrue();
        jdbc.update("update modeling_model_implementation set inputs_json='[{\"generatorType\":\"DBT\",\"config\":{}}]'::jsonb");
        var dataFacts = adapter.readPlanFacts("default", plan, java.util.List.of(root));
        assertThat(dataFacts.dependencies().models()).extracting(fact -> fact.model().id()).containsExactlyInAnyOrder(root, upstream);
        assertThat(dataFacts.implementations()).doesNotContainKey(upstream);
    }

    @Test
    void previewReturnsBusinessBlockerWithoutPoisoningCallerTransaction() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        var readPort = org.mockito.Mockito.mock(ModelImplementationDependencyReadPort.class);
        var planId = java.util.UUID.randomUUID();
        var modelId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(readPort.readPlanFacts("default", planId, java.util.List.of(modelId)))
            .thenThrow(new ModelSpecException("MODEL_IMPLEMENTATION_REQUIRED", "Implementation is missing",
                ModelSpecException.Kind.UNPROCESSABLE));
        var target = new ModelImplementationDependencyService(readPort);
        var proxy = new org.springframework.aop.framework.ProxyFactory(target);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var properties = new com.yuzhi.dts.platform.config.ModelMaterializationProperties();
        properties.setExecutionTargetKey("postgres-primary");
        properties.setAdapter("postgres");
        var service = new ModelMaterializationPlanService((ModelImplementationDependencyService) proxy.getProxy(),
            org.mockito.Mockito.mock(ModelMaterializationPlanRelationPort.class), properties);
        var command = new ModelMaterializationPlanContract.PreviewCommand(planId, "dev", java.util.List.of(modelId),
            ModelMaterializationPlanContract.Strategy.WITH_MISSING_UPSTREAMS);
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status -> {
            var preview = service.preview("default", command);
            assertThat(preview.canStart()).isFalse();
            assertThat(preview.blockers()).extracting(ModelMaterializationPlanContract.Blocker::code)
                .containsExactly("MODEL_IMPLEMENTATION_REQUIRED");
            assertThat(status.isRollbackOnly()).isFalse();
        });
    }
}
