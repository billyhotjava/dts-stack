package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseSelfServiceLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260814_01_model_release_candidate_self_service.xml";

    @Test
    void permitsPublishedAuditWithoutReviewWhileRetainingHonestActorEvidence()
        throws Exception {
        String xml = read(CHANGELOG);
        String forward = xml.substring(0, xml.indexOf("<rollback>"));

        assertThat(forward)
            .contains("DROP CONSTRAINT ck_model_release_candidate_audit")
            .contains("ADD CONSTRAINT ck_model_release_candidate_audit")
            .contains("published_date >= COALESCE(approved_date, submitted_date, created_date)")
            .contains("status NOT IN ('REVIEW_PENDING', 'REJECTED', 'APPROVED')")
            .contains("status NOT IN ('APPROVED')")
            .contains("status NOT IN ('PARTIAL', 'PUBLISHED', 'ROLLED_BACK')")
            .doesNotContain("btrim(submitted_by) <> btrim(published_by)");
    }

    @Test
    void masterIncludesSelfServiceReleaseConstraintAfterCandidateSchema() throws Exception {
        String master = read("/config/liquibase/master.xml");

        assertThat(master)
            .contains("20260724_03_model_release_candidate.xml")
            .contains("20260814_01_model_release_candidate_self_service.xml");
        assertThat(master.indexOf("20260814_01_model_release_candidate_self_service.xml"))
            .isGreaterThan(master.indexOf("20260724_03_model_release_candidate.xml"));
    }

    private static String read(String resource) throws Exception {
        try (var stream = ModelReleaseSelfServiceLiquibaseTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
