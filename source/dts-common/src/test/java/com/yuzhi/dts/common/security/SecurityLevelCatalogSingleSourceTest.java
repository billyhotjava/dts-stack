package com.yuzhi.dts.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the canonical data-classification ladder as the single source of truth.
 *
 * <p>Historically several services carried private copies of this ladder. One of them
 * (analysis/dashboard publication) had SECRET and CONFIDENTIAL swapped and carried an extra
 * SENSITIVE step, which let a CONFIDENTIAL asset pass a publication gate that should have
 * blocked it. These tests freeze the ordering and the accepted aliases so a divergent copy
 * cannot be reintroduced silently.
 */
class SecurityLevelCatalogSingleSourceTest {

    @Test
    void dataLadderMustKeepConfidentialAboveSecret() {
        assertThat(SecurityLevelCatalog.dataRank("PUBLIC")).isEqualTo(0);
        assertThat(SecurityLevelCatalog.dataRank("INTERNAL")).isEqualTo(1);
        assertThat(SecurityLevelCatalog.dataRank("SECRET")).isEqualTo(2);
        assertThat(SecurityLevelCatalog.dataRank("CONFIDENTIAL")).isEqualTo(3);

        assertThat(SecurityLevelCatalog.dataRank("CONFIDENTIAL"))
            .isGreaterThan(SecurityLevelCatalog.dataRank("SECRET"));
    }

    @Test
    void dataLadderMustExposeExactlyFourStepsInOrder() {
        assertThat(SecurityLevelCatalog.dataCodesInOrder())
            .containsExactly("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");
    }

    @Test
    void chineseLabelsMustMatchTheGovernanceVocabulary() {
        assertThat(SecurityLevelCatalog.dataLabelsZh())
            .containsEntry("PUBLIC", "公开")
            .containsEntry("INTERNAL", "内部")
            .containsEntry("SECRET", "秘密")
            .containsEntry("CONFIDENTIAL", "机密");
    }

    /**
     * SENSITIVE was never a canonical step. Two independent call sites already collapsed it onto
     * SECRET, so it is accepted as an alias rather than becoming a fifth level. Without this,
     * parse() returns null and every downstream access check fails closed on legacy rows.
     */
    @Test
    void sensitiveMustResolveToSecretForLegacyValues() {
        assertThat(SecurityLevelCatalog.normalizeDataCode("SENSITIVE")).isEqualTo("SECRET");
        assertThat(SecurityLevelCatalog.normalizeDataCode("DATA_SENSITIVE")).isEqualTo("SECRET");
        assertThat(SecurityLevelCatalog.normalizeDataCode("敏感")).isEqualTo("SECRET");
        assertThat(SecurityLevelCatalog.dataRank("DATA_SENSITIVE")).isEqualTo(2);
    }

    @Test
    void sensitiveMustNotBecomeAFifthStep() {
        assertThat(SecurityLevelCatalog.dataCodes()).doesNotContain("SENSITIVE");
        assertThat(SecurityLevelCatalog.dataCodesInOrder()).hasSize(4);
    }

    @Test
    void prefixedFormMustRoundTripForEveryStep() {
        for (String code : SecurityLevelCatalog.dataCodesInOrder()) {
            assertThat(SecurityLevelCatalog.normalizePrefixedDataCode(code)).isEqualTo("DATA_" + code);
            assertThat(SecurityLevelCatalog.normalizeDataCode("DATA_" + code)).isEqualTo(code);
        }
    }

    /**
     * The publication gate asks "is the chosen level at least the dataset level?". With the old
     * private ladder, publishing a CONFIDENTIAL dataset as SECRET passed. It must not.
     */
    @Test
    void publishingConfidentialDataAsSecretMustCountAsDowngrade() {
        assertThat(SecurityLevelCatalog.isDataAtLeast("SECRET", "CONFIDENTIAL")).isFalse();
        assertThat(SecurityLevelCatalog.isDataDowngrade("CONFIDENTIAL", "SECRET")).isTrue();
        assertThat(SecurityLevelCatalog.isDataAtLeast("CONFIDENTIAL", "SECRET")).isTrue();
    }

    @Test
    void unknownValuesMustStayUnresolvedSoCallersCanFailClosed() {
        assertThat(SecurityLevelCatalog.dataRankOrNull("NOT_A_LEVEL")).isNull();
        assertThat(SecurityLevelCatalog.normalizeDataCode("NOT_A_LEVEL")).isNull();
        assertThat(SecurityLevelCatalog.dataRank("NOT_A_LEVEL")).isEqualTo(-1);
    }

    @Test
    void personnelLadderMustStayIndependentOfTheDataLadder() {
        assertThat(SecurityLevelCatalog.dataCodes()).doesNotContainAnyElementsOf(List.of("GENERAL", "IMPORTANT", "CORE"));
    }
}
