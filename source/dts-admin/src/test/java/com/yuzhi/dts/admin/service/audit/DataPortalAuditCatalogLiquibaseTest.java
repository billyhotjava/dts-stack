package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataPortalAuditCatalogLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260830-02_data_portal_audit_catalog.xml";

    @Test
    void masterIncludesForwardOnlyDataPortalAuditDictionary() throws Exception {
        String master = classpathText("config/liquibase/master.xml");
        String changelog = classpathText(CHANGELOG);

        assertThat(master).contains(CHANGELOG);
        assertThat(changelog)
            .contains("ON CONFLICT (source_system, module_key) DO UPDATE SET")
            .contains("ON CONFLICT (source_system, action_code) DO UPDATE SET")
            .contains("'analytics.data-portal'")
            .contains("'ANALYTICS_DATA_PORTAL_READ'")
            .contains("'ANALYTICS_DATA_PORTAL_CREATE'")
            .contains("'ANALYTICS_DATA_PORTAL_UPDATE'")
            .contains("'ANALYTICS_DATA_PORTAL_DELETE'")
            .contains("'DATA_PORTAL'");
        String rollback = changelog.substring(changelog.indexOf("<rollback>"), changelog.indexOf("</rollback>"));
        assertThat(rollback)
            .doesNotContain("DELETE FROM audit_action_catalog")
            .doesNotContain("DELETE FROM audit_module_catalog")
            .contains("SELECT 1");
    }

    private String classpathText(String name) throws Exception {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(name);
        assertThat(resource).as(name).isNotNull();
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
