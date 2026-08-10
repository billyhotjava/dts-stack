package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.EXCLUDED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction.CONFIRM;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceAction.EXCLUDE;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Sprint89WarehousePlanSourceActionContractTest {

    @Test
    void acceptsTheConfirmAndExcludeActionsReturnedByAllowedActions() {
        SourceBindingCommand confirm = new SourceBindingCommand(
            UUID.randomUUID(),
            null,
            null,
            CONFIRMED,
            null,
            CONFIRM,
            null,
            null
        );
        SourceBindingCommand exclude = new SourceBindingCommand(
            UUID.randomUUID(),
            null,
            null,
            EXCLUDED,
            "not in scope",
            EXCLUDE,
            null,
            null
        );

        assertThat(WarehousePlanContract.validateSourceInventoryCommand(new SourceInventoryCommand(List.of(confirm, exclude))))
            .isEmpty();
    }

    @Test
    void rejectsActionStatusMismatchesAndVersionsOutsideReconfirmation() {
        SourceBindingCommand mismatchedConfirm = new SourceBindingCommand(
            UUID.randomUUID(),
            null,
            null,
            EXCLUDED,
            "not in scope",
            CONFIRM,
            null,
            null
        );
        SourceBindingCommand mismatchedExclude = new SourceBindingCommand(
            UUID.randomUUID(),
            null,
            null,
            CONFIRMED,
            null,
            EXCLUDE,
            "schema-v1",
            null
        );

        assertThat(
            WarehousePlanContract.validateSourceInventoryCommand(
                new SourceInventoryCommand(List.of(mismatchedConfirm, mismatchedExclude))
            )
        )
            .extracting(WarehousePlanContract.DomainIssue::code)
            .containsExactly("SOURCE_ACTION_STATUS_INVALID", "SOURCE_ACTION_STATUS_INVALID", "SOURCE_ACTION_VERSION_NOT_ALLOWED");
    }
}
