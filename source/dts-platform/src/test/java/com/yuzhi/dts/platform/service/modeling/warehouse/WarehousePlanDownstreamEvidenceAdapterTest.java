package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.MetricRef;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.service.governance.GovernanceIndicatorEvidenceReadPort;
import com.yuzhi.dts.platform.service.governance.GovernanceIndicatorEvidenceReadService;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class WarehousePlanDownstreamEvidenceAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildStageRequiresAReleaseCandidateRegardlessOfTheModelLifecycleGate() {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000067");
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(modelId);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        GovernanceIndicatorEvidenceReadPort indicators = mock(GovernanceIndicatorEvidenceReadPort.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());
        WarehousePlanDownstreamEvidenceAdapter adapter = new WarehousePlanDownstreamEvidenceAdapter(
            indicators,
            jdbc,
            new ObjectMapper()
        );

        var evidence = adapter.read(
            "tenant-1",
            UUID.fromString("10000000-0000-0000-0000-000000000067"),
            List.of(model)
        );

        assertThat(evidence).filteredOn(item -> item.stageCode() == WarehousePlanStageProjectionService.StageCode.BUILD_QUALITY_RELEASE).singleElement().satisfies(item -> {
            assertThat(item.stageCode()).isEqualTo(WarehousePlanStageProjectionService.StageCode.BUILD_QUALITY_RELEASE);
            assertThat(item.status()).isEqualTo(WarehousePlanStageProjectionService.StageStatus.BLOCKED);
            assertThat(item.blockerCode()).isEqualTo("MODEL_RELEASE_EVIDENCE_REQUIRED");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildStageRequiresAModelBeforeReleaseEvidenceCanBeRecorded() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());
        WarehousePlanDownstreamEvidenceAdapter adapter = new WarehousePlanDownstreamEvidenceAdapter(
            mock(GovernanceIndicatorEvidenceReadPort.class),
            jdbc,
            new ObjectMapper()
        );

        var evidence = adapter.read("tenant-1", UUID.fromString("10000000-0000-0000-0000-000000000069"), List.of());

        assertThat(evidence)
            .filteredOn(item -> item.stageCode() == WarehousePlanStageProjectionService.StageCode.BUILD_QUALITY_RELEASE)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo(WarehousePlanStageProjectionService.StageStatus.BLOCKED);
                assertThat(item.blockerCode()).isEqualTo("MODEL_SPEC_REQUIRED");
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricStageRequiresAnExactPublishedIndicatorVersionFromTheOwner() {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000068");
        UUID indicatorId = UUID.fromString("60000000-0000-0000-0000-000000000068");
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(modelId);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.modelType()).thenReturn(ModelType.FACT);
        when(model.metricRefs()).thenReturn(List.of(new MetricRef(indicatorId.toString(), 1)));
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        indicator.setStatus("PUBLISHED");
        indicator.setVersion("v2");
        GovIndicatorDefinitionRepository indicators = mock(GovIndicatorDefinitionRepository.class);
        when(indicators.findById(indicatorId)).thenReturn(Optional.of(indicator));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());
        WarehousePlanDownstreamEvidenceAdapter adapter = new WarehousePlanDownstreamEvidenceAdapter(
            new GovernanceIndicatorEvidenceReadService(indicators),
            jdbc,
            new ObjectMapper()
        );

        var evidence = adapter.read(
            "tenant-1",
            UUID.fromString("10000000-0000-0000-0000-000000000068"),
            List.of(model)
        );

        assertThat(evidence)
            .filteredOn(item -> item.stageCode() == WarehousePlanStageProjectionService.StageCode.METRIC_SYSTEM)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo(WarehousePlanStageProjectionService.StageStatus.BLOCKED);
                assertThat(item.freshness()).isEqualTo(WarehousePlanStageProjectionService.EvidenceFreshness.STALE);
                assertThat(item.blockerCode()).isEqualTo("MODEL_METRIC_REF_STALE");
            });
    }
}
