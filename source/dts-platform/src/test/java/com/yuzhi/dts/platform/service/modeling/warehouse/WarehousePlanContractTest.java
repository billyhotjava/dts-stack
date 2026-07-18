package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.BASELINE_READY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.PUBLISHED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.MetricRequirement;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ProcessBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehousePlanContractTest {

    private static final UUID DOMAIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void acceptsNeutralCreateCommandAndRejectsRequestOwnedTenant() {
        CreateWarehousePlanCommand command = new CreateWarehousePlanCommand(
            "warehouse_operations_2026",
            "Operations warehouse",
            "Create reusable operational analysis",
            "First delivery scope",
            "owner-1",
            "department-1",
            BUSINESS_FIRST
        );

        assertThat(WarehousePlanContract.validateCreate(command)).isEmpty();
        assertThat(WarehousePlanContract.validateRequestedTenant(null)).isEmpty();
        assertThat(WarehousePlanContract.validateRequestedTenant("request-value"))
            .extracting(DomainIssue::code)
            .containsExactly("WAREHOUSE_PLAN_TENANT_NOT_ACCEPTED");
    }

    @Test
    void evaluatesOneBaselineFromConfirmedBusinessAndSourceFacts() {
        BusinessScope scope = readyScope();
        List<SourceBinding> sources = readySources();
        List<SourceBusinessMapping> mappings = readyMappings();
        PlanningPolicy policy = readyPolicy();

        assertThat(WarehousePlanContract.evaluateBaseline(scope, sources, mappings, policy).ready()).isTrue();
        assertThat(WarehousePlanContract.evaluateBaseline(scope, sources, mappings, policy).missingCodes()).isEmpty();
    }

    @Test
    void returnsStableMissingCodesWithoutTreatingCandidatesAsConfirmed() {
        assertThat(WarehousePlanContract.evaluateBaseline(null, List.of(), List.of(), null).missingCodes())
            .containsExactly(
                "BUSINESS_SCOPE_INCOMPLETE",
                "DOMAIN_PROCESS_CONFIRMATION_INCOMPLETE",
                "SOURCE_INVENTORY_INCOMPLETE",
                "SOURCE_BUSINESS_MAPPING_INCOMPLETE",
                "PLANNING_POLICY_INCOMPLETE"
            );

        assertThat(WarehousePlanContract.evaluateBaseline(readyScope(), readySources(), List.of(), readyPolicy()).ready()).isFalse();
        assertThat(WarehousePlanContract.evaluateBaseline(readyScope(), readySources(), List.of(), readyPolicy()).missingCodes())
            .containsExactly("SOURCE_BUSINESS_MAPPING_INCOMPLETE");
    }

    @Test
    void permitsOnlyExplicitLifecycleTransitions() {
        assertThat(WarehousePlanContract.canTransition(DRAFT, BASELINE_READY)).isTrue();
        assertThat(WarehousePlanContract.canTransition(PUBLISHED, DRAFT)).isFalse();
        assertThat(WarehousePlanContract.canTransition(DRAFT, DRAFT)).isFalse();
    }

    private static BusinessScope readyScope() {
        return new BusinessScope(
            true,
            List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)),
            List.of(new ProcessBinding("process-1", DOMAIN_ID, "ACCUMULATING_SNAPSHOT", CONFIRMED)),
            List.<MetricRequirement>of()
        );
    }

    private static List<SourceBinding> readySources() {
        return List.of(new SourceBinding(SOURCE_ID, CATALOG_TABLE, "asset-1", "schema-v1", CONFIRMED, null));
    }

    private static List<SourceBusinessMapping> readyMappings() {
        return List.of(new SourceBusinessMapping(UUID.randomUUID(), SOURCE_ID, DOMAIN_ID, "process-1", CONFIRMED, null));
    }

    private static PlanningPolicy readyPolicy() {
        return new PlanningPolicy("CLASSIC_ODS_DWD_DWS_ADS", "naming-policy-1", "PRESERVE_BUSINESS_HISTORY", "Asia/Shanghai");
    }
}
