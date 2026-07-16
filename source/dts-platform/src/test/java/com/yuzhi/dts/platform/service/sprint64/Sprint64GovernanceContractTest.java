package com.yuzhi.dts.platform.service.sprint64;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Sprint64GovernanceContractTest {

    @Test
    void exposesTheSixWarehouseLayersWithOptionalStgRules() {
        List<Sprint64GovernanceContract.WarehouseLayerDto> layers = Sprint64GovernanceContract.warehouseLayers();

        assertThat(layers).extracting(Sprint64GovernanceContract.WarehouseLayerDto::code)
            .containsExactly("ODS_RAW", "ODS_STANDARDIZED", "STG", "DWD", "DWS", "ADS");
        assertThat(Sprint64GovernanceContract.isLayerFlowAllowed("ODS_RAW", "ODS_STANDARDIZED")).isTrue();
        assertThat(Sprint64GovernanceContract.isLayerFlowAllowed("ODS_STANDARDIZED", "STG")).isTrue();
        assertThat(Sprint64GovernanceContract.isLayerFlowAllowed("ODS_RAW", "DWD")).isFalse();
        assertThat(Sprint64GovernanceContract.isLayerFlowAllowed("DWD", "ADS")).isTrue();
        Sprint64GovernanceContract.WarehouseLayerDto stg = Sprint64GovernanceContract.resolveLayer("STG").orElseThrow();
        assertThat(stg.optional()).isTrue();
        assertThat(stg.businessOutput()).isFalse();
        assertThat(stg.dbtRole()).contains("staging");
    }

    @Test
    void shipsEightConformedDimensionsAndRequiresGrainForDwdAndAbove() {
        assertThat(Sprint64GovernanceContract.conformedDimensions()).hasSize(8);
		assertThat(Sprint64GovernanceContract.validateGrain("ODS_RAW", "", List.of()).status()).isEqualTo("not_required");
		assertThat(Sprint64GovernanceContract.validateGrain("STG", "", List.of()).status()).isEqualTo("not_required");
        assertThat(Sprint64GovernanceContract.validateGrain("UNKNOWN", "", List.of()).status()).isEqualTo("blocked");
        assertThat(Sprint64GovernanceContract.validateGrain("DWD", "", List.of()).status()).isEqualTo("blocked");
        assertThat(Sprint64GovernanceContract.validateGrain("DWD", "订单明细", List.of("order_id")).status()).isEqualTo("ready");
    }

    @Test
    void processIdsAreStableApiKeysAndRejectUnsafeValues() {
        assertThat(Sprint64GovernanceService.requiredProcessId(" Node_Plan-Loop ")).isEqualTo("node_plan-loop");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Sprint64GovernanceService.requiredProcessId("中文过程"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
