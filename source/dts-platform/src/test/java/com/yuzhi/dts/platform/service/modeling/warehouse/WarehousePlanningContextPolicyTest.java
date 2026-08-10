package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessCategoryMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessProcessMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehousePlanningContextPolicyTest {

    private static final UUID DEFAULT_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000087");

    @Test
    void defaultsOldPolicyPayloadsToSingleCategoryAndAutomaticSingleProcessSelection() {
        PlanningPolicyView policy = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand(
                "CLASSIC_ODS_DWD_DWS_ADS",
                "CLASSIC_LOWER_SNAKE",
                "PRESERVE_BUSINESS_HISTORY",
                "Asia/Shanghai"
            )
        );

        assertThat(policy.businessCategoryMode()).isEqualTo(BusinessCategoryMode.SINGLE_DEFAULT);
        assertThat(policy.defaultBusinessCategoryId()).isNull();
        assertThat(policy.businessProcessMode()).isEqualTo(BusinessProcessMode.AUTO_SELECT_SINGLE);
    }

    @Test
    void evaluatesExplicitPlanningContextWithoutChangingExistingGovernanceReadiness() {
        PlanningPolicyView policy = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand(
                "CLASSIC_ODS_DWD_DWS_ADS",
                "CLASSIC_LOWER_SNAKE",
                "PRESERVE_BUSINESS_HISTORY",
                "Asia/Shanghai",
                false,
                "KEY_AND_MEASURE",
                "BLOCKING",
                "MULTI_SELECT",
                DEFAULT_CATEGORY_ID,
                "MANAGED"
            )
        );

        assertThat(policy.businessCategoryMode()).isEqualTo(BusinessCategoryMode.MULTI_SELECT);
        assertThat(policy.defaultBusinessCategoryId()).isEqualTo(DEFAULT_CATEGORY_ID);
        assertThat(policy.businessProcessMode()).isEqualTo(BusinessProcessMode.MANAGED);
        assertThat(policy.readiness()).isEqualTo(WarehousePlanContract.PlanningPolicyReadiness.IMPLEMENTATION_READY);
        assertThat(policy.issues()).isEmpty();
    }

    @Test
    void rejectsUnsupportedPlanningContextModesWithStableIssueCodes() {
        PlanningPolicyView policy = WarehousePlanContract.evaluatePlanningPolicy(
            new PlanningPolicyCommand(null, null, null, null, false, null, null, "AUTO", null, "DEFAULT")
        );

        assertThat(policy.issues())
            .extracting(DomainIssue::code)
            .contains("BUSINESS_CATEGORY_MODE_UNSUPPORTED", "BUSINESS_PROCESS_MODE_UNSUPPORTED");
        assertThat(WarehousePlanContract.hasInvalidPolicyValues(policy)).isTrue();
    }
}
