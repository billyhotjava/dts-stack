package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataArchitectureAuditCatalogLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260809-02_data_architecture_audit_catalog.xml";

    @Test
    void masterIncludesIdempotentForwardOnlyDataArchitectureAuditDictionary() throws Exception {
        String master = classpathText("config/liquibase/master.xml");
        String changelog = classpathText(CHANGELOG);

        assertThat(master).contains(CHANGELOG);
        assertThat(changelog)
            .contains("ON CONFLICT (resource_key) DO UPDATE SET")
            .contains("ON CONFLICT (source_system, module_key) DO UPDATE SET")
            .contains("ON CONFLICT (source_system, action_code) DO UPDATE SET")
            .contains("'CATALOG_DOMAIN_CREATE'")
            .contains("'CATALOG_DOMAIN_UPDATE'")
            .contains("'CATALOG_DOMAIN_DELETE'")
            .contains("'CATALOG_DOMAIN_MOVE'")
            .contains("'MODELING_BUSINESS_PROCESS_CREATE'")
            .contains("'MODELING_BUSINESS_PROCESS_DELETE'")
            .contains("'SPRINT64_PROCESS_CREATE'")
            .contains("'SPRINT64_PROCESS_DELETE'")
            .contains("'MODELING_WAREHOUSE_LAYER_CREATE'")
            .contains("'MODELING_WAREHOUSE_LAYER_DELETE'")
            .contains("'MODELING_DATA_MART_CREATE'")
            .contains("'MODELING_DATA_MART_UPDATE'")
            .contains("'MODELING_DATA_MART_CURRENT'")
            .contains("'MODELING_DATA_MART_RETIRED'")
            .contains("'MODELING_SUBJECT_DOMAIN_CREATE'")
            .contains("'MODELING_SUBJECT_DOMAIN_UPDATE'")
            .contains("'MODELING_SUBJECT_DOMAIN_CURRENT'")
            .contains("'MODELING_SUBJECT_DOMAIN_RETIRED'")
            .contains("<rollback>");
        String rollback = changelog.substring(changelog.indexOf("<rollback>"), changelog.indexOf("</rollback>"));
        assertThat(rollback)
            .doesNotContain("DELETE FROM audit_action_catalog")
            .doesNotContain("DELETE FROM audit_module_catalog")
            .doesNotContain("DELETE FROM audit_resource_dictionary")
            .contains("SELECT 1");
    }

    private String classpathText(String name) throws Exception {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(name);
        assertThat(resource).as(name).isNotNull();
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
