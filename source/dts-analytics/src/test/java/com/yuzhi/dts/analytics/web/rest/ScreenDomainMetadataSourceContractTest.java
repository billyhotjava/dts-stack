package com.yuzhi.dts.analytics.web.rest;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenDomainMetadataSourceContractTest {

    @Test
    void analyticsScreenPersistsDomainIdAsScreenMetadata() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreen.java"));

        assertThat(source).contains("@Column(name = \"domain_id\"");
        assertThat(source).contains("private String domainId");
        assertThat(source).contains("getDomainId()");
        assertThat(source).contains("setDomainId(String domainId)");
    }

    @Test
    void screenResourceReadsAndReturnsDomainId() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java"));

        assertThat(source).contains("readOptionalDomainId");
        assertThat(source).contains("screen.setDomainId(readOptionalDomainId(body))");
        assertThat(source).contains("node.put(\"domainId\", screen.getDomainId())");
        assertThat(source).contains("domainUnassigned");
    }

    @Test
    void liquibaseAddsScreenDomainIdColumnAndIndex() throws Exception {
        String master = Files.readString(Path.of("src/main/resources/config/liquibase/master.xml"));
        String changelog = Files.readString(Path.of("src/main/resources/config/liquibase/changelog/0048_screen_domain_id.xml"));

        assertThat(master).contains("0048_screen_domain_id.xml");
        assertThat(changelog).contains("tableName=\"analytics_screen\"");
        assertThat(changelog).contains("column name=\"domain_id\"");
        assertThat(changelog).contains("idx_analytics_screen_domain_id");
    }
}
