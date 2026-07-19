package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.governance.IndicatorService;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelSpecMetricReferenceServiceTest {

    @Test
    void appendsExactPublishedIndicatorVersionAsANewPublishedModelRevision() {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000071");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000071");
        UUID indicatorId = UUID.fromString("60000000-0000-0000-0000-000000000071");
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecCompatibilityReader reader = mock(ModelSpecCompatibilityReader.class);
        ModelSpecSnapshotCodec codec = mock(ModelSpecSnapshotCodec.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        IndicatorService indicators = mock(IndicatorService.class);
        StoredModelSpec stored = mock(StoredModelSpec.class);
        ModelSpecView current = mock(ModelSpecView.class);
        ModelSpecView replacement = mock(ModelSpecView.class);
        when(repository.findCurrent("tenant-1", modelId)).thenReturn(Optional.of(stored));
        when(reader.read(stored)).thenReturn(current);
        when(current.id()).thenReturn(modelId);
        when(current.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(current.planId()).thenReturn(planId);
        when(current.status()).thenReturn(ModelStatus.PUBLISHED);
        when(current.modelType()).thenReturn(ModelType.FACT);
        when(current.revision()).thenReturn(3);
        when(current.checksum()).thenReturn("a".repeat(64));
        when(current.metricRefs()).thenReturn(List.of());
        when(repository.lockPlan("tenant-1", planId)).thenReturn(Optional.of(new PlanState(planId, "DRAFT")));
        when(writeAccess.canMaintain("tenant-1", planId, "actor-1")).thenReturn(true);
        IndicatorDto indicator = new IndicatorDto();
        indicator.setId(indicatorId);
        indicator.setStatus("PUBLISHED");
        indicator.setVersion("v2");
        when(indicators.get(indicatorId, null)).thenReturn(indicator);
        when(codec.toUpdatedView(eq(current), any(UpdateModelSpecCommand.class), eq(4), any(Instant.class)))
            .thenReturn(replacement);
        when(codec.write(replacement)).thenReturn("{}");
        when(repository.compareAndSetPublishedMetricRefs("tenant-1", "actor-1", 3, "a".repeat(64), replacement, "{}"))
            .thenReturn(1);

        ModelSpecView result = new ModelSpecMetricReferenceService(
            repository,
            reader,
            codec,
            writeAccess,
            indicators,
            new ModelSpecFeatureFlags(true, true),
            Clock.fixed(Instant.parse("2026-07-20T09:00:00Z"), ZoneOffset.UTC)
        ).bind(
            "tenant-1",
            "actor-1",
            modelId,
            new ExpectedVersion(modelId, 3, "a".repeat(64)),
            new ModelSpecMetricReferenceService.BindMetricReferenceCommand(indicatorId, 2)
        );

        assertThat(result).isSameAs(replacement);
        ArgumentCaptor<UpdateModelSpecCommand> command = ArgumentCaptor.forClass(UpdateModelSpecCommand.class);
        verify(codec).toUpdatedView(eq(current), command.capture(), eq(4), any(Instant.class));
        assertThat(command.getValue().metricRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.metricId()).isEqualTo(indicatorId.toString());
            assertThat(ref.version()).isEqualTo(2);
        });
        verify(repository).insertV2Revision("tenant-1", "actor-1", replacement, "{}");
    }
}
