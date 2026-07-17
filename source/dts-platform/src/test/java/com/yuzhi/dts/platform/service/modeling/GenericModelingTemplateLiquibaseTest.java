package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class GenericModelingTemplateLiquibaseTest {

    private static final String CHANGELOG = "/config/liquibase/changelog/20260717_01_generic_modeling_template_boundary.xml";

    @Test
    void masterIncludesGenericModelingTemplateBoundaryChangelog() throws IOException {
        String master = read("/config/liquibase/master.xml");

        assertThat(master).contains("20260717_01_generic_modeling_template_boundary.xml");
    }

    @Test
    void changelogAddsDomainOwnedProvenanceAndConformedDimensionsWithRollback() throws Exception {
        Document document = parse(CHANGELOG);
        String xml = read(CHANGELOG);

        assertThat(xml).contains("tableName=\"sprint64_business_process\"");
        assertThat(xml).contains("name=\"source_type\"");
        assertThat(xml).contains("name=\"source_id\"");
        assertThat(xml).contains("name=\"source_version\"");
        assertThat(xml).contains("name=\"confirmed\"");
        assertThat(xml).contains("tableName=\"sprint64_conformed_dimension\"");
        assertThat(xml).contains("constraintName=\"pk_sprint64_conformed_dimension\"");
        assertThat(xml).contains("columnNames=\"domain_id, dimension_id\"");
        assertThat(document.getElementsByTagName("rollback").getLength()).isGreaterThanOrEqualTo(1);
        assertThat(xml).contains("dropTable tableName=\"sprint64_conformed_dimension\"");
        assertThat(xml).contains("dropColumn tableName=\"sprint64_business_process\"");
    }

    private static Document parse(String resource) throws Exception {
        try (InputStream stream = GenericModelingTemplateLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = GenericModelingTemplateLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
