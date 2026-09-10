package com.yuzhi.dts.analytics.service.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import org.junit.jupiter.api.Test;

/**
 * Regression cover for the publication classification gate (S10DC-84 / S10DC-87).
 *
 * <p>This service used to own a private ladder that ranked
 * PUBLIC(0) &lt; INTERNAL(1) &lt; CONFIDENTIAL(2) &lt; SENSITIVE(3) &lt; SECRET(4).
 * That put CONFIDENTIAL — the highest level in the platform vocabulary — below SECRET, so
 * "publication classification cannot be lower than the dataset classification" accepted a
 * CONFIDENTIAL dataset published as SECRET. It also decoded S0..S4 in the opposite direction to
 * {@code ClassificationMapper} (S1=CONFIDENTIAL ... S4=PUBLIC).
 */
class AnalysisPublicationClassificationLadderTest {

    @Test
    void rankMustFollowTheCanonicalLadder() {
        assertThat(AnalysisPublicationService.classificationRank("DATA_PUBLIC")).isEqualTo(0);
        assertThat(AnalysisPublicationService.classificationRank("DATA_INTERNAL")).isEqualTo(1);
        assertThat(AnalysisPublicationService.classificationRank("DATA_SECRET")).isEqualTo(2);
        assertThat(AnalysisPublicationService.classificationRank("DATA_CONFIDENTIAL")).isEqualTo(3);
    }

    @Test
    void confidentialMustOutrankSecret() {
        assertThat(AnalysisPublicationService.classificationRank("DATA_CONFIDENTIAL"))
            .isGreaterThan(AnalysisPublicationService.classificationRank("DATA_SECRET"));
    }

    /**
     * The gate compares publication rank against dataset rank. Publishing CONFIDENTIAL data under
     * SECRET must register as lower, which is what makes it a blocker.
     */
    @Test
    void publishingConfidentialDatasetAsSecretMustRankLower() {
        int published = AnalysisPublicationService.classificationRank("DATA_SECRET");
        int dataset = AnalysisPublicationService.classificationRank("DATA_CONFIDENTIAL");
        assertThat(published).isLessThan(dataset);
    }

    @Test
    void publishingConfidentialDatasetAsConfidentialMustPass() {
        int published = AnalysisPublicationService.classificationRank("DATA_CONFIDENTIAL");
        int dataset = AnalysisPublicationService.classificationRank("DATA_CONFIDENTIAL");
        assertThat(published).isGreaterThanOrEqualTo(dataset);
    }

    @Test
    void normalizeMustProduceCanonicalPrefixedCodes() {
        assertThat(AnalysisPublicationService.normalizeClassification("PUBLIC")).isEqualTo("DATA_PUBLIC");
        assertThat(AnalysisPublicationService.normalizeClassification("internal")).isEqualTo("DATA_INTERNAL");
        assertThat(AnalysisPublicationService.normalizeClassification("SECRET")).isEqualTo("DATA_SECRET");
        assertThat(AnalysisPublicationService.normalizeClassification("CONFIDENTIAL")).isEqualTo("DATA_CONFIDENTIAL");
    }

    @Test
    void blankMustKeepTheInternalDefault() {
        assertThat(AnalysisPublicationService.normalizeClassification(null)).isEqualTo("DATA_INTERNAL");
        assertThat(AnalysisPublicationService.normalizeClassification("   ")).isEqualTo("DATA_INTERNAL");
    }

    /**
     * SENSITIVE is no longer a step of its own; it resolves onto SECRET. Legacy rows carrying it
     * must therefore rank as SECRET instead of failing to resolve.
     */
    @Test
    void legacySensitiveMustResolveOntoSecret() {
        assertThat(AnalysisPublicationService.normalizeClassification("DATA_SENSITIVE")).isEqualTo("DATA_SECRET");
        assertThat(AnalysisPublicationService.classificationRank("DATA_SENSITIVE"))
            .isEqualTo(AnalysisPublicationService.classificationRank("DATA_SECRET"));
    }

    /**
     * S-codes are owned by ClassificationMapper, not by this service. Decoding them here produced
     * the inverted reading, so they are now simply unrecognized and fail closed (-1), which the
     * gate treats as lower than any real dataset level.
     */
    @Test
    void sCodesMustNoLongerBeDecodedHere() {
        assertThat(AnalysisPublicationService.classificationRank("S1")).isEqualTo(-1);
        assertThat(AnalysisPublicationService.classificationRank("S4")).isEqualTo(-1);
    }

    @Test
    void unknownValuesMustFailClosed() {
        assertThat(AnalysisPublicationService.classificationRank("NOT_A_LEVEL")).isEqualTo(-1);
        assertThat(AnalysisPublicationService.classificationRank("NOT_A_LEVEL"))
            .isLessThan(AnalysisPublicationService.classificationRank("DATA_PUBLIC"));
    }

    @Test
    void rankMustAgreeWithTheCanonicalCatalog() {
        for (String code : SecurityLevelCatalog.dataCodesInOrder()) {
            assertThat(AnalysisPublicationService.classificationRank("DATA_" + code))
                .isEqualTo(SecurityLevelCatalog.dataRank(code));
        }
    }
}
