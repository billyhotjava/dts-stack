package com.yuzhi.dts.platform.service.modeling.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelAuthoringModelValidator;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.IssueSeverity;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringProjection;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.ActiveView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CreateAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.DraftIntent;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.SaveAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringOrigin;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringSeed;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidationView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftService;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProjectionCoverage;
import com.yuzhi.dts.platform.service.modeling.representation.ModelVisualizationCapabilityEvaluator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelAuthoringDraftServiceTest {

    private static final String TENANT = "default";
    private static final String ACTOR = "xiezm";
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000092");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000092");
    private static final UUID DRAFT_ID = UUID.fromString("40000000-0000-0000-0000-000000000092");
    private static final String CHECKSUM = "a".repeat(64);

    private final ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
    private final ModelLifecycleService lifecycle = mock(ModelLifecycleService.class);
    private final DbtImplementationDraftService drafts = mock(DbtImplementationDraftService.class);
    private final ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
    private final ModelAuthoringProjectionService projections = mock(ModelAuthoringProjectionService.class);
    private final ModelVisualizationCapabilityEvaluator capabilities = new ModelVisualizationCapabilityEvaluator();
    private final ModelAuthoringModelValidator modelValidator = mock(ModelAuthoringModelValidator.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ModelAuthoringDraftService service = new ModelAuthoringDraftService(
        modelSpecs,
        lifecycle,
        drafts,
        writeAccess,
        projections,
        capabilities,
        modelValidator,
        objectMapper
    );

    @Test
    void explicitlyForksPublishedBeforeCreatingOneSourceNeutralDraft() {
        ModelSpecView published = model(ModelStatus.PUBLISHED, 1, CHECKSUM, ImplementationMode.DESIGNER_GENERATED);
        ModelSpecView forked = model(ModelStatus.DRAFT, 2, "b".repeat(64), ImplementationMode.DESIGNER_GENERATED);
        SourceBundleView source = new SourceBundleView(
            "project",
            "c".repeat(64),
            "d".repeat(64),
            SourceBundleKind.CANONICAL_INITIALIZATION,
            true,
            List.of()
        );
        DraftView created = new DraftView(
            DRAFT_ID,
            PLAN_ID,
            MODEL_ID,
            2,
            "b".repeat(64),
            null,
            null,
            DraftState.DRAFT,
            "etag-1",
            Instant.parse("2026-08-20T00:00:00Z"),
            source
        );
        AuthoringProjection projection = new AuthoringProjection(
            ProjectionCoverage.FULL,
            true,
            List.of(),
            List.of(),
            List.of()
        );
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(published);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(null, List.of(), List.of()));
        when(drafts.findAuthoringByIdempotency(eq(TENANT), eq(ACTOR), eq(MODEL_ID), eq(PLAN_ID), eq("fork-92"), any()))
            .thenReturn(Optional.empty());
        when(modelSpecs.forkPublishedForAuthoring(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any())).thenReturn(forked);
        when(drafts.createAuthoring(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), any())).thenReturn(created);
        when(projections.project(source)).thenReturn(projection);

        var result = service.create(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateAuthoringDraftRequest(
                DraftIntent.FORK_PUBLISHED,
                1,
                CHECKSUM,
                null,
                null,
                "dim_orders",
                "fork-92"
            )
        );

        assertThat(result.model()).isSameAs(forked);
        assertThat(result.draft().draftId()).isEqualTo(DRAFT_ID);
        var order = inOrder(modelSpecs, drafts);
        order.verify(modelSpecs).forkPublishedForAuthoring(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any());
        ArgumentCaptor<CreateDraftRequest> create = ArgumentCaptor.forClass(CreateDraftRequest.class);
        ArgumentCaptor<AuthoringSeed> seed = ArgumentCaptor.forClass(AuthoringSeed.class);
        order.verify(drafts).createAuthoring(eq(TENANT), eq(ACTOR), eq(MODEL_ID), create.capture(), seed.capture());
        assertThat(create.getValue().baseModelRevision()).isEqualTo(2);
        assertThat(seed.getValue().origin()).isEqualTo(AuthoringOrigin.SYSTEM_GENERATED);
        assertThat(seed.getValue().requestHash()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void requiresTheExplicitForkIntentForPublishedModels() {
        ModelSpecView published = model(ModelStatus.PUBLISHED, 1, CHECKSUM, ImplementationMode.DBT_MANAGED);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(published);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(null, List.of(), List.of()));
        when(drafts.findAuthoringByIdempotency(eq(TENANT), eq(ACTOR), eq(MODEL_ID), eq(PLAN_ID), eq("edit-92"), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () ->
                service.create(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new CreateAuthoringDraftRequest(
                        DraftIntent.EDIT_DRAFT,
                        1,
                        CHECKSUM,
                        null,
                        null,
                        null,
                        "edit-92"
                    )
                )
        )
            .isInstanceOf(ModelAuthoringException.class)
            .extracting(error -> ((ModelAuthoringException) error).code())
            .isEqualTo("MODEL_AUTHORING_PUBLISHED_FORK_REQUIRED");

        verify(modelSpecs, org.mockito.Mockito.never()).forkPublishedForAuthoring(any(), any(), any(), any());
    }

    @Test
    void saveReturnsTheCanonicalFilesPersistedByTheServer() {
        ModelSpecView editable = model(ModelStatus.DRAFT, 2, CHECKSUM, ImplementationMode.DESIGNER_GENERATED);
        List<FileInput> submitted = List.of(
            new FileInput("dbt_project.yml", "name: sprint92\nmodel-paths: [models]\n"),
            new FileInput("models/orders.sql", "select old_value as order_id\n")
        );
        List<FileInput> canonical = List.of(
            submitted.get(0),
            new FileInput("models/orders.sql", "select new_value as order_id\n")
        );
        SourceBundleView source = new SourceBundleView(
            "sprint92",
            "c".repeat(64),
            "d".repeat(64),
            SourceBundleKind.CANONICAL_INITIALIZATION,
            true,
            List.of()
        );
        DraftView open = new DraftView(
            DRAFT_ID,
            PLAN_ID,
            MODEL_ID,
            2,
            CHECKSUM,
            null,
            null,
            DraftState.DRAFT,
            "etag-1",
            Instant.parse("2026-08-20T00:30:00Z"),
            source
        );
        AuthoringProjection projection = new AuthoringProjection(
            ProjectionCoverage.FULL,
            true,
            List.of("models/orders.sql"),
            List.of(),
            List.of()
        );
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(editable);
        when(drafts.findOpenAuthoring(TENANT, ACTOR, MODEL_ID)).thenReturn(Optional.of(open));
        when(projections.project(any(SourceBundleView.class))).thenReturn(projection);
        when(
            drafts.saveAuthoring(
                eq(TENANT),
                eq(ACTOR),
                eq(MODEL_ID),
                eq(DRAFT_ID),
                eq("etag-1"),
                any(),
                any(),
                eq(submitted),
                eq(true)
            )
        )
            .thenReturn(new SaveFilesView(DRAFT_ID, "etag-2", Instant.parse("2026-08-20T01:00:00Z"), 2, 120));
        when(drafts.authoringFiles(TENANT, ACTOR, MODEL_ID, DRAFT_ID)).thenReturn(canonical);

        var result = service.save(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            new SaveAuthoringDraftRequest(
                "etag-1",
                new ObjectMapper().createObjectNode().put("name", "orders"),
                submitted,
                ActiveView.VISUAL
            )
        );

        assertThat(result.files()).containsExactlyElementsOf(canonical);
    }

    @Test
    void validationReportsCanonicalModelAndProjectionIssuesBeforeCommit() {
        UpdateModelSpecCommand command = modelCommand();
        var snapshot = objectMapper.createObjectNode();
        snapshot.put("schemaVersion", 1);
        snapshot.set("modelSpec", objectMapper.valueToTree(command));
        SourceBundleView source = new SourceBundleView(
            "sprint92",
            "c".repeat(64),
            "d".repeat(64),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            false,
            List.of()
        );
        DraftView open = new DraftView(
            DRAFT_ID,
            PLAN_ID,
            MODEL_ID,
            2,
            CHECKSUM,
            null,
            null,
            DraftState.DRAFT,
            "etag-1",
            Instant.parse("2026-08-20T00:30:00Z"),
            source,
            snapshot,
            null,
            AuthoringOrigin.MANUAL_CODE
        );
        FieldIssue modelIssue = new FieldIssue("MODEL_SPEC_NAME_REQUIRED", "name", IssueSeverity.ERROR, "Name is required");
        ValidationView implementationValidation = new ValidationView(
            DRAFT_ID,
            DraftState.VALIDATED,
            "etag-2",
            Instant.parse("2026-08-20T01:00:00Z"),
            "e".repeat(64),
            List.of(),
            List.of()
        );
        when(drafts.findOpenAuthoring(TENANT, ACTOR, MODEL_ID)).thenReturn(Optional.of(open));
        when(projections.project(source)).thenReturn(
            new AuthoringProjection(
                ProjectionCoverage.NONE,
                false,
                List.of(),
                List.of(),
                List.of("MODEL_AUTHORING_PROJECTION_ALIAS_UNAVAILABLE")
            )
        );
        when(modelValidator.validate(command)).thenReturn(List.of(modelIssue));
        when(drafts.validate(TENANT, ACTOR, MODEL_ID, DRAFT_ID, new com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidateDraftRequest("etag-1")))
            .thenReturn(implementationValidation);

        var result = service.validate(TENANT, ACTOR, MODEL_ID, DRAFT_ID, "etag-1");

        assertThat(result.implementationValidation()).isSameAs(implementationValidation);
        assertThat(result.modelIssues()).containsExactly(modelIssue);
        assertThat(result.projectionIssues()).extracting("code", "severity").containsExactly(
            org.assertj.core.groups.Tuple.tuple("MODEL_AUTHORING_PROJECTION_ALIAS_UNAVAILABLE", "WARNING")
        );
    }

    private static UpdateModelSpecCommand modelCommand() {
        return new UpdateModelSpecCommand(
            PLAN_ID,
            UUID.fromString("20000000-0000-0000-0000-000000000092"),
            ModelType.DIMENSION,
            Layer.DWD,
            "orders",
            "Unified authoring model",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per order", List.of("order_id")),
            null,
            null,
            List.of(new ModelField("order_id", "bigint", false, "src_1.order_id", FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    private static ModelSpecView model(ModelStatus status, int revision, String checksum, ImplementationMode mode) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.status()).thenReturn(status);
        when(model.revision()).thenReturn(revision);
        when(model.checksum()).thenReturn(checksum);
        when(model.implementationMode()).thenReturn(mode);
        when(model.materialization()).thenReturn("table");
        when(model.name()).thenReturn("orders");
        when(model.fields()).thenReturn(List.of());
        when(model.sourceRefs()).thenReturn(List.of());
        when(model.dependsOn()).thenReturn(List.of());
        when(model.dimensionRefs()).thenReturn(List.of());
        when(model.metricRefs()).thenReturn(List.of());
        when(model.standardBindings()).thenReturn(List.of());
        return model;
    }
}
