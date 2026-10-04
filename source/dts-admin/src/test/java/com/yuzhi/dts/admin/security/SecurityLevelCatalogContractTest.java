package com.yuzhi.dts.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import org.junit.jupiter.api.Test;

class SecurityLevelCatalogContractTest {

    @Test
    void normalizesPersonnelAndDataAliases() {
        assertThat(SecurityLevelCatalog.normalizePersonnelCode("1")).isEqualTo("IMPORTANT");
        assertThat(SecurityLevelCatalog.normalizePersonnelCode("IM")).isEqualTo("IMPORTANT");
        assertThat(SecurityLevelCatalog.normalizePersonnelCode("CONFIDENTIAL")).isEqualTo("CORE");

        assertThat(SecurityLevelCatalog.normalizeDataCode("DATA_TOP_SECRET")).isEqualTo("CONFIDENTIAL");
        assertThat(SecurityLevelCatalog.normalizeDataCode("GENERAL")).isEqualTo("INTERNAL");
        assertThat(SecurityLevelCatalog.normalizePrefixedDataCode("CONFIDENTIAL")).isEqualTo("DATA_CONFIDENTIAL");
    }

    @Test
    void resolvesPersonnelMaxDataLevelWithoutChangingDataCodes() {
        assertThat(SecurityLevelCatalog.normalizeMaxDataCode("GENERAL")).isEqualTo("SECRET");
        assertThat(SecurityLevelCatalog.normalizeMaxDataCode("INTERNAL")).isEqualTo("INTERNAL");
        assertThat(SecurityLevelCatalog.maxDataRankForPersonnel("GENERAL")).isEqualTo(2);
        assertThat(SecurityLevelCatalog.canPersonnelAccessData("GENERAL", "SECRET")).isTrue();
        assertThat(SecurityLevelCatalog.canPersonnelAccessData("GENERAL", "CONFIDENTIAL")).isFalse();
        assertThat(SecurityLevelCatalog.canPersonnelAccessData("IMPORTANT", "CONFIDENTIAL")).isTrue();
        assertThat(SecurityLevelCatalog.dataStorageTokens(SecurityLevelCatalog.DataSecurityLevel.CONFIDENTIAL))
            .containsExactly("CONFIDENTIAL", "DATA_CONFIDENTIAL", "TOP_SECRET", "DATA_TOP_SECRET");
    }
}
