package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class AnalysisDraftSchemaVerificationTest {

    private static final String RESOURCE_PATH = "config/liquibase/changelog/0043_analysis_drafts.xml";
    private static final String AGENT_BI_METADATA_RESOURCE_PATH =
            "config/liquibase/changelog/0056_analysis_draft_agent_bi_metadata.xml";
    private static final String MASTER_PATH = "config/liquibase/master.xml";

    @Test
    void shouldIncludeAnalysisDraftChangelogAndCreateExpectedColumns() throws Exception {
        Document changelog = loadDocument(RESOURCE_PATH);
        Document master = loadDocument(MASTER_PATH);

        assertMasterIncludesAnalysisDraft(master);
        Element table = childElementsByLocalName(changelog.getDocumentElement(), "createTable").stream()
                .filter(element -> "analysis_draft".equals(element.getAttribute("tableName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected createTable for analysis_draft"));

        assertThat(columnNames(table)).contains(
                "id",
                "entity_id",
                "title",
                "source_type",
                "session_id",
                "message_id",
                "question",
                "database_id",
                "sql_text",
                "explanation_text",
                "suggested_display",
                "status",
                "linked_card_id",
                "linked_dashboard_id",
                "linked_screen_id",
                "creator_id",
                "created_at",
                "updated_at");
    }

    @Test
    void shouldAddAgentBiMetadataColumnsThroughIncrementalChangelog() throws Exception {
        Document changelog = loadDocument(AGENT_BI_METADATA_RESOURCE_PATH);
        Document master = loadDocument(MASTER_PATH);

        assertMasterIncludesAgentBiMetadata(master);
        Element addColumn = childElementsByLocalName(changelog.getDocumentElement(), "addColumn").stream()
                .filter(element -> "analysis_draft".equals(element.getAttribute("tableName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected addColumn for analysis_draft"));

        assertThat(columnNames(addColumn)).contains(
                "response_kind",
                "data_surface",
                "quality_level",
                "quality_notes",
                "report_code");
    }

    private static void assertMasterIncludesAnalysisDraft(Document master) {
        List<Element> includes = childElementsByLocalName(master.getDocumentElement(), "include");
        int previousIndex = -1;
        int currentIndex = -1;
        for (int i = 0; i < includes.size(); i++) {
            String file = includes.get(i).getAttribute("file");
            if ("config/liquibase/changelog/0042_backfill_placeholder_review_flags.xml".equals(file)) {
                previousIndex = i;
            }
            if (RESOURCE_PATH.equals(file)) {
                currentIndex = i;
            }
        }
        assertThat(currentIndex).isGreaterThanOrEqualTo(0);
        assertThat(previousIndex).isGreaterThanOrEqualTo(0);
        assertThat(currentIndex).isGreaterThan(previousIndex);
    }

    private static void assertMasterIncludesAgentBiMetadata(Document master) {
        List<Element> includes = childElementsByLocalName(master.getDocumentElement(), "include");
        int previousIndex = -1;
        int currentIndex = -1;
        for (int i = 0; i < includes.size(); i++) {
            String file = includes.get(i).getAttribute("file");
            if ("config/liquibase/changelog/0055_database_role_hardening.xml".equals(file)) {
                previousIndex = i;
            }
            if (AGENT_BI_METADATA_RESOURCE_PATH.equals(file)) {
                currentIndex = i;
            }
        }
        assertThat(currentIndex).isGreaterThanOrEqualTo(0);
        assertThat(previousIndex).isGreaterThanOrEqualTo(0);
        assertThat(currentIndex).isGreaterThan(previousIndex);
    }

    private static List<String> columnNames(Element createTable) {
        List<String> names = new ArrayList<>();
        NodeList columns = createTable.getElementsByTagNameNS("*", "column");
        for (int i = 0; i < columns.getLength(); i++) {
            if (columns.item(i) instanceof Element column) {
                names.add(column.getAttribute("name"));
            }
        }
        return names;
    }

    private static Document loadDocument(String resourcePath) throws Exception {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        assertThat(stream).as("Expected Liquibase resource to exist at %s", resourcePath).isNotNull();
        try (InputStream input = stream) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(input, StandardCharsets.UTF_8.name());
            document.getDocumentElement().normalize();
            return document;
        }
    }

    private static List<Element> childElementsByLocalName(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element childElement) {
                elements.add(childElement);
            }
        }
        return elements;
    }
}
