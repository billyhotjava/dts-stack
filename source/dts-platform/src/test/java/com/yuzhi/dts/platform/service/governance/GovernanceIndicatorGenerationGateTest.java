package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.governance.GovIndicatorSubscriptionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.GovernanceIndicatorResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class GovernanceIndicatorGenerationGateTest {

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
    void validatesPublishedAccessBeforeGeneratingAnyArtifact() {
        UUID id = UUID.randomUUID();
        when(dbtGenerator.generateAndRun(List.of(id))).thenReturn(
            new GenerationResult(
                1,
                List.of("models/ads/general/ind_GMV.sql"),
                "READY",
                "PENDING",
                List.of()
            )
        );

        ApiResponse<GenerationResult> response = resource.generateIndicators(
            Map.of("indicatorIds", List.of(id.toString())),
            "DEPT_A"
        );

        assertThat(response.getData().generatedFiles()).containsExactly("models/ads/general/ind_GMV.sql");
        assertThat(response.getData().runStatus()).isNull();
        InOrder order = inOrder(indicators, dbtGenerator);
        order.verify(indicators).validateGenerationAccess(List.of(id), "DEPT_A");
        order.verify(dbtGenerator).generateAndRun(List.of(id));
        verify(dbtGenerator, never()).generateBatch(anyList());
        verify(dbtGenerator, never()).generateSchemaYml(anyList());
    }

    @Test
    void rejectsEmptyOrOversizedGenerationBatchesBeforeAccessingRepositories() {
        assertThatThrownBy(() -> resource.generateIndicators(Map.of("indicatorIds", List.of()), "DEPT_A"))
            .isInstanceOf(IndicatorRequestException.class)
            .hasMessageContaining("1 到 64");

        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            tooMany.add(UUID.randomUUID().toString());
        }
        assertThatThrownBy(() -> resource.generateIndicators(Map.of("indicatorIds", tooMany), "DEPT_A"))
            .isInstanceOf(IndicatorRequestException.class)
            .hasMessageContaining("1 到 64")
            .hasMessageContaining("含传递依赖");

        verify(indicators, never()).validateGenerationAccess(anyList(), any());
        verify(dbtGenerator, never()).generateBatch(anyList());
        verify(dbtGenerator, never()).generateAndRun(anyList());
    }

    @Test
    void lifecyclePreviewStartsOnlyAfterTheSharedGraphLock() {
        UUID id = UUID.randomUUID();
        when(previewService.preview(id, "DEPT_A")).thenReturn(
            Map.of(
                "readyToPublish",
                false,
                "failureReasonCode",
                "IND_VALIDATION_FAILED",
                "blockingIssues",
                List.of(Map.of("message", "校验已过期"))
            )
        );

        assertThatThrownBy(() -> resource.publishIndicator(id, "DEPT_A"))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("IND_VALIDATION_FAILED");

        InOrder order = inOrder(indicators, previewService);
        order.verify(indicators).lockLifecycleGraphForUpdate();
        order.verify(previewService).preview(id, "DEPT_A");
        verify(indicators, never()).publish(any(UUID.class), any());
    }

    @Test
    void indicatorDomainExceptionsExposeNarrowHttpStatuses() {
        assertThat(statusOf(IndicatorRequestException.class)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(IndicatorNotFoundException.class)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(IndicatorConflictException.class)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(AnnotatedElementUtils.findMergedAnnotation(IllegalStateException.class, ResponseStatus.class))
            .isNull();
    }

    @Test
    void technicalDbtFailureIsNotReclassifiedAsClientError() {
        UUID id = UUID.randomUUID();
        IllegalStateException technicalFailure = new IllegalStateException("dbt filesystem unavailable");
        when(dbtGenerator.generateAndRun(List.of(id))).thenThrow(technicalFailure);

        assertThatThrownBy(() ->
            resource.generateIndicators(Map.of("indicatorIds", List.of(id.toString())), null)
        ).isSameAs(technicalFailure);
    }

    @Test
    void dbtPublishPreviewFailureRemainsAServerError() {
        UUID id = UUID.randomUUID();
        when(previewService.preview(id, null)).thenReturn(
            Map.of(
                "readyToPublish",
                false,
                "failureReasonCode",
                "IND_DBT_COMPILE_FAILED",
                "blockingIssues",
                List.of(Map.of("message", "dbt 指标产物编译未通过"))
            )
        );

        assertThatThrownBy(() -> resource.publishIndicator(id, null))
            .isExactlyInstanceOf(IllegalStateException.class)
            .hasMessageContaining("IND_DBT_COMPILE_FAILED");
    }

    @Test
    void rollbackAndPublishUsesTheSamePreviewGateInsideTheResourceTransaction() {
        UUID id = UUID.randomUUID();
        Map<String, Object> draftRollback = new java.util.LinkedHashMap<>();
        draftRollback.put("rollbackToVersion", "v4");
        draftRollback.put("published", false);
        when(indicators.rollbackToVersion(id, "v1", "DEPT_A", "restore", false)).thenReturn(draftRollback);
        when(previewService.preview(id, "DEPT_A")).thenReturn(Map.of("readyToPublish", true));
        IndicatorDto published = new IndicatorDto();
        published.setId(id);
        published.setStatus("PUBLISHED");
        published.setVersion("v4");
        when(indicators.publish(id, "DEPT_A")).thenReturn(published);

        ApiResponse<Map<String, Object>> response = resource.rollbackIndicatorVersion(
            id,
            "v1",
            new GovernanceIndicatorResource.IndicatorRollbackRequest("restore", true),
            "DEPT_A"
        );

        assertThat(response.getData()).containsEntry("published", true);
        assertThat(response.getData()).containsEntry("indicator", published);
        InOrder order = inOrder(indicators, previewService);
        order.verify(indicators).rollbackToVersion(id, "v1", "DEPT_A", "restore", false);
        order.verify(previewService).preview(id, "DEPT_A");
        order.verify(indicators).publish(id, "DEPT_A");
    }

    private static HttpStatus statusOf(Class<? extends RuntimeException> type) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(type, ResponseStatus.class);
        assertThat(status).isNotNull();
        return status.code();
    }
}
