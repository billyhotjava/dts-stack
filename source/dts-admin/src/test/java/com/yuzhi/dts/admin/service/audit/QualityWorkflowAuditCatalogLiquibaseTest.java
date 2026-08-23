package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityWorkflowAuditCatalogLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260822_01_quality_workflow_audit_catalog.xml";

    @Test
    void registersTheUnifiedQualityWorkflowAuditActions() throws Exception {
        String master = classpathText("config/liquibase/master.xml");
        String changelog = classpathText(CHANGELOG);

        assertThat(master).contains(CHANGELOG);
        assertThat(changelog)
            .contains("author=\"xiezm\"")
            .contains("'governance.qualityWorkflows'")
            .contains("'GOV_QUALITY_WORKFLOW_START'")
            .contains("'GOV_QUALITY_WORKFLOW_FINALIZE'")
            .contains("'GOV_QUALITY_WORKFLOW_RETRY'")
            .contains("'GOV_QUALITY_WORKFLOW_CANCEL'")
            .contains("'governance.qualityTasks'")
            .contains("'查看运行策略列表'")
            .contains("ON CONFLICT (source_system, action_code) DO UPDATE SET");
    }

    private String classpathText(String name) throws Exception {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(name);
        assertThat(resource).as(name).isNotNull();
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
