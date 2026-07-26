package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.DataMartContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.PlanBaselineCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.UpdateCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataMartContractTest {

    private static final UUID FINANCE_DOMAIN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROJECT_DOMAIN = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void acceptsAPlanningDataMartAcrossMultipleBusinessCategories() {
        CreateCommand command = new CreateCommand(
            "FINANCE_MART",
            "财务分析集市",
            "支持财务项目和预算分析",
            "owner-1",
            List.of(FINANCE_DOMAIN, PROJECT_DOMAIN),
            "create-finance-mart"
        );

        assertThat(DataMartContract.validateCreate(command)).isEmpty();
        assertThat(
            DataMartContract.validateUpdate(
                new UpdateCommand(command.name(), command.purpose(), command.ownerId(), command.domainIds())
            )
        )
            .isEmpty();
    }

    @Test
    void rejectsInvalidCodesDuplicateDomainsAndMissingOwnership() {
        CreateCommand command = new CreateCommand(
            "finance-mart",
            " ",
            " ",
            " ",
            List.of(FINANCE_DOMAIN, FINANCE_DOMAIN),
            " "
        );

        assertThat(DataMartContract.validateCreate(command))
            .extracting(FieldIssue::code)
            .contains(
                "DATA_MART_CODE_INVALID",
                "DATA_MART_NAME_REQUIRED",
                "DATA_MART_PURPOSE_REQUIRED",
                "DATA_MART_OWNER_REQUIRED",
                "DATA_MART_DOMAIN_IDS_INVALID",
                "DATA_MART_IDEMPOTENCY_KEY_REQUIRED"
            );
    }

    @Test
    void baselineUsesUniqueCurrentPlanningObjectIdsAndCasVersion() {
        assertThat(DataMartContract.validatePlanBaseline(new PlanBaselineCommand(List.of(), 0))).isEmpty();
        assertThat(
            DataMartContract.validatePlanBaseline(
                new PlanBaselineCommand(List.of(FINANCE_DOMAIN, FINANCE_DOMAIN), -1)
            )
        )
            .extracting(FieldIssue::code)
            .containsExactly(
                "DATA_MART_BASELINE_VERSION_INVALID",
                "DATA_MART_BASELINE_IDS_INVALID"
            );
    }
}
