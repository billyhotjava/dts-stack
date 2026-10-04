package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecBusinessContextContractTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROCESS_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MART_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Test
    void fullFactRequiresAStableBusinessProcessWhileInteractiveDraftDefersCompleteness() {
        CreateModelSpecCommand command = fact(null);

        assertThat(codes(ModelSpecContract.validateDeliverableCreate(command))).contains("MODEL_SPEC_BUSINESS_PROCESS_REQUIRED");
        assertThat(codes(ModelSpecContract.validateInteractiveCreate(command)))
            .doesNotContain("MODEL_SPEC_BUSINESS_PROCESS_REQUIRED");
    }

    @Test
    void applicationRequiresBothDataMartAndSubjectDomain() {
        CreateModelSpecCommand command = application(null, null);

        assertThat(codes(ModelSpecContract.validateDeliverableCreate(command)))
            .contains("MODEL_SPEC_DATA_MART_REQUIRED", "MODEL_SPEC_SUBJECT_DOMAIN_REQUIRED");
    }

    @Test
    void stableContextIsRejectedOnTheWrongModelType() {
        CreateModelSpecCommand applicationWithProcess = withContext(application(MART_ID, SUBJECT_ID), PROCESS_ID, MART_ID, SUBJECT_ID);
        CreateModelSpecCommand factWithSubject = withContext(fact(PROCESS_ID), PROCESS_ID, MART_ID, SUBJECT_ID);

        assertThat(codes(ModelSpecContract.validateCreate(applicationWithProcess)))
            .contains("MODEL_SPEC_BUSINESS_PROCESS_NOT_ALLOWED");
        assertThat(codes(ModelSpecContract.validateCreate(factWithSubject)))
            .contains("MODEL_SPEC_SUBJECT_DOMAIN_NOT_ALLOWED");
    }

    @Test
    void editableDraftDefersMissingStableContextButRejectsSuppliedWrongTypeContext() {
        CreateModelSpecCommand incompleteFact = fact(null);
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        ModelSpecView editableFact = codec.toCreatedView(
            UUID.fromString("60000000-0000-0000-0000-000000000002"),
            incompleteFact,
            Instant.EPOCH
        );

        assertThat(codes(ModelSpecContract.validateUpdate(update(incompleteFact))))
            .doesNotContain("MODEL_SPEC_BUSINESS_PROCESS_REQUIRED");
        assertThat(codes(ModelSpecContract.validateEditableView(editableFact)))
            .doesNotContain("MODEL_SPEC_BUSINESS_PROCESS_REQUIRED");
        assertThat(codes(ModelSpecContract.validateDeliverableCreate(incompleteFact)))
            .contains("MODEL_SPEC_BUSINESS_PROCESS_REQUIRED");
        assertThat(codes(ModelSpecContract.validateView(editableFact)))
            .contains("MODEL_SPEC_BUSINESS_PROCESS_REQUIRED");

        UpdateModelSpecCommand wrongTypeContext = update(withContext(incompleteFact, PROCESS_ID, MART_ID, SUBJECT_ID));
        assertThat(codes(ModelSpecContract.validateUpdate(wrongTypeContext)))
            .contains("MODEL_SPEC_SUBJECT_DOMAIN_NOT_ALLOWED");
    }

    @Test
    void snapshotRoundTripPinsStableContextAndLegacyUpdatePayloadCannotDropIt() {
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        CreateModelSpecCommand command = fact(PROCESS_ID);
        ModelSpecView current = codec.toCreatedView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            command,
            Instant.EPOCH
        );
        ModelSpecView decoded = codec.readView(codec.write(current));
        UpdateModelSpecCommand legacyUpdate = new UpdateModelSpecCommand(
            current.planId(),
            current.domainId(),
            current.modelType(),
            current.layer(),
            current.name(),
            "updated",
            current.implementationMode(),
            current.materialization(),
            current.businessActivityRef(),
            current.consumptionScenario(),
            current.grain(),
            current.factShape(),
            current.timeSemantics(),
            current.fields(),
            current.sourceRefs(),
            current.dependsOn(),
            current.dimensionRefs(),
            current.metricRefs(),
            current.standardBindings(),
            current.generationStrategy(),
            current.dimensionProfile()
        );
        ModelSpecView replacement = codec.toUpdatedView(current, legacyUpdate, 2, Instant.EPOCH.plusSeconds(1));

        assertThat(decoded.businessProcessId()).isEqualTo(PROCESS_ID);
        assertThat(decoded.subjectDomainId()).isNull();
        assertThat(replacement.businessProcessId()).isEqualTo(PROCESS_ID);
        assertThat(replacement.subjectDomainId()).isNull();
        assertThat(codec.matchesStoredContentChecksum(codec.write(decoded), decoded, decoded.checksum())).isTrue();
    }

    private static CreateModelSpecCommand fact(UUID processId) {
        return new CreateModelSpecCommand(
            PLAN_ID,
            DOMAIN_ID,
            ModelType.FACT,
            Layer.DWD,
            "fact_order",
            "Order fact",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "legacy-order-process",
            null,
            new Grain("one row per order", List.of("order_id")),
            null,
            null,
            List.of(new ModelField("order_id", "订单ID", "varchar", false, null, FieldRole.KEY, null, null, null, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            "fact-order",
            null,
            null,
            null,
            "DWD",
            processId,
            null
        );
    }

    private static CreateModelSpecCommand application(UUID martId, UUID subjectId) {
        return new CreateModelSpecCommand(
            PLAN_ID,
            DOMAIN_ID,
            ModelType.APPLICATION,
            Layer.ADS,
            "app_order_dashboard",
            "Order dashboard",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            "operations dashboard",
            new Grain("one row per day", List.of("day_id")),
            null,
            null,
            List.of(new ModelField("day_id", "日期ID", "varchar", false, null, FieldRole.KEY, null, null, null, null)),
            List.of(),
            List.of(new ModelRevisionRef(UUID.fromString("70000000-0000-0000-0000-000000000001"), 1)),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            "app-order-dashboard",
            martId,
            null,
            null,
            "ADS",
            null,
            subjectId
        );
    }

    private static CreateModelSpecCommand withContext(
        CreateModelSpecCommand command,
        UUID processId,
        UUID martId,
        UUID subjectId
    ) {
        return new CreateModelSpecCommand(
            command.planId(), command.domainId(), command.modelType(), command.layer(), command.name(),
            command.description(), command.implementationMode(), command.materialization(), command.businessActivityRef(),
            command.consumptionScenario(), command.grain(), command.factShape(), command.timeSemantics(), command.fields(),
            command.sourceRefs(), command.dependsOn(), command.dimensionRefs(), command.metricRefs(), command.standardBindings(),
            command.generationStrategy(), command.dimensionProfile(), command.dimensionDefinitionRef(), command.idempotencyKey(),
            martId, command.variantCode(), command.implementationPolicy(), command.warehouseLayerCode(), processId, subjectId
        );
    }

    private static UpdateModelSpecCommand update(CreateModelSpecCommand command) {
        return new UpdateModelSpecCommand(
            command.planId(), command.domainId(), command.modelType(), command.layer(), command.name(),
            command.description(), command.implementationMode(), command.materialization(), command.businessActivityRef(),
            command.consumptionScenario(), command.grain(), command.factShape(), command.timeSemantics(), command.fields(),
            command.sourceRefs(), command.dependsOn(), command.dimensionRefs(), command.metricRefs(), command.standardBindings(),
            command.generationStrategy(), command.dimensionProfile(), command.dataMartId(), command.variantCode(),
            command.implementationPolicy(), command.warehouseLayerCode(), command.businessProcessId(), command.subjectDomainId()
        );
    }

    private static List<String> codes(List<FieldIssue> issues) {
        return issues.stream().map(FieldIssue::code).toList();
    }
}
