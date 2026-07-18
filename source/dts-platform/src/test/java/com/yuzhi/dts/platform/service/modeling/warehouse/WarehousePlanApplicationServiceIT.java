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

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.UpdatePlanHeaderCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.MetricRequirement;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ProcessBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class WarehousePlanApplicationServiceIT {

    @Autowired
    private WarehousePlanApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsListsAndLoadsPlansInsideServerTenantScope() {
        String tenantA = tenant("scope-a");
        String tenantB = tenant("scope-b");
        String code = code("scope");

        try {
            WarehousePlanHeader tenantAPlan = service.create(tenantA, createCommand(code, BUSINESS_FIRST));
            WarehousePlanHeader tenantBPlan = service.create(tenantB, createCommand(code, ASSET_FIRST));

            assertThat(tenantAPlan.tenantId()).isEqualTo(tenantA);
            assertThat(tenantBPlan.tenantId()).isEqualTo(tenantB);
            assertThat(service.list(tenantA, null)).extracting(WarehousePlanHeader::id).contains(tenantAPlan.id()).doesNotContain(tenantBPlan.id());
            assertThat(service.get(tenantA, tenantAPlan.id())).isEqualTo(tenantAPlan);
            assertWarehouseError(() -> service.get(tenantA, tenantBPlan.id()), "WAREHOUSE_PLAN_NOT_FOUND", null);
        } finally {
            deleteTenant(tenantA);
            deleteTenant(tenantB);
        }
    }

    @Test
    void rejectsTenantScopedDuplicateCode() {
        String tenant = tenant("duplicate");
        String code = code("duplicate");

        try {
            service.create(tenant, createCommand(code, BUSINESS_FIRST));

            assertWarehouseError(() -> service.create(tenant, createCommand(code, ASSET_FIRST)), "WAREHOUSE_PLAN_CODE_CONFLICT", null);
        } finally {
            deleteTenant(tenant);
        }
    }

    @Test
    void detectsStaleHeaderVersionAndArchivesWithoutDeleting() {
        String tenant = tenant("version");

        try {
            WarehousePlanHeader created = service.create(tenant, createCommand(code("version"), BUSINESS_FIRST));
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
            WarehousePlanHeader plan = service.create(tenant, createCommand(code("edit_units"), BUSINESS_FIRST));
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
            WarehousePlanHeader plan = service.create(tenant, createCommand(code("baseline"), ASSET_FIRST));

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

    private CreateWarehousePlanCommand createCommand(String code, WarehousePlanContract.OnboardingMode mode) {
        return new CreateWarehousePlanCommand(
            code,
            "Neutral warehouse plan",
            "Reusable analysis objective",
            "Initial business scope",
            "owner-1",
            "department-1",
            mode
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
        jdbcTemplate.update("delete from modeling_warehouse_plan_source_mapping where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan_source where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan_metric_need where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan_process where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan_domain where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan_policy where tenant_id = ?", tenant);
        jdbcTemplate.update("delete from modeling_warehouse_plan where tenant_id = ?", tenant);
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

    private static String code(String prefix) {
        return "warehouse_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
