package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidateCancelLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260728_01_model_release_candidate_cancel.xml";

    @Test
    void addsCancelledToStateChecksAndReleasesBothActiveClaims() throws Exception {
        String master = read("/config/liquibase/master.xml");
        String changelog = read(CHANGELOG);

        assertThat(master)
            .containsSubsequence(
                "20260727_13_physical_relation_observation.xml",
                "20260728_01_model_release_candidate_cancel.xml"
            );
        assertThat(changelog)
            .contains("ck_model_release_candidate_status")
            .contains("ck_model_release_candidate_entry_status")
            .contains("ck_model_release_candidate_command_payload")
            .contains("'CANCELLED'")
            .contains("uk_model_release_candidate_active_plan")
            .contains("status NOT IN ('REJECTED', 'ROLLED_BACK', 'STALE', 'CANCELLED')")
            .contains("ck_model_release_candidate_entry_build_snapshot")
            .contains("status = 'CANCELLED' AND active_claim_key IS NULL")
            .contains("ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_CANCEL_DATA_EXISTS");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
