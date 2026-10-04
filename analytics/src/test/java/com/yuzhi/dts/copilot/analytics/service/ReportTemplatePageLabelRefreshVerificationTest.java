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

class ReportTemplatePageLabelRefreshVerificationTest {

    private static final String RESOURCE_PATH = "config/liquibase/changelog/0041_refresh_fixed_report_page_labels.xml";
    private static final String MASTER_PATH = "config/liquibase/master.xml";

    @Test
    void shouldIncludePageLabelRefreshAfter0040SeedAndRefreshRepresentativeTemplates() throws Exception {
        Document changelog = loadDocument(RESOURCE_PATH);
        Document master = loadDocument(MASTER_PATH);

        assertMasterIncludesRefresh(master);
        assertUpdateForTemplate(changelog, "FIN-AR-OVERVIEW", "财务结算汇总");
        assertUpdateForTemplate(changelog, "PROC-SUPPLIER-AMOUNT-RANK", "采购汇总");
        assertUpdateForTemplate(changelog, "WH-STOCK-OVERVIEW", "库存现量");
    }

    private static void assertMasterIncludesRefresh(Document master) {
        List<Element> includes = childElementsByLocalName(master.getDocumentElement(), "include");
        int seedIndex = -1;
        int refreshIndex = -1;
        for (int i = 0; i < includes.size(); i++) {
            String file = includes.get(i).getAttribute("file");
            if ("config/liquibase/changelog/0040_seed_finance_procurement_templates.xml".equals(file)) {
                seedIndex = i;
            }
            if (RESOURCE_PATH.equals(file)) {
                refreshIndex = i;
            }
        }
        assertThat(seedIndex).isGreaterThanOrEqualTo(0);
        assertThat(refreshIndex).isGreaterThan(seedIndex);
    }

    private static void assertUpdateForTemplate(Document changelog, String templateCode, String expectedName) {
        for (Element update : childElementsByLocalName(changelog.getDocumentElement(), "update")) {
            String where = normalize(updateText(update, "where"));
            if (!where.contains("template_code = '" + templateCode.toLowerCase() + "'")) {
                continue;
            }
            assertThat(columnValue(update, "name")).isEqualTo(expectedName);
            return;
        }
        throw new AssertionError("Expected update for template " + templateCode);
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

    private static String columnValue(Element update, String columnName) {
        NodeList columns = update.getElementsByTagNameNS("*", "column");
        for (int i = 0; i < columns.getLength(); i++) {
            Element column = (Element) columns.item(i);
            if (columnName.equals(column.getAttribute("name"))) {
                return column.getAttribute("value").trim();
            }
        }
        return "";
    }

    private static String updateText(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private static String normalize(String value) {
        return String.valueOf(value).replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
