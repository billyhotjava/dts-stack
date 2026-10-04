package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.UpdateCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubjectDomainContractTest {

    private static final UUID MART = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void acceptsAPlanningSubjectDomainAttachedToADataMart() {
        CreateCommand command = new CreateCommand("BUDGET_COCKPIT", "预算驾驶舱", "预算分析主题", MART, "create-budget-cockpit");

        assertThat(SubjectDomainContract.validateCreate(command)).isEmpty();
        assertThat(
            SubjectDomainContract.validateUpdate(
                new UpdateCommand(command.name(), command.purpose(), command.martId())
            )
        )
            .isEmpty();
    }

    @Test
    void rejectsInvalidCodeMissingNameMartAndIdempotencyKey() {
        CreateCommand command = new CreateCommand("budget-cockpit", " ", null, null, " ");

        assertThat(SubjectDomainContract.validateCreate(command))
            .extracting(FieldIssue::code)
            .contains(
                "SUBJECT_DOMAIN_CODE_INVALID",
                "SUBJECT_DOMAIN_NAME_REQUIRED",
                "SUBJECT_DOMAIN_MART_REQUIRED",
                "SUBJECT_DOMAIN_IDEMPOTENCY_KEY_REQUIRED"
            );
    }

    @Test
    void updateRequiresNameAndMart() {
        assertThat(SubjectDomainContract.validateUpdate(new UpdateCommand(" ", null, null)))
            .extracting(FieldIssue::code)
            .containsExactlyInAnyOrder("SUBJECT_DOMAIN_NAME_REQUIRED", "SUBJECT_DOMAIN_MART_REQUIRED");
    }
}
