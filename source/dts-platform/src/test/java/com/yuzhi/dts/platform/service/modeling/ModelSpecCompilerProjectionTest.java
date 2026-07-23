package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecCompilerProjectionTest {

    @Test
    void projectsCanonicalFieldsWithoutInventingABusinessObject() {
        ModelSpecView view = view(ModelType.FACT, List.of(), List.of(source()));

        ModelingVNextContract.ModelSpec projected = ModelSpecCompilerProjection.project(view, ignored -> null);

        assertThat(projected.objectId()).isNull();
        assertThat(projected.dimensions()).contains("customer_id");
        assertThat(projected.metrics()).contains("amount");
        assertThat(projected.sourceRefs()).extracting(ModelingVNextContract.SourceRef::ref).containsExactly("ods.customer");
    }

    @Test
    void resolvesPinnedUpstreamNamesForDerivedModels() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        ModelRevisionRef ref = new ModelRevisionRef(upstreamId, 3);
        ModelSpecView derived = view(ModelType.SUMMARY, List.of(ref), List.of());
        ModelSpecView upstream = new ModelSpecView(
            2, upstreamId, derived.planId(), derived.domainId(), ModelType.FACT, Layer.DWD, "customer_detail", null,
            ImplementationMode.DESIGNER_GENERATED, "table", null, null, derived.grain(), null, null,
            derived.fields(), List.of(), List.of(), List.of(), List.of(), List.of(), null, ModelStatus.DRAFT, 3,
            "a".repeat(64), Instant.EPOCH, Instant.EPOCH, CompatibilityMode.CANONICAL, null
        );

        ModelingVNextContract.ModelSpec projected = ModelSpecCompilerProjection.project(derived, candidate -> candidate.equals(ref) ? upstream : null);

        assertThat(projected.sourceRefs()).singleElement().satisfies(source -> {
            assertThat(source.kind()).isEqualTo("DBT_MODEL");
            assertThat(source.ref()).isEqualTo("customer_detail");
        });
    }

    @Test
    void resolvesPinnedUpstreamNamesForFactWithoutADirectPhysicalSource() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        ModelRevisionRef ref = new ModelRevisionRef(upstreamId, 3);
        ModelSpecView fact = view(ModelType.FACT, List.of(ref), List.of());
        ModelSpecView upstream = new ModelSpecView(
            2, upstreamId, fact.planId(), fact.domainId(), ModelType.FACT, Layer.DWD, "staged_customer_event", null,
            ImplementationMode.DESIGNER_GENERATED, "table", null, null, fact.grain(), null, null,
            fact.fields(), List.of(), List.of(), List.of(), List.of(), List.of(), null, ModelStatus.DRAFT, 3,
            "a".repeat(64), Instant.EPOCH, Instant.EPOCH, CompatibilityMode.CANONICAL, null
        );

        ModelingVNextContract.ModelSpec projected = ModelSpecCompilerProjection.project(
            fact,
            candidate -> candidate.equals(ref) ? upstream : null
        );

        assertThat(projected.sourceRefs()).singleElement().satisfies(source -> {
            assertThat(source.kind()).isEqualTo("DBT_MODEL");
            assertThat(source.ref()).isEqualTo("staged_customer_event");
        });
    }

    @Test
    void validatesPinnedReferencesEvenWhenDirectSourcesExist() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        ModelRevisionRef ref = new ModelRevisionRef(upstreamId, 3);
        ModelSpecView view = view(
            ModelType.FACT,
            List.of(ref),
            List.of(source())
        );

        assertThatThrownBy(() -> ModelSpecCompilerProjection.project(view, ignored -> null))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_UPSTREAM_REVISION_MISSING");
    }

    private static ModelSpecView view(ModelType type, List<ModelRevisionRef> dependsOn, List<SourceRef> sources) {
        return new ModelSpecView(
            2,
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            type,
            type == ModelType.SUMMARY ? Layer.DWS : Layer.DWD,
            type == ModelType.SUMMARY ? "customer_summary" : "customer_detail",
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(
                new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null),
                new ModelField("amount", "numeric", true, null, FieldRole.MEASURE, null)
            ),
            sources,
            dependsOn,
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            1,
            "b".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static SourceRef source() {
        return new SourceRef(
            SourceKind.TABLE,
            "ods.customer",
            Layer.ODS,
            SourceRole.PRIMARY,
            null,
            null,
            null,
            0,
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            "v1"
        );
    }
}
