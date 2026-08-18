package com.yuzhi.dts.analytics.service.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GovernedBiUpgradeContractTest {

    private static final Path CHANGELOG = Path.of(
        "src/main/resources/config/liquibase/changelog/0052_analysis_publication.xml"
    );
    private static final Path CLEANUP = Path.of(
        "src/main/resources/ops/sprint94_legacy_bi_cleanup.sql"
    );

    @Test
    void revisionBackfillIsLinearAndKeepsTheExpandSchemaOldWriterCompatible() throws Exception {
        String changelog = Files.readString(CHANGELOG);

        assertThat(changelog).contains("ROW_NUMBER() OVER");
        assertThat(changelog).doesNotContain("SELECT COUNT(*)");
        assertThat(changelog).doesNotContain("ALTER COLUMN version_no SET NOT NULL");
        assertThat(changelog).doesNotContain("ALTER COLUMN status SET NOT NULL");
    }

    @Test
    void controlledCleanupProtectsHistoricalScreensAndDefaultsToDryRun() throws Exception {
        String cleanup = Files.readString(CLEANUP);

        assertThat(cleanup).contains("\\if :apply");
        assertThat(cleanup).contains("analytics_screen");
        assertThat(cleanup).contains("analytics_screen_version");
        assertThat(cleanup).contains("cardId|card_id|sourceCardId|source_card_id");
        assertThat(cleanup).contains("legacy BI cleanup blocked");
        assertThat(cleanup).contains("ROLLBACK");
    }

    @Test
    void everyComposeModePassesTheScreenPublicSharingSwitchExplicitly() throws Exception {
        for (String name : new String[] {
            "docker-compose-app.yml",
            "docker-compose.dev.yml",
            "docker-compose.legacy.yml",
        }) {
            String compose = Files.readString(Path.of("../..", name));
            assertThat(compose).contains(
                "ANALYTICS_PUBLIC_SHARING_ENABLED: ${ANALYTICS_PUBLIC_SHARING_ENABLED:-false}"
            );
        }
    }
}
