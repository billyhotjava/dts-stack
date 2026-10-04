package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanModelCandidateService.ConfirmCandidateCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehousePlanModelCandidateServiceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000067");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000067");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000067");
    private static final AccessContext ACTOR = new AccessContext("tenant-1", "owner-1", null);

    @Test
    void previewIsStableAndDoesNotPersistASecondCandidateLedger() {
        WarehousePlanApplicationService plans = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        when(plans.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(inventory(4, "v4"));
        WarehousePlanModelCandidateService service = new WarehousePlanModelCandidateService(plans, modelSpecs);

        var first = service.preview("tenant-1", PLAN_ID, ACTOR);
        var second = service.preview("tenant-1", PLAN_ID, ACTOR);

        assertThat(first).isEqualTo(second);
        assertThat(first.sourcesVersion()).isEqualTo(4);
        assertThat(first.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.candidateId()).hasSize(64);
            assertThat(candidate.sourceBindingId()).isEqualTo(BINDING_ID);
            assertThat(candidate.suggestedModelType()).isEqualTo(ModelSpecContract.ModelType.FACT);
        });
        verify(modelSpecs, never()).create(any(), any(), any());
    }

    @Test
    void confirmRevalidatesTheCandidateAndCallsOnlyCanonicalModelSpecCreate() {
        WarehousePlanApplicationService plans = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        when(plans.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(inventory(4, "v4"));
        when(modelSpecs.create(eq("tenant-1"), eq("owner-1"), any())).thenReturn(
            new ModelSpecApplicationService.CreateResult(mock(ModelSpecContract.ModelSpecView.class), false)
        );
        WarehousePlanModelCandidateService service = new WarehousePlanModelCandidateService(plans, modelSpecs);
        String candidateId = service.preview("tenant-1", PLAN_ID, ACTOR).candidates().getFirst().candidateId();

        service.confirm(
            "tenant-1",
            "owner-1",
            PLAN_ID,
            ACTOR,
            new ConfirmCandidateCommand(candidateId, factCommand())
        );

        ArgumentCaptor<CreateModelSpecCommand> command = ArgumentCaptor.forClass(CreateModelSpecCommand.class);
        verify(modelSpecs).create(eq("tenant-1"), eq("owner-1"), command.capture());
        assertThat(command.getValue().sourceRefs()).singleElement().satisfies(source -> {
            assertThat(source.sourceBindingId()).isEqualTo(BINDING_ID);
            assertThat(source.ref()).isEqualTo("catalog.dataset.table");
            assertThat(source.resolvedVersion()).isEqualTo("v4");
        });
    }

    @Test
    void confirmRejectsAStaleCandidateAfterSourceVersionChanges() {
        WarehousePlanApplicationService plans = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        when(plans.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(inventory(4, "v4"), inventory(5, "v5"));
        WarehousePlanModelCandidateService service = new WarehousePlanModelCandidateService(plans, modelSpecs);
        String staleCandidate = service.preview("tenant-1", PLAN_ID, ACTOR).candidates().getFirst().candidateId();

        assertThatThrownBy(() ->
            service.confirm(
                "tenant-1",
                "owner-1",
                PLAN_ID,
                ACTOR,
                new ConfirmCandidateCommand(staleCandidate, factCommand())
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_CANDIDATE_STALE");
        verify(modelSpecs, never()).create(any(), any(), any());
    }

    private static SourceInventoryView inventory(int version, String resolvedVersion) {
        SourceBindingView binding = new SourceBindingView(
            BINDING_ID,
            SourceType.CATALOG_TABLE,
            new SourceLocator(UUID.fromString("40000000-0000-0000-0000-000000000067"), null, null, null, null, null, null),
            "catalog.dataset.table",
            ConfirmationStatus.CONFIRMED,
            null,
            "验收来源表",
            resolvedVersion,
            resolvedVersion,
            SourceReferenceResolver.ResolutionStatus.AVAILABLE,
            SourceFreshness.CURRENT,
            Instant.EPOCH
        );
        return new SourceInventoryView(List.of(binding), SourceInventoryReadiness.READY, List.of(), version, "sources:" + version, Instant.EPOCH);
    }

    private static CreateModelSpecCommand factCommand() {
        return new CreateModelSpecCommand(
            PLAN_ID,
            DOMAIN_ID,
            ModelSpecContract.ModelType.FACT,
            ModelSpecContract.Layer.DWD,
            "source_fact",
            "Source-derived fact",
            ModelSpecContract.ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new ModelSpecContract.Grain("one row per event", List.of("event_id")),
            ModelSpecContract.FactShape.TRANSACTION,
            new ModelSpecContract.TimeSemantics(ModelSpecContract.TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            List.of(
                new ModelSpecContract.ModelField("event_id", "varchar", false, null, ModelSpecContract.FieldRole.KEY, "INTERNAL"),
                new ModelSpecContract.ModelField("event_time", "timestamp", false, null, ModelSpecContract.FieldRole.TIME, "INTERNAL")
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            "candidate-confirm-test"
        );
    }
}
