package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.SealRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogClassificationBoundaryAdapterTest {

    @Mock
    private CatalogClassificationService classifications;

    @Test
    void mapsResolvedJpaSnapshotToImmutableFact() {
        CatalogClassificationSnapshot snapshot = snapshot("ASSET", "dbt:model.orders", "DATA_SECRET", "PROPAGATED");
        when(classifications.resolve("ASSET", "dbt:model.orders")).thenReturn(Optional.of(snapshot));

        ClassificationFact fact = new CatalogClassificationBoundaryAdapter(classifications)
            .resolve("ASSET", "dbt:model.orders")
            .orElseThrow();

        assertThat(fact)
            .isEqualTo(new ClassificationFact("ASSET", "dbt:model.orders", "DATA_SECRET", "PROPAGATED"));
        assertThat(fact.propagated()).isTrue();
    }

    @Test
    void preservesEverySealFieldAndReturnsOnlyTheImmutableFact() {
        SealRequest request = new SealRequest(
            "ASSET",
            "dbt:model.orders",
            "DBT_MODEL",
            null,
            null,
            null,
            List.of("DATA_INTERNAL"),
            "UPSTREAM_INHERITANCE",
            "candidate:1",
            "checksum",
            "{}"
        );
        when(classifications.sealOrRaise(any())).thenReturn(
            snapshot("ASSET", "dbt:model.orders", "DATA_INTERNAL", "PROPAGATION_PENDING")
        );

        ClassificationFact fact = new CatalogClassificationBoundaryAdapter(classifications).sealOrRaise(request);

        ArgumentCaptor<CatalogClassificationService.SealCommand> command = ArgumentCaptor.forClass(
            CatalogClassificationService.SealCommand.class
        );
        verify(classifications).sealOrRaise(command.capture());
        assertThat(command.getValue().subjectType()).isEqualTo(request.subjectType());
        assertThat(command.getValue().subjectKey()).isEqualTo(request.subjectKey());
        assertThat(command.getValue().assetType()).isEqualTo(request.assetType());
        assertThat(command.getValue().upstreamLevels()).isEqualTo(List.of("DATA_INTERNAL"));
        assertThat(command.getValue().originType()).isEqualTo(request.originType());
        assertThat(command.getValue().originRef()).isEqualTo(request.originRef());
        assertThat(command.getValue().evidenceChecksum()).isEqualTo(request.evidenceChecksum());
        assertThat(command.getValue().evidenceJson()).isEqualTo(request.evidenceJson());
        assertThat(fact.propagated()).isFalse();
    }

    private static CatalogClassificationSnapshot snapshot(
        String subjectType,
        String subjectKey,
        String effectiveLevel,
        String propagationStatus
    ) {
        CatalogClassificationSnapshot snapshot = new CatalogClassificationSnapshot();
        snapshot.setSubjectType(subjectType);
        snapshot.setSubjectKey(subjectKey);
        snapshot.setEffectiveLevel(effectiveLevel);
        snapshot.setPropagationStatus(propagationStatus);
        return snapshot;
    }
}
