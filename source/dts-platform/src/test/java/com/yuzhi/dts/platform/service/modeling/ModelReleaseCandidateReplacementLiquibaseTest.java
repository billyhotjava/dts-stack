package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidateReplacementLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260728_03_model_release_candidate_claim_transfer.xml";

    @Test
    void permitsAtomicClaimReservationAcrossStaleReplacementAndStart()
        throws Exception {
        String master = read("/config/liquibase/master.xml");
        String changelog = read(CHANGELOG);

        assertThat(master)
            .containsSubsequence(
                "20260728_01_model_release_candidate_cancel.xml",
                "20260728_03_model_release_candidate_claim_transfer.xml"
            );
        assertThat(changelog)
            .contains("ck_model_release_candidate_entry_build_snapshot")
            .contains("status IN ('DRAFT', 'BUILDING')")
            .contains("status IN ('REJECTED', 'ROLLED_BACK', 'STALE')")
            .contains("ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_CLAIM_TRANSFER_DATA_EXISTS");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
