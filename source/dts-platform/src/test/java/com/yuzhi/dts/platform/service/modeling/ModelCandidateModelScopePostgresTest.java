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
class ModelCandidateModelScopePostgresTest {
    private static final String CHANGELOG = "config/liquibase/changelog/20260908_02_model_candidate_model_scope.xml";
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    @Test
    void scopesReadsBeforeLimitingAndSerializesConcurrentAdmissionsByModel() throws Exception {
        var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("create schema scoped_reads");
        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=scoped_reads";
        var source = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            create table modeling_warehouse_plan (id uuid primary key, tenant_id text);
            create table modeling_model_release_candidate (
                id uuid primary key, tenant_id text, plan_id uuid, environment text, status text, version int,
                idempotency_key text, request_hash text, created_by text, created_date timestamptz,
                submitted_by text, submitted_date timestamptz, approved_by text, approved_date timestamptz,
                published_by text, published_date timestamptz, last_modified_by text, last_modified_date timestamptz,
                origin text, execution_target_key text, adapter text, profile_key text, target_name text
            );
            create table modeling_model_release_candidate_entry (
                id uuid primary key, tenant_id text, candidate_id uuid, plan_id uuid, model_spec_id uuid,
                revision int, checksum text, implementation_id uuid, implementation_mode text, status text,
                sort_order int, selected_reason text
            );
            """);
        var repository = new com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository(jdbc);
        var plan = java.util.UUID.randomUUID();
        var selectedModel = java.util.UUID.randomUUID();
        var oldest = fixture(plan, selectedModel, "dev");
        jdbc.update("insert into modeling_warehouse_plan values (?, 'tenant')", plan);
        repository.insert(oldest);
        for (int i = 0; i < 4; i++) repository.insert(fixture(plan, java.util.UUID.randomUUID(), "dev"));
        repository.insert(fixture(plan, selectedModel, "test"));
        assertThat(repository.listForModelScope("tenant", plan, "dev", java.util.List.of(selectedModel)))
            .extracting(ModelReleaseCandidateContract.CandidateView::id).containsExactly(oldest.id());
        assertThat(repository.listForModelScope("other-tenant", plan, "dev", java.util.List.of(selectedModel))).isEmpty();
        assertThat(repository.listForModelScope("tenant", java.util.UUID.randomUUID(), "dev", java.util.List.of(selectedModel))).isEmpty();
        assertThat(repository.listForModelScope("tenant", plan, "dev", java.util.List.of(java.util.UUID.randomUUID()))).isEmpty();

        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        var duplicate = java.util.UUID.randomUUID();
        assertThat(compete(repository, manager, plan, duplicate, duplicate)).isEqualTo(1);
        assertThat(compete(repository, manager, plan, java.util.UUID.randomUUID(), java.util.UUID.randomUUID())).isEqualTo(2);
    }

    private static int compete(com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository repository,
        org.springframework.transaction.PlatformTransactionManager manager, java.util.UUID plan,
        java.util.UUID first, java.util.UUID second) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var model : java.util.List.of(first, second)) {
                tasks.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    return new org.springframework.transaction.support.TransactionTemplate(manager).execute(status -> {
                        repository.lockPlanForCandidate("tenant", plan);
                        if (repository.listActiveForPlan("tenant", plan).stream().anyMatch(candidate ->
                            ModelCandidateScopePolicy.conflicts(candidate, ModelReleaseCandidateContract.CandidateOrigin.BATCH_WORKBENCH,
                                "dev", java.util.List.of(model)))) return false;
                        repository.insert(fixture(plan, model, "dev"));
                        return true;
                    });
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int accepted = 0;
            for (var task : tasks) if (task.get(15, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            return accepted;
        }
    }

    private static ModelReleaseCandidateContract.CandidateView fixture(java.util.UUID plan, java.util.UUID model, String environment) {
        var id = java.util.UUID.randomUUID();
        var now = java.time.Instant.now();
        return new ModelReleaseCandidateContract.CandidateView(id, "tenant", plan, environment,
            ModelLifecycleContract.DeliveryStatus.DRAFT, 1, id.toString(), "a".repeat(64),
            new ModelLifecycleContract.DeliveryAuditView("actor", now, null, null, null, null, null, null), "actor", now,
            java.util.List.of(new ModelReleaseCandidateContract.EntryView(java.util.UUID.randomUUID(), "tenant", id, plan,
                model, 1, "b".repeat(64), null, ModelSpecContract.ImplementationMode.DBT_MANAGED,
                ModelLifecycleContract.DeliveryStatus.DRAFT, 0, "MATERIALIZATION_ROOT")),
            ModelReleaseCandidateContract.CandidateOrigin.BATCH_WORKBENCH, null, null, null, null);
    }

    @Test
    void preservesHistoryAllowsDisjointBuildsKeepsClaimsAndBlocksUnsafeRollback() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("""
            create table modeling_model_release_candidate (
                id int primary key, tenant_id text not null, plan_id int not null, origin text not null, status text not null
            );
            create unique index uk_model_release_candidate_active_plan on modeling_model_release_candidate(tenant_id, plan_id)
                where origin <> 'SCHEMA_ONLY_INTENT' and status not in ('REJECTED','ROLLED_BACK','STALE','CANCELLED','PUBLISHED');
            create table modeling_model_release_candidate_entry (candidate_id int, active_claim_key text);
            create unique index uk_model_release_candidate_entry_active_claim on modeling_model_release_candidate_entry(active_claim_key);
            insert into modeling_model_release_candidate values (1,'tenant',1,'BATCH_WORKBENCH','BUILT');
            insert into modeling_model_release_candidate_entry values (1,'model-a-dev');
            """);
        migrate(false);
        migrate(true);
        migrate(false);
        jdbc.update("insert into modeling_model_release_candidate values (2,'tenant',1,'BATCH_WORKBENCH','BUILT'),(3,'tenant',1,'SINGLE_MODEL_INTENT','BUILDING')");
        jdbc.update("insert into modeling_model_release_candidate_entry values (2,'model-b-dev'),(3,'model-c-dev')");
        assertThat(jdbc.queryForObject("select status from modeling_model_release_candidate where id=1", String.class)).isEqualTo("BUILT");
        assertThatThrownBy(() -> jdbc.update("insert into modeling_model_release_candidate_entry values (3,'model-a-dev')"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> migrate(true)).hasStackTraceContaining("ROLLBACK_BLOCKED_MULTIPLE_ACTIVE_MODEL_CANDIDATES");
        assertThat(jdbc.queryForObject("select count(*) from modeling_model_release_candidate", Integer.class)).isEqualTo(3);
        jdbc.update("update modeling_model_release_candidate set status='CANCELLED' where id in (2,3)");
        migrate(true);
        assertThatThrownBy(() -> jdbc.update("insert into modeling_model_release_candidate values (4,'tenant',1,'BATCH_WORKBENCH','DRAFT')"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private void migrate(boolean rollback) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(),
                 DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            if (rollback) liquibase.rollback(1, new Contexts(), new LabelExpression());
            else liquibase.update(new Contexts(), new LabelExpression());
        }
    }
}
