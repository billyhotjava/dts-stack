package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class ModelingVNextLiquibaseTest {

    private static final String CHANGELOG = "/config/liquibase/changelog/20260714-01_modeling_vnext.xml";

    @Test
    void masterIncludesModelingVNextChangelog() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/config/liquibase/master.xml")) {
            assertThat(stream).isNotNull();
            String master = new String(stream.readAllBytes());
            assertThat(master).contains("20260714-01_modeling_vnext.xml");
        }
    }

    @Test
    void modelingVNextChangelogDefinesAllMetadataTablesAndRollback() throws Exception {
        Document document = parse(CHANGELOG);
        String xml = read(CHANGELOG);

        for (String table : List.of(
            "modeling_business_object",
            "modeling_warehouse_plan",
            "modeling_model_spec",
            "modeling_model_spec_revision",
            "modeling_standard_binding",
            "modeling_dbt_artifact",
            "modeling_pipeline_run",
            "modeling_lineage_edge"
        )) {
            assertThat(xml).contains("tableName=\"" + table + "\"");
        }
        assertThat(xml).contains("uk_modeling_dbt_artifact_project_unique");
        assertThat(xml).contains("uk_modeling_model_spec_revision");
        assertThat(xml).contains("uk_modeling_pipeline_run_idempotency");
        assertThat(xml).contains("name=\"addax_task_id\"");
        assertThat(document.getElementsByTagName("rollback").getLength()).isGreaterThanOrEqualTo(1);
        assertThat(xml).contains("dropTable");
    }

    private static Document parse(String resource) throws Exception {
        try (InputStream stream = ModelingVNextLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = ModelingVNextLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
