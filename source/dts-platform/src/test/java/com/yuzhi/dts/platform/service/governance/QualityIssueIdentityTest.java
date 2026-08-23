package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualityIssueIdentityTest {

    @Test
    void keepsTheProblemStableAcrossRunsAndSeparatesErrorCategories() {
        UUID datasetId = UUID.fromString("10000000-0000-0000-0000-000000000104");
        UUID bindingId = UUID.fromString("20000000-0000-0000-0000-000000000104");
        GovQualityRun first = run(datasetId, bindingId, "execution error");
        GovQualityRun repeated = run(datasetId, bindingId, "EXECUTION_ERROR");
        GovQualityRun different = run(datasetId, bindingId, "THRESHOLD_FAILED");

        assertThat(QualityIssueIdentity.problemKey(first)).isEqualTo(QualityIssueIdentity.problemKey(repeated));
        assertThat(QualityIssueIdentity.problemKey(different)).isNotEqualTo(QualityIssueIdentity.problemKey(first));
        assertThat(QualityIssueIdentity.problemKey(first)).startsWith(QualityIssueIdentity.problemPrefix(first));
    }

    private GovQualityRun run(UUID datasetId, UUID bindingId, String category) {
        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(bindingId);
        GovQualityRun run = new GovQualityRun();
        run.setDatasetId(datasetId);
        run.setBinding(binding);
        run.setErrorCategory(category);
        return run;
    }
}
