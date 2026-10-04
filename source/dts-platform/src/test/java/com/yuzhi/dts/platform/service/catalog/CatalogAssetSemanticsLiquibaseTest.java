package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogAssetSemanticsLiquibaseTest {

    private static final Path CHANGELOG = Path.of(
        "src/main/resources/config/liquibase/changelog/20260810_02_catalog_asset_semantics_projection.xml"
    );

    @Test
    void expandMigrationPersistsOrthogonalSemanticsAndNeverGuessesLegacyLayers() throws Exception {
        String xml = Files.readString(CHANGELOG);

        assertThat(xml)
            .contains("author=\"xiezm\"")
            .contains("catalog_asset_semantic_projection")
            .contains("catalog_asset_producer_ref")
            .contains("catalog_asset_registration_evidence")
            .contains("catalog_asset_stats_projection")
            .contains("catalog_asset_projection_event")
            .contains("catalog_asset_normalization_issue")
            .contains("SOURCE_PRODUCER_UNRESOLVED")
            .contains("LAYER_NORMALIZATION_REQUIRED")
            .contains("uk_catalog_asset_current_producer")
            .doesNotContain("UPDATE catalog_dataset SET warehouse_layer")
            .doesNotContain("lower(name) LIKE");
    }

    @Test
    void platformMasterIncludesTheExpandMigration() throws Exception {
        String master = Files.readString(Path.of("src/main/resources/config/liquibase/master.xml"));

        assertThat(master).contains("20260810_02_catalog_asset_semantics_projection.xml");
    }
}
