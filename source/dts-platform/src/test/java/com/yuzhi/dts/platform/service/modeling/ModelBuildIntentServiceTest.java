package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.QueuedBuildGroup;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelBuildIntentServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "builder-a";
    private static final UUID PLAN_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Mock
    private ModelSpecApplicationService modelSpecs;

    @Mock
    private ModelSpecPlanWriteAccessPort planAccess;

    @Mock
    private ModelReleaseCandidateRepository candidates;

    @Mock
    private ModelReleaseCandidateService candidateCommands;

    @Mock
    private ModelMaterializationStartService materializationStarts;

    @Mock
    private ModelMaterializationBuildRepository builds;

    private ModelBuildIntentService service;

    @BeforeEach
    void setUp() {
        service = new ModelBuildIntentService(
            modelSpecs,
            planAccess,
            candidates,
            candidateCommands,
            materializationStarts,
            builds
        );
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
    }

    @Test
    void createsAnExactSingleModelCandidateAndStartsTheCanonicalBuild() {
        ModelSpecView model = model(ModelStatus.READY_TO_PUBLISH);
        CandidateView draft = candidate(DeliveryStatus.DRAFT, CandidateOrigin.SINGLE_MODEL_INTENT);
        CandidateView building = candidate(
            DeliveryStatus.BUILDING,
            CandidateOrigin.SINGLE_MODEL_INTENT
        );
        QueuedBuildGroup group = group();
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(candidates.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of());
        when(candidateCommands.createSingleModelIntent(eq(TENANT), eq(ACTOR), any()))
            .thenReturn(new CommandResult(draft, false, List.of()));
        when(
            materializationStarts.startWithBuild(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                1,
                ModelBuildIntentService.startCommandKey("intent-key"),
                "Build current model materialization"
            )
        )
            .thenReturn(
                new ModelMaterializationStartService.StartResult(
                    new CommandResult(building, false, List.of()),
                    group
                )
            );

        ModelBuildIntentService.BuildIntentResult result = service.start(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 3, CHECKSUM),
            new ModelBuildIntentService.BuildIntentCommand(
                PLAN_ID,
                "DEV",
                "intent-key"
            )
        );

        assertThat(result.candidate()).isSameAs(building);
        assertThat(result.build()).isSameAs(group);
        assertThat(result.replayed()).isFalse();
        ArgumentCaptor<CreateCandidateCommand> create =
            ArgumentCaptor.forClass(CreateCandidateCommand.class);
        verify(candidateCommands).createSingleModelIntent(
            eq(TENANT),
            eq(ACTOR),
            create.capture()
        );
        assertThat(create.getValue().planId()).isEqualTo(PLAN_ID);
        assertThat(create.getValue().environment()).isEqualTo("DEV");
        assertThat(create.getValue().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.modelSpecId()).isEqualTo(MODEL_ID));
        assertThat(create.getValue().idempotencyKey())
            .isEqualTo(ModelBuildIntentService.createCommandKey("intent-key"));
    }

    @Test
    void exactlyReusesAnExistingSingleModelBuildWithoutAnotherTransition() {
        CandidateView building = candidate(
            DeliveryStatus.BUILDING,
            CandidateOrigin.SINGLE_MODEL_INTENT
        );
        QueuedBuildGroup group = group();
        ModelSpecView model = model(ModelStatus.READY_TO_PUBLISH);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(candidates.listForWorkbench(TENANT, PLAN_ID))
            .thenReturn(List.of(building));
        when(builds.requireQueuedBuild(building)).thenReturn(group);

        ModelBuildIntentService.BuildIntentResult result = service.start(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 3, CHECKSUM),
            new ModelBuildIntentService.BuildIntentCommand(
                PLAN_ID,
                "DEV",
                "another-request-key"
            )
        );

        assertThat(result.replayed()).isTrue();
        assertThat(result.candidate()).isSameAs(building);
        assertThat(result.build()).isSameAs(group);
        verify(candidateCommands, never()).createSingleModelIntent(any(), any(), any());
        verify(materializationStarts, never())
            .startWithBuild(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void neverMutatesOrShrinksAnActiveBatchCandidate() {
        CandidateView batch = candidate(
            DeliveryStatus.DRAFT,
            CandidateOrigin.BATCH_WORKBENCH
        );
        ModelSpecView model = model(ModelStatus.READY_TO_PUBLISH);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(candidates.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(batch));

        assertThatThrownBy(() ->
            service.start(
                TENANT,
                ACTOR,
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 3, CHECKSUM),
                new ModelBuildIntentService.BuildIntentCommand(
                    PLAN_ID,
                    "DEV",
                    "intent-key"
                )
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error -> ((ModelReleaseCandidateException) error).code())
            .isEqualTo("MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT");

        verify(candidateCommands, never()).createSingleModelIntent(any(), any(), any());
        verify(materializationStarts, never())
            .startWithBuild(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void publishedModelIsDirectedToOperationalRunInsteadOfAnotherReleaseBuild() {
        ModelSpecView model = model(ModelStatus.PUBLISHED);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);

        assertThatThrownBy(() ->
            service.start(
                TENANT,
                ACTOR,
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 3, CHECKSUM),
                new ModelBuildIntentService.BuildIntentCommand(
                    PLAN_ID,
                    "DEV",
                    "intent-key"
                )
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error -> ((ModelReleaseCandidateException) error).code())
            .isEqualTo("MODEL_OPERATIONAL_RUN_REQUIRED");

        verify(candidates, never()).listForWorkbench(any(), any());
    }

    private static ModelSpecView model(ModelStatus status) {
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        lenient().when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn(CHECKSUM);
        when(model.status()).thenReturn(status);
        return model;
    }

    private static CandidateView candidate(
        DeliveryStatus status,
        CandidateOrigin origin
    ) {
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "DEV",
            status,
            status == DeliveryStatus.DRAFT ? 1 : 2,
            "candidate-key",
            "b".repeat(64),
            new DeliveryAuditView(
                ACTOR,
                NOW.minusSeconds(60),
                null,
                null,
                null,
                null,
                null,
                null
            ),
            ACTOR,
            NOW,
            List.of(
                new EntryView(
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    TENANT,
                    CANDIDATE_ID,
                    PLAN_ID,
                    MODEL_ID,
                    3,
                    CHECKSUM,
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    status,
                    0,
                    "single-model build intent"
                )
            ),
            origin,
            status == DeliveryStatus.DRAFT ? null : "postgres-primary",
            status == DeliveryStatus.DRAFT ? null : "postgres",
            status == DeliveryStatus.DRAFT ? null : "dts",
            status == DeliveryStatus.DRAFT ? null : "dev"
        );
    }

    private static QueuedBuildGroup group() {
        return new QueuedBuildGroup(
            CANDIDATE_ID,
            2,
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            "postgres-primary",
            "dts_release_build_postgres_primary",
            "dts_rc_test",
            "c".repeat(64),
            List.of()
        );
    }
}
