package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecCompilerProjectionTest {

    @Test
    void projectsOnlyCompilerOwnedFieldsFromTheCanonicalModel() {
        ModelSpecView view = view(ModelType.FACT, List.of(), List.of(source()));

        ModelingCompilerContract.CompilerModel projected = ModelSpecCompilerProjection.project(view, ignored -> null);

        assertThat(projected.id()).isEqualTo(view.id().toString());
        assertThat(projected.dimensions()).contains("customer_id");
        assertThat(projected.metrics()).contains("amount");
        assertThat(projected.sourceRefs()).extracting(ModelingCompilerContract.SourceRef::ref).containsExactly("ods.customer");
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

        ModelingCompilerContract.CompilerModel projected = ModelSpecCompilerProjection.project(derived, candidate -> candidate.equals(ref) ? upstream : null);

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

        ModelingCompilerContract.CompilerModel projected = ModelSpecCompilerProjection.project(
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

    @Test
    void projectsTheExactImplementationRevisionAndCanonicalSettings() {
        ModelSpecView view = view(ModelType.DIMENSION, List.of(), List.of());
        ImplementationView implementation = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            view.id(),
            view.planId(),
            view.revision(),
            view.checksum(),
            ImplementationMode.DESIGNER_GENERATED,
            "warehouse",
            "model.warehouse.customer_detail",
            "ACTIVE",
            4,
            "c".repeat(64),
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of("end", "2030-12-31"))),
            List.of(new FieldMapping("calendar_date", "customer_id")),
            Map.of("deduplicateBy", List.of("customer_id")),
            "table"
        );

        ModelSpecCompilerProjection.ImplementationProjection projected = ModelSpecCompilerProjection.project(view, implementation, ignored -> null);

        assertThat(projected.model().sourceRefs()).isEmpty();
        assertThat(projected.implementationRevision()).isEqualTo(4);
        assertThat(projected.inputMode()).isEqualTo(InputMode.GENERATED);
        assertThat(projected.fieldMappings()).containsExactly(new FieldMapping("calendar_date", "customer_id"));
        assertThat(projected.settings().keySet()).containsExactly("deduplicateBy");
        assertThat(projected.keyFields()).containsExactly("customer_id");
        assertThat(projected.typedFields())
            .containsExactly(
                new ModelSpecCompilerProjection.CompilerField(
                    "customer_id",
                    "varchar",
                    false
                ),
                new ModelSpecCompilerProjection.CompilerField(
                    "amount",
                    "numeric",
                    true
                )
            );
    }

    @Test
    void rejectsAnUpstreamInputWhenItsPinnedChecksumNoLongerMatches() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        ModelRevisionRef logicalRef = new ModelRevisionRef(upstreamId, 3);
        ModelSpecView derived = view(ModelType.SUMMARY, List.of(logicalRef), List.of());
        ModelSpecView upstream = new ModelSpecView(
            2, upstreamId, derived.planId(), derived.domainId(), ModelType.FACT, Layer.DWD, "customer_detail", null,
            ImplementationMode.DESIGNER_GENERATED, "table", null, null, derived.grain(), null, null,
            derived.fields(), List.of(), List.of(), List.of(), List.of(), List.of(), null, ModelStatus.DRAFT, 3,
            "a".repeat(64), Instant.EPOCH, Instant.EPOCH, CompatibilityMode.CANONICAL, null
        );
        ImplementationView implementation = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            derived.id(),
            derived.planId(),
            derived.revision(),
            derived.checksum(),
            ImplementationMode.DESIGNER_GENERATED,
            "warehouse",
            "model.warehouse.customer_summary",
            "ACTIVE",
            2,
            "c".repeat(64),
            InputMode.UPSTREAM_MODEL,
            List.of(
                new ModelLifecycleContract.UpstreamModelInput(
                    upstreamId,
                    3,
                    "b".repeat(64),
                    1,
                    "d".repeat(64),
                    "model.warehouse.customer_detail"
                )
            ),
            List.of(),
            Map.of(),
            "table"
        );

        assertThatThrownBy(() -> ModelSpecCompilerProjection.project(
            derived,
            implementation,
            candidate -> candidate.equals(logicalRef) ? upstream : null
        ))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_STALE");
    }

    @Test
    void projectsThePinnedUpstreamDbtNodeInsteadOfRecomputingItFromTheModelName() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        ModelRevisionRef logicalRef = new ModelRevisionRef(upstreamId, 3);
        ModelSpecView derived = view(ModelType.SUMMARY, List.of(logicalRef), List.of());
        ModelSpecView upstream = new ModelSpecView(
            2, upstreamId, derived.planId(), derived.domainId(), ModelType.FACT, Layer.DWD, "customer_detail", null,
            ImplementationMode.DESIGNER_GENERATED, "table", null, null, derived.grain(), null, null,
            derived.fields(), List.of(), List.of(), List.of(), List.of(), List.of(), null, ModelStatus.DRAFT, 3,
            "a".repeat(64), Instant.EPOCH, Instant.EPOCH, CompatibilityMode.CANONICAL, null
        );
        ImplementationView implementation = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            derived.id(),
            derived.planId(),
            derived.revision(),
            derived.checksum(),
            ImplementationMode.DESIGNER_GENERATED,
            "warehouse",
            "model.warehouse.customer_summary",
            "ACTIVE",
            2,
            "c".repeat(64),
            InputMode.UPSTREAM_MODEL,
            List.of(
                new ModelLifecycleContract.UpstreamModelInput(
                    upstreamId,
                    3,
                    "a".repeat(64),
                    5,
                    "d".repeat(64),
                    "model.warehouse.renamed_customer_detail"
                )
            ),
            List.of(),
            Map.of(),
            "table"
        );

        ModelSpecCompilerProjection.ImplementationProjection projected = ModelSpecCompilerProjection.project(
            derived,
            implementation,
            candidate -> candidate.equals(logicalRef) ? upstream : null
        );

        assertThat(projected.model().sourceRefs()).singleElement().satisfies(source -> {
            assertThat(source.kind()).isEqualTo("DBT_MODEL");
            assertThat(source.ref()).isEqualTo("renamed_customer_detail");
        });
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
