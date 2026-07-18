package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class WarehousePlanCanonicalLiquibaseTest {

    private static final String CHANGELOG = "/config/liquibase/changelog/20260718_01_warehouse_plan_canonical.xml";

    @Test
    void masterIncludesCanonicalWarehousePlanChangelog() throws IOException {
        String master = read("/config/liquibase/master.xml");

        assertThat(master).contains("20260718_01_warehouse_plan_canonical.xml");
    }

    @Test
    void canonicalChangelogDefinesAggregateVersionsAndTenantScopedChildren() throws Exception {
        Document document = parse(CHANGELOG);
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("name=\"code\"")
            .contains("name=\"onboarding_mode\"")
            .contains("name=\"lifecycle_status\"")
            .contains("name=\"business_scope_confirmed\"")
            .contains("name=\"business_scope_version\"")
            .contains("name=\"sources_version\"")
            .contains("name=\"source_mappings_version\"")
            .contains("name=\"policy_version\"")
            .contains("uk_modeling_warehouse_plan_tenant_code")
            .contains("uk_modeling_warehouse_plan_process_layer");

        for (String table : List.of(
            "modeling_warehouse_plan_domain",
            "modeling_warehouse_plan_process",
            "modeling_warehouse_plan_source",
            "modeling_warehouse_plan_source_mapping",
            "modeling_warehouse_plan_metric_need",
            "modeling_warehouse_plan_policy",
            "modeling_warehouse_plan_version",
            "modeling_warehouse_plan_review",
            "modeling_warehouse_plan_stage_evidence",
            "modeling_legacy_plan_mapping"
        )) {
            assertThat(xml).contains("tableName=\"" + table + "\"");
        }

        assertThat(document.getElementsByTagName("rollback").getLength()).isGreaterThanOrEqualTo(1);
        assertThat(xml).contains("dropNotNullConstraint").contains("dropUniqueConstraint");
    }

    private static Document parse(String resource) throws Exception {
        try (InputStream stream = WarehousePlanCanonicalLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("Liquibase resource %s", resource).isNotNull();
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = WarehousePlanCanonicalLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("Liquibase resource %s", resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
