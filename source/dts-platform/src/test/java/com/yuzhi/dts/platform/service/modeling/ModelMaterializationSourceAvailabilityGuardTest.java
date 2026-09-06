package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository.AvailabilityPin;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository.PinnedSnapshot;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository.InputSnapshot;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository.SourceBindingDescriptor;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.CurrentSource;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.ExpectedSource;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.SourceDescriptor;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelMaterializationSourceAvailabilityGuardTest {

    private static final UUID CANDIDATE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAN = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID MODEL = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID SOURCE = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID DISPATCH = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    private final ModelMaterializationSourceSnapshotRepository snapshots = mock(
        ModelMaterializationSourceSnapshotRepository.class
    );
    private final ModelSpecSourceValidationPort sourceValidation = mock(ModelSpecSourceValidationPort.class);
    private final CatalogMaterializationSourceAvailabilityPort catalogAvailability = mock(
        CatalogMaterializationSourceAvailabilityPort.class
    );
    private final ModelMaterializationAvailabilityPinRepository pins = mock(
        ModelMaterializationAvailabilityPinRepository.class
    );
    private ModelMaterializationSourceAvailabilityGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ModelMaterializationSourceAvailabilityGuard(
            snapshots,
            sourceValidation,
            catalogAvailability,
            pins,
            new ObjectMapper()
        );
    }

    @Test
    void currentPhysicalSourcePassesAtTheExecutionBoundary() {
        InputSnapshot physical = physical(MODEL);
        when(snapshots.findCandidateInputs("tenant-a", CANDIDATE, 4)).thenReturn(List.of(physical));
        when(sourceValidation.isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1"))
            .thenReturn(true);

        assertThatCode(() -> guard.requireCandidateCurrent("tenant-a", CANDIDATE, 4)).doesNotThrowAnyException();

        verify(sourceValidation).isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1");
    }

    @Test
    void resolvesExactSourceAndTableFromTheVersionBoundDispatchInput() {
        when(snapshots.findDispatchInputs(DISPATCH)).thenReturn(List.of(physical(MODEL)));
        when(sourceValidation.isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1"))
            .thenReturn(true);
        when(sourceValidation.resolveCurrentBindingForExecutionCompiler("tenant-a", PLAN, SOURCE, "source-version-1"))
            .thenReturn(Optional.of(new SourceRef(
                SourceKind.TABLE,
                "public.prjdemo_ods_project_task_clean",
                Layer.ODS,
                SourceRole.PRIMARY,
                null,
                null,
                null,
                0,
                SOURCE,
                "source-version-1"
            )));

        assertThat(guard.pinnedDispatchSources(DISPATCH)).containsExactly(
            new ModelMaterializationSourceAvailabilityGuard.PinnedSourceDefinition(
                SOURCE,
                "source-version-1",
                "public",
                "public",
                "prjdemo_ods_project_task_clean"
            )
        );
        verify(sourceValidation).resolveCurrentBindingForExecutionCompiler(
            "tenant-a",
            PLAN,
            SOURCE,
            "source-version-1"
        );
    }

    @Test
    void operationalSourcesUseFixedRunSnapshots() {
        when(snapshots.findOperationalInputs(DISPATCH)).thenReturn(List.of(physical(MODEL)));
        when(sourceValidation.isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1")).thenReturn(true);
        when(sourceValidation.resolveCurrentBindingForExecutionCompiler("tenant-a", PLAN, SOURCE, "source-version-1"))
            .thenReturn(Optional.of(new SourceRef(SourceKind.TABLE, "public.sales", Layer.ODS, SourceRole.PRIMARY,
                null, null, null, 0, SOURCE, "source-version-1")));

        assertThat(guard.pinnedOperationalSources(DISPATCH)).containsExactly(
            new ModelMaterializationSourceAvailabilityGuard.PinnedSourceDefinition(SOURCE, "source-version-1", "public", "public", "sales")
        );
        verify(snapshots).findOperationalInputs(DISPATCH);
        org.mockito.Mockito.verifyNoMoreInteractions(snapshots);
    }

    @Test
    void operationalUpstreamCannotFallBackToTheCurrentPublishedHead() {
        UUID upstream = UUID.randomUUID();
        InputSnapshot root = new InputSnapshot("tenant-a", PLAN, MODEL, 2, "a".repeat(64), "UPSTREAM_MODEL",
            "[{\"modelSpecId\":\"" + upstream + "\",\"revision\":3,\"checksum\":\"" + "b".repeat(64) + "\"}]", "[]");
        when(snapshots.findOperationalInputs(DISPATCH)).thenReturn(List.of(root));

        assertThatThrownBy(() -> guard.pinnedOperationalSources(DISPATCH))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .hasMessageContaining("fixed operational scope");
        verify(snapshots).findOperationalInputs(DISPATCH);
        org.mockito.Mockito.verifyNoMoreInteractions(snapshots);
    }

    @Test
    void fencedPhysicalSourceFailsClosed() {
        when(snapshots.findCandidateInputs("tenant-a", CANDIDATE, 4)).thenReturn(List.of(physical(MODEL)));

        assertThatThrownBy(() -> guard.requireCandidateCurrent("tenant-a", CANDIDATE, 4))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error -> ((ModelReleaseCandidateException) error).code())
            .isEqualTo(ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE);
    }

    @Test
    void publishedUpstreamIsTraversedUntilItsPhysicalSource() {
        UUID upstream = UUID.fromString("50000000-0000-0000-0000-000000000005");
        InputSnapshot root = new InputSnapshot(
            "tenant-a",
            PLAN,
            MODEL,
            2,
            "a".repeat(64),
            "UPSTREAM_MODEL",
            "[{\"modelSpecId\":\"" + upstream + "\",\"revision\":3,\"checksum\":\"" + "b".repeat(64) + "\"}]",
            "[]"
        );
        when(snapshots.findDispatchInputs(CANDIDATE)).thenReturn(List.of(root));
        when(snapshots.findPublishedInput("tenant-a", upstream, 3, "b".repeat(64)))
            .thenReturn(List.of(physical(upstream)));
        when(sourceValidation.isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1"))
            .thenReturn(true);

        assertThatCode(() -> guard.requireDispatchCurrent(CANDIDATE)).doesNotThrowAnyException();

        verify(snapshots).findPublishedInput("tenant-a", upstream, 3, "b".repeat(64));
        verify(sourceValidation).isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1");
    }

    @Test
    void generatedImplementationUsesTheSameVersionBoundSourceFence() {
        InputSnapshot generated = new InputSnapshot(
            "tenant-a",
            PLAN,
            MODEL,
            3,
            "b".repeat(64),
            "GENERATED",
            "[{\"generator\":\"designer\"}]",
            "[{\"sourceBindingId\":\"" + SOURCE + "\",\"resolvedVersion\":\"source-version-1\"}]"
        );
        when(snapshots.findCandidateInputs("tenant-a", CANDIDATE, 4)).thenReturn(List.of(generated));
        when(sourceValidation.isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1"))
            .thenReturn(true);

        assertThatCode(() -> guard.requireCandidateCurrent("tenant-a", CANDIDATE, 4)).doesNotThrowAnyException();

        verify(sourceValidation).isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1");
    }

    @Test
    void multipleBuildModelsMayShareOneExactSourceGeneration() {
        UUID secondModel = UUID.fromString("30000000-0000-0000-0000-000000000004");
        when(snapshots.findCandidateInputs("tenant-a", CANDIDATE, 4))
            .thenReturn(List.of(physical(MODEL), physical(secondModel)));
        when(sourceValidation.isCurrentBindingForExecution("tenant-a", PLAN, SOURCE, "source-version-1"))
            .thenReturn(true);

        assertThatCode(() -> guard.requireCandidateCurrent("tenant-a", CANDIDATE, 4)).doesNotThrowAnyException();
    }

    @Test
    void runtimeLeasePinsExactCatalogGeneration() {
        InputSnapshot physical = physical(MODEL);
        SourceDescriptor descriptor = new SourceDescriptor(
            SOURCE,
            "CATALOG_TABLE",
            SOURCE.toString(),
            "{\"assetId\":\"" + SOURCE + "\"}"
        );
        when(pins.findSnapshot(DISPATCH)).thenReturn(
            new PinnedSnapshot(DISPATCH, false, null, 0, List.of())
        );
        when(snapshots.findDispatchInputs(DISPATCH)).thenReturn(
            List.of(physical)
        );
        when(
            sourceValidation.isCurrentBindingForExecution(
                "tenant-a",
                PLAN,
                SOURCE,
                "source-version-1"
            )
        ).thenReturn(true);
        when(
            snapshots.findSourceBindingDescriptor(
                "tenant-a",
                PLAN,
                SOURCE,
                "source-version-1"
            )
        ).thenReturn(
            java.util.Optional.of(
                new SourceBindingDescriptor(
                    SOURCE,
                    descriptor.sourceType(),
                    descriptor.sourceId(),
                    descriptor.locatorJson(),
                    "source-version-1"
                )
            )
        );
        when(catalogAvailability.lockAndRead(List.of(descriptor)))
            .thenReturn(
                List.of(
                    new CurrentSource(
                        SOURCE,
                        CatalogAssetType.DATASET,
                        "catalog://dataset/finance",
                        "AVAILABLE",
                        7L,
                        100L,
                        "available-100"
                    )
                )
            );

        guard.pinDispatchCurrent(DISPATCH, NOW);

        verify(pins).persistSnapshot(
            DISPATCH,
            List.of(
                new AvailabilityPin(
                    SOURCE,
                    CatalogAssetType.DATASET,
                    "catalog://dataset/finance",
                    "AVAILABLE",
                    7L,
                    100L,
                    "available-100",
                    "source-version-1"
                )
            ),
            NOW
        );
    }

    @Test
    void exactEpochSequenceOrEventDriftIsRejected() {
        AvailabilityPin pin = new AvailabilityPin(
            SOURCE,
            CatalogAssetType.DATASET,
            "catalog://dataset/finance",
            "AVAILABLE",
            7L,
            100L,
            "available-100",
            "source-version-1"
        );
        when(pins.lockSnapshot(DISPATCH)).thenReturn(
            new PinnedSnapshot(DISPATCH, true, NOW, 1, List.of(pin))
        );
        when(
            catalogAvailability.lockAndCompare(
                List.of(
                    new ExpectedSource(
                        SOURCE,
                        CatalogAssetType.DATASET,
                        "catalog://dataset/finance",
                        "AVAILABLE",
                        7L,
                        100L,
                        "available-100"
                    )
                )
            )
        ).thenReturn(
            List.of(
                new CatalogMaterializationSourceAvailabilityPort.GenerationDrift(
                    SOURCE,
                    CatalogAssetType.DATASET,
                    "catalog://dataset/finance",
                    "AVAILABLE",
                    7L,
                    100L,
                    "available-100",
                    "FENCED",
                    7L,
                    101L,
                    "fence-101"
                )
            )
        );

        var result = guard.checkPinnedCurrentForUpdate(DISPATCH);

        assertThat(result.current()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(
            ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
        );
        assertThat(result.drift()).singleElement().satisfies(drift -> {
            assertThat(drift.pinnedEpoch()).isEqualTo(7L);
            assertThat(drift.pinnedSourceSequence()).isEqualTo(100L);
            assertThat(drift.currentStatus()).isEqualTo("FENCED");
            assertThat(drift.currentSourceSequence()).isEqualTo(101L);
        });
    }

    private InputSnapshot physical(UUID modelSpecId) {
        return new InputSnapshot(
            "tenant-a",
            PLAN,
            modelSpecId,
            3,
            "b".repeat(64),
            "PHYSICAL_ASSET",
            "[{\"sourceBindingId\":\"" + SOURCE + "\",\"resolvedVersion\":\"source-version-1\"}]",
            "[]"
        );
    }
}
