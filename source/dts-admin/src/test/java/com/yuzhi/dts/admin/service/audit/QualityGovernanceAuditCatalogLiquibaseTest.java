package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityGovernanceAuditCatalogLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801-03_quality_governance_audit_catalog.xml";

    @Test
    void masterIncludesIdempotentQualityGovernanceAuditDictionary() throws Exception {
        String master = classpathText("config/liquibase/master.xml");
        String changelog = classpathText(CHANGELOG);

        assertThat(master).contains(CHANGELOG);
        assertThat(changelog)
            .contains("ON CONFLICT (resource_key) DO UPDATE SET")
            .contains("display_name = EXCLUDED.display_name")
            .contains("category = EXCLUDED.category")
            .contains("ON CONFLICT (source_system, module_key) DO UPDATE SET")
            .contains("module_name = EXCLUDED.module_name")
            .contains("parent_module_key = EXCLUDED.parent_module_key")
            .contains("ON CONFLICT (source_system, action_code) DO UPDATE SET")
            .contains("operation_kind = EXCLUDED.operation_kind")
            .contains("resource_type = EXCLUDED.resource_type")
            .contains("enabled = EXCLUDED.enabled")
            .contains("version = EXCLUDED.version")
            .contains("'GOV_QUALITY_TEMPLATE_CREATE'")
            .contains("'GOV_QUALITY_TASK_EXECUTE'")
            .contains("'GOV_QUALITY_RUN_EXECUTE'")
            .contains("'GOV_ISSUE_CREATE'")
            .contains("'GOV_QUALITY_CLEANSING_EXECUTE'")
            .contains("'GOV_QUALITY_SQL_REPAIR_EXECUTE'")
            .contains("'GOV_QUALITY_REPORT_EXPORT'")
            .contains("<rollback>");
        String rollback = changelog.substring(changelog.indexOf("<rollback>"), changelog.indexOf("</rollback>"));
        assertThat(rollback)
            .doesNotContain("DELETE FROM audit_action_catalog")
            .contains("SELECT 1");
    }

    private String classpathText(String name) throws Exception {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(name);
        assertThat(resource).as(name).isNotNull();
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
