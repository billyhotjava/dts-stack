package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.governance.GovIndicatorSubscriptionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.GovernanceIndicatorResource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.prepost.PreAuthorize;

class IndicatorRevisionWorkflowTest {

    private final IndicatorService indicators = mock(IndicatorService.class);
    private final IndicatorPublishPreviewService previewService = mock(IndicatorPublishPreviewService.class);
    private final DbtIndicatorGenerator dbtGenerator = mock(DbtIndicatorGenerator.class);
    private final GovernanceIndicatorResource resource = new GovernanceIndicatorResource(
        indicators,
        mock(DimensionService.class),
        mock(IndicatorReferenceService.class),
        previewService,
        mock(IndicatorObservabilityService.class),
        mock(IndicatorTemplateService.class),
        mock(IndicatorDashboardService.class),
        mock(IndicatorCalculationService.class),
        dbtGenerator,
        mock(GovIndicatorSubscriptionRepository.class),
        mock(AuditService.class),
        mock(PlatformEventOutboxService.class)
    );

    @Test
    void stagesPreviewsAndPublishesRevisionInThatOrder() {
        UUID id = UUID.randomUUID();
        IndicatorUpsertRequest request = new IndicatorUpsertRequest();
        request.setCode("GMV");
        IndicatorDto draft = indicator(id, "GMV", "DRAFT", "v2");
        IndicatorDto published = indicator(id, "GMV", "PUBLISHED", "v2");
        when(indicators.stageRevision(id, request, null)).thenReturn(draft);
        when(previewService.preview(id, null)).thenReturn(Map.of("readyToPublish", true));
        when(indicators.publish(id, null)).thenReturn(published);

        ApiResponse<IndicatorDto> response = resource.publishIndicatorRevision(id, request, null);

        assertThat(response.getData().getStatus()).isEqualTo("PUBLISHED");
        assertThat(response.getData().getVersion()).isEqualTo("v2");
        InOrder order = inOrder(indicators, previewService);
        order.verify(indicators).stageRevision(id, request, null);
        order.verify(previewService).preview(id, null);
        order.verify(indicators).publish(id, null);
    }

    @Test
    void failedPreviewStopsPublishSoOuterTransactionCanRollBackStage() {
        UUID id = UUID.randomUUID();
        IndicatorUpsertRequest request = new IndicatorUpsertRequest();
        request.setCode("GMV");
        when(indicators.stageRevision(id, request, null)).thenReturn(indicator(id, "GMV", "DRAFT", "v2"));
        when(previewService.preview(id, null))
            .thenReturn(
                Map.of(
                    "readyToPublish",
                    false,
                    "failureReasonCode",
                    "DERIVATION_CYCLE",
                    "blockingIssues",
                    List.of(Map.of("message", "依赖链形成循环"))
                )
            );

        assertThatThrownBy(() -> resource.publishIndicatorRevision(id, request, null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("DERIVATION_CYCLE");
        verify(indicators, never()).publish(any(UUID.class), any());
    }

    @Test
    void dbtPreviewRequiresMaintainerRoleAndIndicatorAccess() throws Exception {
        UUID id = UUID.randomUUID();
        when(dbtGenerator.previewSql(id)).thenReturn(Map.of("sql", "select 1"));

        ApiResponse<Map<String, String>> response = resource.previewIndicatorSql(id, "DEPT_A");

        assertThat(response.getData()).containsEntry("sql", "select 1");
        verify(indicators).validateGenerationAccess(List.of(id), "DEPT_A");
        PreAuthorize preAuthorize = GovernanceIndicatorResource.class
            .getMethod("previewIndicatorSql", UUID.class, String.class)
            .getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("GOVERNANCE_MAINTAINERS");
    }

    private static IndicatorDto indicator(UUID id, String code, String status, String version) {
        IndicatorDto value = new IndicatorDto();
        value.setId(id);
        value.setCode(code);
        value.setName(code);
        value.setStatus(status);
        value.setVersion(version);
        return value;
    }
}
