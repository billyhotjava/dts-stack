package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ReportTemplateSeedVerificationTest {

    private static final String RESOURCE_PATH = "config/liquibase/changelog/0040_seed_finance_procurement_templates.xml";
    private static final String MASTER_PATH = "config/liquibase/master.xml";
    private static final Set<String> EXPECTED_TEMPLATE_CODES = Set.of(
            "FIN-AR-OVERVIEW",
            "FIN-CUSTOMER-AR-RANK",
            "FIN-PROJECT-COLLECTION-PROGRESS",
            "FIN-PENDING-RECEIPTS-DETAIL",
            "FIN-PENDING-PAYMENT-APPROVAL",
            "FIN-ADVANCE-REQUEST-STATUS",
            "FIN-REIMBURSEMENT-STATUS",
            "FIN-INVOICE-RECONCILIATION",
            "PROC-PURCHASE-REQUEST-TODO",
            "PROC-ORDER-EXECUTION-PROGRESS",
            "PROC-SUPPLIER-AMOUNT-RANK",
            "PROC-ARRIVAL-ONTIME-RATE",
            "PROC-PENDING-INBOUND-LIST",
            "PROC-INTRANSIT-BOARD",
            "WH-STOCK-OVERVIEW",
            "WH-LOW-STOCK-ALERT");

    @Test
    void shouldSeedExactFinanceAndProcurementWarehouseCertifiedTemplatesWithRequiredFields() throws Exception {
        Document document = loadSeedDocument();
        Document master = loadDocument(MASTER_PATH);
        List<Element> inserts = childElementsByLocalName(document, "insert");
        List<Element> changeSets = childElementsByLocalName(document, "changeSet");

        assertMasterIncludesSeed(master);
        assertThat(inserts).hasSize(EXPECTED_TEMPLATE_CODES.size());
        assertThat(changeSets).hasSize(EXPECTED_TEMPLATE_CODES.size());

        List<Element> financeRows = new ArrayList<>();
        List<Element> procurementWarehouseRows = new ArrayList<>();
        Set<String> actualTemplateCodes = new LinkedHashSet<>();
        for (Element insert : inserts) {
            String templateCode = columnValue(insert, "template_code");
            String domain = columnValue(insert, "domain");
            actualTemplateCodes.add(templateCode);
            if ("财务".equals(domain)) {
                financeRows.add(insert);
            }
            if ("采购".equals(domain) || "仓库".equals(domain)) {
                procurementWarehouseRows.add(insert);
            }

            assertRequiredSeedFields(insert);
        }

        assertThat(actualTemplateCodes).containsExactlyInAnyOrderElementsOf(EXPECTED_TEMPLATE_CODES);
        assertThat(financeRows).hasSize(8);
        assertThat(procurementWarehouseRows).hasSize(8);
        assertEachChangeSetGuardsSingleTemplate(changeSets);
        assertPlaceholderReviewFlagPresentForAllSeedTemplates(inserts);
    }

    private static void assertRequiredSeedFields(Element insert) {
        assertThat(columnValue(insert, "template_code")).isNotBlank();
        assertThat(columnValue(insert, "domain")).isNotBlank();
        assertThat(columnValue(insert, "category")).isNotBlank();
        assertThat(columnValue(insert, "data_source_type")).isNotBlank();
        assertThat(columnValue(insert, "target_object")).isNotBlank();
        assertThat(columnValue(insert, "refresh_policy")).isNotBlank();
        assertThat(columnValue(insert, "certification_status")).isNotBlank();
        assertThat(columnValue(insert, "spec_json")).isNotBlank();
        assertThat(Boolean.parseBoolean(columnValue(insert, "published"))).isTrue();
    }

    private static void assertMasterIncludesSeed(Document master) {
        List<Element> includes = childElementsByLocalName(master, "include");
        int registryIndex = -1;
        int seedIndex = -1;
        for (int i = 0; i < includes.size(); i++) {
            String file = includes.get(i).getAttribute("file");
            if ("config/liquibase/changelog/0039_report_template_registry.xml".equals(file)) {
                registryIndex = i;
            }
            if (RESOURCE_PATH.equals(file)) {
                seedIndex = i;
            }
        }
        assertThat(seedIndex)
                .as("Expected master changelog to include %s", RESOURCE_PATH)
                .isGreaterThanOrEqualTo(0);
        assertThat(registryIndex)
                .as("Expected master changelog to include 0039_report_template_registry.xml before 0040")
                .isGreaterThanOrEqualTo(0);
        assertThat(seedIndex)
                .as("Expected 0040 seed changelog to be included after 0039 registry expansion")
                .isGreaterThan(registryIndex);
    }

    private static void assertEachChangeSetGuardsSingleTemplate(List<Element> changeSets) {
        for (Element changeSet : changeSets) {
            List<Element> inserts = childElementsByLocalName(changeSet, "insert");
            assertThat(inserts).hasSize(1);
            String templateCode = columnValue(inserts.get(0), "template_code");
            assertThat(EXPECTED_TEMPLATE_CODES).contains(templateCode);

            List<Element> sqlChecks = childElementsByLocalName(changeSet, "sqlCheck");
            assertThat(sqlChecks).hasSize(1);
            Element sqlCheck = sqlChecks.get(0);
            String normalizedSql = sqlCheck.getTextContent().replaceAll("\\s+", " ").trim().toLowerCase();
            assertThat(sqlCheck.getAttribute("expectedResult")).isEqualTo("0");
            assertThat(normalizedSql).contains("template_code = '" + templateCode.toLowerCase() + "'");
        }
    }

    private static void assertPlaceholderReviewFlagPresentForAllSeedTemplates(List<Element> inserts) {
        for (Element insert : inserts) {
            String templateCode = columnValue(insert, "template_code");
            String specJson = columnValue(insert, "spec_json");
            assertThat(specJson)
                    .as("Expected %s spec_json to keep placeholderReviewRequired=true until real backing lands", templateCode)
                    .contains("\"placeholderReviewRequired\":true");
        }
    }

    private static Document loadSeedDocument() throws Exception {
        return loadDocument(RESOURCE_PATH);
    }

    private static Document loadDocument(String resourcePath) throws Exception {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        assertThat(stream)
                .as("Expected Liquibase resource to exist at %s", resourcePath)
                .isNotNull();

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

    private static List<Element> childElementsByLocalName(Document document, String localName) {
        return childElementsByLocalName(document.getDocumentElement(), localName);
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

    private static String columnValue(Element insert, String columnName) {
        NodeList columns = insert.getElementsByTagNameNS("*", "column");
        for (int i = 0; i < columns.getLength(); i++) {
            Element column = (Element) columns.item(i);
            if (!columnName.equals(column.getAttribute("name"))) {
                continue;
            }
            String value = firstNonBlank(
                    column.getAttribute("value"),
                    column.getAttribute("valueBoolean"),
                    column.getAttribute("valueNumeric"),
                    column.getAttribute("valueDate"),
                    column.getAttribute("valueComputed"));
            if (value != null) {
                return value.trim();
            }
            String text = column.getTextContent();
            return text == null ? "" : text.trim();
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
