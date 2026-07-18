package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class CatalogDomainFactsLiquibaseTest {

    private static final String CHANGELOG = "/config/liquibase/changelog/20260719_02_catalog_domain_resolution_facts.xml";

    @Test
    void masterIncludesCatalogDomainFactsChangelogWithExplicitSafeRollback() throws Exception {
        String master = read("/config/liquibase/master.xml");
        String xml = read(CHANGELOG);
        Document document = parse(CHANGELOG);

        assertThat(master).contains("20260719_02_catalog_domain_resolution_facts.xml");
        assertThat(document.getElementsByTagName("changeSet").getLength()).isEqualTo(1);
        assertThat(xml)
            .contains("dbms=\"postgresql\"")
            .contains("splitStatements=\"true\"")
            .contains("<rollback>")
            .contains("DROP CONSTRAINT IF EXISTS ck_catalog_domain_lifecycle_status")
            .contains("DROP CONSTRAINT IF EXISTS ck_catalog_domain_access_policy")
            .contains("<dropColumn tableName=\"catalog_domain\" columnName=\"lifecycle_status\"")
            .contains("<dropColumn tableName=\"catalog_domain\" columnName=\"access_policy\"");

        int rollback = xml.indexOf("<rollback>");
        int dropLifecycleCheck = xml.indexOf("DROP CONSTRAINT IF EXISTS ck_catalog_domain_lifecycle_status");
        int dropAccessCheck = xml.indexOf("DROP CONSTRAINT IF EXISTS ck_catalog_domain_access_policy");
        int dropLifecycleColumn = xml.indexOf("<dropColumn tableName=\"catalog_domain\" columnName=\"lifecycle_status\"");
        int dropAccessColumn = xml.indexOf("<dropColumn tableName=\"catalog_domain\" columnName=\"access_policy\"");
        assertThat(rollback).isLessThan(dropLifecycleCheck);
        assertThat(dropLifecycleCheck).isLessThan(dropLifecycleColumn);
        assertThat(dropAccessCheck).isLessThan(dropAccessColumn);
        assertThat(Math.max(dropLifecycleCheck, dropAccessCheck))
            .isLessThan(Math.min(dropLifecycleColumn, dropAccessColumn));
    }

    @Test
    void migrationBackfillsLegacyRowsBeforeEnforcingDefaultsAndConstraints() throws IOException {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("name=\"lifecycle_status\"")
            .contains("name=\"access_policy\"")
            .contains("value=\"ACTIVE\"")
            .contains("value=\"PUBLIC\"")
            .contains("defaultValue=\"ACTIVE\"")
            .contains("defaultValue=\"PUBLIC\"")
            .contains("ck_catalog_domain_lifecycle_status")
            .contains("lifecycle_status IN ('ACTIVE', 'ARCHIVED')")
            .contains("ck_catalog_domain_access_policy")
            .contains("access_policy IN ('PUBLIC', 'RESTRICTED')");

        assertThat(xml.indexOf("value=\"ACTIVE\"")).isLessThan(xml.indexOf("columnName=\"lifecycle_status\""));
        assertThat(xml.indexOf("value=\"PUBLIC\"")).isLessThan(xml.indexOf("columnName=\"access_policy\""));
    }

    private static Document parse(String resource) throws Exception {
        try (InputStream stream = CatalogDomainFactsLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("Liquibase resource %s", resource).isNotNull();
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = CatalogDomainFactsLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("Liquibase resource %s", resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
