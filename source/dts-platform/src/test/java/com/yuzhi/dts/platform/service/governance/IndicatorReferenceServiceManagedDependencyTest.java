package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndicatorReferenceServiceManagedDependencyTest {

    private final GovIndicatorDefinitionRepository indicatorRepository = mock(GovIndicatorDefinitionRepository.class);
    private final GovIndicatorReferenceRepository referenceRepository = mock(GovIndicatorReferenceRepository.class);
    private final IndicatorService indicatorService = mock(IndicatorService.class);
    private final IndicatorReferenceService service = new IndicatorReferenceService(
        indicatorRepository,
        referenceRepository,
        indicatorService
    );

    @Test
    void rejectsManualCreationOfManagedIndicatorReference() {
        UUID indicatorId = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));

        assertThatThrownBy(() ->
            service.create(
                indicatorId,
                null,
                new IndicatorReferenceService.ReferenceUpsertRequest("INDICATOR", UUID.randomUUID().toString(), null, null)
            )
        )
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("dependencyIndicators");
        verify(referenceRepository, never()).save(any(GovIndicatorReference.class));
    }

    @Test
    void rejectsManualUpdateOrDeletionOfManagedIndicatorReference() {
        UUID indicatorId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        GovIndicatorReference reference = new GovIndicatorReference();
        reference.setId(referenceId);
        reference.setIndicator(indicator);
        reference.setRefType("INDICATOR");
        reference.setRefTarget(UUID.randomUUID().toString());
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(referenceRepository.findById(referenceId)).thenReturn(Optional.of(reference));

        assertThatThrownBy(() ->
            service.update(
                indicatorId,
                referenceId,
                null,
                new IndicatorReferenceService.ReferenceUpsertRequest("DATASET", UUID.randomUUID().toString(), null, null)
            )
        )
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("dependencyIndicators");
        assertThatThrownBy(() -> service.delete(indicatorId, referenceId, null))
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("dependencyIndicators");
        verify(referenceRepository, never()).save(any(GovIndicatorReference.class));
        verify(referenceRepository, never()).delete(reference);
    }

    @Test
    void mapsInvalidPayloadAndMissingReferenceToDomainHttpExceptions() {
        UUID indicatorId = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        when(indicatorRepository.findById(indicatorId))
            .thenReturn(Optional.of(indicator));

        assertThatThrownBy(() -> service.create(indicatorId, null, null))
            .isInstanceOf(IndicatorRequestException.class);
        assertThatThrownBy(() ->
            service.update(
                indicatorId,
                UUID.randomUUID(),
                null,
                new IndicatorReferenceService.ReferenceUpsertRequest("DATASET", "dataset-1", null, null)
            )
        )
            .isInstanceOf(IndicatorNotFoundException.class);
    }
}
