package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyFacts;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ImplementationPin;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelImplementationDependencySnapshotResolverTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DIM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID FACT_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID DWS_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID SOURCE_A = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_B = UUID.fromString("50000000-0000-0000-0000-000000000002");

    private final ModelImplementationDependencySnapshotResolver resolver =
        new ModelImplementationDependencySnapshotResolver();

    @Test
    void resolvesOdsAndDimensionInputsIntoOneStableSnapshotRegardlessOfFactOrdering() {
        SourceRef sourceA = source(SOURCE_A, "ods.project", "source-v1");
        SourceRef sourceB = source(SOURCE_B, "ods.budget", "source-v2");
        ModelSpecView dimension = model(DIM_ID, ModelType.DIMENSION, Layer.DWD, List.of(), List.of(), List.of(sourceA));
        ModelSpecView fact = model(
            FACT_ID,
            ModelType.FACT,
            Layer.DWD,
            List.of(),
            List.of(new ModelRevisionRef(DIM_ID, 1)),
            List.of(sourceB, sourceA)
        );
        ImplementationView implementation = implementation(
            fact,
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(SOURCE_B, "source-v2"), new PhysicalAssetInput(SOURCE_A, "source-v1"))
        );
        DependencyFacts facts = facts(
            List.of(modelFact(fact), modelFact(dimension)),
            List.of(
                new PhysicalSourceFact(SOURCE_B, "source-v2", true),
                new PhysicalSourceFact(SOURCE_A, "source-v1", true)
            )
        );

        Snapshot first = resolver.resolve(fact, implementation, "dts_fact", facts);
        Snapshot replay = resolver.resolve(
            fact,
            implementation,
            "dts_fact",
            facts(List.of(modelFact(dimension), modelFact(fact)), facts.physicalSources().reversed())
        );

        assertThat(first.physicalSources()).extracting(item -> item.sourceBindingId().toString())
            .containsExactly(SOURCE_A.toString(), SOURCE_B.toString());
        assertThat(first.modelInputs()).singleElement().satisfies(input -> {
            assertThat(input.modelSpecId()).isEqualTo(DIM_ID);
            assertThat(input.role()).isEqualTo(ModelImplementationDependencySnapshotResolver.DependencyRole.DIMENSION);
            assertThat(input.implementationRevision()).isEqualTo(1);
        });
        assertThat(first.dependencyChecksum()).matches("^[0-9a-f]{64}$");
        assertThat(replay).isEqualTo(first);
    }

    @Test
    void changesDependencyChecksumWhenAnUpstreamImplementationPinChanges() {
        ModelSpecView upstream = model(FACT_ID, ModelType.FACT, Layer.DWD, List.of(), List.of(), List.of(source(SOURCE_A, "ods.project", "v1")));
        ModelSpecView summary = model(
            DWS_ID,
            ModelType.SUMMARY,
            Layer.DWS,
            List.of(new ModelRevisionRef(FACT_ID, 1)),
            List.of(),
            List.of()
        );
        ImplementationView owner = implementation(
            summary,
            InputMode.UPSTREAM_MODEL,
            List.of(upstreamInput(upstream, 1, "model.pjm." + upstream.name()))
        );
        Snapshot current = resolver.resolve(summary, owner, "dts_summary", facts(List.of(modelFact(summary), modelFact(upstream)), List.of()));
        ModelFact changed = new ModelFact(
            upstream,
            new ImplementationPin(1, upstream.checksum(), 2, "e".repeat(64), "model.pjm." + upstream.name(), "ACTIVE")
        );

        assertThatThrownBy(() -> resolver.resolve(summary, owner, "dts_summary", facts(List.of(modelFact(summary), changed), List.of())))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE");

        ImplementationView repinnedOwner = implementation(
            summary,
            InputMode.UPSTREAM_MODEL,
            List.of(upstreamInput(upstream, 2, "model.pjm." + upstream.name()))
        );
        Snapshot repinned = resolver.resolve(summary, repinnedOwner, "dts_summary", facts(List.of(modelFact(summary), changed), List.of()));
        assertThat(repinned.dependencyChecksum()).isNotEqualTo(current.dependencyChecksum());
    }

    @Test
    void rejectsMissingUndeclaredStaleAndCyclicDependenciesWithStableCodes() {
        ModelSpecView dimension = model(DIM_ID, ModelType.DIMENSION, Layer.DWD, List.of(), List.of(), List.of());
        ModelSpecView fact = model(
            FACT_ID,
            ModelType.FACT,
            Layer.DWD,
            List.of(),
            List.of(new ModelRevisionRef(DIM_ID, 1)),
            List.of(source(SOURCE_A, "ods.project", "v1"))
        );
        ImplementationView owner = implementation(
            fact,
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(SOURCE_A, "v1"))
        );

        assertCode(
            "MODEL_IMPLEMENTATION_DEPENDENCY_MISSING",
            () -> resolver.resolve(fact, owner, "dts_fact", facts(List.of(modelFact(fact)), List.of(new PhysicalSourceFact(SOURCE_A, "v1", true))))
        );
        assertCode(
            "MODEL_SOURCE_BINDING_STALE",
            () -> resolver.resolve(fact, owner, "dts_fact", facts(List.of(modelFact(fact), modelFact(dimension)), List.of(new PhysicalSourceFact(SOURCE_A, "v2", true))))
        );

        ImplementationView undeclared = implementation(
            fact,
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(SOURCE_A, "v1"), new PhysicalAssetInput(SOURCE_B, "v1"))
        );
        assertCode(
            "MODEL_IMPLEMENTATION_DEPENDENCY_UNDECLARED",
            () -> resolver.resolve(fact, undeclared, "dts_fact", facts(List.of(modelFact(fact), modelFact(dimension)), List.of(new PhysicalSourceFact(SOURCE_A, "v1", true))))
        );

        ModelSpecView cyclicDimension = model(
            DIM_ID,
            ModelType.DIMENSION,
            Layer.DWD,
            List.of(new ModelRevisionRef(FACT_ID, 1)),
            List.of(),
            List.of()
        );
        assertCode(
            "MODEL_IMPLEMENTATION_DEPENDENCY_CYCLE",
            () -> resolver.resolve(fact, owner, "dts_fact", facts(List.of(modelFact(fact), modelFact(cyclicDimension)), List.of(new PhysicalSourceFact(SOURCE_A, "v1", true))))
        );
    }

    @Test
    void reconcilesParsedSourceAndRefEvidenceAgainstTheDeclaredSnapshot() {
        ModelSpecView dimension = model(DIM_ID, ModelType.DIMENSION, Layer.DWD, List.of(), List.of(), List.of());
        ModelSpecView fact = model(
            FACT_ID,
            ModelType.FACT,
            Layer.DWD,
            List.of(),
            List.of(new ModelRevisionRef(DIM_ID, 1)),
            List.of(source(SOURCE_A, "ods.project", "v1"))
        );
        Snapshot snapshot = resolver.resolve(
            fact,
            implementation(fact, InputMode.PHYSICAL_ASSET, List.of(new PhysicalAssetInput(SOURCE_A, "v1"))),
            "dts_fact",
            facts(List.of(modelFact(fact), modelFact(dimension)), List.of(new PhysicalSourceFact(SOURCE_A, "v1", true)))
        );
        List<String> exact = List.of(
            snapshot.physicalSources().getFirst().dbtSourceUniqueId(),
            snapshot.modelInputs().getFirst().dbtUniqueId()
        );

        assertThat(resolver.reconcileParsedDependencies(snapshot, exact).matched()).containsExactlyInAnyOrderElementsOf(exact);
        assertCode(
            "MODEL_IMPLEMENTATION_DEPENDENCY_UNDECLARED",
            () -> resolver.reconcileParsedDependencies(snapshot, List.of(exact.getFirst(), "model.other.rogue"))
        );
        assertCode(
            "MODEL_IMPLEMENTATION_DEPENDENCY_MISSING",
            () -> resolver.reconcileParsedDependencies(snapshot, List.of(exact.getFirst()))
        );
    }

    private static DependencyFacts facts(List<ModelFact> models, List<PhysicalSourceFact> physicalSources) {
        return new DependencyFacts(models, physicalSources);
    }

    private static ModelFact modelFact(ModelSpecView model) {
        return new ModelFact(
            model,
            new ImplementationPin(
                model.revision(),
                model.checksum(),
                1,
                checksum(model.id()),
                "model.pjm." + model.name(),
                "ACTIVE"
            )
        );
    }

    private static ImplementationView implementation(
        ModelSpecView model,
        InputMode inputMode,
        List<ImplementationInput> inputs
    ) {
        return new ImplementationView(
            UUID.nameUUIDFromBytes((model.id() + "-implementation").getBytes()),
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            ImplementationMode.DESIGNER_GENERATED,
            "dts_owner",
            "model.dts_owner." + model.name(),
            "ACTIVE",
            1,
            checksum(model.id()),
            inputMode,
            inputs,
            List.of(),
            Map.of(),
            "table"
        );
    }

    private static UpstreamModelInput upstreamInput(ModelSpecView model, int implementationRevision, String dbtUniqueId) {
        return new UpstreamModelInput(
            model.id(),
            model.revision(),
            model.checksum(),
            implementationRevision,
            implementationRevision == 1 ? checksum(model.id()) : "e".repeat(64),
            dbtUniqueId
        );
    }

    private static ModelSpecView model(
        UUID id,
        ModelType type,
        Layer layer,
        List<ModelRevisionRef> dependsOn,
        List<ModelRevisionRef> dimensionRefs,
        List<SourceRef> sources
    ) {
        return new ModelSpecView(
            2,
            id,
            PLAN_ID,
            DOMAIN_ID,
            type,
            layer,
            type.name().toLowerCase() + "_" + id.toString().substring(0, 4),
            "test model",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per key", List.of("id")),
            null,
            null,
            List.of(new ModelField("id", "bigint", false, null, FieldRole.KEY, null)),
            sources,
            dependsOn,
            dimensionRefs,
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            1,
            id.equals(FACT_ID) ? "b".repeat(64) : id.equals(DIM_ID) ? "c".repeat(64) : "d".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static SourceRef source(UUID id, String ref, String version) {
        return new SourceRef(SourceKind.TABLE, ref, Layer.ODS, SourceRole.PRIMARY, null, null, null, 0, id, version);
    }

    private static String checksum(UUID id) {
        return id.equals(DIM_ID) ? "1".repeat(64) : id.equals(FACT_ID) ? "2".repeat(64) : "3".repeat(64);
    }

    private static void assertCode(String expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo(expected);
    }
}
