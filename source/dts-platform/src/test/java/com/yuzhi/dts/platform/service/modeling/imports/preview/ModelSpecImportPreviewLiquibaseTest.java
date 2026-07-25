package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class ModelSpecImportPreviewLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260725_01_model_spec_import_run.xml";

    @Test
    void definesOnlyPreviewRunAndItemControlPlaneTables() throws Exception {
        String xml = read(CHANGELOG);
        Document document = parse(CHANGELOG);

        assertThat(xml)
            .contains("tableName=\"modeling_model_spec_import_run\"")
            .contains("tableName=\"modeling_model_spec_import_run_item\"")
            .contains("name=\"tenant_id\"")
            .contains("name=\"plan_id\"")
            .contains("name=\"preview_hash\"")
            .contains("name=\"apply_payload_json\"")
            .contains("name=\"apply_payload_checksum\"")
            .contains("name=\"apply_plan_json\"")
            .contains("name=\"expires_at\"")
            .contains("name=\"issues_json\"")
            .contains("uk_model_spec_import_run_item_unique")
            .doesNotContain(
                "tableName=\"modeling_model_spec\"",
                "tableName=\"modeling_model_implementation\"",
                "tableName=\"modeling_dbt_artifact\"",
                "<insert",
                "<update"
            );
        assertThat(document.getElementsByTagName("createTable").getLength()).isEqualTo(2);
    }

    @Test
    void masterIncludesPreviewControlPlaneChangelogOnce() throws IOException {
        String master = read("/config/liquibase/master.xml");

        assertThat(master.split("20260725_01_model_spec_import_run.xml", -1)).hasSize(2);
        assertThat(master.split("20260725_03_model_spec_import_apply_plan_checksum.xml", -1)).hasSize(2);
        assertThat(read("/config/liquibase/changelog/20260725_03_model_spec_import_apply_plan_checksum.xml"))
            .contains("tableName=\"modeling_model_spec_import_run\"", "name=\"apply_plan_checksum\"");
    }

    private static Document parse(String resource) throws Exception {
        try (InputStream stream = ModelSpecImportPreviewLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("Liquibase resource %s", resource).isNotNull();
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = ModelSpecImportPreviewLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("Liquibase resource %s", resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
