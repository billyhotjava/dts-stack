package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FactShape;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.TimeSemantics;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.TimeSemanticsType;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ModelSpecContractTest {

    @Test
    void createContractContainsOnlyCanonicalClientInputs() {
        Set<String> fields = Arrays.stream(CreateModelSpecCommand.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrderElementsOf(ModelSpecContract.CREATE_FIELDS);
        assertThat(fields).doesNotContain(
            "objectId",
            "processId",
            "legacyRef",
            "legacyRefs",
            "revision",
            "checksum",
            "id",
            "status"
        );
        assertThat(Arrays.stream(SourceRef.class.getRecordComponents()).map(RecordComponent::getName))
            .doesNotContain("legacyRef", "legacyRawRole");
    }

    @Test
    void canonicalDimensionCreationRequiresAPinnedDimensionDefinitionReference() {
        assertThat(ModelSpecContract.CREATE_FIELDS).contains("dimensionDefinitionRef");
        assertThat(ModelSpecContract.UPDATE_FIELDS).doesNotContain("dimensionDefinitionRef");

        CreateModelSpecCommand existingDimension = validCommand(ModelType.DIMENSION, null);
        CreateModelSpecCommand missingReference = new CreateModelSpecCommand(
            existingDimension.planId(),
            existingDimension.domainId(),
            existingDimension.modelType(),
            existingDimension.layer(),
            existingDimension.name(),
            existingDimension.description(),
            existingDimension.implementationMode(),
            existingDimension.materialization(),
            existingDimension.businessActivityRef(),
            existingDimension.consumptionScenario(),
            existingDimension.grain(),
            existingDimension.factShape(),
            existingDimension.timeSemantics(),
            existingDimension.fields(),
            existingDimension.sourceRefs(),
            existingDimension.dependsOn(),
            existingDimension.dimensionRefs(),
            existingDimension.metricRefs(),
            existingDimension.standardBindings(),
            existingDimension.generationStrategy(),
            existingDimension.dimensionProfile(),
            null,
            existingDimension.idempotencyKey()
        );

        assertThat(ModelSpecContract.validateCreate(missingReference))
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .contains(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED", "dimensionDefinitionRef"));
    }

    @Test
    void dimensionUpdatesDoNotRevalidateTheImmutableCreationPin() {
        CreateModelSpecCommand dimension = validCommand(ModelType.DIMENSION, null);
        UpdateModelSpecCommand update = new UpdateModelSpecCommand(
            dimension.planId(), dimension.domainId(), dimension.modelType(), dimension.layer(), "updated_dimension", dimension.description(),
            dimension.implementationMode(), dimension.materialization(), dimension.businessActivityRef(), dimension.consumptionScenario(),
            dimension.grain(), dimension.factShape(), dimension.timeSemantics(), dimension.fields(), dimension.sourceRefs(),
            dimension.dependsOn(), dimension.dimensionRefs(), dimension.metricRefs(), dimension.standardBindings(),
            dimension.generationStrategy(), dimension.dimensionProfile()
        );

        assertThat(ModelSpecContract.validateUpdate(update)).isEmpty();
    }

    @Test
    void definitionValidationDefersOnlyTheMissingDerivedModelUpstream() {
        for (ModelType type : List.of(ModelType.SUMMARY, ModelType.APPLICATION)) {
            CreateModelSpecCommand model = validCommand(type, null);
            UpdateModelSpecCommand update = new UpdateModelSpecCommand(
                model.planId(), model.domainId(), model.modelType(), model.layer(), model.name(), model.description(),
                model.implementationMode(), model.materialization(), model.businessActivityRef(), model.consumptionScenario(),
                model.grain(), model.factShape(), model.timeSemantics(), model.fields(), model.sourceRefs(), List.of(),
                model.dimensionRefs(), model.metricRefs(), model.standardBindings(), model.generationStrategy(), model.dimensionProfile()
            );

            assertThat(ModelSpecContract.validateUpdate(update)).extracting(ModelSpecContract.FieldIssue::code)
                .contains("MODEL_SPEC_UPSTREAM_REQUIRED");
            assertThat(ModelSpecContract.validateDefinitionUpdate(update)).isEmpty();
        }

        CreateModelSpecCommand summary = validCommand(ModelType.SUMMARY, null);
        UpdateModelSpecCommand update = new UpdateModelSpecCommand(
            summary.planId(), summary.domainId(), summary.modelType(), summary.layer(), summary.name(), summary.description(),
            summary.implementationMode(), summary.materialization(), summary.businessActivityRef(), summary.consumptionScenario(),
            summary.grain(), summary.factShape(), summary.timeSemantics(), summary.fields(), summary.sourceRefs(), List.of(),
            summary.dimensionRefs(), summary.metricRefs(), summary.standardBindings(), summary.generationStrategy(), summary.dimensionProfile()
        );

        UpdateModelSpecCommand invalidFields = new UpdateModelSpecCommand(
            update.planId(), update.domainId(), update.modelType(), update.layer(), update.name(), update.description(),
            update.implementationMode(), update.materialization(), update.businessActivityRef(), update.consumptionScenario(),
            update.grain(), update.factShape(), update.timeSemantics(), List.of(update.fields().getFirst(), update.fields().getFirst()), update.sourceRefs(), update.dependsOn(),
            update.dimensionRefs(), update.metricRefs(), update.standardBindings(), update.generationStrategy(), update.dimensionProfile()
        );
        assertThat(ModelSpecContract.validateDefinitionUpdate(invalidFields)).extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_FIELD_INVALID");
    }

    @Test
    void validateViewRequiresTheCreationPinForDimensions() {
        ModelSpecView pinned = view(validCommand(ModelType.DIMENSION, null));

        assertThat(ModelSpecContract.validateView(withDimensionDefinitionRef(pinned, null)))
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .contains(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED", "dimensionDefinitionRef"));
    }

    @Test
    void validateViewRejectsInvalidDimensionPins() {
        ModelSpecView pinned = view(validCommand(ModelType.DIMENSION, null));

        assertThat(
            ModelSpecContract.validateView(
                withDimensionDefinitionRef(pinned, new ModelSpecContract.DimensionDefinitionRef(null, 1))
            )
        )
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .contains(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_DIMENSION_DEFINITION_INVALID", "dimensionDefinitionRef"));
        assertThat(
            ModelSpecContract.validateView(
                withDimensionDefinitionRef(
                    pinned,
                    new ModelSpecContract.DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 0)
                )
            )
        )
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .contains(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_DIMENSION_DEFINITION_INVALID", "dimensionDefinitionRef"));
    }

    @Test
    void validateViewRejectsDimensionPinsForEveryNonDimensionType() {
        ModelSpecContract.DimensionDefinitionRef ref = new ModelSpecContract.DimensionDefinitionRef(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            1
        );

        for (ModelType type : List.of(ModelType.FACT, ModelType.SUMMARY, ModelType.APPLICATION)) {
            assertThat(ModelSpecContract.validateView(withDimensionDefinitionRef(view(validCommand(type, null)), ref)))
                .as(type.name())
                .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
                .contains(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_DIMENSION_DEFINITION_NOT_ALLOWED", "dimensionDefinitionRef"));
        }
    }

    @Test
    void archivedModelsCannotBecomeNewCanonicalReferenceTargets() {
        ModelSpecView active = view(validCommand(ModelType.FACT, null));
        ModelSpecView archived = withStatus(active, ModelSpecContract.ModelStatus.ARCHIVED);

        assertThat(ModelSpecContract.isCanonicalReferenceTarget(active)).isTrue();
        assertThat(ModelSpecContract.isCanonicalReferenceTarget(archived)).isFalse();
    }

    @Test
    void viewOwnsServerVersionAndCompatibilityMetadata() {
        Set<String> fields = Arrays.stream(ModelSpecView.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

        assertThat(ModelSpecContract.CONTRACT_VERSION).isEqualTo(2);
        assertThat(fields)
            .contains("contractVersion", "id", "status", "revision", "checksum", "createdAt", "updatedAt", "compatibilityMode")
            .doesNotContain("objectId", "processId", "idempotencyKey");
    }

    @Test
    void rejectsRetiredAndUnknownCreateFields() {
        assertThat(ModelSpecContract.validateCreateFieldNames(Set.of("planId", "objectId", "futureGuess")))
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_FIELD_NOT_ALLOWED", "objectId"),
                org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_FIELD_NOT_ALLOWED", "futureGuess")
            );
    }

    @Test
    void onlyFactAcceptsOptionalBusinessActivity() {
        for (ModelType type : ModelType.values()) {
            CreateModelSpecCommand command = validCommand(type, "activity-ref");
            assertThat(ModelSpecContract.validateCreate(command).stream().map(ModelSpecContract.FieldIssue::code))
                .as(type.name())
                .containsExactlyInAnyOrderElementsOf(
                    type == ModelType.FACT ? List.of() : List.of("MODEL_SPEC_BUSINESS_ACTIVITY_NOT_ALLOWED")
                );
        }

        assertThat(ModelSpecContract.validateCreate(validCommand(ModelType.FACT, null))).isEmpty();
    }

    @Test
    void allFourTypesExposeDeterministicSaveBoundaries() {
        assertThat(ModelSpecContract.validateCreate(validCommand(ModelType.DIMENSION, null))).isEmpty();
        assertThat(ModelSpecContract.validateCreate(validCommand(ModelType.FACT, null))).isEmpty();
        assertThat(ModelSpecContract.validateCreate(validCommand(ModelType.SUMMARY, null))).isEmpty();
        assertThat(ModelSpecContract.validateCreate(validCommand(ModelType.APPLICATION, null))).isEmpty();
    }

    @Test
    void fourTableModelsRejectTargetLayersOwnedByIngestionOrAnotherModelType() {
        for (ModelType type : ModelType.values()) {
            CreateModelSpecCommand valid = validCommand(type, null);
            for (Layer layer : Layer.values()) {
                CreateModelSpecCommand candidate = copyLayer(valid, layer);
                if (layer == valid.layer()) {
                    assertThat(ModelSpecContract.validateCreate(candidate)).as(type + " -> " + layer).isEmpty();
                } else {
                    assertThat(ModelSpecContract.validateCreate(candidate))
                        .as(type + " -> " + layer)
                        .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
                        .contains(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_TYPE_LAYER_MISMATCH", "layer"));
                }
            }
        }
    }

    @Test
    void upstreamModelMatrixOnlyAcceptsCanonicalTypeLayerPairs() {
        for (ModelType ownerType : ModelType.values()) {
            for (ModelType upstreamType : ModelType.values()) {
                for (Layer upstreamLayer : Layer.values()) {
                    boolean expected = ModelSpecContract.matchesTargetLayer(upstreamType, upstreamLayer) &&
                        switch (ownerType) {
                            case SOURCE, DIMENSION -> false;
                            case FACT -> upstreamType == ModelType.SOURCE || upstreamType == ModelType.FACT;
                            case SUMMARY -> upstreamType != ModelType.SOURCE && upstreamType != ModelType.APPLICATION;
                            case APPLICATION -> upstreamType != ModelType.SOURCE;
                        };

                    assertThat(ModelSpecContract.allowsUpstreamModel(ownerType, upstreamType, upstreamLayer))
                        .as("%s <- %s@%s", ownerType, upstreamType, upstreamLayer)
                        .isEqualTo(expected);
                }
            }
        }
    }

    @Test
    void inputKindsAndPhysicalUpstreamLayersFollowTheFourTablePolicy() {
        CreateModelSpecCommand factFromAds = copyInputs(
            validCommand(ModelType.FACT, null),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ads.customer_dashboard",
                    Layer.ADS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    UUID.fromString("50000000-0000-0000-0000-000000000002"),
                    "v1"
                )
            ),
            List.of(),
            null
        );
        assertThat(ModelSpecContract.validateCreate(factFromAds))
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED");

        CreateModelSpecCommand summaryWithPhysicalSource = copyInputs(
            validCommand(ModelType.SUMMARY, null),
            validCommand(ModelType.FACT, null).sourceRefs(),
            validCommand(ModelType.SUMMARY, null).dependsOn(),
            null
        );
        assertThat(ModelSpecContract.validateCreate(summaryWithPhysicalSource))
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED");

        CreateModelSpecCommand dimensionWithModelDependency = copyInputs(
            validCommand(ModelType.DIMENSION, null),
            List.of(),
            List.of(new ModelSpecContract.ModelRevisionRef(UUID.fromString("30000000-0000-0000-0000-000000000001"), 1)),
            null
        );
        assertThat(ModelSpecContract.validateCreate(dimensionWithModelDependency))
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED");

        for (ModelType nonFactType : List.of(ModelType.DIMENSION, ModelType.SUMMARY, ModelType.APPLICATION)) {
            CreateModelSpecCommand nonFactWithFactOnlyInputs = copyFactOnlyInputs(
                validCommand(nonFactType, null),
                FactShape.TRANSACTION,
                new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
                List.of(new ModelSpecContract.ModelRevisionRef(UUID.fromString("30000000-0000-0000-0000-000000000002"), 1))
            );

            assertThat(ModelSpecContract.validateCreate(nonFactWithFactOnlyInputs))
                .as(nonFactType + " with FACT-only inputs")
                .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
                .contains(
                    org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED", "dimensionRefs"),
                    org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED", "factShape"),
                    org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED", "timeSemantics")
                );
        }
    }

    @Test
    void factDraftCanDescribeItsGrainBeforePhysicalSourceMapping() {
        CreateModelSpecCommand base = validCommand(ModelType.FACT, null);
        CreateModelSpecCommand draft = new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            List.of(),
            List.of(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            base.idempotencyKey()
        );

        assertThat(ModelSpecContract.validateCreate(draft)).isEmpty();
    }

    @Test
    void dimensionDraftRequiresGrainAndKeyButNotSourceOrGenerationStrategy() {
        CreateModelSpecCommand dimension = validCommand(ModelType.DIMENSION, null);

        assertThat(dimension.sourceRefs()).isEmpty();
        assertThat(dimension.generationStrategy()).isNull();
        assertThat(ModelSpecContract.validateCreate(dimension)).isEmpty();

        CreateModelSpecCommand generatedWithoutKey = copyDimension(
            dimension,
            dimension.grain(),
            List.of(new ModelField("label", "varchar", true, null, FieldRole.ATTRIBUTE, null)),
            new ModelSpecContract.GenerationStrategy("SEQUENCE", null)
        );
        assertThat(ModelSpecContract.validateCreate(generatedWithoutKey))
            .extracting(ModelSpecContract.FieldIssue::code)
            .containsExactly("MODEL_SPEC_DIMENSION_KEY_REQUIRED");

        CreateModelSpecCommand withoutGrain = copyDimension(dimension, null, dimension.fields(), null);
        assertThat(ModelSpecContract.validateCreate(withoutGrain))
            .extracting(ModelSpecContract.FieldIssue::code)
            .containsExactly("MODEL_SPEC_GRAIN_REQUIRED");
    }

    @Test
    void legacyV2DimensionDefinitionAndKeyMappingRemainCompatible() {
        CreateModelSpecCommand dimensionWithoutDefinition = copyDescription(validCommand(ModelType.DIMENSION, null), "   ");
        CreateModelSpecCommand duplicateGrainKeys = copyDimension(
            dimensionWithoutDefinition,
            new ModelSpecContract.Grain("one row per record", List.of("record_id", "record_id")),
            dimensionWithoutDefinition.fields(),
            null
        );
        CreateModelSpecCommand mismatchedKeyMapping = copyDimension(
            dimensionWithoutDefinition,
            new ModelSpecContract.Grain("one row per record", List.of("legacy_record_id")),
            dimensionWithoutDefinition.fields(),
            null
        );

        assertThat(ModelSpecContract.validateCreate(dimensionWithoutDefinition)).isEmpty();
        assertThat(ModelSpecContract.validateCreate(duplicateGrainKeys)).isEmpty();
        assertThat(ModelSpecContract.validateCreate(mismatchedKeyMapping)).isEmpty();
    }

    @Test
    void jacksonRequiredScalarsUseBoxedTypesSoMissingValuesCannotBecomeFalseOrZero() {
        assertThat(Arrays.stream(ModelField.class.getRecordComponents()).filter(component -> component.getName().equals("nullable")))
            .map(component -> component.getType().getName())
            .containsExactly(Boolean.class.getName());
        assertThat(Arrays.stream(SourceRef.class.getRecordComponents()).filter(component -> component.getName().equals("sortOrder")))
            .map(component -> component.getType().getName())
            .containsExactly(Integer.class.getName());
    }

    @Test
    void immutableCopiesStillReportNullNestedItemsAsFieldIssues() {
        CreateModelSpecCommand base = validCommand(ModelType.FACT, null);
        CreateModelSpecCommand command = new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            Arrays.asList(base.fields().getFirst(), null),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            base.idempotencyKey()
        );

        assertThat(ModelSpecContract.validateCreate(command))
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_FIELD_INVALID");
    }

    @Test
    void nestedReferencesRejectIncompleteIdsAndNonPositiveVersions() {
        CreateModelSpecCommand base = validCommand(ModelType.SUMMARY, null);
        CreateModelSpecCommand command = new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "catalog.dataset.source",
                    Layer.ODS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    -1,
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "v1"
                )
            ),
            List.of(new ModelSpecContract.ModelRevisionRef(null, 0)),
            List.of(new ModelSpecContract.ModelRevisionRef(null, -1)),
            List.of(new ModelSpecContract.MetricRef(null, 0)),
            List.of(new ModelSpecContract.StandardBinding("record_id", null, 1, null, -1, null, 0, null)),
            base.generationStrategy(),
            base.idempotencyKey()
        );

        assertThat(ModelSpecContract.validateCreate(command))
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains(
                "MODEL_SPEC_SOURCE_INVALID",
                "MODEL_SPEC_DEPENDENCY_INVALID",
                "MODEL_SPEC_DIMENSION_REF_INVALID",
                "MODEL_SPEC_METRIC_REF_INVALID",
                "MODEL_SPEC_STANDARD_BINDING_INVALID"
            );
    }

    @Test
    void rejectsStandardReferencesToMissingFields() {
        CreateModelSpecCommand base = validCommand(ModelType.FACT, null);
        CreateModelSpecCommand command = new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            List.of(new ModelSpecContract.StandardBinding("missing_standard_field", null, null, null, null, null, null, "INTERNAL")),
            base.generationStrategy(),
            base.idempotencyKey()
        );

        assertThat(ModelSpecContract.validateCreate(command))
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_STANDARD_BINDING_INVALID");
    }

    @Test
    void rejectsMultipleStandardBindingsForTheSameField() {
        CreateModelSpecCommand base = validCommand(ModelType.FACT, null);
        ModelSpecContract.StandardBinding binding = new ModelSpecContract.StandardBinding(
            "record_id",
            null,
            null,
            null,
            null,
            null,
            null,
            "INTERNAL"
        );
        CreateModelSpecCommand command = new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            List.of(binding, binding),
            base.generationStrategy(),
            base.idempotencyKey()
        );

        assertThat(ModelSpecContract.validateCreate(command))
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_STANDARD_BINDING_INVALID");
    }

    private static CreateModelSpecCommand validCommand(ModelType type, String activity) {
        List<ModelField> fields = List.of(
            new ModelField("record_id", "varchar", false, "source.record_id", FieldRole.KEY, null),
            new ModelField("event_time", "timestamp", false, "source.event_time", FieldRole.TIME, null)
        );
        List<SourceRef> sources = List.of(
            new SourceRef(
                SourceKind.TABLE,
                "catalog.dataset.source",
                Layer.ODS,
                SourceRole.PRIMARY,
                null,
                null,
                null,
                0,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                "v1"
            )
        );
        List<ModelSpecContract.ModelRevisionRef> dependencies = type == ModelType.SUMMARY || type == ModelType.APPLICATION
            ? List.of(new ModelSpecContract.ModelRevisionRef(UUID.fromString("30000000-0000-0000-0000-000000000001"), 1))
            : List.of();
        return new CreateModelSpecCommand(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            type,
            switch (type) {
                case SOURCE -> Layer.ODS;
                case DIMENSION, FACT -> Layer.DWD;
                case SUMMARY -> Layer.DWS;
                case APPLICATION -> Layer.ADS;
            },
            "generic_" + type.name().toLowerCase(),
            "Generic cross-industry model",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            activity,
            type == ModelType.APPLICATION ? "operational reporting" : null,
            new ModelSpecContract.Grain("one row per record", List.of("record_id")),
            type == ModelType.FACT ? FactShape.TRANSACTION : null,
            type == ModelType.FACT ? new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")) : null,
            fields,
            type == ModelType.FACT ? sources : List.of(),
            dependencies,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            type == ModelType.DIMENSION
                ? new ModelSpecContract.DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 1)
                : null,
            "model-spec-contract-test-" + type.name().toLowerCase()
        );
    }

    private static CreateModelSpecCommand copyDimension(
        CreateModelSpecCommand base,
        ModelSpecContract.Grain grain,
        List<ModelField> fields,
        ModelSpecContract.GenerationStrategy generationStrategy
    ) {
        return new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            grain,
            base.factShape(),
            base.timeSemantics(),
            fields,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            generationStrategy,
            null,
            base.dimensionDefinitionRef(),
            base.idempotencyKey()
        );
    }

    private static CreateModelSpecCommand copyLayer(CreateModelSpecCommand base, Layer layer) {
        return new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            layer,
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            null,
            base.dimensionDefinitionRef(),
            base.idempotencyKey()
        );
    }

    private static CreateModelSpecCommand copyInputs(
        CreateModelSpecCommand base,
        List<SourceRef> sources,
        List<ModelSpecContract.ModelRevisionRef> dependencies,
        ModelSpecContract.GenerationStrategy generationStrategy
    ) {
        return new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            sources,
            dependencies,
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            generationStrategy,
            null,
            base.dimensionDefinitionRef(),
            base.idempotencyKey()
        );
    }

    private static CreateModelSpecCommand copyFactOnlyInputs(
        CreateModelSpecCommand base,
        FactShape factShape,
        TimeSemantics timeSemantics,
        List<ModelSpecContract.ModelRevisionRef> dimensionRefs
    ) {
        return new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            factShape,
            timeSemantics,
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            dimensionRefs,
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            null,
            base.dimensionDefinitionRef(),
            base.idempotencyKey()
        );
    }

    private static CreateModelSpecCommand copyDescription(CreateModelSpecCommand base, String description) {
        return new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            description,
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            null,
            base.dimensionDefinitionRef(),
            base.idempotencyKey()
        );
    }

    private static ModelSpecView view(CreateModelSpecCommand command) {
        return new ModelSpecSnapshotCodec(new ObjectMapper().findAndRegisterModules())
            .toCreatedView(UUID.randomUUID(), command, Instant.EPOCH);
    }

    private static ModelSpecView withDimensionDefinitionRef(
        ModelSpecView base,
        ModelSpecContract.DimensionDefinitionRef dimensionDefinitionRef
    ) {
        return new ModelSpecView(
            base.contractVersion(),
            base.id(),
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            base.dimensionProfile(),
            dimensionDefinitionRef,
            base.status(),
            base.revision(),
            base.checksum(),
            base.createdAt(),
            base.updatedAt(),
            base.compatibilityMode(),
            base.legacyRefs()
        );
    }

    private static ModelSpecView withStatus(ModelSpecView base, ModelSpecContract.ModelStatus status) {
        return new ModelSpecView(
            base.contractVersion(),
            base.id(),
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            base.dimensionProfile(),
            base.dimensionDefinitionRef(),
            status,
            base.revision(),
            base.checksum(),
            base.createdAt(),
            base.updatedAt(),
            base.compatibilityMode(),
            base.legacyRefs(),
            base.dataMartId(),
            base.variantCode(),
            base.implementationPolicy(),
            base.warehouseLayerCode()
        );
    }
}
