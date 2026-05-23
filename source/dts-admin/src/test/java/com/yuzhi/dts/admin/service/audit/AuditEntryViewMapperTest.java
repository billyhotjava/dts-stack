package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEntryViewMapperTest {

    @Test
    void mapsAnalyticsSourceSystemAsAnalysisAuditInsteadOfAdminAudit() {
        AuditEntryViewMapper mapper = new AuditEntryViewMapper(mock(AuditResourceDictionaryService.class), new ObjectMapper());

        assertThat(mapper.mapSourceSystemText("analytics")).isEqualTo("BI分析");
        assertThat(mapper.mapLogType("analytics")).isEqualTo("分析端审计");
    }

    @Test
    void operationContentPrefersCatalogNameWhenSummaryIsRawActionCode() {
        AuditEntryViewMapper mapper = new AuditEntryViewMapper(mock(AuditResourceDictionaryService.class), new ObjectMapper());
        AuditEntryView view = new AuditEntryView(
            1L,
            Instant.parse("2026-05-23T09:16:08Z"),
            "platform",
            "catalog.domain",
            "主题域",
            "CATALOG_DOMAIN_TREE",
            "CATALOG_DOMAIN_TREE",
            "查看主题域树",
            AuditOperationKind.QUERY,
            "SUCCESS",
            "CATALOG_DOMAIN_TREE",
            "xiezm",
            "测试xiezm",
            List.of(),
            null,
            "10.20.0.1",
            null,
            "/api/catalog/domains/tree",
            "GET",
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            "catalog_domain"
        );

        Map<String, Object> response = mapper.toResponse(view, false);

        assertThat(response.get("module")).isEqualTo("主题域");
        assertThat(response.get("operationContent")).isEqualTo("查看主题域树");
    }
}
