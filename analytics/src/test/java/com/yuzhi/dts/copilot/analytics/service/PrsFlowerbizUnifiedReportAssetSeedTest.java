package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrsFlowerbizUnifiedReportAssetSeedTest {

    private static final String CHANGELOG_PATH =
            "config/liquibase/changelog/0062_prs_flowerbiz_unified_report_assets.xml";
    private static final Set<String> EXPECTED_SPLIT_TEMPLATE_CODES = Set.of(
            "PRS-FLOWERBIZ-OVERVIEW-KPI",
            "PRS-FLOWERBIZ-LEASE-ORDER-MIX",
            "PRS-FLOWERBIZ-LEASE-RENT",
            "PRS-FLOWERBIZ-FINANCE-SALE",
            "PRS-FLOWERBIZ-FINANCE-BADDEBT",
            "PRS-FLOWERBIZ-FINANCE-EXTRA-COST",
            "PRS-FLOWERBIZ-CURING-WORKLOAD-RANK",
            "PRS-FLOWERBIZ-PENDING-STATUS",
            "PRS-FLOWERBIZ-PROJECT-CUSTOMER-TOP",
            "PRS-FLOWERBIZ-CHANGE-AMOUNT",
            "PRS-FLOWERBIZ-RECOVERY-COST",
            "PRS-FLOWERBIZ-AUDIT-LAG");

    @Test
    void masterShouldIncludeUnifiedPrsReportAssetsChangelog() throws Exception {
        String master = readResource("config/liquibase/master.xml");

        assertThat(master).contains(CHANGELOG_PATH);
        assertThat(master.indexOf("0061_prs_flowerbiz_fixed_report_primary_models.xml"))
                .isLessThan(master.indexOf("0062_prs_flowerbiz_unified_report_assets.xml"));
    }

    @Test
    void changelogShouldSeedFocusedDbtSplitReportsAndKeepAssetGroups() throws Exception {
        String changelog = readResource(CHANGELOG_PATH);

        assertThat(changelog).contains("DBT_SCREEN_TABLE", "DBT_SPLIT", "assetGroup", "outputColumns");
        assertThat(changelog).contains("http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.22.xsd");
        assertThat(changelog).doesNotContain("http://www.liquibase.org/xml/ns/dbchangelog-4.22.xsd");
        for (String templateCode : EXPECTED_SPLIT_TEMPLATE_CODES) {
            assertThat(changelog).contains(templateCode);
        }
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
