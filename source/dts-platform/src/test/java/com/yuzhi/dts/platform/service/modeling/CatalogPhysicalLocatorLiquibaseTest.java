package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CatalogPhysicalLocatorLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260728_06_catalog_dataset_physical_locator.xml";

    @Test
    void blocksDuplicateLocatorsBeforeInstallingTheDatabaseAuthority() throws Exception {
        String master = read("/config/liquibase/master.xml");
        String changelog = read(CHANGELOG);

        assertThat(master)
            .containsSubsequence(
                "20260728_05_model_plan_execution_binding.xml",
                "20260728_06_catalog_dataset_physical_locator.xml"
            );
        assertThat(changelog)
            .contains("CATALOG_DATASET_PHYSICAL_LOCATOR_DUPLICATES_EXIST")
            .contains("source_id")
            .contains("lower(btrim(hive_database))")
            .contains("lower(btrim(hive_table))")
            .contains("CREATE UNIQUE INDEX uk_catalog_dataset_physical_locator");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
