package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Sprint93AuditCatalogLiquibaseSeedTest {

    @Test
    void sprint93ModelingActionsHaveDurableCanonicalClassifications() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL changelogResource = loader.getResource(
            "config/liquibase/changelog/20260817-01_sprint93_modeling_audit_catalog.xml"
        );
        URL masterResource = loader.getResource("config/liquibase/master.xml");

        assertThat(changelogResource).isNotNull();
        assertThat(masterResource).isNotNull();

        String changelog = java.nio.file.Files.readString(Path.of(changelogResource.toURI()), StandardCharsets.UTF_8);
        String master = java.nio.file.Files.readString(Path.of(masterResource.toURI()), StandardCharsets.UTF_8);

        assertThat(master).contains("20260817-01_sprint93_modeling_audit_catalog.xml");
        assertThat(changelog).contains(
            "20260817-01-sprint93-modeling-audit-catalog",
            "('MODELING_SEMANTIC_SYNC_RETRY', '重试模型目录同步', 'REFRESH')",
            "('MODELING_SEMANTIC_SYNC_TERMINAL_FAILURE', '模型目录同步终态失败', 'REFRESH')",
            "('CATALOG_ASSET_SEMANTIC_BACKFILL_APPLY', '应用资产语义投影补齐批次', 'EXECUTE')",
            "('CATALOG_ASSET_SEMANTIC_BACKFILL_ROLLBACK', '回滚资产语义投影补齐批次', 'DELETE')",
            "'modeling.dbt-roundtrip'",
            "FALSE",
            "ON CONFLICT (source_system, action_code) DO UPDATE SET"
        );
    }
}
