package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsAssetAccessRegistrar;
import com.yuzhi.dts.analytics.service.AnalyticsClassificationClient.ExportSeal;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.QueryExportService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.AnalysisDto;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryGateway;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryGateway.AnalysisQueryResult;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpec;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnalysisResourceExportTest {

    private AnalysisApplicationService analysisService;
    private AnalyticsSessionService sessionService;
    private AnalysisQueryGateway queryGateway;
    private AnalyticsConsumerClassificationService classificationService;
    private AnalysisResource resource;
    private AnalyticsUser actor;

    @BeforeEach
    void setUp() {
        analysisService = mock(AnalysisApplicationService.class);
        sessionService = mock(AnalyticsSessionService.class);
        queryGateway = mock(AnalysisQueryGateway.class);
        classificationService = mock(AnalyticsConsumerClassificationService.class);
        resource = new AnalysisResource(
            analysisService,
            sessionService,
            mock(AnalyticsAssetAccessRegistrar.class),
            queryGateway,
            mock(AnalysisPublicationService.class),
            new QueryExportService(new ObjectMapper()),
            classificationService
        );
        actor = new AnalyticsUser();
        actor.setId(7L);
        actor.setActive(true);
    }

    @Test
    void csvAndXlsxExportsCarryTheGovernedSealAndBoundedQueryResult() throws Exception {
        MockHttpServletRequest request = request();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(actor));
        when(analysisService.get(42L, actor)).thenReturn(analysis());
        when(classificationService.sealCardExport(eq(42L), anyString(), any()))
            .thenReturn(new ExportSeal("snapshot-1", "subject-1", "DATA_INTERNAL", 3L));
        when(queryGateway.preview(eq(actor), any(AnalysisQuerySpec.class), any()))
            .thenReturn(new AnalysisQueryResult(
                "query-1",
                List.of(Map.of("name", "department", "display_name", "部门"), Map.of("name", "total", "display_name", "数量")),
                List.of(List.of("研发", 12)),
                1,
                false,
                false,
                5,
                "c".repeat(64)
            ));

        MockHttpServletResponse csv = new MockHttpServletResponse();
        resource.exportCsv(42L, request, csv);
        assertThat(csv.getStatus()).isEqualTo(200);
        assertThat(csv.getContentType()).startsWith("text/csv");
        assertThat(csv.getHeader("Content-Disposition")).contains("analysis-42.csv", "filename*=UTF-8''");
        assertThat(csv.getHeader("X-DTS-Classification")).isEqualTo("DATA_INTERNAL");
        assertThat(csv.getHeader("X-DTS-Classification-Snapshot")).isEqualTo("snapshot-1");
        assertThat(csv.getContentAsString(StandardCharsets.UTF_8)).contains("部门,数量", "研发,12");

        MockHttpServletResponse xlsx = new MockHttpServletResponse();
        resource.exportXlsx(42L, request, xlsx);
        assertThat(xlsx.getStatus()).isEqualTo(200);
        assertThat(xlsx.getContentType()).contains("spreadsheetml.sheet");
        assertThat(xlsx.getContentAsByteArray()).startsWith(new byte[] { 'P', 'K' });
    }

    @Test
    void exportDeniesPersonnelBelowTheAnalysisClassification() throws Exception {
        MockHttpServletRequest request = request();
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(actor));
        when(analysisService.get(42L, actor)).thenReturn(analysis());
        when(classificationService.sealCardExport(eq(42L), anyString(), any()))
            .thenThrow(new AnalyticsConsumerClassificationService.PersonnelClassificationDeniedException("clearance denied"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        resource.exportCsv(42L, request, response);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("clearance denied");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/analysis/42/query/csv");
        request.addHeader("X-DTS-Classification", "DATA_INTERNAL");
        return request;
    }

    private AnalysisDto analysis() {
        UUID datasetId = UUID.randomUUID();
        AnalysisQuerySpec spec = new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(datasetId, 1, "v1", "c".repeat(64)),
            List.of(new AnalysisQuerySpec.DimensionSelection("department", null)),
            List.of(new AnalysisQuerySpec.MetricSelection("total", null)),
            List.of(),
            List.of(),
            null,
            List.of(),
            100,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );
        return new AnalysisDto(
            42L,
            "部门分析",
            null,
            "PUBLISHED",
            1,
            9L,
            datasetId,
            1,
            "v1",
            spec.visualization(),
            spec,
            "7",
            Instant.now(),
            Map.of("read", true, "write", false, "publish", true, "export", true)
        );
    }
}
