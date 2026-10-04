package com.yuzhi.dts.platform.security.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityPolicyLevelAdapterTest {

    @Test
    void dataLevelAdapterUsesSharedCatalogAliases() {
        assertThat(DataLevel.normalize("DATA_TOP_SECRET")).isEqualTo(DataLevel.DATA_CONFIDENTIAL);
        assertThat(DataLevel.normalize("非密")).isEqualTo(DataLevel.DATA_PUBLIC);
        assertThat(DataLevel.normalize("GENERAL")).isEqualTo(DataLevel.DATA_INTERNAL);
    }

    @Test
    void personnelLevelAdapterUsesSharedCatalogAccessRule() {
        assertThat(PersonnelLevel.normalize("1")).isEqualTo(PersonnelLevel.IMPORTANT);
        assertThat(PersonnelLevel.GENERAL.rank()).isEqualTo(DataLevel.DATA_SECRET.rank());
        assertThat(PersonnelLevel.IMPORTANT.maxClassification()).isEqualTo("CONFIDENTIAL");
    }
}
