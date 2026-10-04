package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidatePublishedHistoryLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260815_01_model_release_candidate_published_history.xml";

    @Test
    void publishedCandidatesRemainHistoryAndNoLongerOccupyTheNextDeliverySlot() throws Exception {
        String changelog = read(CHANGELOG);
        String master = read("/config/liquibase/master.xml");

        assertThat(master).contains("20260815_01_model_release_candidate_published_history.xml");
        assertThat(changelog)
            .contains("https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.24.xsd")
            .contains("runInTransaction=\"false\"")
            .contains("CREATE UNIQUE INDEX CONCURRENTLY")
            .contains("DROP INDEX CONCURRENTLY uk_model_release_candidate_active_plan")
            .contains("'CANCELLED', 'PUBLISHED'")
            .contains("ROLLBACK_BLOCKED_PUBLISHED_CANDIDATE_HISTORY_HAS_NEXT_DELIVERY")
            .contains("HAVING count(*) > 1");
    }

    private String read(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
