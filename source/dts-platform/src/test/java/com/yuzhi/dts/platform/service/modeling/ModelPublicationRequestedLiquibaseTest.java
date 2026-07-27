package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelPublicationRequestedLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260728_04_model_release_publication_requested.xml";

    @Test
    void constrainsPublicationRequestedToTheAtomicBuiltToQualityTransitionAndRestoresRollbackConstraint() throws Exception {
        try (var input = getClass().getResourceAsStream(CHANGELOG)) {
            assertThat(input).isNotNull();
            var xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(xml)
                .contains("'PUBLICATION_REQUESTED', 'STALE_DETECTED'")
                .contains("event_type <> 'PUBLICATION_REQUESTED'")
                .contains("from_status = 'BUILT' AND to_status = 'QUALITY_RUNNING'")
                .contains("<rollback>")
                .contains("'CREATED', 'SCOPE_REPLACED', 'STATUS_CHANGED', 'STALE_DETECTED'");
        }
    }
}
