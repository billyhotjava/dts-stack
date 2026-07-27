package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PhysicalRelationObservationLiquibaseTest {

    private static final String ORIGINAL_CHANGELOG =
        "/config/liquibase/changelog/20260727_13_physical_relation_observation.xml";
    private static final String ADAPTER_CHANGELOG =
        "/config/liquibase/changelog/20260728_02_physical_relation_observation_adapter.xml";

    @Test
    void keepsAppliedObservationChangeSetCompatibleWithBothPublishedChecksums() throws Exception {
        String changelog = read(ORIGINAL_CHANGELOG);

        assertThat(changelog)
            .contains("<validCheckSum>9:134dbfe7347c0dcece2c6ad64bfe12b1</validCheckSum>")
            .contains("AND adapter = 'postgres'")
            .doesNotContain("AND adapter ~ '^[a-z][a-z0-9_]{0,63}$'");
    }

    @Test
    void widensAdapterConstraintOnlyInFollowUpChangeSet() throws Exception {
        String master = read("/config/liquibase/master.xml");
        String changelog = read(ADAPTER_CHANGELOG);

        assertThat(master)
            .containsSubsequence(
                "20260727_13_physical_relation_observation.xml",
                "20260728_02_physical_relation_observation_adapter.xml"
            );
        assertThat(changelog)
            .contains("DROP CONSTRAINT ck_physical_relation_observation")
            .contains("ADD CONSTRAINT ck_physical_relation_observation")
            .contains("AND adapter ~ '^[a-z][a-z0-9_]{0,63}$'")
            .contains("AND adapter = 'postgres'");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
