package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.ARCHIVED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.BASELINE_READY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.ASSET_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit.BUSINESS_SCOPE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.UpdatePlanHeaderCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanResult;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.MetricRequirement;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.InitialSourceRef;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ProcessBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class WarehousePlanApplicationServiceIT {

    @Autowired
    private WarehousePlanApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createsListsAndLoadsPlansInsideServerTenantScope() {
        String tenantA = tenant("scope-a");
        String tenantB = tenant("scope-b");
        String idempotencyKey = "scope-key-" + UUID.randomUUID();

        try {
            WarehousePlanHeader tenantAPlan = service.create(tenantA, createCommand(BUSINESS_FIRST, idempotencyKey)).plan();
            WarehousePlanHeader tenantBPlan = service.create(tenantB, createCommand(ASSET_FIRST, idempotencyKey)).plan();

            assertThat(tenantAPlan.tenantId()).isEqualTo(tenantA);
            assertThat(tenantBPlan.tenantId()).isEqualTo(tenantB);
            assertThat(tenantAPlan.code()).matches("wp_[0-9a-f]{32}");
            assertThat(tenantBPlan.code()).matches("wp_[0-9a-f]{32}");
            assertThat(service.list(tenantA, null)).extracting(WarehousePlanHeader::id).contains(tenantAPlan.id()).doesNotContain(tenantBPlan.id());
            assertThat(service.get(tenantA, tenantAPlan.id())).isEqualTo(tenantAPlan);
            assertWarehouseError(() -> service.get(tenantA, tenantBPlan.id()), "WAREHOUSE_PLAN_NOT_FOUND", null);
        } finally {
            deleteTenant(tenantA);
            deleteTenant(tenantB);
        }
    }

    @Test
    void replaysTheSameRequestAndRejectsAChangedPayloadForTheSameTenantKey() {
        String tenant = tenant("idempotency");
        String idempotencyKey = "retry-key-" + UUID.randomUUID();

        try {
            CreateWarehousePlanResult created = service.create(tenant, createCommand(BUSINESS_FIRST, idempotencyKey));
            CreateWarehousePlanResult replayed = service.create(tenant, createCommand(BUSINESS_FIRST, idempotencyKey));

            assertThat(created.replayed()).isFalse();
            assertThat(replayed.replayed()).isTrue();
            assertThat(replayed.planId()).isEqualTo(created.planId());
            assertThat(replayed.etag()).isEqualTo(created.etag());
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_warehouse_plan where tenant_id = ? and idempotency_key = ?",
                Long.class,
                tenant,
                idempotencyKey
            )).isEqualTo(1L);

            CreateWarehousePlanCommand changed = new CreateWarehousePlanCommand(
                "Changed name",
                "Reusable analysis objective",
                "Initial business scope",
                "owner-1",
                "department-1",
                BUSINESS_FIRST,
                List.of(),
                idempotencyKey
            );
            assertWarehouseError(
                () -> service.create(tenant, changed),
                "WAREHOUSE_PLAN_IDEMPOTENCY_CONFLICT",
                null
            );
        } finally {
            deleteTenant(tenant);
        }
    }

    @Test
    void replaysTheImmutableCreateSnapshotAfterThePlanAndSourcesChange() {
        String tenant = tenant("idempotency-snapshot");
        String idempotencyKey = "snapshot-key-" + UUID.randomUUID();
        CreateWarehousePlanCommand command = createCommand(ASSET_FIRST, idempotencyKey);

        try {
            CreateWarehousePlanResult created = service.create(tenant, command);
            service.updateHeader(
                tenant,
                created.planId(),
                created.version(),
                new UpdatePlanHeaderCommand("Changed after create", "Changed objective", null, "owner-2", "department-2")
            );
            service.saveSources(tenant, created.planId(), 1, readySources());

            CreateWarehousePlanResult replayed = service.create(tenant, command);

            assertThat(replayed.replayed()).isTrue();
            assertThat(replayed.plan()).isEqualTo(created.plan());
            assertThat(replayed.version()).isEqualTo(1);
            assertThat(replayed.etag()).isEqualTo(created.etag());
            assertThat(replayed.initialSourceBindings()).isEqualTo(created.initialSourceBindings());
            assertThat(service.get(tenant, created.planId()).name()).isEqualTo("Changed after create");
        } finally {
            deleteTenant(tenant);
        }
    }

    @Test
    void atomicallyCreatesAssetFirstSourcesAndConvergesConcurrentRetries() throws Exception {
        String tenant = tenant("concurrent");
        String idempotencyKey = "concurrent-key-" + UUID.randomUUID();
        CreateWarehousePlanCommand command = createCommand(ASSET_FIRST, idempotencyKey);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String probeTable = "modeling_wp_create_probe_" + suffix;
        String probeFunction = "modeling_wp_create_probe_fn_" + suffix;
        String probeTrigger = "modeling_wp_create_probe_trg_" + suffix;
        CyclicBarrier start = new CyclicBarrier(3);
        var executor = Executors.newFixedThreadPool(2);

        try {
            executeCommitted(
                "create table " + probeTable + " (tenant_id varchar(255) not null, backend_pid integer not null, tx_id bigint not null)"
            );
            executeCommitted(
                """
                create function %s() returns trigger language plpgsql as $probe$
                begin
                  insert into %s (tenant_id, backend_pid, tx_id) values (NEW.tenant_id, pg_backend_pid(), txid_current());
                  perform pg_sleep(1.0);
                  return NEW;
                end;
                $probe$
                """.formatted(probeFunction, probeTable)
            );
            executeCommitted(
                "create trigger " +
                probeTrigger +
                " before insert on modeling_warehouse_plan for each row when (NEW.tenant_id = '" +
                tenant +
                "') execute function " +
                probeFunction +
                "()"
            );

            HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
            assertThat(hikari.getMaximumPoolSize()).as("the concurrency test needs at least two database connections").isGreaterThanOrEqualTo(2);
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return service.create(tenant, command);
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return service.create(tenant, command);
            });
            start.await(10, TimeUnit.SECONDS);
            boolean observedConcurrentConnections = false;
            long observationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < observationDeadline) {
                if (hikari.getHikariPoolMXBean().getActiveConnections() >= 2) {
                    observedConcurrentConnections = true;
                    break;
                }
                Thread.sleep(10);
            }
            CreateWarehousePlanResult firstResult = first.get(30, TimeUnit.SECONDS);
            CreateWarehousePlanResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(observedConcurrentConnections).as("two create transactions must overlap in the pool").isTrue();
            assertThat(firstResult.planId()).isEqualTo(secondResult.planId());
            assertThat(List.of(firstResult.replayed(), secondResult.replayed())).containsExactlyInAnyOrder(false, true);
            assertThat(firstResult.initialSourceBindings()).hasSize(1);
            assertThat(secondResult.initialSourceBindings()).hasSize(1);
            assertThat(firstResult.initialSourceBindings().getFirst().confirmationStatus())
                .isEqualTo(WarehousePlanContract.ConfirmationStatus.CANDIDATE);
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_warehouse_plan where tenant_id = ?",
                Long.class,
                tenant
            )).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_warehouse_plan_source where tenant_id = ?",
                Long.class,
                tenant
            )).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                "select count(distinct backend_pid) from " + probeTable + " where tenant_id = ?",
                Long.class,
                tenant
            )).as("the trigger must observe two PostgreSQL sessions").isEqualTo(2L);
            assertThat(jdbcTemplate.queryForObject(
                "select count(distinct tx_id) from " + probeTable + " where tenant_id = ?",
                Long.class,
                tenant
            )).as("the trigger must observe two distinct create transactions").isEqualTo(2L);
        } finally {
            executor.shutdownNow();
            executeCommitted("drop trigger if exists " + probeTrigger + " on modeling_warehouse_plan");
            executeCommitted("drop function if exists " + probeFunction + "()");
            executeCommitted("drop table if exists " + probeTable);
            deleteTenant(tenant);
        }
    }

    @Test
    void rollsBackThePlanHeaderWhenInitialSourcePersistenceFails() {
        String tenant = tenant("source-rollback");
        String idempotencyKey = "source-rollback-" + UUID.randomUUID();
        String rejectedSourceId = "asset-force-rollback-" + UUID.randomUUID();
        String constraintName = "ck_wp_source_test_" + UUID.randomUUID().toString().replace("-", "");
        CreateWarehousePlanCommand command = new CreateWarehousePlanCommand(
            "Source rollback plan",
            null,
            null,
            "owner-1",
            "department-1",
            ASSET_FIRST,
            List.of(new InitialSourceRef(CATALOG_TABLE, rejectedSourceId, "schema-v1")),
            idempotencyKey
        );

        try {
            executeCommitted(
                "alter table modeling_warehouse_plan_source add constraint " +
                constraintName +
                " check (source_id <> '" +
                rejectedSourceId +
                "')"
            );
            assertThatThrownBy(() -> service.create(tenant, command))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_warehouse_plan where tenant_id = ? and idempotency_key = ?",
                Long.class,
                tenant,
                idempotencyKey
            )).isZero();
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_warehouse_plan_source where tenant_id = ?",
                Long.class,
                tenant
            )).isZero();
        } finally {
            executeCommitted("alter table modeling_warehouse_plan_source drop constraint if exists " + constraintName);
            deleteTenant(tenant);
        }
    }

    @Test
    void detectsStaleHeaderVersionAndArchivesWithoutDeleting() {
        String tenant = tenant("version");

        try {
            WarehousePlanHeader created = service.create(tenant, createCommand(BUSINESS_FIRST, "version-" + UUID.randomUUID())).plan();
            WarehousePlanHeader updated = service.updateHeader(
                tenant,
                created.id(),
                created.version(),
                new UpdatePlanHeaderCommand("Renamed warehouse", "Updated objective", "Updated scope", "owner-2", "department-2")
            );

            assertThat(updated.name()).isEqualTo("Renamed warehouse");
            assertThat(updated.version()).isEqualTo(2);
            assertWarehouseError(
                () -> service.updateHeader(
                    tenant,
                    created.id(),
                    created.version(),
                    new UpdatePlanHeaderCommand("Stale overwrite", null, null, "owner-3", null)
                ),
                "WAREHOUSE_PLAN_VERSION_CONFLICT",
                2
            );

            WarehousePlanHeader archived = service.archive(tenant, created.id(), updated.version());
            assertThat(archived.lifecycleStatus()).isEqualTo(ARCHIVED);
            assertThat(archived.version()).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_warehouse_plan where tenant_id = ? and id = ?",
                Long.class,
                tenant,
                created.id()
            )).isEqualTo(1L);
        } finally {
            deleteTenant(tenant);
        }
    }

    @Test
    void keepsBusinessScopeAndSourcesOnIndependentVersions() {
        String tenant = tenant("edit-units");

        try {
            WarehousePlanHeader plan = service.create(tenant, createCommand(BUSINESS_FIRST, "edit-units-" + UUID.randomUUID())).plan();
            BusinessScope scope = readyScope();
            List<SourceBinding> sources = readySources();

            Versioned<BusinessScope> savedScope = service.saveBusinessScope(tenant, plan.id(), 1, scope);
            Versioned<List<SourceBinding>> savedSources = service.saveSources(tenant, plan.id(), 1, sources);

            assertThat(savedScope.version()).isEqualTo(2);
            assertThat(savedSources.version()).isEqualTo(2);
            assertThat(savedSources.value()).extracting(SourceBinding::id).containsExactly(sources.getFirst().id());
            assertThatThrownBy(() -> service.saveBusinessScope(tenant, plan.id(), 1, scope))
                .isInstanceOfSatisfying(WarehousePlanException.class, error -> {
                    assertThat(error.code()).isEqualTo("WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT");
                    assertThat(error.currentVersion()).isEqualTo(2);
                    assertThat(error.editUnit()).isEqualTo(BUSINESS_SCOPE);
                });
        } finally {
            deleteTenant(tenant);
        }
    }

    @Test
    void blocksIncompleteBaselineAndConfirmsReadyBaselineAtomically() {
        String tenant = tenant("baseline");

        try {
            WarehousePlanHeader plan = service.create(tenant, createCommand(ASSET_FIRST, "baseline-" + UUID.randomUUID())).plan();

            PlanningBaseline incomplete = service.getBaseline(tenant, plan.id());
            assertThat(incomplete.ready()).isFalse();
            assertThat(incomplete.missingCodes()).contains("CATEGORY_SCOPE_INCOMPLETE", "SOURCE_INVENTORY_INCOMPLETE");
            assertWarehouseError(
                () -> service.confirmBaseline(tenant, plan.id(), plan.version()),
                "WAREHOUSE_PLAN_BASELINE_INCOMPLETE",
                null
            );

            BusinessScope scope = readyScope();
            List<SourceBinding> sources = readySources();
            List<SourceBusinessMapping> mappings = readyMappings(sources.getFirst().id(), scope.domainBindings().getFirst().domainId());
            PlanningPolicy policy = readyPolicy();

            service.saveBusinessScope(tenant, plan.id(), 1, scope);
            service.saveSources(tenant, plan.id(), 1, sources);
            service.saveSourceMappings(tenant, plan.id(), 1, mappings);
            service.savePolicy(tenant, plan.id(), 1, policy);

            assertThat(service.getBaseline(tenant, plan.id()).ready()).isTrue();
            assertThat(service.confirmBaseline(tenant, plan.id(), plan.version()).ready()).isTrue();
            WarehousePlanHeader confirmed = service.get(tenant, plan.id());
            assertThat(confirmed.lifecycleStatus()).isEqualTo(BASELINE_READY);
            assertThat(confirmed.version()).isEqualTo(2);
        } finally {
            deleteTenant(tenant);
        }
    }

    private CreateWarehousePlanCommand createCommand(WarehousePlanContract.OnboardingMode mode, String idempotencyKey) {
        return new CreateWarehousePlanCommand(
            "Neutral warehouse plan",
            "Reusable analysis objective",
            "Initial business scope",
            "owner-1",
            "department-1",
            mode,
            mode == ASSET_FIRST ? List.of(new InitialSourceRef(CATALOG_TABLE, "asset-1", "schema-v1")) : List.of(),
            idempotencyKey
        );
    }

    private void assertWarehouseError(Runnable action, String code, Integer currentVersion) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(WarehousePlanException.class, error -> {
                assertThat(error.code()).isEqualTo(code);
                assertThat(error.currentVersion()).isEqualTo(currentVersion);
            });
    }

    private void deleteTenant(String tenant) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("delete from modeling_warehouse_plan_source_mapping where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_source where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_metric_need where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_process where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_domain where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan_policy where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_warehouse_plan where tenant_id = ?", tenant);
        });
    }

    private void executeCommitted(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to execute committed test SQL", exception);
        }
    }

    private static BusinessScope readyScope() {
        UUID domainId = UUID.randomUUID();
        return new BusinessScope(
            true,
            List.of(new DomainBinding(domainId, CONFIRMED)),
            List.of(new ProcessBinding("process-1", domainId, "ACCUMULATING_SNAPSHOT", CONFIRMED)),
            List.<MetricRequirement>of()
        );
    }

    private static List<SourceBinding> readySources() {
        return List.of(new SourceBinding(UUID.randomUUID(), CATALOG_TABLE, "asset-1", "schema-v1", CONFIRMED, null));
    }

    private static List<SourceBusinessMapping> readyMappings(UUID sourceId, UUID domainId) {
        return List.of(new SourceBusinessMapping(UUID.randomUUID(), sourceId, domainId, "process-1", CONFIRMED, "confirmed"));
    }

    private static PlanningPolicy readyPolicy() {
        return new PlanningPolicy("CLASSIC_ODS_DWD_DWS_ADS", "naming-policy-1", "PRESERVE_BUSINESS_HISTORY", "Asia/Shanghai");
    }

    private static String tenant(String prefix) {
        return "it-warehouse-" + prefix + "-" + UUID.randomUUID();
    }

}
