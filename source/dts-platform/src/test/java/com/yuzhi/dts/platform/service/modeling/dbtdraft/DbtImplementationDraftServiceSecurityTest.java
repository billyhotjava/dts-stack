package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.DraftRow;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.FileRow;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.NewDraft;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ArtifactType;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportResult;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ErrorKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleFile;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RepresentationEvidence;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DbtImplementationDraftServiceSecurityTest {

    private static final String TENANT = "tenant-83";
    private static final String ACTOR = "maintainer-83";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000083");
    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000083");
    private static final UUID DRAFT_ID = UUID.fromString("30000000-0000-0000-0000-000000000083");
    private static final UUID IMPLEMENTATION_ID = UUID.fromString("40000000-0000-0000-0000-000000000083");
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String VALIDATED_CHECKSUM = "b".repeat(64);
    private static final String PROJECT_CHECKSUM = "c".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "d".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    private final DbtImplementationDraftRepository repository = org.mockito.Mockito.mock(
        DbtImplementationDraftRepository.class
    );
    private final ModelSpecApplicationService modelSpecs = org.mockito.Mockito.mock(ModelSpecApplicationService.class);
    private final ModelLifecycleService lifecycle = org.mockito.Mockito.mock(ModelLifecycleService.class);
    private final ModelSpecPlanWriteAccessPort writeAccess = org.mockito.Mockito.mock(ModelSpecPlanWriteAccessPort.class);
    private final AdvancedDbtDraftStaticValidator validator = org.mockito.Mockito.mock(AdvancedDbtDraftStaticValidator.class);
    private final ModelingDbtArtifactImportService artifactImports = org.mockito.Mockito.mock(
        ModelingDbtArtifactImportService.class
    );
    private final ModelRepresentationEvidencePort representationEvidence = org.mockito.Mockito.mock(
        ModelRepresentationEvidencePort.class
    );
    private final DbtImplementationDraftAuditRecorder audit = org.mockito.Mockito.mock(
        DbtImplementationDraftAuditRecorder.class
    );
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DbtImplementationDraftService service;

    @BeforeEach
    void setUp() {
        service = new DbtImplementationDraftService(
            repository,
            modelSpecs,
            lifecycle,
            writeAccess,
            validator,
            artifactImports,
            representationEvidence,
            audit,
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void crossActorIsForbiddenWhileCrossTenantRemainsNotFoundAndBothAreStrictlyAudited() {
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.empty());
        when(repository.existsForTenant(TENANT, MODEL_ID, DRAFT_ID)).thenReturn(true);

        assertFailure(
            ErrorKind.FORBIDDEN,
            () -> service.saveFiles(TENANT, ACTOR, MODEL_ID, DRAFT_ID, saveRequest("select 1"))
        );
        verify(audit).recordFailure(eq("MODELING_DBT_DRAFT_SAVE"), eq(DRAFT_ID.toString()), any());

        when(repository.findForActor("other-tenant", MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.empty());
        when(repository.existsForTenant("other-tenant", MODEL_ID, DRAFT_ID)).thenReturn(false);
        assertFailure(
            ErrorKind.NOT_FOUND,
            () -> service.saveFiles("other-tenant", ACTOR, MODEL_ID, DRAFT_ID, saveRequest("select 1"))
        );
        verify(repository).existsForTenant("other-tenant", MODEL_ID, DRAFT_ID);
    }

    @Test
    void auditsGoneConflictPreconditionAndUnprocessableFailuresWithCorrelation() {
        DraftRow expired = row(DraftState.DRAFT, "etag", NOW, null, null, null, null, null, null);
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(expired));
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        assertFailure(ErrorKind.GONE, () -> service.saveFiles(TENANT, ACTOR, MODEL_ID, DRAFT_ID, saveRequest("select 1")));

        DraftRow committed = row(
            DraftState.COMMITTED,
            "etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            PROJECT_CHECKSUM,
            "e".repeat(64),
            "{}",
            "commit-key",
            IMPLEMENTATION_ID
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(committed));
        assertFailure(
            ErrorKind.CONFLICT,
            () -> service.saveFiles(TENANT, ACTOR, MODEL_ID, DRAFT_ID, saveRequest("select 1"))
        );

        DraftRow draft = row(DraftState.DRAFT, "etag", NOW.plusSeconds(60), null, null, null, null, null, null);
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(draft));
        when(repository.replaceFiles(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        assertFailure(
            ErrorKind.PRECONDITION_FAILED,
            () -> service.saveFiles(TENANT, ACTOR, MODEL_ID, DRAFT_ID, saveRequest("select 1"))
        );

        when(repository.listFiles(DRAFT_ID)).thenReturn(List.of());
        assertFailure(
            ErrorKind.UNPROCESSABLE,
            () -> service.validate(TENANT, ACTOR, MODEL_ID, DRAFT_ID, new ValidateDraftRequest("etag"))
        );
        verify(audit, org.mockito.Mockito.atLeast(4)).recordFailure(any(), eq(DRAFT_ID.toString()), argThat(this::safeAudit));
    }

    @Test
    void rejectsDesignerOwnedModelsBeforeCreatingADraft() {
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);

        assertFailure(
            ErrorKind.UNPROCESSABLE,
            () ->
                service.create(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new DbtImplementationDraftContract.CreateDraftRequest(
                        PLAN_ID,
                        3,
                        MODEL_CHECKSUM,
                        null,
                        null,
                        "create-83"
                    )
                )
        );
        verify(repository, never()).create(any());
    }

    @Test
    void restoresEveryEditableFileFromTheExactlyPinnedFrozenBundleDuringDraftCreation() {
        List<FileRow> files = List.of(
            file("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            file("models/orders.sql", "select 1\n"),
            file("models/schema.yml", "version: 2\nmodels: []\n")
        );
        BundleSnapshot bundle = bundle(files, project());
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        ImplementationView implementation = baseImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(implementation);
        when(
            representationEvidence.findExact(
                TENANT,
                MODEL_ID,
                3,
                MODEL_CHECKSUM,
                2,
                true
            )
        ).thenReturn(Optional.of(representation(bundle)));
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));

        var created = service.create(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(PLAN_ID, 3, MODEL_CHECKSUM, 2, IMPLEMENTATION_CHECKSUM, "create-83")
        );

        assertThat(created.sourceBundle()).isNotNull();
        assertThat(created.sourceBundle().bundleChecksum()).isEqualTo(bundle.bundleChecksum());
        assertThat(created.sourceBundle().files())
            .extracting(DbtImplementationDraftContract.BundleFileView::path)
            .containsExactly("dbt_project.yml", "models/orders.sql", "models/schema.yml");
        assertThat(created.sourceBundle().files())
            .allSatisfy(file -> {
                assertThat(file.checksum()).matches("^[0-9a-f]{64}$");
                assertThat(file.byteSize()).isEqualTo(file.content().getBytes(StandardCharsets.UTF_8).length);
            });
        verify(audit).recordSuccess(
            eq("MODELING_DBT_DRAFT_CREATE"),
            eq(created.draftId().toString()),
            argThat(payload -> safeAudit(payload) && bundle.bundleChecksum().equals(payload.get("bundleChecksum")))
        );
    }

    @Test
    void carriesThePriorImplementationBundleForwardAfterTheLogicalModelAdvances() {
        String advancedModelChecksum = "f".repeat(64);
        List<FileRow> files = List.of(
            file("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            file("models/orders.sql", "select 1\n"),
            file("models/schema.yml", "version: 2\nmodels: []\n")
        );
        BundleSnapshot bundle = bundle(files, project());
        ModelSpecView model = model(4, advancedModelChecksum);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        ImplementationView implementation = baseImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(implementation);
        when(representationEvidence.findExact(TENANT, MODEL_ID, 3, MODEL_CHECKSUM, 2, true))
            .thenReturn(Optional.of(representation(bundle)));
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));

        var created = service.create(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(PLAN_ID, 4, advancedModelChecksum, 2, IMPLEMENTATION_CHECKSUM, "advance-83")
        );

        assertThat(created.baseModelRevision()).isEqualTo(4);
        assertThat(created.baseModelChecksum()).isEqualTo(advancedModelChecksum);
        assertThat(created.baseImplementationRevision()).isEqualTo(2);
        assertThat(created.sourceBundle().sourceKind()).isEqualTo(SourceBundleKind.FROZEN_SOURCE_BUNDLE);
        assertThat(created.sourceBundle().files())
            .extracting(DbtImplementationDraftContract.BundleFileView::path)
            .containsExactly("dbt_project.yml", "models/orders.sql", "models/schema.yml");
        verify(representationEvidence).findExact(TENANT, MODEL_ID, 3, MODEL_CHECKSUM, 2, true);
    }

    @Test
    void reconstructsANonLosslessCanonicalProjectFromExactlyPinnedImportedArtifacts() {
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        ImplementationView implementation = baseImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(implementation);
        when(
            representationEvidence.findExact(TENANT, MODEL_ID, 3, MODEL_CHECKSUM, 2, true)
        ).thenReturn(Optional.of(importedRepresentation()));
        when(validator.validate(any())).thenReturn(project());
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));

        var created = service.create(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(PLAN_ID, 3, MODEL_CHECKSUM, 2, IMPLEMENTATION_CHECKSUM, "create-imported-83")
        );

        assertThat(created.sourceBundle()).isNotNull();
        assertThat(created.sourceBundle().sourceKind()).isEqualTo(SourceBundleKind.CANONICAL_ARTIFACT_RECONSTRUCTION);
        assertThat(created.sourceBundle().lossless()).isFalse();
        assertThat(created.sourceBundle().files())
            .extracting(DbtImplementationDraftContract.BundleFileView::path)
            .containsExactly("dbt_project.yml", "models/orders.sql", "models/schema.yml");
        assertThat(created.sourceBundle().files())
            .filteredOn(file -> "models/orders.sql".equals(file.path()))
            .singleElement()
            .extracting(DbtImplementationDraftContract.BundleFileView::content)
            .isEqualTo("select 1\n");
    }

    @Test
    void createsAnExplicitNonLosslessCanonicalProjectForTheFirstDbtImplementation() {
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.name()).thenReturn("预算科目");
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(null);
        when(validator.validate(any())).thenReturn(initialProject());
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));

        var created = service.create(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(PLAN_ID, 3, MODEL_CHECKSUM, null, null, "create-first-83")
        );

        assertThat(created.sourceBundle()).isNotNull();
        assertThat(created.sourceBundle().sourceKind()).isEqualTo(SourceBundleKind.CANONICAL_INITIALIZATION);
        assertThat(created.sourceBundle().lossless()).isFalse();
        assertThat(created.sourceBundle().files())
            .extracting(DbtImplementationDraftContract.BundleFileView::path)
            .contains("dbt_project.yml")
            .anyMatch(path -> path.startsWith("models/") && path.endsWith(".sql"));
    }

    @Test
    void replaysThePersistedCreateResponseBeforeReadingMutableModelOrImplementationState() throws Exception {
        String sql = "select 1\n";
        String projectFile = "name: sprint83\nmodel-paths: [models]\n";
        var sourceBundle = new DbtImplementationDraftContract.SourceBundleView(
            "sprint83",
            PROJECT_CHECKSUM,
            "e".repeat(64),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            true,
            List.of(
                new DbtImplementationDraftContract.BundleFileView(
                    "dbt_project.yml",
                    projectFile,
                    ModelPackageChecksum.sha256Text(projectFile),
                    projectFile.getBytes(StandardCharsets.UTF_8).length
                ),
                new DbtImplementationDraftContract.BundleFileView(
                    "models/orders.sql",
                    sql,
                    ModelPackageChecksum.sha256Text(sql),
                    sql.getBytes(StandardCharsets.UTF_8).length
                )
            )
        );
        String requestHash = createRequestHash(2, IMPLEMENTATION_CHECKSUM);
        DraftRow existing = replayRow(requestHash, objectMapper.writeValueAsString(sourceBundle));
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(repository.findByIdempotency(TENANT, PLAN_ID, MODEL_ID, ACTOR, "create-replay-83"))
            .thenReturn(Optional.of(existing));

        var replayed = service.create(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(PLAN_ID, 3, MODEL_CHECKSUM, 2, IMPLEMENTATION_CHECKSUM, "create-replay-83")
        );

        assertThat(replayed.draftId()).isEqualTo(existing.id());
        assertThat(replayed.sourceBundle()).isEqualTo(sourceBundle);
        verify(modelSpecs, never()).get(any(), any());
        verify(lifecycle, never()).timeline(any(), any());
        verify(repository, never()).create(any());
    }

    @Test
    void rejectsSensitiveFilesBeforeRepositoryPersistenceAndStrictlyAuditsWithoutTheirPathOrBody() {
        DraftRow draft = row(DraftState.DRAFT, "etag", NOW.plusSeconds(60), null, null, null, null, null, null);
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(draft));
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);

        DraftException failure = assertFailure(
            ErrorKind.BAD_REQUEST,
            () ->
                service.saveFiles(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    DRAFT_ID,
                    new SaveFilesRequest("etag", List.of(new FileInput(".env.production", "secret-token-body")))
                )
        );

        assertThat(failure.code()).isEqualTo("DBT_DRAFT_SENSITIVE_FILE_FORBIDDEN");
        verify(repository, never()).replaceFiles(any(), any(), any(), any(), any(), any(), any(), any());
        verify(audit).recordFailure(
            eq("MODELING_DBT_DRAFT_SAVE"),
            eq(DRAFT_ID.toString()),
            argThat(payload -> {
                String serialized = String.valueOf(payload);
                return safeAudit(payload) &&
                !serialized.contains(".env") &&
                !serialized.contains("secret-token-body");
            })
        );
    }

    @Test
    void replaysOnlyTheCommitWhoseIdempotencyIsBoundToValidatedAndBundleChecksums() {
        String bundleChecksum = "e".repeat(64);
        String receiptKey = commitKey("commit-83", VALIDATED_CHECKSUM, bundleChecksum);
        DraftRow committed = row(
            DraftState.COMMITTED,
            "committed-etag",
            NOW.minusSeconds(1),
            VALIDATED_CHECKSUM,
            PROJECT_CHECKSUM,
            bundleChecksum,
            "{}",
            receiptKey,
            IMPLEMENTATION_ID
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(committed));
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);

        var replay = service.commit(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            new CommitDraftRequest("stale-etag-is-ignored-for-replay", VALIDATED_CHECKSUM, "commit-83")
        );

        assertThat(replay.implementationId()).isEqualTo(IMPLEMENTATION_ID);
        verify(lifecycle, never()).saveImportedDbtImplementation(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(audit).recordSuccess(eq("MODELING_DBT_DRAFT_COMMIT"), eq(DRAFT_ID.toString()), argThat(this::safeAudit));
    }

    @Test
    void rejectsBaseModelDriftBeforeClaimingTheCommitAndAuditsTheConflict() {
        List<FileRow> files = files();
        ValidatedProject project = project();
        BundleSnapshot bundle = bundle(files, project);
        DraftRow validated = row(
            DraftState.VALIDATED,
            "etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            bundle.manifest(),
            null,
            null
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(validated));
        when(repository.listFiles(DRAFT_ID)).thenReturn(files);
        when(validator.validate(any())).thenReturn(project);
        ModelSpecView drifted = model(4, "f".repeat(64));
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(drifted);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);

        assertFailure(
            ErrorKind.CONFLICT,
            () ->
                service.commit(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    DRAFT_ID,
                    new CommitDraftRequest("etag", VALIDATED_CHECKSUM, "commit-83")
                )
        );
        verify(repository, never()).claimCommit(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void commitsTheFrozenBundleAsAnImmutableConfigArtifactAndBindsItIntoImplementationIdempotency() {
        List<FileRow> files = files();
        ValidatedProject project = project();
        BundleSnapshot bundle = bundle(files, project);
        String derivedKey = commitKey("commit-83", VALIDATED_CHECKSUM, bundle.bundleChecksum());
        DraftRow validated = row(
            DraftState.VALIDATED,
            "etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            bundle.manifest(),
            null,
            null
        );
        DraftRow claimed = row(
            DraftState.COMMITTING,
            "claimed-etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            bundle.manifest(),
            derivedKey,
            null
        );
        DraftRow committed = row(
            DraftState.COMMITTED,
            "committed-etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            bundle.manifest(),
            derivedKey,
            IMPLEMENTATION_ID
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(validated));
        when(repository.listFiles(DRAFT_ID)).thenReturn(files);
        when(validator.validate(any())).thenReturn(project);
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(null);
        when(repository.claimCommit(any(), any(), any(), any(), any(), any(), eq(derivedKey), any(), any()))
            .thenReturn(Optional.of(claimed));
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        when(implementation.id()).thenReturn(IMPLEMENTATION_ID);
        when(implementation.implementationRevision()).thenReturn(1);
        when(implementation.implementationChecksum()).thenReturn(IMPLEMENTATION_CHECKSUM);
        when(lifecycle.saveImportedDbtImplementation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(implementation);
        ImportResult importResult = org.mockito.Mockito.mock(ImportResult.class);
        when(importResult.artifactCount()).thenReturn(3);
        when(artifactImports.importArtifacts(any())).thenReturn(importResult);
        when(repository.completeCommit(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), anyInt(), any()))
            .thenReturn(committed);

        service.commit(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            new CommitDraftRequest("etag", VALIDATED_CHECKSUM, "commit-83")
        );

        ArgumentCaptor<SaveImplementationCommand> implementationCommand = ArgumentCaptor.forClass(
            SaveImplementationCommand.class
        );
        verify(lifecycle).saveImportedDbtImplementation(
            eq(TENANT),
            eq(ACTOR),
            eq(MODEL_ID),
            any(),
            any(),
            any(),
            eq("sprint83"),
            eq("model.sprint83.orders"),
            implementationCommand.capture()
        );
        assertThat(implementationCommand.getValue().idempotencyKey()).isEqualTo(derivedKey);
        assertThat(implementationCommand.getValue().settings())
            .containsEntry("targetPhysicalName", "orders")
            .containsEntry("loadStrategy", "FULL")
            .containsEntry("partitionFields", List.of());
        GeneratedInput generated = (GeneratedInput) implementationCommand.getValue().inputs().getFirst();
        assertThat(generated.config())
            .containsEntry("bundleChecksum", bundle.bundleChecksum())
            .containsEntry("projectChecksum", bundle.projectChecksum());

        ArgumentCaptor<ImportCommand> importCommand = ArgumentCaptor.forClass(ImportCommand.class);
        verify(artifactImports).importArtifacts(importCommand.capture());
        assertThat(importCommand.getValue().idempotencyKey()).isEqualTo(derivedKey);
        assertThat(importCommand.getValue().artifacts())
            .filteredOn(artifact -> artifact.artifactType() == ArtifactType.CONFIG)
            .singleElement()
            .satisfies(artifact -> {
                assertThat(artifact.content()).isEqualTo(bundle.manifest());
                assertThat(artifact.checksum()).isEqualTo(bundle.bundleChecksum());
            });
    }

    @Test
    void convertsUnexpectedFailuresToSafeCorrelated500AndNeverAuditsFileContent() {
        DraftRow draft = row(DraftState.DRAFT, "etag", NOW.plusSeconds(60), null, null, null, null, null, null);
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(draft));
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(repository.replaceFiles(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("database exploded with select secret_value"));

        DraftException failure = assertFailure(
            ErrorKind.SYSTEM_ERROR,
            () -> service.saveFiles(TENANT, ACTOR, MODEL_ID, DRAFT_ID, saveRequest("select secret_value"))
        );

        assertThat(failure.getMessage()).doesNotContain("database", "secret_value");
        verify(audit).recordFailure(
            eq("MODELING_DBT_DRAFT_SAVE"),
            eq(DRAFT_ID.toString()),
            argThat(payload -> safeAudit(payload) && !payload.toString().contains("secret_value"))
        );
    }

    private DraftException assertFailure(ErrorKind expected, ThrowingCall call) {
        DraftException failure = catchThrowableOfType(call::run, DraftException.class);
        assertThat(failure).isNotNull();
        assertThat(failure.kind()).isEqualTo(expected);
        assertThat(failure.details()).containsKey("correlationId");
        return failure;
    }

    private boolean safeAudit(Map<String, Object> payload) {
        return payload != null && payload.containsKey("correlationId") && !payload.toString().contains("select ");
    }

    private static SaveFilesRequest saveRequest(String content) {
        return new SaveFilesRequest("etag", List.of(new FileInput("models/orders.sql", content)));
    }

    private ModelSpecView model(int revision, String checksum) {
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(revision);
        when(model.checksum()).thenReturn(checksum);
        when(model.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.materialization()).thenReturn("table");
        return model;
    }

    private List<FileRow> files() {
        return List.of(
            file("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            file("models/orders.sql", "select 1\n")
        );
    }

    private static FileRow file(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new FileRow(path, content, ModelPackageChecksum.sha256(bytes), bytes.length);
    }

    private BundleSnapshot bundle(List<FileRow> files, ValidatedProject project) {
        return DbtProjectBundleManifest.freeze(
            objectMapper,
            files.stream().map(file -> new BundleFile(file.path(), file.content(), file.checksum(), file.byteSize())).toList(),
            project
        );
    }

    private ImplementationView baseImplementation() {
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        when(implementation.id()).thenReturn(IMPLEMENTATION_ID);
        when(implementation.modelSpecId()).thenReturn(MODEL_ID);
        when(implementation.planId()).thenReturn(PLAN_ID);
        when(implementation.revision()).thenReturn(3);
        when(implementation.modelChecksum()).thenReturn(MODEL_CHECKSUM);
        when(implementation.ownership()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(implementation.projectKey()).thenReturn("sprint83");
        when(implementation.dbtUniqueId()).thenReturn("model.sprint83.orders");
        when(implementation.implementationRevision()).thenReturn(2);
        when(implementation.implementationChecksum()).thenReturn(IMPLEMENTATION_CHECKSUM);
        return implementation;
    }

    private RepresentationEvidence representation(BundleSnapshot bundle) {
        var inputs = objectMapper.valueToTree(
            List.of(
                Map.of(
                    "generatorType",
                    "DBT",
                    "config",
                    Map.of(
                        "projectKey",
                        "sprint83",
                        "dbtUniqueId",
                        "model.sprint83.orders",
                        "projectChecksum",
                        bundle.projectChecksum(),
                        "bundleChecksum",
                        bundle.bundleChecksum()
                    )
                )
            )
        );
        ImplementationSnapshot implementation = new ImplementationSnapshot(
            IMPLEMENTATION_ID,
            MODEL_ID,
            PLAN_ID,
            3,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM,
            ImplementationMode.DBT_MANAGED,
            "sprint83",
            "model.sprint83.orders",
            "GENERATED",
            inputs,
            objectMapper.createArrayNode(),
            objectMapper.createObjectNode(),
            "table"
        );
        ArtifactEvidence manifest = new ArtifactEvidence(
            "CONFIG",
            ".dts/dbt-project-bundle.json",
            bundle.bundleChecksum(),
            "IMPORTED",
            bundle.manifest(),
            MODEL_ID,
            3,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM
        );
        return new RepresentationEvidence(implementation, List.of(manifest), null, null, null, List.of());
    }

    private RepresentationEvidence importedRepresentation() {
        ImplementationSnapshot implementation = new ImplementationSnapshot(
            IMPLEMENTATION_ID,
            MODEL_ID,
            PLAN_ID,
            3,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM,
            ImplementationMode.DBT_MANAGED,
            "sprint83",
            "model.sprint83.orders",
            "IMPORTED",
            objectMapper.createArrayNode(),
            objectMapper.createArrayNode(),
            objectMapper.createObjectNode(),
            "table"
        );
        return new RepresentationEvidence(
            implementation,
            List.of(
                artifact("SQL", "models/orders.sql", "select 1\n"),
                artifact("SCHEMA", "models/orders.sql#schema", "{\"columns\":[],\"tests\":[]}"),
                artifact("CONFIG", "models/orders.sql#config", "{\"materialized\":\"table\"}"),
                artifact("DEPENDENCY", "models/orders.sql#dependency", "[]")
            ),
            null,
            null,
            null,
            List.of()
        );
    }

    private static ArtifactEvidence artifact(String type, String path, String content) {
        return new ArtifactEvidence(
            type,
            path,
            ModelPackageChecksum.sha256Text(content),
            "IMPORTED",
            content,
            MODEL_ID,
            3,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM
        );
    }

    private static DraftRow draft(NewDraft draft) {
        return new DraftRow(
            draft.id(),
            draft.tenantId(),
            draft.planId(),
            draft.modelSpecId(),
            draft.actorId(),
            draft.baseModelRevision(),
            draft.baseModelChecksum(),
            draft.baseImplementationRevision(),
            draft.baseImplementationChecksum(),
            draft.idempotencyKey(),
            draft.requestHash(),
            draft.sourceBundleSnapshot(),
            DraftState.DRAFT,
            draft.etag(),
            draft.expiresAt(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            draft.createdAt(),
            draft.createdAt()
        );
    }

    private ValidatedProject project() {
        String schema = "{\"columns\":[],\"tests\":[]}";
        ValidatedNode node = new ValidatedNode(
            "model.sprint83.orders",
            "orders",
            "models/orders.sql",
            "table",
            "MODEL",
            "select 1\n",
            ModelPackageChecksum.sha256Text("select 1\n"),
            schema,
            ModelPackageChecksum.sha256Text(schema),
            List.of(),
            List.of()
        );
        return new ValidatedProject(VALIDATED_CHECKSUM, PROJECT_CHECKSUM, "sprint83", List.of(node), List.of());
    }

    private ValidatedProject initialProject() {
        String name = "model_200000000000";
        String sql = "-- DTS canonical initialization; replace this placeholder before commit.\n" +
        "select 1 as _dts_placeholder where 1 = 0\n";
        String schema = "{\"columns\":[],\"tests\":[]}";
        ValidatedNode node = new ValidatedNode(
            "model.dts_model_200000000000." + name,
            name,
            "models/" + name + ".sql",
            "table",
            "MODEL",
            sql,
            ModelPackageChecksum.sha256Text(sql),
            schema,
            ModelPackageChecksum.sha256Text(schema),
            List.of(),
            List.of()
        );
        return new ValidatedProject(
            VALIDATED_CHECKSUM,
            PROJECT_CHECKSUM,
            "dts_model_200000000000",
            List.of(node),
            List.of()
        );
    }

    private static String commitKey(String idempotencyKey, String validatedChecksum, String bundleChecksum) {
        return ModelPackageChecksum.sha256Text(
            String.join("\u0000", "dbt-draft-commit-v1", idempotencyKey, validatedChecksum, bundleChecksum)
        );
    }

    private static String createRequestHash(Integer implementationRevision, String implementationChecksum) {
        return ModelPackageChecksum.sha256Text(
            String.join(
                "\u0000",
                TENANT,
                ACTOR,
                PLAN_ID.toString(),
                MODEL_ID.toString(),
                "3",
                MODEL_CHECKSUM,
                Objects.toString(implementationRevision, ""),
                Objects.toString(implementationChecksum, "")
            )
        );
    }

    private static DraftRow replayRow(String requestHash, String sourceBundleSnapshot) {
        return new DraftRow(
            DRAFT_ID,
            TENANT,
            PLAN_ID,
            MODEL_ID,
            ACTOR,
            3,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM,
            "create-replay-83",
            requestHash,
            sourceBundleSnapshot,
            DraftState.DRAFT,
            "etag-replay",
            NOW.plusSeconds(3600),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            NOW,
            NOW
        );
    }

    private static DraftRow row(
        DraftState state,
        String etag,
        Instant expiresAt,
        String validatedChecksum,
        String projectChecksum,
        String bundleChecksum,
        String bundleManifest,
        String commitKey,
        UUID implementationId
    ) {
        return new DraftRow(
            DRAFT_ID,
            TENANT,
            PLAN_ID,
            MODEL_ID,
            ACTOR,
            3,
            MODEL_CHECKSUM,
            null,
            null,
            "create-83",
            "f".repeat(64),
            null,
            state,
            etag,
            expiresAt,
            validatedChecksum,
            projectChecksum,
            bundleChecksum,
            bundleManifest,
            null,
            commitKey,
            implementationId,
            implementationId == null ? null : 1,
            implementationId == null ? null : IMPLEMENTATION_CHECKSUM,
            implementationId == null ? 0 : 3,
            implementationId == null ? null : NOW,
            NOW,
            NOW
        );
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
