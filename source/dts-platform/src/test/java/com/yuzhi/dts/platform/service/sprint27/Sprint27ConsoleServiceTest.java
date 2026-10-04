package com.yuzhi.dts.platform.service.sprint27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.etl.DbtReleaseGateService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventSummaryDto;
import com.yuzhi.dts.platform.service.governance.GovernanceOpsMetricsService;
import com.yuzhi.dts.platform.service.governance.IndicatorObservabilityService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Sprint27ConsoleServiceTest {

    private IngestionServiceClient ingestionClient;
    private IndicatorObservabilityService indicatorObservabilityService;
    private PlatformEventOutboxService eventOutboxService;
    private GovernanceOpsMetricsService governanceOpsMetricsService;
    private DbtReleaseGateService dbtReleaseGateService;
    private ModelSpecApplicationService modelSpecApplicationService;
    private Sprint27ConsoleService service;

    @BeforeEach
    void setUp() {
        ingestionClient = mock(IngestionServiceClient.class);
        indicatorObservabilityService = mock(IndicatorObservabilityService.class);
        modelSpecApplicationService = mock(ModelSpecApplicationService.class);
        eventOutboxService = mock(PlatformEventOutboxService.class);
        governanceOpsMetricsService = mock(GovernanceOpsMetricsService.class);
        dbtReleaseGateService = mock(DbtReleaseGateService.class);
        service =
            new Sprint27ConsoleService(
                ingestionClient,
                indicatorObservabilityService,
                modelSpecApplicationService,
                eventOutboxService,
                governanceOpsMetricsService,
                dbtReleaseGateService,
                "default"
            );
    }

    @Test
    void metricOperationsUsesCanonicalModelProjectionWithoutBusinessObjects() {
        when(indicatorObservabilityService.overview(168, null)).thenReturn(Map.of());
        when(indicatorObservabilityService.trend(168, 24, null)).thenReturn(List.of());
        when(modelSpecApplicationService.list("default", null, null, null, null)).thenReturn(List.of());

        Map<String, Object> result = service.metricOperations(168, 24, null);

        assertThat(result).containsKeys("domains", "metrics", "models", "runs");
        assertThat(result).doesNotContainKey("objects");
    }

    @Test
    void eltConsoleReturnsSourceStatusAndFallbackViewModel() {
        when(ingestionClient.getExecutionsObservability(any())).thenReturn(
            new ApiResponse<>(
                ResultStatus.SUCCESS.getCode(),
                "ok",
                Map.of("total", 8, "success", 6, "failed", 1, "running", 1, "terminal", 7, "timeout", 0)
            )
        );
        when(ingestionClient.getGovernanceOverview(any())).thenReturn(
            new ApiResponse<>(ResultStatus.SUCCESS.getCode(), "ok", Map.of("running", 1, "queueLength", 2, "blockedByPolicy", 0))
        );

        Map<String, Object> result = service.eltConsole(7, 24);

        assertThat(result).containsKeys("sources", "observability", "governance", "stages", "chainItems");
        assertThat((List<?>) result.get("stages")).hasSize(5);
        assertThat((List<?>) result.get("chainItems")).hasSize(4);
        Map<?, ?> sources = (Map<?, ?>) result.get("sources");
        assertThat(((Map<?, ?>) sources.get("observability")).get("status")).isEqualTo("READY");
        assertThat(((Map<?, ?>) sources.get("governance")).get("status")).isEqualTo("READY");
    }

    @Test
    void releaseGovernanceDoesNotMarkReadyWhenDependencySourceFails() {
        when(governanceOpsMetricsService.overview(7)).thenReturn(
            Map.of("kpi", Map.of("qualitySuccessRate", 100, "issueOverdueRate", 0))
        );
        when(indicatorObservabilityService.overview(168, null)).thenReturn(Map.of("validation", Map.of("failed", 0)));
        when(ingestionClient.getExecutionsObservability(any())).thenReturn(new ApiResponse<>(500, "ingestion unavailable", null));
        when(eventOutboxService.summarize()).thenReturn(
            new PlatformEventSummaryDto(0, 0, 0, 0, 0, false, "dts.platform.events", Map.of(), Map.of(), Map.of())
        );
        when(dbtReleaseGateService.evaluate(isNull(), isNull(), isNull(), isNull())).thenReturn(
            new DbtReleaseGateService.DbtReleaseGateResult(null, true, null, null, "PASS", false, false, List.of(), List.of(), null)
        );

        Map<String, Object> result = service.releaseGovernance(null);

        assertThat(result).containsEntry("readyForRelease", false);
        Map<?, ?> sources = (Map<?, ?>) result.get("sources");
        assertThat(((Map<?, ?>) sources.get("ingestion")).get("status")).isEqualTo("ERROR");
        assertThat((List<?>) result.get("checks"))
            .anySatisfy(check -> {
                Map<?, ?> row = (Map<?, ?>) check;
                assertThat(row.get("key")).isEqualTo("source-status");
                assertThat(row.get("passed")).isEqualTo(false);
            });
    }
}
