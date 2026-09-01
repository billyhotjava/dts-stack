package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataPortalMigrationContractTest {

    @Test
    void masterIncludesReversiblePortalDirectoryAndBindingSchema() throws Exception {
        String master = Files.readString(Path.of("src/main/resources/config/liquibase/master.xml"));
        String changelog = Files.readString(
                Path.of("src/main/resources/config/liquibase/changelog/0053_data_portal.xml"));

        assertThat(master).contains("0053_data_portal.xml");
        assertThat(changelog)
                .contains("analytics_data_portal_directory")
                .contains("analytics_data_portal_item")
                .contains("fk_data_portal_directory_parent")
                .contains("uk_data_portal_directory_active_sibling_name")
                .contains("uk_data_portal_item_content")
                .contains("ck_data_portal_item_type")
                .contains("idx_data_portal_item_content")
                .contains("<rollback>")
                .contains("<dropTable tableName=\"analytics_data_portal_item\"")
                .contains("<dropTable tableName=\"analytics_data_portal_directory\"");
    }
}
