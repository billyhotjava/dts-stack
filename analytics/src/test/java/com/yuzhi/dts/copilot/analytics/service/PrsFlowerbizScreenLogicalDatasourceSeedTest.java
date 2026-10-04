package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PrsFlowerbizScreenLogicalDatasourceSeedTest {

    private static final String SCREEN_RECORDS_CHANGELOG =
            "config/liquibase/changelog/0064_prs_flowerbiz_copilot_screen_records.xml";
    private static final String LOGICAL_DATASOURCE_CHANGELOG =
            "config/liquibase/changelog/0065_prs_flowerbiz_screen_logical_datasource.xml";
    private static final String PRS_FLOWERBIZ_ALIAS = "prs.flowerbiz.federated";

    @Test
    void masterShouldIncludeLogicalDatasourceRepairAfterScreenRecords() throws Exception {
        String master = readResource("config/liquibase/master.xml");

        assertThat(master).contains(SCREEN_RECORDS_CHANGELOG, LOGICAL_DATASOURCE_CHANGELOG);
        assertThat(master.indexOf(SCREEN_RECORDS_CHANGELOG))
                .isLessThan(master.indexOf(LOGICAL_DATASOURCE_CHANGELOG));
    }

    @Test
    void screenRecordsShouldSeedLogicalAliasInsteadOfEnvironmentDatabaseIdPlaceholder() throws Exception {
        String changelog = readResource(SCREEN_RECORDS_CHANGELOG);

        assertThat(changelog).contains(PRS_FLOWERBIZ_ALIAS);
        assertThat(changelog).doesNotContain("{{DATABASE_ID}}", "replace(s.components_json, '{DATABASE_ID}'");
    }

    @Test
    void screenRecordsShouldAcceptPreviouslyAppliedChecksumForExistingDeployments() throws Exception {
        String changelog = readResource(SCREEN_RECORDS_CHANGELOG);

        assertThat(changelog).contains("<validCheckSum>9:45fe6734ac0d2919b388c8a9b0957182</validCheckSum>");
    }

    @Test
    void repairChangelogShouldBackfillAliasBindingAndExistingScreenJson() throws Exception {
        String changelog = readResource(LOGICAL_DATASOURCE_CHANGELOG);

        assertThat(changelog).contains(PRS_FLOWERBIZ_ALIAS);
        assertThat(changelog).contains("logicalSourceAliases");
        assertThat(changelog).contains("regexp_replace");
        assertThat(changelog).contains("290001", "290012");
        assertThat(changelog).contains("databaseAlias");
        assertThat(changelog).doesNotContain("\"databaseId\":\"{8}\"");
    }

    private static String readResource(String path) throws Exception {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        assertThat(stream)
                .as("Expected Liquibase resource to exist at %s", path)
                .isNotNull();
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
