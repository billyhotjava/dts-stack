package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FactShape;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
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
            .extracting(RecordComponent::getType)
            .containsExactly(Boolean.class);
        assertThat(Arrays.stream(SourceRef.class.getRecordComponents()).filter(component -> component.getName().equals("sortOrder")))
            .extracting(RecordComponent::getType)
            .containsExactly(Integer.class);
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
            type == ModelType.SUMMARY || type == ModelType.APPLICATION || type == ModelType.DIMENSION ? List.of() : sources,
            dependencies,
            List.of(),
            List.of(),
            List.of(),
            null,
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
            base.idempotencyKey()
        );
    }
}
