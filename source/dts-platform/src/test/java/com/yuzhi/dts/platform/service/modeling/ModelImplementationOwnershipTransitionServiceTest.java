package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleCommandReceiptRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelImplementationOwnershipTransitionServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "alice";
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000091");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000091");
    private static final UUID IMPLEMENTATION_ID = UUID.fromString("60000000-0000-0000-0000-000000000091");
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final String TARGET_MODEL_CHECKSUM = "c".repeat(64);
    private static final String TARGET_IMPLEMENTATION_CHECKSUM = "d".repeat(64);
    private static final String PROJECT_KEY = "dts";
    private static final String UNIQUE_ID = "model.dts.model_30000000_0000_0000_0000_000000000091";

    private ModelSpecApplicationService modelSpecs;
    private ModelLifecycleRepository lifecycle;
    private ModelLifecycleCompilerPort compiler;
    private ModelingDbtArtifactImportService artifacts;
    private AuditService audit;
    private ModelLifecycleCommandReceiptRepository receipts;
    private ModelImplementationOwnershipTransitionService service;
    private ModelSpecView designer;
    private ImplementationView designerImplementation;

    @BeforeEach
    void setUp() {
        modelSpecs = mock(ModelSpecApplicationService.class);
        lifecycle = mock(ModelLifecycleRepository.class);
        compiler = mock(ModelLifecycleCompilerPort.class);
        artifacts = mock(ModelingDbtArtifactImportService.class);
        audit = mock(AuditService.class);
        receipts = mock(ModelLifecycleCommandReceiptRepository.class);
        service = new ModelImplementationOwnershipTransitionService(
            modelSpecs,
            lifecycle,
            compiler,
            artifacts,
            new AdvancedDbtDraftStaticValidator(),
            new ObjectMapper().findAndRegisterModules(),
            audit,
            receipts
        );
        designer = model(7, MODEL_CHECKSUM, ImplementationMode.DESIGNER_GENERATED);
        designerImplementation = implementation(7, MODEL_CHECKSUM, 3, IMPLEMENTATION_CHECKSUM, ImplementationMode.DESIGNER_GENERATED);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(designer);
        when(lifecycle.findImplementation(TENANT, MODEL_ID)).thenReturn(Optional.of(designerImplementation));
        when(compiler.compile(TENANT, designer, designerImplementation)).thenReturn(generatedArtifacts());
    }

    @Test
    void previewCollapsesCompilerKindsByPathExcludesTestsAndValidatesTheFourFileBundle() {
        var preview = service.preview(TENANT, MODEL_ID, modelPin(), implementationPin());

        assertThat(preview.files()).hasSize(3);
        assertThat(preview.files()).anySatisfy(file -> assertThat(file.artifactTypes()).containsExactly("SCHEMA", "TEST"));
        assertThat(preview.files()).filteredOn(file -> file.nodeKind().equals("STG")).hasSize(1);
        assertThat(preview.files()).noneMatch(file -> file.path().endsWith(".tests.yml") || file.path().endsWith(".md"));
        assertThat(preview.readOnly()).isTrue();
        assertThat(preview.previewChecksum()).matches("^[0-9a-f]{64}$");
        verify(artifacts, never()).importArtifacts(any());
    }

    @Test
    void successfulTransitionUsesDedicatedCasImportsOnlyTheAcceptedTypesAndWritesStrictEvidence() {
        var preview = service.preview(TENANT, MODEL_ID, modelPin(), implementationPin());
        ModelSpecView targetModel = model(8, TARGET_MODEL_CHECKSUM, ImplementationMode.DBT_MANAGED);
        ImplementationView targetImplementation = implementation(8, TARGET_MODEL_CHECKSUM, 4, TARGET_IMPLEMENTATION_CHECKSUM, ImplementationMode.DBT_MANAGED);
        when(receipts.payloadHash(any())).thenReturn("e".repeat(64));
        when(receipts.find(TENANT, MODEL_ID, "OWNERSHIP_TRANSITION", "take-over-91")).thenReturn(Optional.empty());
        when(modelSpecs.transitionToDbtManaged(TENANT, ACTOR, MODEL_ID, modelPin())).thenReturn(targetModel);
        when(lifecycle.transitionDesignerImplementationToDbtManaged(
            eq(TENANT), eq(ACTOR), eq(targetModel), eq(PROJECT_KEY), eq(UNIQUE_ID), any(SaveImplementationCommand.class),
            eq(7), eq(MODEL_CHECKSUM), eq(3), eq(IMPLEMENTATION_CHECKSUM), any(Instant.class)
        )).thenReturn(1);
        when(lifecycle.findImplementation(TENANT, MODEL_ID)).thenReturn(Optional.of(designerImplementation), Optional.of(targetImplementation));

        var result = service.transition(
            TENANT, ACTOR, MODEL_ID, modelPin(), implementationPin(), preview.previewChecksum(), "take-over-91"
        );

        assertThat(result.artifactTypes()).containsExactly("SQL", "SCHEMA", "CONFIG");
        assertThat(result.model()).isEqualTo(targetModel);
        assertThat(result.implementation()).isEqualTo(targetImplementation);
        ArgumentCaptor<ModelingDbtArtifactImportService.ImportCommand> imported = ArgumentCaptor.forClass(
            ModelingDbtArtifactImportService.ImportCommand.class
        );
        verify(artifacts).importArtifacts(imported.capture());
        assertThat(imported.getValue().artifacts())
            .extracting(ModelingDbtArtifactImportService.ImportedArtifact::artifactType)
            .containsExactlyInAnyOrder(
                ModelingDbtArtifactImportService.ArtifactType.SQL,
                ModelingDbtArtifactImportService.ArtifactType.SCHEMA,
                ModelingDbtArtifactImportService.ArtifactType.CONFIG
            );
        ModelingDbtArtifactImportService.ImportedArtifact config = imported.getValue().artifacts().stream()
            .filter(item -> item.artifactType() == ModelingDbtArtifactImportService.ArtifactType.CONFIG)
            .findFirst().orElseThrow();
        assertThat(config.content()).contains("dbt_project.yml").contains("stg_model_30000000_0000_0000_0000_000000000091.sql");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String projectChecksum;
        try {
            projectChecksum = mapper.readTree(config.content()).path("projectChecksum").asText();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        DbtProjectBundleManifest.RestoredBundle restored = DbtProjectBundleManifest.restore(
            mapper, config.content(), config.checksum(), projectChecksum
        );
        assertThat(restored.files()).hasSize(4).anySatisfy(file -> {
            assertThat(file.path()).endsWith("stg_model_30000000_0000_0000_0000_000000000091.sql");
            assertThat(file.content()).isEqualTo("{{ config(materialized='ephemeral') }}\nselect 1 as customer_id");
        });
        ArgumentCaptor<Object> auditPayload = ArgumentCaptor.forClass(Object.class);
        verify(audit).auditActionStrict(
            eq("MODEL_IMPLEMENTATION_OWNERSHIP_TRANSITION"), eq(AuditStage.SUCCESS), anyString(), auditPayload.capture()
        );
        assertThat(auditPayload.getValue()).isInstanceOfSatisfying(Map.class, payload -> {
            assertThat(payload)
                .containsEntry("sourceOwnership", "DESIGNER_GENERATED")
                .containsEntry("targetOwnership", "DBT_MANAGED")
                .containsEntry("modelBeforeRevision", 7)
                .containsEntry("modelAfterRevision", 8)
                .containsEntry("implementationBeforeRevision", 3)
                .containsEntry("implementationAfterRevision", 4);
            assertThat(payload.toString()).doesNotContain("select customer_id").doesNotContain("dbt_project.yml");
        });
        verify(receipts).append(
            eq(TENANT), eq(MODEL_ID), eq("OWNERSHIP_TRANSITION"), eq("take-over-91"),
            eq("e".repeat(64)), eq(targetImplementation), eq(ACTOR), any(Instant.class)
        );
    }

    @Test
    void sameReceiptReplaysWithoutWritesAndDifferentPayloadIsRejected() {
        ImplementationView resultImplementation = implementation(8, TARGET_MODEL_CHECKSUM, 4, TARGET_IMPLEMENTATION_CHECKSUM, ImplementationMode.DBT_MANAGED);
        ModelSpecView resultModel = model(8, TARGET_MODEL_CHECKSUM, ImplementationMode.DBT_MANAGED);
        when(receipts.payloadHash(any())).thenReturn("e".repeat(64));
        when(receipts.find(TENANT, MODEL_ID, "OWNERSHIP_TRANSITION", "take-over-91"))
            .thenReturn(Optional.of(new ModelLifecycleCommandReceiptRepository.Receipt("e".repeat(64), resultImplementation)));
        when(modelSpecs.revision(TENANT, new ModelSpecContract.ModelRevisionRef(MODEL_ID, 8))).thenReturn(resultModel);

        var replay = service.transition(
            TENANT, ACTOR, MODEL_ID, modelPin(), implementationPin(), "f".repeat(64), "take-over-91"
        );

        assertThat(replay.model()).isEqualTo(resultModel);
        verify(modelSpecs, never()).transitionToDbtManaged(any(), any(), any(), any());
        verify(artifacts, never()).importArtifacts(any());
        verify(audit, never()).auditActionStrict(anyString(), any(), anyString(), any());

        when(receipts.payloadHash(any())).thenReturn("9".repeat(64));
        assertThatThrownBy(() -> service.transition(
            TENANT, ACTOR, MODEL_ID, modelPin(), implementationPin(), "0".repeat(64), "take-over-91"
        )).isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_LIFECYCLE_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void stalePreviewStopsBeforeAnyOwnershipWrite() {
        when(receipts.payloadHash(any())).thenReturn("e".repeat(64));
        when(receipts.find(TENANT, MODEL_ID, "OWNERSHIP_TRANSITION", "take-over-91")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(
            TENANT, ACTOR, MODEL_ID, modelPin(), implementationPin(), "0".repeat(64), "take-over-91"
        )).isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_DBT_PREVIEW_STALE");
        verify(modelSpecs, never()).transitionToDbtManaged(any(), any(), any(), any());
        verify(artifacts, never()).importArtifacts(any());
    }

    @Test
    void invalidGeneratedProjectIsReportedAsUnprocessableWithoutWrites() {
        List<ArtifactWrite> invalid = generatedArtifacts().stream().map(artifact -> {
            if (!"SQL".equals(artifact.artifactType())) return artifact;
            String unsafe = "{{ config(materialized=env_var('DBT_MATERIALIZATION')) }}\nselect 1";
            return artifact("SQL", artifact.path(), unsafe, "MODEL", "table");
        }).toList();
        when(compiler.compile(TENANT, designer, designerImplementation)).thenReturn(invalid);

        assertThatThrownBy(() -> service.preview(TENANT, MODEL_ID, modelPin(), implementationPin()))
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                assertThat(((ModelSpecException) error).kind()).isEqualTo(ModelSpecException.Kind.UNPROCESSABLE);
                assertThat(((ModelSpecException) error).code()).startsWith("DBT_DRAFT_");
            });
        verify(modelSpecs, never()).transitionToDbtManaged(any(), any(), any(), any());
        verify(artifacts, never()).importArtifacts(any());
    }

    private static ExpectedVersion modelPin() {
        return new ExpectedVersion(MODEL_ID, 7, MODEL_CHECKSUM);
    }

    private static ExpectedImplementationVersion implementationPin() {
        return new ExpectedImplementationVersion(MODEL_ID, 3, IMPLEMENTATION_CHECKSUM);
    }

    private static List<ArtifactWrite> generatedArtifacts() {
        String stg = "{{ config(materialized='ephemeral') }}\nselect 1 as customer_id";
        String model = "{{ config(materialized='table', alias='customer_detail', meta={'modelSpecId':'" + MODEL_ID + "','revision':7}) }}\n" +
            "select customer_id from {{ ref('stg_model_30000000_0000_0000_0000_000000000091') }}";
        String schema = "version: 2\nmodels:\n  - name: model_30000000_0000_0000_0000_000000000091\n    columns:\n      - name: customer_id\n";
        String base = "models/dwd/customer_detail/v7/i3/";
        return List.of(
            artifact("STG_SQL", base + "stg_model_30000000_0000_0000_0000_000000000091.sql", stg, "STG", "ephemeral"),
            artifact("SQL", base + "model_30000000_0000_0000_0000_000000000091.sql", model, "MODEL", "table"),
            artifact("SCHEMA", base + "model_30000000_0000_0000_0000_000000000091.yml", schema, "MODEL", "table"),
            artifact("TEST", base + "model_30000000_0000_0000_0000_000000000091.yml", schema, "MODEL", "table"),
            artifact("TEST", base + "model_30000000_0000_0000_0000_000000000091.tests.yml", "version: 2\n", "TEST", "table"),
            artifact("DOC", base + "model.md", "documentation", "DOC", "table")
        );
    }

    private static ArtifactWrite artifact(String type, String path, String content, String nodeKind, String materialization) {
        return new ArtifactWrite(type, path, ModelPackageChecksum.sha256Text(content), content, nodeKind, materialization, null);
    }

    private static ModelSpecView model(int revision, String checksum, ImplementationMode mode) {
        return new ModelSpecView(
            ModelSpecContract.CONTRACT_VERSION, MODEL_ID, PLAN_ID,
            UUID.fromString("20000000-0000-0000-0000-000000000091"), ModelType.FACT, Layer.DWD,
            "customer_detail", null, mode, "table", null, null,
            new Grain("one row per customer", List.of("customer_id")), null, null,
            List.of(new ModelField("customer_id", "varchar", false, null, ModelSpecContract.FieldRole.KEY, null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
            ModelStatus.DRAFT, revision, checksum, Instant.parse("2026-08-13T00:00:00Z"), Instant.parse("2026-08-13T00:00:00Z"),
            CompatibilityMode.CANONICAL, new ModelSpecContract.LegacyRefs(null, List.of(), List.of(), List.of()), null, null, null, null, null, null
        );
    }

    private static ImplementationView implementation(
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        ImplementationMode ownership
    ) {
        return new ImplementationView(
            IMPLEMENTATION_ID, MODEL_ID, PLAN_ID, modelRevision, modelChecksum, ownership,
            PROJECT_KEY, UNIQUE_ID, "ACTIVE", implementationRevision, implementationChecksum,
            InputMode.GENERATED,
            List.of(new GeneratedInput(ownership == ImplementationMode.DBT_MANAGED ? "DBT" : "SQL_EXPRESSION", Map.of())),
            List.of(), Map.of(), "table"
        );
    }
}
