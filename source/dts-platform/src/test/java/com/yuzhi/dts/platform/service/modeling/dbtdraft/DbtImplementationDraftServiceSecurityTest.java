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
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleCompilerPort;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyReadPort;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.Resolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyFacts;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Reconciliation;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtCompiler.CompileException;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ArtifactType;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportResult;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.NodeKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringOrigin;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringSeed;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ErrorKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    void unifiedAuthoringCreatesASourceNeutralDraftWithServerDerivedMetadata() {
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn(MODEL_CHECKSUM);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(model.materialization()).thenReturn("table");
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(null, List.of(), List.of()));
        when(validator.validate(any())).thenReturn(
            new ValidatedProject(
                VALIDATED_CHECKSUM,
                PROJECT_CHECKSUM,
                "dts_model_200000000000",
                List.of(
                    new ValidatedNode(
                        "model.dts_model_200000000000.dim_orders",
                        "dim_orders",
                        "models/dim_orders.sql",
                        "table",
                        "MODEL",
                        "select 1 as _dts_placeholder where 1 = 0\n",
                        "1".repeat(64),
                        "{}",
                        "2".repeat(64),
                        List.of(),
                        List.of()
                    )
                ),
                List.of()
            )
        );
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));
        var modelSnapshot = objectMapper.createObjectNode().put("name", "orders");
        var projection = objectMapper.createObjectNode().put("coverage", "FULL");

        var created = service.createAuthoring(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(PLAN_ID, 3, MODEL_CHECKSUM, null, null, "dim_orders", "create-authoring-83"),
            new AuthoringSeed(modelSnapshot, projection, AuthoringOrigin.SYSTEM_GENERATED, "9".repeat(64))
        );

        assertThat(created.modelSpecSnapshot()).isEqualTo(modelSnapshot);
        assertThat(created.projectionSummary()).isEqualTo(projection);
        assertThat(created.authoringOrigin()).isEqualTo(AuthoringOrigin.SYSTEM_GENERATED);
        ArgumentCaptor<NewDraft> inserted = ArgumentCaptor.forClass(NewDraft.class);
        verify(repository).create(inserted.capture());
        assertThat(inserted.getValue().requestHash()).isEqualTo("9".repeat(64));
        assertThat(inserted.getValue().authoringOrigin()).isEqualTo("SYSTEM_GENERATED");
    }

    @Test
    void unifiedAuthoringCompilesThePinnedDesignerImplementationIntoOneEditableBundle() {
        ModelLifecycleCompilerPort visualCompiler = org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class);
        ReflectionTestUtils.setField(service, "visualCompiler", visualCompiler);
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn(MODEL_CHECKSUM);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(model.materialization()).thenReturn("table");
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        when(implementation.id()).thenReturn(IMPLEMENTATION_ID);
        when(implementation.modelSpecId()).thenReturn(MODEL_ID);
        when(implementation.planId()).thenReturn(PLAN_ID);
        when(implementation.revision()).thenReturn(3);
        when(implementation.modelChecksum()).thenReturn(MODEL_CHECKSUM);
        when(implementation.ownership()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(implementation.projectKey()).thenReturn("sprint83");
        when(implementation.dbtUniqueId()).thenReturn("model.sprint83.orders");
        when(implementation.implementationRevision()).thenReturn(2);
        when(implementation.implementationChecksum()).thenReturn(IMPLEMENTATION_CHECKSUM);
        when(implementation.materialization()).thenReturn("table");
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(implementation, List.of(), List.of()));
        when(visualCompiler.compile(TENANT, model, implementation)).thenReturn(
            List.of(
                new ArtifactWrite(
                    "SQL",
                    "models/dwd/orders/v3/i2/orders.sql",
                    "1".repeat(64),
                    "select 1 as order_id\n",
                    "MODEL",
                    "table",
                    null
                ),
                new ArtifactWrite(
                    "SCHEMA",
                    "models/dwd/orders/v3/i2/orders.yml",
                    "2".repeat(64),
                    "version: 2\nmodels: []\n",
                    "MODEL",
                    "table",
                    null
                )
            )
        );
        when(validator.validate(any(Map.class))).thenReturn(
            new ValidatedProject(VALIDATED_CHECKSUM, PROJECT_CHECKSUM, "sprint83", List.of(), List.of())
        );
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));

        var created = service.createAuthoring(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(
                PLAN_ID,
                3,
                MODEL_CHECKSUM,
                2,
                IMPLEMENTATION_CHECKSUM,
                null,
                "create-designer-authoring-83"
            ),
            new AuthoringSeed(
                objectMapper.createObjectNode().put("name", "orders"),
                objectMapper.createObjectNode().put("coverage", "FULL"),
                AuthoringOrigin.SYSTEM_GENERATED,
                "8".repeat(64)
            )
        );

        assertThat(created.sourceBundle().sourceKind()).isEqualTo(SourceBundleKind.CANONICAL_ARTIFACT_RECONSTRUCTION);
        assertThat(created.sourceBundle().files())
            .extracting(DbtImplementationDraftContract.BundleFileView::path)
            .containsExactly(
                "dbt_project.yml",
                "models/dwd/orders/v3/i2/orders.sql",
                "models/dwd/orders/v3/i2/orders.yml"
            );
        verify(visualCompiler).compile(TENANT, model, implementation);
    }

    @Test
    void unifiedAuthoringPreservesTheCanonicalCompilerFailureCode() {
        ModelLifecycleCompilerPort visualCompiler = org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class);
        ReflectionTestUtils.setField(service, "visualCompiler", visualCompiler);
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn(MODEL_CHECKSUM);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        when(implementation.modelSpecId()).thenReturn(MODEL_ID);
        when(implementation.planId()).thenReturn(PLAN_ID);
        when(implementation.revision()).thenReturn(3);
        when(implementation.modelChecksum()).thenReturn(MODEL_CHECKSUM);
        when(implementation.ownership()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(implementation.projectKey()).thenReturn("sprint83");
        when(implementation.dbtUniqueId()).thenReturn("model.sprint83.orders");
        when(implementation.implementationRevision()).thenReturn(2);
        when(implementation.implementationChecksum()).thenReturn(IMPLEMENTATION_CHECKSUM);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(implementation, List.of(), List.of()));
        when(visualCompiler.compile(TENANT, model, implementation)).thenThrow(
            new CompileException("IMPLEMENTATION_PARTITION_UNSUPPORTED")
        );

        DraftException failure = catchThrowableOfType(
            () ->
                service.createAuthoring(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new CreateDraftRequest(
                        PLAN_ID,
                        3,
                        MODEL_CHECKSUM,
                        2,
                        IMPLEMENTATION_CHECKSUM,
                        null,
                        "create-invalid-designer-authoring-83"
                    ),
                    new AuthoringSeed(
                        objectMapper.createObjectNode().put("name", "orders"),
                        objectMapper.createObjectNode().put("coverage", "FULL"),
                        AuthoringOrigin.SYSTEM_GENERATED,
                        "7".repeat(64)
                    )
                ),
            DraftException.class
        );

        assertThat(failure.code()).isEqualTo("IMPLEMENTATION_PARTITION_UNSUPPORTED");
        assertThat(failure.code()).isNotEqualTo("DBT_DRAFT_SOURCE_BUNDLE_UNAVAILABLE");
    }

    @Test
    void visualSaveRecompilesManagedFilesAndPreservesCurrentUnmanagedFiles() throws Exception {
        ModelLifecycleCompilerPort visualCompiler = org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class);
        ReflectionTestUtils.setField(service, "visualCompiler", visualCompiler);
        String projectFile = "name: sprint83\nversion: '1.0'\nconfig-version: 2\nmodel-paths:\n  - models\nmodels:\n  sprint83:\n    +materialized: table\n";
        String managedPath = "models/dwd/orders/v3/i2/orders.sql";
        String unmanagedPath = "models/dwd/orders/v3/i2/custom_business_rule.sql";
        List<FileInput> submitted = List.of(
            new FileInput("dbt_project.yml", projectFile),
            new FileInput(managedPath, "select old_value as order_id\n"),
            new FileInput(unmanagedPath, "{% macro custom_business_rule() %}1{% endmacro %}\n")
        );
        SourceBundleView source = sourceBundle("sprint83", submitted);
        var projection = objectMapper.createObjectNode();
        projection.putArray("managedPaths").add(managedPath);
        String snapshot = objectMapper.writeValueAsString(versionedVisualSnapshot());
        DraftRow current = authoringRowWithSource(snapshot, objectMapper.writeValueAsString(source), "{}");
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(current));
        when(repository.listFiles(DRAFT_ID)).thenReturn(
            submitted.stream().map(file -> file(file.path(), file.content())).toList()
        );
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        ImplementationView implementation = designerImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(implementation, List.of(), List.of()));
        when(visualCompiler.compile(eq(TENANT), any(ModelSpecView.class), any(ImplementationView.class))).thenReturn(
            List.of(
                new ArtifactWrite(
                    "SQL",
                    managedPath,
                    "1".repeat(64),
                    "select new_value as order_id\n",
                    "MODEL",
                    "table",
                    null
                ),
                new ArtifactWrite(
                    "SCHEMA",
                    "models/dwd/orders/v3/i2/orders.yml",
                    "2".repeat(64),
                    "version: 2\nmodels: []\n",
                    "MODEL",
                    "table",
                    null
                )
            )
        );
        when(repository.replaceAuthoringContent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Optional.of(current));

        service.saveAuthoring(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            "authoring-etag",
            versionedVisualSnapshot(),
            projection,
            submitted,
            true
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FileInput>> savedFiles = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceAuthoringContent(
            eq(TENANT),
            eq(MODEL_ID),
            eq(DRAFT_ID),
            eq(ACTOR),
            eq("authoring-etag"),
            any(),
            any(),
            any(),
            savedFiles.capture(),
            any()
        );
        assertThat(savedFiles.getValue())
            .filteredOn(file -> managedPath.equals(file.path()))
            .singleElement()
            .extracting(FileInput::content)
            .isEqualTo("select new_value as order_id\n");
        assertThat(savedFiles.getValue())
            .filteredOn(file -> unmanagedPath.equals(file.path()))
            .singleElement()
            .extracting(FileInput::content)
            .isEqualTo("{% macro custom_business_rule() %}1{% endmacro %}\n");
    }

    @Test
    void visualSaveUpgradesUnchangedFrozenCompilerFilesWhenGeneratedContentHasEvolved() throws Exception {
        ModelLifecycleCompilerPort visualCompiler = org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class);
        ReflectionTestUtils.setField(service, "visualCompiler", visualCompiler);
        String projectFile = "name: sprint83\nversion: '1.0'\nconfig-version: 2\nmodel-paths:\n  - models\nmodels:\n  sprint83:\n    +materialized: table\n";
        String modelPath = "models/dwd/orders/v3/i2/orders.sql";
        String schemaPath = "models/dwd/orders/v3/i2/orders.yml";
        String unmanagedPath = "models/dwd/orders/v3/i2/business_notes.md";
        List<FileInput> submitted = List.of(
            new FileInput("dbt_project.yml", projectFile),
            new FileInput(modelPath, "select old_value as order_id\n"),
            new FileInput(schemaPath, "version: 2\nmodels: []\n"),
            new FileInput(unmanagedPath, "Keep this hand-authored note.\n")
        );
        SourceBundleView source = sourceBundle("sprint83", submitted);
        var projection = objectMapper.createObjectNode();
        projection.put("coverage", "NONE");
        projection.putArray("managedPaths");
        String snapshot = objectMapper.writeValueAsString(versionedVisualSnapshot());
        DraftRow current = authoringRowWithSource(snapshot, objectMapper.writeValueAsString(source), projection.toString());
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(current));
        when(repository.listFiles(DRAFT_ID)).thenReturn(
            submitted.stream().map(file -> file(file.path(), file.content())).toList()
        );
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        ImplementationView implementation = designerImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(implementation, List.of(), List.of()));
        List<ArtifactWrite> newCompilerFiles = List.of(
            new ArtifactWrite("SQL", modelPath, "3".repeat(64), "select new_value as order_id\n", "MODEL", "table", null),
            new ArtifactWrite("SCHEMA", schemaPath, "4".repeat(64), "version: 2\nmodels:\n  - name: orders\n", "MODEL", "table", null)
        );
        when(visualCompiler.compile(eq(TENANT), any(ModelSpecView.class), any(ImplementationView.class)))
            .thenReturn(newCompilerFiles);
        when(repository.replaceAuthoringContent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Optional.of(current));

        service.saveAuthoring(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            "authoring-etag",
            versionedVisualSnapshot(),
            projection,
            submitted,
            true
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FileInput>> savedFiles = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceAuthoringContent(
            eq(TENANT),
            eq(MODEL_ID),
            eq(DRAFT_ID),
            eq(ACTOR),
            eq("authoring-etag"),
            any(),
            any(),
            any(),
            savedFiles.capture(),
            any()
        );
        assertThat(savedFiles.getValue())
            .filteredOn(file -> modelPath.equals(file.path()))
            .singleElement()
            .extracting(FileInput::content)
            .isEqualTo("select new_value as order_id\n");
        assertThat(savedFiles.getValue())
            .filteredOn(file -> schemaPath.equals(file.path()))
            .singleElement()
            .extracting(FileInput::content)
            .isEqualTo("version: 2\nmodels:\n  - name: orders\n");
        assertThat(savedFiles.getValue())
            .filteredOn(file -> unmanagedPath.equals(file.path()))
            .singleElement()
            .extracting(FileInput::content)
            .isEqualTo("Keep this hand-authored note.\n");
    }

    @Test
    void visualSaveUpgradesAProvenLegacySchemaAtTheCurrentVersionPath() throws Exception {
        ModelLifecycleCompilerPort visualCompiler = org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class);
        ReflectionTestUtils.setField(service, "visualCompiler", visualCompiler);
        String projectFile = "name: sprint83\nmodel-paths: [models]\n";
        String previousSchemaPath = "models/dwd/orders/v2/i1/orders.yml";
        String currentSchemaPath = "models/dwd/orders/v3/i2/orders.yml";
        String frozenSchema = """
            version: 2
            models:
              - name: orders
                columns:
                  - name: order_id
                    description: "模型字段"
                    data_type: text
                    meta:
                      dts_logical_data_type: "string"
            """;
        String workingSchema = """
            version: 2
            models:
              - name: orders
                columns:
                  - name: order_id
                    description: "模型字段"
                    data_type: text
                    meta:
                      dts_logical_data_type: "text"
            """;
        String expectedSchema = """
            version: 2
            models:
              - name: orders
                columns:
                  - name: order_id
                    data_type: text
                    meta:
                      dts_logical_data_type: "text"
            """;
        String notesPath = "models/dwd/orders/v3/i2/business_notes.md";
        List<FileInput> frozenFiles = List.of(
            new FileInput("dbt_project.yml", projectFile),
            new FileInput(previousSchemaPath, frozenSchema)
        );
        List<FileInput> submitted = List.of(
            new FileInput("dbt_project.yml", projectFile),
            new FileInput(previousSchemaPath, frozenSchema),
            new FileInput(currentSchemaPath, workingSchema),
            new FileInput(notesPath, "Keep this hand-authored note.\n")
        );
        SourceBundleView canonical = sourceBundle("sprint83", frozenFiles);
        SourceBundleView source = new SourceBundleView(
            canonical.projectKey(), canonical.projectChecksum(), canonical.bundleChecksum(),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE, true, canonical.files()
        );
        var projection = objectMapper.createObjectNode();
        projection.putArray("managedPaths");
        DraftRow current = authoringRowWithSource(
            objectMapper.writeValueAsString(versionedVisualSnapshot()),
            objectMapper.writeValueAsString(source),
            projection.toString()
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(current));
        when(repository.listFiles(DRAFT_ID)).thenReturn(submitted.stream().map(file -> file(file.path(), file.content())).toList());
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        ImplementationView implementation = dbtManagedGeneratedImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(implementation, List.of(), List.of()));
        when(visualCompiler.compile(eq(TENANT), any(ModelSpecView.class), any(ImplementationView.class)))
            .thenReturn(List.of(new ArtifactWrite("SCHEMA", currentSchemaPath, "5".repeat(64), expectedSchema, "MODEL", "table", null)));
        when(repository.replaceAuthoringContent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Optional.of(current));

        service.saveAuthoring(TENANT, ACTOR, MODEL_ID, DRAFT_ID, "authoring-etag", versionedVisualSnapshot(), projection, submitted, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FileInput>> savedFiles = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceAuthoringContent(any(), any(), any(), any(), any(), any(), any(), any(), savedFiles.capture(), any());
        assertThat(savedFiles.getValue()).filteredOn(file -> currentSchemaPath.equals(file.path())).singleElement()
            .extracting(FileInput::content).isEqualTo(expectedSchema);
        assertThat(savedFiles.getValue()).filteredOn(file -> notesPath.equals(file.path())).singleElement()
            .extracting(FileInput::content).isEqualTo("Keep this hand-authored note.\n");
    }

    @Test
    void visualSaveRejectsAChangedCurrentLegacySchemaDespiteFrozenEvidence() throws Exception {
        ModelLifecycleCompilerPort visualCompiler = org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class);
        ReflectionTestUtils.setField(service, "visualCompiler", visualCompiler);
        String previousSchemaPath = "models/dwd/orders/v2/i1/orders.yml";
        String currentSchemaPath = "models/dwd/orders/v3/i2/orders.yml";
        String frozenSchema = "      - name: order_id\n        description: \"模型字段\"\n        data_type: text\n";
        String changedSchema = frozenSchema + "        # user extension\n";
        String typeChangedSchema = "      - name: order_id\n        description: \"模型字段\"\n        data_type: bigint\n";
        List<FileInput> frozenFiles = List.of(new FileInput("dbt_project.yml", "name: sprint83\n"), new FileInput(previousSchemaPath, frozenSchema));
        List<FileInput> submitted = List.of(
            new FileInput("dbt_project.yml", "name: sprint83\n"),
            new FileInput(previousSchemaPath, frozenSchema),
            new FileInput(currentSchemaPath, changedSchema)
        );
        List<FileInput> typeChanged = List.of(
            new FileInput("dbt_project.yml", "name: sprint83\n"),
            new FileInput(previousSchemaPath, frozenSchema),
            new FileInput(currentSchemaPath, typeChangedSchema)
        );
        SourceBundleView canonical = sourceBundle("sprint83", frozenFiles);
        SourceBundleView frozen = new SourceBundleView(
            canonical.projectKey(), canonical.projectChecksum(), canonical.bundleChecksum(),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE, true, canonical.files()
        );
        DraftRow current = authoringRowWithSource(
            objectMapper.writeValueAsString(versionedVisualSnapshot()),
            objectMapper.writeValueAsString(frozen),
            "{\"managedPaths\":[]}"
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(current));
        when(repository.listFiles(DRAFT_ID)).thenReturn(submitted.stream().map(file -> file(file.path(), file.content())).toList());
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        ImplementationView implementation = dbtManagedGeneratedImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(implementation, List.of(), List.of()));
        when(visualCompiler.compile(eq(TENANT), any(ModelSpecView.class), any(ImplementationView.class)))
            .thenReturn(List.of(new ArtifactWrite("SCHEMA", currentSchemaPath, "6".repeat(64), "      - name: order_id\n        data_type: text\n", "MODEL", "table", null)));

        DraftException failure = assertFailure(
            ErrorKind.CONFLICT,
            () -> service.saveAuthoring(TENANT, ACTOR, MODEL_ID, DRAFT_ID, "authoring-etag", versionedVisualSnapshot(), objectMapper.createObjectNode().putArray("managedPaths"), submitted, true)
        );

        assertThat(failure.code()).isEqualTo("MODEL_AUTHORING_UNMANAGED_FILE_CHANGED");
        when(repository.listFiles(DRAFT_ID)).thenReturn(typeChanged.stream().map(file -> file(file.path(), file.content())).toList());
        DraftException typeFailure = assertFailure(
            ErrorKind.CONFLICT,
            () -> service.saveAuthoring(TENANT, ACTOR, MODEL_ID, DRAFT_ID, "authoring-etag", versionedVisualSnapshot(), objectMapper.createObjectNode().putArray("managedPaths"), typeChanged, true)
        );
        assertThat(typeFailure.code()).isEqualTo("MODEL_AUTHORING_UNMANAGED_FILE_CHANGED");

        List<FileInput> matchingLegacy = List.of(
            new FileInput("dbt_project.yml", "name: sprint83\n"),
            new FileInput(previousSchemaPath, frozenSchema),
            new FileInput(currentSchemaPath, frozenSchema)
        );
        when(repository.listFiles(DRAFT_ID)).thenReturn(matchingLegacy.stream().map(file -> file(file.path(), file.content())).toList());
        ImplementationView physicalImplementation = dbtManagedPhysicalImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(physicalImplementation, List.of(), List.of()));
        DraftException nonGeneratedFailure = assertFailure(
            ErrorKind.CONFLICT,
            () -> service.saveAuthoring(TENANT, ACTOR, MODEL_ID, DRAFT_ID, "authoring-etag", versionedVisualSnapshot(), objectMapper.createObjectNode().putArray("managedPaths"), matchingLegacy, true)
        );
        assertThat(nonGeneratedFailure.code()).isEqualTo("MODEL_AUTHORING_UNMANAGED_FILE_CHANGED");

        ImplementationView mismatched = dbtManagedGeneratedImplementation();
        when(mismatched.implementationRevision()).thenReturn(3);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(mismatched, List.of(), List.of()));
        DraftException pinFailure = assertFailure(
            ErrorKind.CONFLICT,
            () -> service.saveAuthoring(TENANT, ACTOR, MODEL_ID, DRAFT_ID, "authoring-etag", versionedVisualSnapshot(), objectMapper.createObjectNode().putArray("managedPaths"), matchingLegacy, true)
        );
        assertThat(pinFailure.code()).isEqualTo("DBT_DRAFT_BASE_IMPLEMENTATION_CONFLICT");
        verify(repository, never()).replaceAuthoringContent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void validationRetiresOnlyUnchangedHistoricalCompilerSiblings() throws Exception {
        ModelLifecycleCompilerPort visualCompiler = org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class);
        ReflectionTestUtils.setField(service, "visualCompiler", visualCompiler);
        String projectFile = "name: sprint83\nversion: '1.0'\nconfig-version: 2\nmodel-paths: [models]\n";
        String stalePath = "models/dwd/orders/v2/i1/stg_orders.sql";
        String currentStgPath = "models/dwd/orders/v3/i2/stg_orders.sql";
        String currentModelPath = "models/dwd/orders/v3/i2/orders.sql";
        String customPath = "models/dwd/orders/v2/i1/custom_business_rule.sql";
        List<FileInput> working = List.of(
            new FileInput("dbt_project.yml", projectFile),
            new FileInput(stalePath, "select old_value as record_id\n"),
            new FileInput(currentStgPath, "select current_value as record_id\n"),
            new FileInput(currentModelPath, "select record_id from {{ ref('stg_orders') }}\n"),
            new FileInput(customPath, "select 'keep me' as note\n")
        );
        SourceBundleView canonical = sourceBundle("sprint83", working);
        SourceBundleView frozen = new SourceBundleView(
            canonical.projectKey(),
            canonical.projectChecksum(),
            canonical.bundleChecksum(),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            true,
            canonical.files()
        );
        DraftRow current = authoringRowWithSource(
            objectMapper.writeValueAsString(versionedVisualSnapshot()),
            objectMapper.writeValueAsString(frozen),
            "{}"
        );
        DraftRow repaired = copyDraft(current, DraftState.DRAFT, "repair-etag");
        List<FileRow> beforeRepair = working.stream().map(file -> file(file.path(), file.content())).toList();
        List<FileRow> afterRepair = beforeRepair.stream().filter(file -> !stalePath.equals(file.path())).toList();
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(current));
        when(repository.listFiles(DRAFT_ID)).thenReturn(beforeRepair, afterRepair);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        ImplementationView implementation = baseImplementation();
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(implementation, List.of(), List.of()));
        when(visualCompiler.compile(eq(TENANT), any(ModelSpecView.class), any(ImplementationView.class)))
            .thenReturn(List.of(
                new ArtifactWrite("SQL", currentStgPath, "1".repeat(64), "select current_value as record_id\n", "MODEL", "ephemeral", null),
                new ArtifactWrite("SQL", currentModelPath, "2".repeat(64), "select record_id from {{ ref('stg_orders') }}\n", "MODEL", "table", null)
            ));
        when(repository.replaceFiles(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Optional.of(repaired));
        ValidatedProject validatedProject = project();
        when(validator.validate(any(Map.class))).thenReturn(validatedProject);
        when(repository.markValidated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Optional.of(copyDraft(repaired, DraftState.VALIDATED, "validated-etag")));

        service.validate(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            new ValidateDraftRequest("authoring-etag")
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FileInput>> repairedFiles = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceFiles(
            eq(TENANT),
            eq(MODEL_ID),
            eq(DRAFT_ID),
            eq(ACTOR),
            eq("authoring-etag"),
            any(),
            repairedFiles.capture(),
            eq(NOW)
        );
        assertThat(repairedFiles.getValue()).extracting(FileInput::path)
            .doesNotContain(stalePath)
            .contains(currentStgPath, currentModelPath, customPath);
        verify(repository).markValidated(
            eq(TENANT),
            eq(MODEL_ID),
            eq(DRAFT_ID),
            eq(ACTOR),
            eq("repair-etag"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(NOW)
        );
    }

    @Test
    void visualSaveRejectsAnyMutationOfAnUnmanagedBundleFileBeforeCompilation() throws Exception {
        String managedPath = "models/dwd/orders/v3/i2/orders.sql";
        String unmanagedPath = "macros/custom_business_rule.sql";
        List<FileInput> persisted = List.of(
            new FileInput("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            new FileInput(managedPath, "select old_value as order_id\n"),
            new FileInput(unmanagedPath, "{% macro custom_business_rule() %}1{% endmacro %}\n")
        );
        SourceBundleView source = sourceBundle("sprint83", persisted);
        var projection = objectMapper.createObjectNode();
        projection.putArray("managedPaths").add(managedPath);
        DraftRow current = authoringRowWithSource(
            objectMapper.writeValueAsString(versionedVisualSnapshot()),
            objectMapper.writeValueAsString(source),
            objectMapper.writeValueAsString(projection)
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(current));
        when(repository.listFiles(DRAFT_ID)).thenReturn(
            persisted.stream().map(file -> file(file.path(), file.content())).toList()
        );
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        List<FileInput> changed = persisted.stream()
            .map(file -> unmanagedPath.equals(file.path())
                ? new FileInput(file.path(), "{% macro custom_business_rule() %}2{% endmacro %}\n")
                : file)
            .toList();

        DraftException failure = assertFailure(
            ErrorKind.CONFLICT,
            () -> service.saveAuthoring(
                TENANT,
                ACTOR,
                MODEL_ID,
                DRAFT_ID,
                "authoring-etag",
                versionedVisualSnapshot(),
                projection,
                changed,
                true
            )
        );

        assertThat(failure.code()).isEqualTo("MODEL_AUTHORING_UNMANAGED_FILE_CHANGED");
        verify(repository, never()).replaceAuthoringContent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reopeningAuthoringReturnsTheLatestSavedWorkingFiles() throws Exception {
        List<FileInput> frozen = List.of(
            new FileInput("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            new FileInput("models/orders.sql", "select old_value as order_id\n")
        );
        SourceBundleView source = sourceBundle("sprint83", frozen);
        DraftRow current = authoringRowWithSource(
            objectMapper.writeValueAsString(modelUpdateSnapshot()),
            objectMapper.writeValueAsString(source),
            "{}"
        );
        when(repository.findOpenForActor(TENANT, MODEL_ID, ACTOR, NOW)).thenReturn(Optional.of(current));
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(repository.listFiles(DRAFT_ID)).thenReturn(
            List.of(
                file("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
                file("models/orders.sql", "select new_value as order_id\n")
            )
        );

        var reopened = service.findOpenAuthoring(TENANT, ACTOR, MODEL_ID).orElseThrow();

        assertThat(reopened.sourceBundle().files())
            .filteredOn(file -> "models/orders.sql".equals(file.path()))
            .singleElement()
            .extracting(DbtImplementationDraftContract.BundleFileView::content)
            .isEqualTo("select new_value as order_id\n");
    }

    @Test
    void legacyDbtDraftWithoutAnAuthoringSnapshotDoesNotHijackTheUnifiedSession() {
        DraftRow legacy = row(DraftState.DRAFT, "legacy-etag", NOW.plusSeconds(3600), null, null, null, null, null, null);
        when(repository.findOpenForActor(TENANT, MODEL_ID, ACTOR, NOW)).thenReturn(Optional.of(legacy));
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);

        assertThat(service.findOpenAuthoring(TENANT, ACTOR, MODEL_ID)).isEmpty();
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
    void rebasesThePriorImplementationPinWhenCreatingAnAuthoringDraftForAnAdvancedModelRevision() {
        String advancedModelChecksum = "f".repeat(64);
        List<FileRow> files = List.of(
            file("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            file("models/orders.sql", "select 1\n"),
            file("models/schema.yml", "version: 2\nmodels: []\n")
        );
        BundleSnapshot bundle = bundle(files, project());
        ModelSpecView model = model(4, advancedModelChecksum);
        when(model.id()).thenReturn(MODEL_ID);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        ImplementationView implementation = baseImplementation();
        when(implementation.status()).thenReturn("ACTIVE");
        when(implementation.inputMode()).thenReturn(InputMode.GENERATED);
        when(implementation.inputs()).thenReturn(List.of(new GeneratedInput("DBT", Map.of())));
        when(implementation.fieldMappings()).thenReturn(List.of());
        when(implementation.settings()).thenReturn(Map.of());
        when(implementation.materialization()).thenReturn("table");
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(implementation);
        when(representationEvidence.findExact(TENANT, MODEL_ID, 3, MODEL_CHECKSUM, 2, true))
            .thenReturn(Optional.of(representation(bundle)));
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));
        ModelImplementationDependencyReadPort dependencyFacts = org.mockito.Mockito.mock(
            ModelImplementationDependencyReadPort.class
        );
        when(dependencyFacts.readFacts(TENANT, model)).thenReturn(new DependencyFacts(List.of(), List.of()));
        DbtImplementationDraftService dependencyAwareService = new DbtImplementationDraftService(
            repository,
            modelSpecs,
            lifecycle,
            writeAccess,
            validator,
            artifactImports,
            representationEvidence,
            audit,
            objectMapper,
            new ModelImplementationDependencyService(dependencyFacts),
            org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var created = dependencyAwareService.createAuthoring(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(PLAN_ID, 4, advancedModelChecksum, 2, IMPLEMENTATION_CHECKSUM, "advance-authoring-92"),
            new AuthoringSeed(
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                AuthoringOrigin.UNKNOWN,
                "e".repeat(64)
            )
        );

        assertThat(created.baseModelRevision()).isEqualTo(4);
        assertThat(created.baseImplementationRevision()).isEqualTo(2);
        assertThat(created.sourceBundle().dependencySnapshot().modelRevision()).isEqualTo(4);
        assertThat(created.sourceBundle().dependencySnapshot().modelChecksum()).isEqualTo(advancedModelChecksum);
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
        String targetPhysicalName = "biz_dwd_budget_account_v2";
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.name()).thenReturn("预算科目");
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(null);
        when(validator.validate(any())).thenReturn(initialProject(targetPhysicalName));
        when(repository.create(any())).thenAnswer(invocation -> draft(invocation.getArgument(0)));

        var created = service.create(
            TENANT,
            ACTOR,
            MODEL_ID,
            new CreateDraftRequest(
                PLAN_ID,
                3,
                MODEL_CHECKSUM,
                null,
                null,
                targetPhysicalName,
                "create-first-83"
            )
        );

        assertThat(created.sourceBundle()).isNotNull();
        assertThat(created.sourceBundle().sourceKind()).isEqualTo(SourceBundleKind.CANONICAL_INITIALIZATION);
        assertThat(created.sourceBundle().lossless()).isFalse();
        assertThat(created.sourceBundle().files())
            .extracting(DbtImplementationDraftContract.BundleFileView::path)
            .contains("dbt_project.yml", "models/" + targetPhysicalName + ".sql");
    }

    @Test
    void rejectsTheFirstDbtDraftWithoutAnExplicitTargetPhysicalName() {
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        TimelineView timeline = org.mockito.Mockito.mock(TimelineView.class);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(timeline);
        when(timeline.implementation()).thenReturn(null);

        DraftException failure = assertFailure(
            ErrorKind.BAD_REQUEST,
            () ->
                service.create(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new CreateDraftRequest(PLAN_ID, 3, MODEL_CHECKSUM, null, null, "create-first-without-target-83")
                )
        );

        assertThat(failure.code()).isEqualTo("DBT_DRAFT_TARGET_PHYSICAL_NAME_REQUIRED");
        verify(repository, never()).create(any());
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
    void commitsTheFrozenBundleAsAnImmutableConfigArtifactAndBindsItIntoImplementationIdempotency() throws Exception {
        String targetSql = "select * from {{ ref('stg_orders') }}\n";
        String stagingSql = "{{ config(materialized='ephemeral') }}\nselect 1 as order_id\n";
        List<FileRow> files = List.of(
            file("dbt_project.yml", "name: sprint83\nmodel-paths: [models]\n"),
            file("models/orders.sql", targetSql),
            file("models/stg_orders.sql", stagingSql)
        );
        ValidatedProject project = projectWithGeneratedStaging(targetSql);
        BundleSnapshot bundle = bundle(files, project);
        String dependencyChecksum = "9".repeat(64);
        Snapshot dependencySnapshot = new Snapshot(
            MODEL_ID,
            3,
            MODEL_CHECKSUM,
            1,
            IMPLEMENTATION_CHECKSUM,
            List.of(),
            List.of(),
            dependencyChecksum
        );
        SourceBundleView sourceBundle = new SourceBundleView(
            "sprint83",
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            true,
            files
                .stream()
                .map(file ->
                    new DbtImplementationDraftContract.BundleFileView(
                        file.path(),
                        file.content(),
                        file.checksum(),
                        file.byteSize()
                    )
                )
                .toList(),
            dependencyChecksum,
            dependencySnapshot,
            Map.of()
        );
        String sourceBundleSnapshot = objectMapper.writeValueAsString(sourceBundle);
        String derivedKey = commitKey(
            "commit-83",
            VALIDATED_CHECKSUM,
            bundle.bundleChecksum(),
            dependencyChecksum
        );
        DraftRow validated = withSourceBundle(
            row(
                DraftState.VALIDATED,
                "etag",
                NOW.plusSeconds(60),
                VALIDATED_CHECKSUM,
                bundle.projectChecksum(),
                bundle.bundleChecksum(),
                bundle.manifest(),
                null,
                null
            ),
            sourceBundleSnapshot
        );
        DraftRow claimed = withSourceBundle(
            row(
                DraftState.COMMITTING,
                "claimed-etag",
                NOW.plusSeconds(60),
                VALIDATED_CHECKSUM,
                bundle.projectChecksum(),
                bundle.bundleChecksum(),
                bundle.manifest(),
                derivedKey,
                null
            ),
            sourceBundleSnapshot
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
        String synchronizedChecksum = "e".repeat(64);
        ModelSpecView synchronizedModel = model(4, synchronizedChecksum);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(modelSpecs.synchronizeDbtManagedFields(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), any()))
            .thenReturn(synchronizedModel);
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
        when(importResult.artifactCount()).thenReturn(4);
        when(artifactImports.importArtifacts(any())).thenReturn(importResult);
        when(repository.completeCommit(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), anyInt(), any()))
            .thenReturn(committed);
        ModelImplementationDependencyService dependencyService = org.mockito.Mockito.mock(
            ModelImplementationDependencyService.class
        );
        when(dependencyService.resolveForDraft(any(), any(), any(), any(), any()))
            .thenReturn(new Resolution(dependencySnapshot, Map.of()));
        when(dependencyService.reconcile(any(), any())).thenReturn(new Reconciliation(List.of(), List.of(), List.of()));
        DbtImplementationDraftService dependencyAwareService = new DbtImplementationDraftService(
            repository,
            modelSpecs,
            lifecycle,
            writeAccess,
            validator,
            artifactImports,
            representationEvidence,
            audit,
            objectMapper,
            dependencyService,
            org.mockito.Mockito.mock(ModelLifecycleCompilerPort.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        CommitView receipt = dependencyAwareService.commit(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            new CommitDraftRequest("etag", VALIDATED_CHECKSUM, dependencyChecksum, "commit-83")
        );

        assertThat(receipt.modelRevision()).isEqualTo(4);
        assertThat(receipt.modelChecksum()).isEqualTo(synchronizedChecksum);

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
        assertThat(generated.config().get("dependencySnapshot"))
            .isInstanceOf(Map.class)
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("modelSpecId", MODEL_ID.toString())
            .containsEntry("dependencyChecksum", dependencyChecksum);

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
        assertThat(importCommand.getValue().artifacts())
            .filteredOn(artifact -> artifact.nodeKind() == NodeKind.EPHEMERAL)
            .singleElement()
            .satisfies(artifact -> {
                assertThat(artifact.artifactType()).isEqualTo(ArtifactType.SQL);
                assertThat(artifact.path()).isEqualTo("models/stg_orders.sql");
                assertThat(artifact.content()).isEqualTo(stagingSql);
            });
    }

    @Test
    void commitsUnifiedAuthoringSnapshotAndProjectedFieldsThroughOneModelRevisionBoundary() throws Exception {
        List<FileRow> files = files();
        ValidatedProject project = project();
        BundleSnapshot bundle = bundle(files, project);
        String derivedKey = commitKey("authoring-commit-83", VALIDATED_CHECKSUM, bundle.bundleChecksum());
        var authoringSnapshot = versionedVisualSnapshot();
        authoringSnapshot
            .withObject("/visualImplementation/settings")
            .put("targetPhysicalName", "biz_dwd_orders");
        DraftRow validated = authoringRow(
            DraftState.VALIDATED,
            "authoring-etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            bundle.manifest(),
            null,
            null,
            objectMapper.writeValueAsString(authoringSnapshot)
        );
        DraftRow claimed = authoringRow(
            DraftState.COMMITTING,
            "authoring-claimed-etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            bundle.manifest(),
            derivedKey,
            null,
            validated.modelSpecSnapshot()
        );
        DraftRow committed = authoringRow(
            DraftState.COMMITTED,
            "authoring-committed-etag",
            NOW.plusSeconds(60),
            VALIDATED_CHECKSUM,
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            bundle.manifest(),
            derivedKey,
            IMPLEMENTATION_ID,
            validated.modelSpecSnapshot()
        );
        when(repository.findForActor(TENANT, MODEL_ID, DRAFT_ID, ACTOR)).thenReturn(Optional.of(validated));
        when(repository.listFiles(DRAFT_ID)).thenReturn(files);
        when(validator.validate(any())).thenReturn(project);
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        ModelSpecView synchronizedModel = model(4, "e".repeat(64));
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(modelSpecs.synchronizeAuthoringDraft(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), any()))
            .thenReturn(synchronizedModel);
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

        CommitView receipt = service.commit(
            TENANT,
            ACTOR,
            MODEL_ID,
            DRAFT_ID,
            new CommitDraftRequest("authoring-etag", VALIDATED_CHECKSUM, "authoring-commit-83")
        );

        assertThat(receipt.modelRevision()).isEqualTo(4);
        ArgumentCaptor<UpdateModelSpecCommand> synchronizedCommand = ArgumentCaptor.forClass(UpdateModelSpecCommand.class);
        verify(modelSpecs).synchronizeAuthoringDraft(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), synchronizedCommand.capture());
        assertThat(synchronizedCommand.getValue().fields())
            .extracting(ModelField::name)
            .containsExactly("order_id");
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
        GeneratedInput generated = (GeneratedInput) implementationCommand.getValue().inputs().getFirst();
        assertThat(generated.config()).containsKey("visualImplementation");
        assertThat(implementationCommand.getValue().settings())
            .containsEntry("targetPhysicalName", "biz_dwd_orders");
        verify(modelSpecs, never()).synchronizeDbtManagedFields(any(), any(), any(), any(), any());
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

    @Test
    void firstVisualImplementationResolvesDependenciesWithoutPersistingAnImplementation() {
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.id()).thenReturn(MODEL_ID);
        var decoded = new com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringSnapshotDecoder(objectMapper)
            .decode(versionedVisualSnapshot());
        assertThat(decoded.valid()).isTrue();
        ImplementationView candidate = ReflectionTestUtils.invokeMethod(
            service, "visualImplementation", MODEL_ID, model, null, decoded.visualImplementation()
        );
        var resolver = new com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver();

        Snapshot resolved = resolver.resolve(model, candidate, "sprint83", new DependencyFacts(List.of(), List.of()));

        assertThat(resolved.modelSpecId()).isEqualTo(MODEL_ID);
        assertThat(resolved.implementationRevision()).isEqualTo(1);
        assertThat(model.status()).isEqualTo(ModelStatus.DRAFT);
        org.mockito.Mockito.verifyNoInteractions(repository, lifecycle);
    }

    @Test
    void visualCompilationDoesNotReactivateAnExistingStaleImplementation() {
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.id()).thenReturn(MODEL_ID);
        ImplementationView current = designerImplementation();
        when(current.status()).thenReturn("STALE");
        var decoded = new com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringSnapshotDecoder(objectMapper)
            .decode(versionedVisualSnapshot());
        ImplementationView candidate = ReflectionTestUtils.invokeMethod(
            service, "visualImplementation", MODEL_ID, model, current, decoded.visualImplementation()
        );
        var resolver = new com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver();

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> resolver.resolve(model, candidate, "sprint83", new DependencyFacts(List.of(), List.of()))
        ).isInstanceOf(com.yuzhi.dts.platform.service.modeling.ModelSpecException.class)
            .hasMessage("The owning implementation pin is unavailable");
    }

    @Test
    void replacesOnlyUneditedCanonicalInitializationSqlWhenCompiledSqlIsAvailable() throws Exception {
        String initialPath = "models/orders.sql";
        String compiledPath = "models/dwd/orders/v3/i1/orders.sql";
        String placeholder = "select 1 as _dts_placeholder where 1 = 0\n";
        SourceBundleView canonical = sourceBundle("sprint83", List.of(new FileInput(initialPath, placeholder)));
        for (SourceBundleKind kind : SourceBundleKind.values()) {
            DraftRow draft = org.mockito.Mockito.mock(DraftRow.class);
            when(draft.sourceBundleSnapshot()).thenReturn(objectMapper.writeValueAsString(new SourceBundleView(
                canonical.projectKey(), canonical.projectChecksum(), canonical.bundleChecksum(), kind, false, canonical.files()
            )));
            java.util.Set<String> owned = new java.util.LinkedHashSet<>();
            Map<String, FileInput> working = Map.of(initialPath, new FileInput(initialPath, placeholder));
            Map<String, String> replacements = Map.of(compiledPath, "select order_id from orders\n");
            ReflectionTestUtils.invokeMethod(service, "addMatchingInitializationFiles", owned, working, draft, replacements);
            if (kind == SourceBundleKind.CANONICAL_INITIALIZATION) {
                assertThat(owned).containsExactly(initialPath);
            } else {
                assertThat(owned).isEmpty();
            }
            owned.clear();
            ReflectionTestUtils.invokeMethod(service, "addMatchingInitializationFiles", owned,
                Map.of(initialPath, new FileInput(initialPath, "select hand_written_rule from orders\n")), draft, replacements);
            assertThat(owned).isEmpty();
            ReflectionTestUtils.invokeMethod(service, "addMatchingInitializationFiles", owned, working, draft, Map.of());
            assertThat(owned).isEmpty();
        }
    }

    @Test
    void validatesNewSourcesFromSavedAuthoringWhileRejectingUndeclaredSql() throws Exception {
        UUID bindingId = UUID.fromString("50000000-0000-0000-0000-000000000083");
        ModelSpecView model = model(3, MODEL_CHECKSUM);
        when(model.id()).thenReturn(MODEL_ID);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(lifecycle.timeline(TENANT, MODEL_ID)).thenReturn(new TimelineView(null, List.of(), List.of()));
        ModelImplementationDependencyReadPort facts = org.mockito.Mockito.mock(ModelImplementationDependencyReadPort.class);
        when(facts.readFacts(eq(TENANT), any())).thenReturn(new DependencyFacts(List.of(), List.of(
            new com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact(
                bindingId, "source-v1", true, "CONNECTION_TABLE", "public.orders"
            )
        )));
        ModelImplementationDependencyService dependencyService = new ModelImplementationDependencyService(facts);
        ReflectionTestUtils.setField(service, "dependencies", dependencyService);
        Snapshot base = dependencyService.resolveForDraft(TENANT, model, null, "sprint83", "orders").snapshot();
        assertThat(base.physicalSources()).isEmpty();
        var snapshot = objectMapper.valueToTree(modelUpdateSnapshot());
        ((com.fasterxml.jackson.databind.node.ObjectNode) snapshot).putArray("sourceRefs").addObject()
            .put("kind", "TABLE").put("role", "PRIMARY").put("layer", "ODS").put("ref", "public.orders")
            .put("sourceBindingId", bindingId.toString()).put("resolvedVersion", "source-v1");
        DraftRow draft = org.mockito.Mockito.mock(DraftRow.class);
        when(draft.planId()).thenReturn(PLAN_ID);
        when(draft.baseModelRevision()).thenReturn(3);
        when(draft.baseModelChecksum()).thenReturn(MODEL_CHECKSUM);
        when(draft.modelSpecSnapshot()).thenReturn(objectMapper.writeValueAsString(snapshot));
        when(draft.sourceBundleSnapshot()).thenReturn(objectMapper.writeValueAsString(new SourceBundleView(
            "sprint83", PROJECT_CHECKSUM, PROJECT_CHECKSUM, SourceBundleKind.CANONICAL_INITIALIZATION,
            false, List.of(), base.dependencyChecksum(), base, Map.of()
        )));
        ValidatedProject project = new AdvancedDbtDraftStaticValidator().validate(Map.of(
            "dbt_project.yml", "name: sprint83\nversion: 1.0\nmodel-paths: [models]\n",
            "models/orders.sql", "{{ config(materialized='table') }}\nselect * from {{ source('public', 'orders') }}\n"
        ));
        DbtImplementationDraftContract.DependencyValidationView result = ReflectionTestUtils.invokeMethod(
            service, "validateDependencies", TENANT, ACTOR, MODEL_ID, draft, project
        );
        assertThat(result.matched()).hasSize(1);
        assertThat(result.undeclared()).isEmpty();
        assertThat(result.dependencyChecksum()).isNotEqualTo(base.dependencyChecksum());

        ValidatedProject undeclared = new AdvancedDbtDraftStaticValidator().validate(Map.of(
            "dbt_project.yml", "name: sprint83\nversion: 1.0\nmodel-paths: [models]\n",
            "models/orders.sql", "{{ config(materialized='table') }}\nselect * from {{ source('public', 'other_table') }}\n"
        ));
        DraftException failure = catchThrowableOfType(
            () -> ReflectionTestUtils.invokeMethod(service, "validateDependencies", TENANT, ACTOR, MODEL_ID, draft, undeclared),
            DraftException.class
        );
        assertThat(failure.code()).isEqualTo("DBT_DRAFT_DEPENDENCY_UNDECLARED");
        when(draft.baseModelChecksum()).thenReturn("0".repeat(64));
        DraftException stale = catchThrowableOfType(
            () -> ReflectionTestUtils.invokeMethod(service, "validateDependencies", TENANT, ACTOR, MODEL_ID, draft, project),
            DraftException.class
        );
        assertThat(stale.code()).isEqualTo("DBT_DRAFT_BASE_MODEL_CONFLICT");
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

    private SourceBundleView sourceBundle(String projectKey, List<FileInput> files) {
        return new SourceBundleView(
            projectKey,
            PROJECT_CHECKSUM,
            "e".repeat(64),
            SourceBundleKind.CANONICAL_ARTIFACT_RECONSTRUCTION,
            false,
            files
                .stream()
                .sorted(java.util.Comparator.comparing(FileInput::path))
                .map(file -> {
                    byte[] bytes = file.content().getBytes(StandardCharsets.UTF_8);
                    return new DbtImplementationDraftContract.BundleFileView(
                        file.path(),
                        file.content(),
                        ModelPackageChecksum.sha256(bytes),
                        bytes.length
                    );
                })
                .toList()
        );
    }

    private com.fasterxml.jackson.databind.node.ObjectNode versionedVisualSnapshot() {
        var root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.set("modelSpec", objectMapper.valueToTree(modelUpdateSnapshot()));
        var visual = root.putObject("visualImplementation");
        visual.put("projectKey", "sprint83");
        visual.put("dbtUniqueId", "model.sprint83.orders");
        visual.put("inputMode", "GENERATED");
        var generated = visual.putArray("inputs").addObject();
        generated.put("generatorType", "DATE_DIMENSION_GENERATOR");
        generated.putObject("config").put("startYear", 2025).put("endYear", 2026);
        visual.putArray("fieldMappings");
        visual.putObject("settings");
        visual.put("ownership", "DESIGNER_GENERATED");
        visual.put("materialization", "table");
        visual.put("idempotencyKey", "visual-save-83");
        return root;
    }

    private ImplementationView designerImplementation() {
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        when(implementation.id()).thenReturn(IMPLEMENTATION_ID);
        when(implementation.modelSpecId()).thenReturn(MODEL_ID);
        when(implementation.planId()).thenReturn(PLAN_ID);
        when(implementation.revision()).thenReturn(3);
        when(implementation.modelChecksum()).thenReturn(MODEL_CHECKSUM);
        when(implementation.ownership()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(implementation.projectKey()).thenReturn("sprint83");
        when(implementation.dbtUniqueId()).thenReturn("model.sprint83.orders");
        when(implementation.status()).thenReturn("ACTIVE");
        when(implementation.implementationRevision()).thenReturn(2);
        when(implementation.implementationChecksum()).thenReturn(IMPLEMENTATION_CHECKSUM);
        when(implementation.inputMode()).thenReturn(InputMode.GENERATED);
        when(implementation.materialization()).thenReturn("table");
        return implementation;
    }

    private ImplementationView dbtManagedGeneratedImplementation() {
        ImplementationView implementation = designerImplementation();
        when(implementation.ownership()).thenReturn(ImplementationMode.DBT_MANAGED);
        return implementation;
    }

    private ImplementationView dbtManagedPhysicalImplementation() {
        ImplementationView implementation = dbtManagedGeneratedImplementation();
        when(implementation.inputMode()).thenReturn(InputMode.PHYSICAL_ASSET);
        return implementation;
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
            draft.modelSpecSnapshot(),
            draft.projectionSummary(),
            draft.authoringOrigin(),
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
        String schema =
            "{\"columns\":[{\"name\":\"order_id\",\"description\":\"Order identifier\",\"dataType\":\"bigint\",\"role\":\"KEY\",\"tests\":[\"not_null\"]}],\"tests\":[]}";
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

    private ValidatedProject projectWithGeneratedStaging(String targetSql) {
        ValidatedNode base = project().nodes().getFirst();
        ValidatedNode target = new ValidatedNode(
            base.dbtUniqueId(),
            base.name(),
            base.resourcePath(),
            base.materialization(),
            base.nodeKind(),
            targetSql,
            ModelPackageChecksum.sha256Text(targetSql),
            base.schema(),
            base.schemaChecksum(),
            List.of("source.sprint83.public.orders"),
            base.reasonCodes()
        );
        return new ValidatedProject(
            VALIDATED_CHECKSUM,
            PROJECT_CHECKSUM,
            "sprint83",
            List.of(target),
            List.of()
        );
    }

    private ValidatedProject initialProject(String name) {
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

    private static String commitKey(
        String idempotencyKey,
        String validatedChecksum,
        String bundleChecksum,
        String dependencyChecksum
    ) {
        return ModelPackageChecksum.sha256Text(
            String.join(
                "\u0000",
                "dbt-draft-commit-v2",
                idempotencyKey,
                validatedChecksum,
                bundleChecksum,
                dependencyChecksum
            )
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

    private static DraftRow withSourceBundle(DraftRow row, String sourceBundleSnapshot) {
        return new DraftRow(
            row.id(),
            row.tenantId(),
            row.planId(),
            row.modelSpecId(),
            row.actorId(),
            row.baseModelRevision(),
            row.baseModelChecksum(),
            row.baseImplementationRevision(),
            row.baseImplementationChecksum(),
            row.idempotencyKey(),
            row.requestHash(),
            sourceBundleSnapshot,
            row.modelSpecSnapshot(),
            row.projectionSummary(),
            row.authoringOrigin(),
            row.state(),
            row.etag(),
            row.expiresAt(),
            row.validatedChecksum(),
            row.projectChecksum(),
            row.bundleChecksum(),
            row.bundleManifest(),
            row.validationSummary(),
            row.commitIdempotencyKey(),
            row.implementationId(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.artifactCount(),
            row.committedAt(),
            row.createdAt(),
            row.updatedAt()
        );
    }

    private static DraftRow copyDraft(DraftRow row, DraftState state, String etag) {
        return new DraftRow(
            row.id(),
            row.tenantId(),
            row.planId(),
            row.modelSpecId(),
            row.actorId(),
            row.baseModelRevision(),
            row.baseModelChecksum(),
            row.baseImplementationRevision(),
            row.baseImplementationChecksum(),
            row.idempotencyKey(),
            row.requestHash(),
            row.sourceBundleSnapshot(),
            row.modelSpecSnapshot(),
            row.projectionSummary(),
            row.authoringOrigin(),
            state,
            etag,
            row.expiresAt(),
            row.validatedChecksum(),
            row.projectChecksum(),
            row.bundleChecksum(),
            row.bundleManifest(),
            row.validationSummary(),
            row.commitIdempotencyKey(),
            row.implementationId(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.artifactCount(),
            row.committedAt(),
            row.createdAt(),
            row.updatedAt()
        );
    }

    private static DraftRow authoringRow(
        DraftState state,
        String etag,
        Instant expiresAt,
        String validatedChecksum,
        String projectChecksum,
        String bundleChecksum,
        String bundleManifest,
        String commitKey,
        UUID implementationId,
        String modelSpecSnapshot
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
            "create-authoring-83",
            "f".repeat(64),
            null,
            modelSpecSnapshot,
            "{}",
            AuthoringOrigin.SYSTEM_GENERATED.name(),
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

    private static DraftRow authoringRowWithSource(
        String modelSpecSnapshot,
        String sourceBundleSnapshot,
        String projectionSummary
    ) {
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
            "create-authoring-83",
            "f".repeat(64),
            sourceBundleSnapshot,
            modelSpecSnapshot,
            projectionSummary,
            AuthoringOrigin.SYSTEM_GENERATED.name(),
            DraftState.DRAFT,
            "authoring-etag",
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

    private static UpdateModelSpecCommand modelUpdateSnapshot() {
        return new UpdateModelSpecCommand(
            PLAN_ID,
            UUID.fromString("50000000-0000-0000-0000-000000000083"),
            ModelType.DIMENSION,
            Layer.DWD,
            "orders",
            "Unified authoring model",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per order", List.of("record_id")),
            null,
            null,
            List.of(new ModelField("record_id", "bigint", false, "source.record_id", FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
