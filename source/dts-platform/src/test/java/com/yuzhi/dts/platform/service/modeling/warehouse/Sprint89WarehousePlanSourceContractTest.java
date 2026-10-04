package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Sprint89WarehousePlanSourceContractTest {

    @Test
    void requiresBothObservedVersionsForAnExplicitReconfirmation() {
        SourceBindingCommand incomplete = new SourceBindingCommand(
            UUID.randomUUID(),
            null,
            null,
            ConfirmationStatus.CONFIRMED,
            null,
            SourceAction.RECONFIRM,
            "schema-v1",
            null
        );

        assertThat(WarehousePlanContract.validateSourceInventoryCommand(new SourceInventoryCommand(List.of(incomplete))))
            .extracting(WarehousePlanContract.DomainIssue::code)
            .containsExactly("SOURCE_RECONFIRM_VERSION_REQUIRED");
    }
}
