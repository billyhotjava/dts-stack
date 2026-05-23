package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AuditEntryViewMapperTest {

    @Test
    void mapsAnalyticsSourceSystemAsAnalysisAuditInsteadOfAdminAudit() {
        AuditEntryViewMapper mapper = new AuditEntryViewMapper(mock(AuditResourceDictionaryService.class), new ObjectMapper());

        assertThat(mapper.mapSourceSystemText("analytics")).isEqualTo("BI分析");
        assertThat(mapper.mapLogType("analytics")).isEqualTo("分析端审计");
    }
}
