package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyCommandCodec;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.ApplyPlan;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TechnicalNode;
import com.yuzhi.dts.platform.service.modeling.imports.dimension.DimensionDefinitionImportResolver;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Action;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewContext;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRequest;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.SemanticOverride;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.DomainBindingSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.ModelOwnershipSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedItem;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedRun;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.SourceBindingSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.StoredRun;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.MergeCheckpoint;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RenameMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelSpecImportPreviewServiceTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000070");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000070");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000070");
    private static final UUID BUSINESS_PROCESS_ID = UUID.fromString("31000000-0000-0000-0000-000000000070");
    private static final UUID DATA_MART_ID = UUID.fromString("32000000-0000-0000-0000-000000000070");
    private static final UUID SUBJECT_DOMAIN_ID = UUID.fromString("33000000-0000-0000-0000-000000000070");
    private static final String DIMENSION_CODE = "dim_70000000000000000000000000000070";
    private static final Instant NOW = Instant.parse("2026-07-25T08:00:00Z");

    @Mock
    private ModelSpecImportPreviewRepository repository;

    @Mock
    private CatalogDomainResolutionPort domainResolver;

    @Mock
    private SourceReferenceResolver sourceResolver;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    @Mock
    private WarehousePlanAuthorizationGuard authorizationGuard;

    @Mock
    private DimensionDefinitionImportResolver dimensionDefinitionResolver;

    private ObjectMapper objectMapper;
    private ModelSpecImportPreviewService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ModelPackageCanonicalProjector canonicalProjector = new ModelPackageCanonicalProjector(
            objectMapper,
            new ModelSpecSnapshotCodec(objectMapper),
            new ModelImplementationChecksumCodec(objectMapper)
        );
        service = new ModelSpecImportPreviewService(
            repository,
            domainResolver,
            sourceResolver,
            actorProvider,
            authorizationGuard,
            objectMapper,
            canonicalProjector,
            dimensionDefinitionResolver,
            new ModelSpecImportApplyPayloadCodec(objectMapper),
            TENANT,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void persistsRecoverableControlPlanePreviewWithoutSqlInDisplayProjection() {
        stubCurrentContext("source-v1");

        var response = service.preview(request(packageForPreview()));

        assertThat(response.previewHash()).hasSize(64);
        assertThat(response.applyPayloadChecksum()).hasSize(64);
        assertThat(response.summary()).isEqualTo(new PreviewSummary(1, 1, 0, 1, 0, 0, 0));
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.CREATE);
            assertThat(item.issues()).isEmpty();
            assertThat(item.proposedImplementation().has("effectiveSqlChecksum")).isTrue();
            assertThat(item.proposedImplementation().has("effectiveSql")).isFalse();
            assertThat(item.proposedImplementation().toString()).doesNotContain("select budget_id");
        });

        ArgumentCaptor<PersistedRun> runCaptor = ArgumentCaptor.forClass(PersistedRun.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(runCaptor.capture(), itemsCaptor.capture());
        assertThat(runCaptor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(runCaptor.getValue().planId()).isEqualTo(PLAN_ID);
        assertThat(runCaptor.getValue().status()).isEqualTo(RunStatus.PREVIEWED);
        assertThat(runCaptor.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(7200));
        assertThat(runCaptor.getValue().requestJson()).contains("\"selectedUniqueIds\"");
        assertThat(runCaptor.getValue().contextSnapshotJson()).contains("\"confirmedVersion\":\"source-v1\"");
        assertThat(runCaptor.getValue().contextSnapshotJson()).contains("\"lastValidatedAt\":\"" + NOW + "\"");
        assertThat(runCaptor.getValue().applyPayloadJson()).doesNotContain("rawSql", "compiledSql", "\"config\"");
        assertThat(runCaptor.getValue().applyPayloadChecksum()).isEqualTo(response.applyPayloadChecksum());
        assertThat(runCaptor.getValue().applyPlanJson()).contains(
            "targetModelSpecId",
            "targetImplementationRevision",
            "expectedModelRevision",
            "expectedImplementationChecksum",
            "proposedModelSpecChecksum",
            "artifactJson",
            "dependencyPinsJson"
        );
        assertThat(runCaptor.getValue().applyPlanJson()).contains(
            "\"expectedModelRevision\":0",
            "\"expectedImplementationRevision\":0",
            "\"planId\":\"" + PLAN_ID + "\"",
            "\"previewHash\":\"" + response.previewHash() + "\""
        );
        assertThat(itemsCaptor.getValue()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.CREATE);
            assertThat(item.sourceSnapshotJson()).contains("\"freshness\":\"CURRENT\"");
            assertThat(item.proposedImplementationJson()).doesNotContain("select budget_id");
        });
    }

    @Test
    void preservesSourceLayerFromExplicitPackageRelationMapping() {
        stubCurrentContext("source-v1");
        String packageSourceId = "source.pm_analytics_v3.pm_ods_v2.budget_v2";
        ModelPackage modelPackage = packageWithSourceRef(packageForPreview(), packageSourceId, "ODS");

        var response = service.preview(
            request(modelPackage, Map.of(packageSourceId, BINDING_ID.toString()))
        );

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.CREATE);
            assertThat(item.issues()).extracting("code").doesNotContain("MODEL_SPEC_SOURCE_INVALID");
            assertThat(item.proposedModelSpec().path("sourceRefs")).singleElement().satisfies(source ->
                assertThat(source.path("layer").asText()).isEqualTo("ODS")
            );
        });
    }

    @Test
    void acceptsAnExplicitBijectiveRenameButBlocksUntilCanonicalOwnershipReassociationExists() {
        stubCurrentContext("source-v1");
        String oldUniqueId = "model.pjm.budget_legacy";
        when(repository.findOwnership(TENANT, "pjm", oldUniqueId)).thenReturn(
            Optional.of(
                new ModelOwnershipSnapshot(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    PLAN_ID,
                    3,
                    "a".repeat(64),
                    4,
                    "b".repeat(64),
                    "DBT_MANAGED",
                    "pjm",
                    oldUniqueId,
                    3,
                    "a".repeat(64),
                    "FACT",
                    "DRAFT",
                    null,
                    null,
                    "c".repeat(64)
                )
            )
        );
        PreviewRequest request = new PreviewRequest(
            objectMapper.valueToTree(packageForPreview()),
            null,
            new PreviewContext(PLAN_ID, Map.of(), Map.of()),
            List.of("model.pjm.budget"),
            List.of(),
            List.of(new RenameMapping(oldUniqueId, "model.pjm.budget"))
        );

        var response = service.preview(request);

        assertThat(response.summary().blocked()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.BLOCKED);
            assertThat(item.issues())
                .extracting("code")
                .contains("MODEL_IMPORT_RENAME_REQUIRES_CANONICAL_REASSOCIATION");
        });
        ArgumentCaptor<PersistedRun> runCaptor = ArgumentCaptor.forClass(PersistedRun.class);
        verify(repository).save(runCaptor.capture(), any());
        assertThat(runCaptor.getValue().requestJson()).contains("renameMappings", oldUniqueId);
        ApplyPlan applyPlan = readValue(runCaptor.getValue().applyPlanJson(), ApplyPlan.class);
        assertThat(applyPlan.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.action()).isEqualTo("BLOCKED");
            assertThat(candidate.evidenceJson())
                .contains("BLOCKED_REMAP", "MODEL_IMPORT_RENAME_REQUIRES_CANONICAL_REASSOCIATION");
        });
    }

    @Test
    void rejectsARenameThatCrossesDbtPackageIdentity() {
        PreviewRequest request = new PreviewRequest(
            objectMapper.valueToTree(packageForPreview()),
            null,
            new PreviewContext(PLAN_ID, Map.of(), Map.of()),
            List.of("model.pjm.budget"),
            List.of(),
            List.of(new RenameMapping("model.other.budget", "model.pjm.budget"))
        );

        assertThatThrownBy(() -> service.preview(request))
            .isInstanceOfSatisfying(ModelSpecImportPreviewException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_RENAME_IDENTITY_INVALID")
            );
        verify(repository, never()).save(any(), any());
    }

    @Test
    void keepsExternalDbtOwnershipWhenSafeSqlCanBeProjectedToBusinessVisuals() {
        stubCurrentContext("source-v1");

        var response = service.preview(request(packageForPreview()));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.proposedModelSpec().path("implementationMode").asText()).isEqualTo("DBT_MANAGED");
            assertThat(item.proposedImplementation().path("command").path("ownership").asText()).isEqualTo("DBT_MANAGED");
        });

        ArgumentCaptor<PersistedRun> runCaptor = ArgumentCaptor.forClass(PersistedRun.class);
        verify(repository).save(runCaptor.capture(), any());
        PersistedRun persisted = runCaptor.getValue();
        ApplyPlan applyPlan = readValue(persisted.applyPlanJson(), ApplyPlan.class);
        var candidate = applyPlan.candidates().getFirst();
        assertThat(candidate.conversionMode()).isEqualTo("DESIGNER_GENERATED");

        var decoded = new ModelSpecImportApplyCommandCodec(objectMapper).decode(
            candidate,
            persisted.applyPayloadJson()
        );
        assertThat(decoded.implementationCommand().ownership()).isEqualTo(ImplementationMode.DBT_MANAGED);
        assertThat(decoded.artifacts()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(
        value = LifecycleStatus.class,
        names = { "DRAFT", "BASELINE_READY", "DESIGNING", "VALIDATING", "READY_TO_PUBLISH" }
    )
    void allowsModelPackageImportForEveryMutablePlanLifecycle(LifecycleStatus lifecycleStatus) {
        stubCurrentContext("source-v1", lifecycleStatus);

        var response = service.preview(request(packageForPreview()));

        assertThat(response.summary().ready()).isEqualTo(1);
        verify(repository).save(any(PersistedRun.class), any());
    }

    @Test
    void acceptsUiMappingsByConfirmedDomainAndSourceBindingIds() {
        stubCurrentContext("source-v1");
        var request = new PreviewRequest(
            objectMapper.valueToTree(packageForPreview()),
            null,
            new PreviewContext(
                PLAN_ID,
                Map.of("PROJECT_MANAGEMENT", DOMAIN_ID.toString()),
                Map.of("source.pjm.budget", BINDING_ID.toString())
            ),
            List.of(),
            stableBusinessContexts(packageForPreview()),
            List.of()
        );

        var response = service.preview(request);

        assertThat(response.summary()).isEqualTo(new PreviewSummary(1, 1, 0, 1, 0, 0, 0));
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.CREATE);
            assertThat(item.issues()).isEmpty();
        });
    }

    @Test
    void failsClosedWhenConfirmedSourceVersionHasDrifted() {
        stubCurrentContext("source-v2");

        var response = service.preview(request(packageForPreview()));

        assertThat(response.summary().blocked()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.BLOCKED);
            assertThat(item.issues()).extracting("code").contains("MODEL_IMPORT_SOURCE_VERSION_STALE");
        });
        ArgumentCaptor<PersistedRun> runCaptor = ArgumentCaptor.forClass(PersistedRun.class);
        verify(repository).save(runCaptor.capture(), any());
        assertThat(runCaptor.getValue().status()).isEqualTo(RunStatus.BLOCKED);
    }

    @Test
    void skipsOnlyWhenCanonicalImplementationAndEffectiveSqlChecksumsAllMatch() {
        stubCurrentContext("source-v1");
        ModelPackage modelPackage = dbtBackedPackage("select budget_id from source_budget");

        service.preview(request(modelPackage));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(PersistedRun.class), itemsCaptor.capture());
        PersistedItem created = itemsCaptor.getValue().getFirst();
        var implementation = readTree(created.proposedImplementationJson());
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget")).thenReturn(
            Optional.of(existingOwnership(created, implementation))
        );
        stubReconciliationBase(created, implementation);

        var response = service.preview(request(modelPackage));

        assertThat(response.items()).singleElement().extracting("action").isEqualTo(Action.SKIP);
    }

    @Test
    void updatesWhenCanonicalProjectionChangesEvenIfTechnicalReconciliationSkips() throws Exception {
        stubCurrentContext("source-v1");
        ModelPackage modelPackage = dbtBackedPackage("select budget_id from source_budget");

        service.preview(request(modelPackage));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(PersistedRun.class), itemsCaptor.capture());
        PersistedItem created = itemsCaptor.getValue().getFirst();
        var implementation = readTree(created.proposedImplementationJson());
        var staleSnapshot = (com.fasterxml.jackson.databind.node.ObjectNode) readTree(created.proposedModelSpecJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) staleSnapshot.withArray("fields").get(0)).put(
                "dataType",
                "text"
            );
        String staleChecksum = new ModelSpecSnapshotCodec(objectMapper).contentChecksum(
            objectMapper.treeToValue(staleSnapshot, CreateModelSpecCommand.class)
        );
        assertThat(staleChecksum).isNotEqualTo(created.modelSpecChecksum());

        UUID implementationId = UUID.fromString("50000000-0000-0000-0000-000000000070");
        UUID modelSpecId = UUID.fromString("60000000-0000-0000-0000-000000000070");
        String implementationChecksum = implementation.path("implementationChecksum").asText();
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget")).thenReturn(
            Optional.of(
                new ModelOwnershipSnapshot(
                    implementationId,
                    modelSpecId,
                    PLAN_ID,
                    1,
                    staleChecksum,
                    1,
                    implementationChecksum,
                    "DBT_MANAGED",
                    "pjm",
                    "model.pjm.budget",
                    1,
                    staleChecksum,
                    "FACT",
                    "DRAFT",
                    null,
                    null,
                    implementation.path("effectiveSqlChecksum").asText()
                )
            )
        );
        when(repository.findCurrentModelSpecSnapshot(TENANT, modelSpecId, 1)).thenReturn(Optional.of(staleSnapshot));
        when(repository.findLatestMergeCheckpoint(TENANT, PLAN_ID, modelSpecId, implementationId, "pjm", "model.pjm.budget"))
            .thenReturn(
                Optional.of(
                    new MergeCheckpoint(
                        TENANT,
                        "pjm",
                        "model.pjm.budget",
                        implementationChecksum,
                        1,
                        implementationChecksum,
                        1,
                        staleChecksum
                    )
                )
            );

        var response = service.preview(request(modelPackage));

        assertThat(response.items()).singleElement().extracting("action").isEqualTo(Action.UPDATE);
        ArgumentCaptor<PersistedRun> runs = ArgumentCaptor.forClass(PersistedRun.class);
        verify(repository, times(2)).save(runs.capture(), any());
        ApplyPlan applyPlan = readValue(runs.getAllValues().getLast().applyPlanJson(), ApplyPlan.class);
        assertThat(applyPlan.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.targetRevision()).isEqualTo(2);
            assertThat(candidate.targetImplementationRevision()).isEqualTo(1);
        });
    }

    @Test
    void updatesWhenOnlyEffectiveSqlChecksumChanges() {
        stubCurrentContext("source-v1");

        service.preview(request(dbtBackedPackage("select budget_id from source_budget")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(PersistedRun.class), itemsCaptor.capture());
        PersistedItem created = itemsCaptor.getValue().getFirst();
        var implementation = readTree(created.proposedImplementationJson());
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget")).thenReturn(
            Optional.of(existingOwnership(created, implementation))
        );
        stubReconciliationBase(created, implementation);

        var response = service.preview(request(dbtBackedPackage("select budget_id, amount from source_budget")));

        assertThat(response.items()).singleElement().extracting("action").isEqualTo(Action.UPDATE);
        ArgumentCaptor<PersistedRun> runs = ArgumentCaptor.forClass(PersistedRun.class);
        verify(repository, times(2)).save(runs.capture(), any());
        ApplyPlan applyPlan = readValue(runs.getAllValues().getLast().applyPlanJson(), ApplyPlan.class);
        assertThat(applyPlan.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.targetRevision()).isEqualTo(1);
            assertThat(candidate.targetImplementationRevision()).isEqualTo(2);
            assertThat(candidate.expectedModelStatus()).isEqualTo("DRAFT");
        });
    }

    @Test
    void conflictsWhenChangedExistingModelIsNotDraft() {
        stubCurrentContext("source-v1");
        ModelPackage original = dbtBackedPackage("select budget_id from source_budget");
        service.preview(request(original));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(PersistedRun.class), itemsCaptor.capture());
        PersistedItem created = itemsCaptor.getValue().getFirst();
        var implementation = readTree(created.proposedImplementationJson());
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget")).thenReturn(
            Optional.of(existingOwnership(created, implementation, "PUBLISHED"))
        );

        var response = service.preview(request(dbtBackedPackage("select budget_id, amount from source_budget")));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.CONFLICT);
            assertThat(item.issues()).extracting("code").contains("MODEL_IMPORT_MODEL_STATUS_READONLY");
        });
    }

    @Test
    void skipsPublishedModelWhenOnlyFieldOrderDiffers() throws Exception {
        stubCurrentContext("source-v1");
        ModelPackage modelPackage = packageWithAdditionalColumn(
            dbtBackedPackage("select budget_id, budget_name from source_budget")
        );
        service.preview(request(modelPackage));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(PersistedRun.class), itemsCaptor.capture());
        PersistedItem created = itemsCaptor.getValue().getFirst();
        var implementation = readTree(created.proposedImplementationJson());
        var reorderedSnapshot = (com.fasterxml.jackson.databind.node.ObjectNode) readTree(created.proposedModelSpecJson());
        var fields = reorderedSnapshot.withArray("fields");
        List<com.fasterxml.jackson.databind.JsonNode> reversed = new java.util.ArrayList<>();
        fields.forEach(field -> reversed.add(0, field.deepCopy()));
        fields.removeAll();
        reversed.forEach(fields::add);
        String reorderedChecksum = new ModelSpecSnapshotCodec(objectMapper).contentChecksum(
            objectMapper.treeToValue(reorderedSnapshot, CreateModelSpecCommand.class)
        );
        assertThat(reorderedChecksum).isNotEqualTo(created.modelSpecChecksum());

        UUID implementationId = UUID.fromString("50000000-0000-0000-0000-000000000070");
        UUID modelSpecId = UUID.fromString("60000000-0000-0000-0000-000000000070");
        String implementationChecksum = implementation.path("implementationChecksum").asText();
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget")).thenReturn(
            Optional.of(
                new ModelOwnershipSnapshot(
                    implementationId,
                    modelSpecId,
                    PLAN_ID,
                    1,
                    reorderedChecksum,
                    1,
                    implementationChecksum,
                    "DBT_MANAGED",
                    "pjm",
                    "model.pjm.budget",
                    1,
                    reorderedChecksum,
                    "FACT",
                    "PUBLISHED",
                    null,
                    null,
                    implementation.path("effectiveSqlChecksum").asText()
                )
            )
        );
        when(repository.findCurrentModelSpecSnapshot(TENANT, modelSpecId, 1)).thenReturn(Optional.of(reorderedSnapshot));
        when(repository.findLatestMergeCheckpoint(TENANT, PLAN_ID, modelSpecId, implementationId, "pjm", "model.pjm.budget"))
            .thenReturn(
                Optional.of(
                    new MergeCheckpoint(
                        TENANT,
                        "pjm",
                        "model.pjm.budget",
                        implementationChecksum,
                        1,
                        implementationChecksum,
                        1,
                        reorderedChecksum
                    )
                )
            );

        var response = service.preview(request(modelPackage));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.SKIP);
            assertThat(item.issues()).extracting("code").doesNotContain("MODEL_IMPORT_MODEL_STATUS_READONLY");
        });
    }

    @Test
    void rebindsDraftFactToCurrentSelectedDimensionRevision() {
        stubCurrentContext("source-v1");
        UUID dimensionDefinitionId = UUID.fromString("70000000-0000-0000-0000-000000000070");
        when(dimensionDefinitionResolver.resolveCurrent(TENANT, PLAN_ID, DOMAIN_ID, DIMENSION_CODE)).thenReturn(
            Optional.of(
                new DimensionDefinitionImportResolver.ResolvedDimensionDefinition(
                    dimensionDefinitionId,
                    1,
                    DIMENSION_CODE,
                    DOMAIN_ID
                )
            )
        );
        ModelPackage modelPackage = packageWithDimensionDependency();
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget_dimension")).thenReturn(Optional.empty());
        service.preview(request(modelPackage));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(PersistedRun.class), itemsCaptor.capture());
        PersistedItem dimension = itemsCaptor
            .getValue()
            .stream()
            .filter(item -> "model.pjm.budget_dimension".equals(item.dbtUniqueId()))
            .findFirst()
            .orElseThrow();
        PersistedItem fact = itemsCaptor
            .getValue()
            .stream()
            .filter(item -> "model.pjm.budget".equals(item.dbtUniqueId()))
            .findFirst()
            .orElseThrow();
        var dimensionImplementation = readTree(dimension.proposedImplementationJson());
        var factImplementation = readTree(fact.proposedImplementationJson());
        UUID dimensionImplementationId = UUID.fromString("51000000-0000-0000-0000-000000000070");
        UUID factImplementationId = UUID.fromString("52000000-0000-0000-0000-000000000070");
        UUID dimensionModelSpecId = UUID.fromString("61000000-0000-0000-0000-000000000070");
        UUID factModelSpecId = UUID.fromString("62000000-0000-0000-0000-000000000070");
        String dimensionImplementationChecksum = dimensionImplementation.path("implementationChecksum").asText();
        String factImplementationChecksum = factImplementation.path("implementationChecksum").asText();
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget_dimension")).thenReturn(
            Optional.of(
                new ModelOwnershipSnapshot(
                    dimensionImplementationId,
                    dimensionModelSpecId,
                    PLAN_ID,
                    2,
                    dimension.modelSpecChecksum(),
                    1,
                    dimensionImplementationChecksum,
                    "DBT_MANAGED",
                    "pjm",
                    "model.pjm.budget_dimension",
                    2,
                    dimension.modelSpecChecksum(),
                    "DIMENSION",
                    "PUBLISHED",
                    dimensionDefinitionId,
                    1,
                    dimensionImplementation.path("effectiveSqlChecksum").asText()
                )
            )
        );
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget")).thenReturn(
            Optional.of(
                new ModelOwnershipSnapshot(
                    factImplementationId,
                    factModelSpecId,
                    PLAN_ID,
                    1,
                    fact.modelSpecChecksum(),
                    1,
                    factImplementationChecksum,
                    "DBT_MANAGED",
                    "pjm",
                    "model.pjm.budget",
                    1,
                    fact.modelSpecChecksum(),
                    "FACT",
                    "DRAFT",
                    null,
                    null,
                    factImplementation.path("effectiveSqlChecksum").asText()
                )
            )
        );
        when(repository.findCurrentModelSpecSnapshot(TENANT, dimensionModelSpecId, 2)).thenReturn(
            Optional.of(readTree(dimension.proposedModelSpecJson()))
        );
        when(repository.findCurrentModelSpecSnapshot(TENANT, factModelSpecId, 1)).thenReturn(
            Optional.of(readTree(fact.proposedModelSpecJson()))
        );
        when(
            repository.findLatestMergeCheckpoint(
                TENANT,
                PLAN_ID,
                dimensionModelSpecId,
                dimensionImplementationId,
                "pjm",
                "model.pjm.budget_dimension"
            )
        ).thenReturn(
            Optional.of(
                new MergeCheckpoint(
                    TENANT,
                    "pjm",
                    "model.pjm.budget_dimension",
                    dimensionImplementationChecksum,
                    2,
                    dimensionImplementationChecksum,
                    2,
                    dimension.modelSpecChecksum()
                )
            )
        );
        when(
            repository.findLatestMergeCheckpoint(
                TENANT,
                PLAN_ID,
                factModelSpecId,
                factImplementationId,
                "pjm",
                "model.pjm.budget"
            )
        ).thenReturn(
            Optional.of(
                new MergeCheckpoint(
                    TENANT,
                    "pjm",
                    "model.pjm.budget",
                    factImplementationChecksum,
                    1,
                    factImplementationChecksum,
                    1,
                    fact.modelSpecChecksum()
                )
            )
        );

        var response = service.preview(request(modelPackage));

        assertThat(response.items())
            .filteredOn(item -> "model.pjm.budget_dimension".equals(item.dbtUniqueId()))
            .singleElement()
            .extracting("action")
            .isEqualTo(Action.SKIP);
        assertThat(response.items())
            .filteredOn(item -> "model.pjm.budget".equals(item.dbtUniqueId()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.action()).isEqualTo(Action.UPDATE);
                var dimensionRefs = item.proposedModelSpec().path("dimensionRefs");
                assertThat(dimensionRefs.size()).isEqualTo(1);
                var ref = dimensionRefs.get(0);
                assertThat(ref.path("modelSpecId").asText()).isEqualTo(dimensionModelSpecId.toString());
                assertThat(ref.path("revision").asInt()).isEqualTo(2);
            });
    }

    @Test
    void restoresCurrentCanonicalUpstreamPinsForDraftReimports() throws ReflectiveOperationException {
        UUID modelSpecId = UUID.fromString("62000000-0000-0000-0000-000000000071");
        UUID upstreamModelSpecId = UUID.fromString("61000000-0000-0000-0000-000000000071");
        ModelOwnershipSnapshot current = new ModelOwnershipSnapshot(
            UUID.fromString("52000000-0000-0000-0000-000000000071"),
            modelSpecId,
            PLAN_ID,
            1,
            "a".repeat(64),
            1,
            "b".repeat(64),
            "DBT_MANAGED",
            "pjm",
            "model.pjm.summary",
            1,
            "a".repeat(64),
            "SUMMARY",
            "DRAFT",
            null,
            null,
            "c".repeat(64)
        );
        var reconciled = objectMapper.createObjectNode();
        reconciled
            .putArray("dependsOn")
            .addObject()
            .put("modelSpecId", upstreamModelSpecId.toString())
            .put("revision", 1);
        var incoming = reconciled.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) incoming.withArray("dependsOn").get(0)).put("revision", 2);

        var method = ModelSpecImportPreviewService.class.getDeclaredMethod(
            "restoreDraftDimensionPins",
            ModelOwnershipSnapshot.class,
            com.fasterxml.jackson.databind.JsonNode.class,
            com.fasterxml.jackson.databind.JsonNode.class
        );
        method.setAccessible(true);
        var restored = (com.fasterxml.jackson.databind.JsonNode) method.invoke(null, current, reconciled, incoming);

        assertThat(restored.path("dependsOn").get(0).path("modelSpecId").asText())
            .isEqualTo(upstreamModelSpecId.toString());
        assertThat(restored.path("dependsOn").get(0).path("revision").asInt()).isEqualTo(2);
    }

    @Test
    void blocksDimensionWhenCurrentDefinitionCannotBeResolvedByStableCode() {
        stubCurrentContext("source-v1");
        when(
            dimensionDefinitionResolver.resolveCurrent(TENANT, PLAN_ID, DOMAIN_ID, DIMENSION_CODE)
        ).thenReturn(Optional.empty());

        var response = service.preview(request(dimensionPackage()));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.BLOCKED);
            assertThat(item.issues()).extracting("code").contains("MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED");
        });
        verify(dimensionDefinitionResolver).resolveCurrent(TENANT, PLAN_ID, DOMAIN_ID, DIMENSION_CODE);
    }

    @Test
    void plansPortableDimensionDefinitionWhenTargetEnvironmentHasNoCurrentDefinition() {
        stubCurrentContext("source-v1");
        when(
            dimensionDefinitionResolver.resolveCurrent(TENANT, PLAN_ID, DOMAIN_ID, DIMENSION_CODE)
        ).thenReturn(Optional.empty());

        var response = service.preview(request(dimensionPackageWithBlueprint()));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(Action.CREATE);
            assertThat(item.issues()).extracting("code").doesNotContain("MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED");
            assertThat(item.proposedModelSpec().path("dimensionDefinitionRef").path("dimensionDefinitionId").asText())
                .isEqualTo("70000000-0000-0000-0000-000000000070");
            assertThat(item.proposedModelSpec().path("dimensionDefinitionRef").path("revision").asInt()).isEqualTo(2);
        });
    }

    @Test
    void keepsMixedReadyAndBlockedCandidatesApplicable() {
        stubCurrentContext("source-v1");

        var response = service.preview(request(packageWithReadyFactAndBlockedDimension()));

        assertThat(response.summary().ready()).isEqualTo(1);
        assertThat(response.summary().blocked()).isEqualTo(1);
        assertThat(response.items())
            .extracting("dbtUniqueId", "action")
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("model.pjm.budget", Action.CREATE),
                org.assertj.core.groups.Tuple.tuple("model.pjm.unresolved_dimension", Action.BLOCKED)
            );
        ArgumentCaptor<PersistedRun> runCaptor = ArgumentCaptor.forClass(PersistedRun.class);
        verify(repository).save(runCaptor.capture(), any());
        assertThat(runCaptor.getValue().status()).isEqualTo(RunStatus.PREVIEWED);
    }

    @Test
    void compressesTechnicalBridgeAndPersistsStableCanonicalTopology() {
        stubCurrentContext("source-v1");
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget_summary")).thenReturn(Optional.empty());

        var response = service.preview(request(packageWithTechnicalBridge()));

        assertThat(response.items())
            .extracting("dbtUniqueId")
            .containsExactly("model.pjm.budget", "model.pjm.budget_summary");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersistedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(PersistedRun.class), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).extracting(PersistedItem::seq).containsExactly(0, 1);
        assertThat(itemsCaptor.getValue().get(1).dependencyJson())
            .contains("model.pjm.budget")
            .contains("model.pjm.bridge");
    }

    @Test
    void rejectsInvalidPackageBeforeCreatingPreviewRun() {
        ModelPackage valid = packageForPreview();
        ModelPackage invalid = new ModelPackage(
            valid.schemaVersion(),
            valid.packageId(),
            "0".repeat(64),
            valid.dbt(),
            valid.defaults(),
            valid.sources(),
            valid.technicalNodes(),
            valid.models(),
            valid.issues()
        );

        assertThatThrownBy(() -> service.preview(request(invalid)))
            .isInstanceOf(ModelSpecImportPreviewException.class)
            .extracting("code")
            .isEqualTo("MODEL_PACKAGE_CHECKSUM_MISMATCH");

        verify(repository, never()).save(any(), any());
    }

    @Test
    void returnsAuthorizedExpiredRunAsRecoverableReadView() {
        UUID runId = UUID.fromString("40000000-0000-0000-0000-000000000070");
        when(repository.findRun(TENANT, runId)).thenReturn(
            Optional.of(
                new StoredRun(
                    runId,
                    PLAN_ID,
                    "a".repeat(64),
                    "b".repeat(64),
                    RunStatus.PREVIEWED,
                    NOW,
                    new PreviewSummary(0, 0, 0, 0, 0, 0, 0),
                    List.of()
                )
            )
        );
        when(repository.findPlan(TENANT, PLAN_ID)).thenReturn(Optional.of(plan()));
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("actor-1", "dept-1"));

        assertThat(service.get(runId).status()).isEqualTo(RunStatus.EXPIRED);

        verify(authorizationGuard).requirePlanRead(eq(plan().header()), any());
    }

    private void stubCurrentContext(String resolvedVersion) {
        stubCurrentContext(resolvedVersion, LifecycleStatus.DESIGNING);
    }

    private void stubCurrentContext(String resolvedVersion, LifecycleStatus lifecycleStatus) {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("actor-1", "dept-1"));
        when(repository.findPlan(TENANT, PLAN_ID)).thenReturn(Optional.of(plan(lifecycleStatus)));
        when(repository.findPlatformDomainBindings()).thenReturn(
            List.of(new DomainBindingSnapshot(DOMAIN_ID, "CONFIRMED", NOW))
        );
        when(domainResolver.resolve(DOMAIN_ID)).thenReturn(
            new CatalogDomainResolutionPort.DomainResolution(
                DOMAIN_ID,
                CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE,
                "项目管理",
                "PROJECT_MANAGEMENT",
                "owner",
                "项目管理域"
            )
        );
        when(repository.findSourceBindings(TENANT, PLAN_ID)).thenReturn(
            List.of(
                new SourceBindingSnapshot(
                    BINDING_ID,
                    SourceType.CATALOG_TABLE.name(),
                    "source.pjm.budget",
                    "source-v1",
                    "{}",
                    "CONFIRMED",
                    "AVAILABLE",
                    NOW
                )
            )
        );
        when(sourceResolver.resolve(eq(SourceType.CATALOG_TABLE), any(), any())).thenReturn(
            SourceReferenceResolver.ResolvedSource.available("budget", resolvedVersion)
        );
        when(repository.findOwnership(TENANT, "pjm", "model.pjm.budget")).thenReturn(Optional.empty());
    }

    private PreviewRequest request(ModelPackage modelPackage) {
        return request(modelPackage, Map.of());
    }

    private PreviewRequest request(ModelPackage modelPackage, Map<String, String> sourceMappings) {
        return new PreviewRequest(
            objectMapper.valueToTree(modelPackage),
            null,
            new PreviewContext(PLAN_ID, Map.of(), sourceMappings),
            List.of(),
            stableBusinessContexts(modelPackage),
            List.of()
        );
    }

    private static List<SemanticOverride> stableBusinessContexts(ModelPackage modelPackage) {
        return modelPackage
            .models()
            .stream()
            .map(model -> {
                String type = model.semantics() == null ? null : model.semantics().modelType();
                return new SemanticOverride(
                    model.dbtUniqueId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "FACT".equals(type) ? BUSINESS_PROCESS_ID : null,
                    "APPLICATION".equals(type) ? DATA_MART_ID : null,
                    "APPLICATION".equals(type) ? SUBJECT_DOMAIN_ID : null
                );
            })
            .toList();
    }

    private static PlanSnapshot plan() {
        return plan(LifecycleStatus.DESIGNING);
    }

    private static PlanSnapshot plan(LifecycleStatus lifecycleStatus) {
        return new PlanSnapshot(
            PLAN_ID,
            TENANT,
            "wp_preview",
            "Preview plan",
            "Preview imported models",
            "PJM",
            "actor-1",
            "dept-1",
            OnboardingMode.BUSINESS_FIRST,
            lifecycleStatus,
            3,
            4,
            5
        );
    }

    private static ModelPackage packageForPreview() {
        ModelPackage base = ModelPackageFixtures.validPackage();
        PackageModel source = base.models().getFirst();
        SemanticMetadata semantics = source.semantics();
        SemanticMetadata previewSemantics = new SemanticMetadata(
            semantics.modelType(),
            semantics.layer(),
            semantics.grain(),
            semantics.factShape(),
            semantics.timeSemantics(),
            semantics.domainCode(),
            List.of(new SourceRef("TABLE", "source.pjm.budget", "ODS")),
            semantics.consumptionScenarios(),
            semantics.fieldRoles(),
            semantics.dimensionStrategy(),
            semantics.overrideSource(),
            semantics.technicalOnly()
        );
        PackageModel previewModel = new PackageModel(
            source.dbtUniqueId(),
            source.name(),
            source.description(),
            source.resourcePath(),
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            source.columns(),
            source.tests(),
            source.dependencies(),
            previewSemantics,
            source.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                ModelPackageContract.SCHEMA_VERSION,
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(previewModel),
                List.of()
            )
        );
    }

    private static ModelPackage packageWithSourceRef(ModelPackage base, String ref, String layer) {
        PackageModel source = base.models().getFirst();
        SemanticMetadata semantics = source.semantics();
        SemanticMetadata mappedSemantics = new SemanticMetadata(
            semantics.modelType(),
            semantics.layer(),
            semantics.grain(),
            semantics.factShape(),
            semantics.timeSemantics(),
            semantics.domainCode(),
            List.of(new SourceRef("TABLE", ref, layer)),
            semantics.consumptionScenarios(),
            semantics.fieldRoles(),
            semantics.dimensionStrategy(),
            semantics.dimensionDefinitionCode(),
            semantics.overrideSource(),
            semantics.technicalOnly()
        );
        PackageModel mappedModel = new PackageModel(
            source.dbtUniqueId(),
            source.name(),
            source.description(),
            source.resourcePath(),
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            source.columns(),
            source.tests(),
            source.dependencies(),
            mappedSemantics,
            source.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(mappedModel),
                base.issues()
            )
        );
    }

    private static ModelPackage packageWithTechnicalBridge() {
        ModelPackage base = packageForPreview();
        PackageModel upstream = base.models().getFirst();
        SemanticMetadata upstreamSemantics = upstream.semantics();
        SemanticMetadata downstreamSemantics = new SemanticMetadata(
            "SUMMARY",
            "DWS",
            upstreamSemantics.grain(),
            null,
            null,
            upstreamSemantics.domainCode(),
            List.of(new SourceRef("DBT_MODEL", upstream.dbtUniqueId(), "DWD")),
            upstreamSemantics.consumptionScenarios(),
            upstreamSemantics.fieldRoles(),
            upstreamSemantics.dimensionStrategy(),
            upstreamSemantics.overrideSource(),
            false
        );
        PackageModel downstream = new PackageModel(
            "model.pjm.budget_summary",
            "budget_summary",
            "预算汇总",
            "models/dws/budget_summary.sql",
            upstream.sql(),
            "table",
            upstream.config(),
            upstream.tags(),
            upstream.columns(),
            upstream.tests(),
            List.of("model.pjm.bridge"),
            downstreamSemantics,
            upstream.conversion()
        );
        TechnicalNode bridge = new TechnicalNode(
            "model.pjm.bridge",
            "bridge",
            "model",
            "models/stg/bridge.sql",
            null,
            Map.of(),
            List.of(upstream.dbtUniqueId()),
            List.of("technical"),
            new ConversionResult(ConversionMode.TECHNICAL_ONLY, List.of("EXPLICIT_TECHNICAL_SEMANTICS"))
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                List.of(bridge),
                List.of(downstream, upstream),
                base.issues()
            )
        );
    }

    private static ModelPackage packageWithReadyFactAndBlockedDimension() {
        ModelPackage base = packageForPreview();
        PackageModel fact = base.models().getFirst();
        SemanticMetadata semantics = fact.semantics();
        SemanticMetadata dimensionSemantics = new SemanticMetadata(
            "DIMENSION",
            "DWD",
            semantics.grain(),
            null,
            null,
            semantics.domainCode(),
            semantics.sourceRefs(),
            semantics.consumptionScenarios(),
            semantics.fieldRoles(),
            semantics.dimensionStrategy(),
            DIMENSION_CODE,
            semantics.overrideSource(),
            false
        );
        PackageModel blockedDimension = new PackageModel(
            "model.pjm.unresolved_dimension",
            "unresolved_dimension",
            "未解析维度",
            "models/dwd/unresolved_dimension.sql",
            fact.sql(),
            fact.materialization(),
            fact.config(),
            fact.tags(),
            fact.columns(),
            fact.tests(),
            fact.dependencies(),
            dimensionSemantics,
            fact.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(fact, blockedDimension),
                base.issues()
            )
        );
    }

    private static ModelPackage packageWithDimensionDependency() {
        ModelPackage base = dbtBackedPackage("select budget_id from source_budget");
        PackageModel source = base.models().getFirst();
        SemanticMetadata semantics = source.semantics();
        SemanticMetadata dimensionSemantics = new SemanticMetadata(
            "DIMENSION",
            "DWD",
            semantics.grain(),
            null,
            null,
            semantics.domainCode(),
            List.of(),
            semantics.consumptionScenarios(),
            semantics.fieldRoles(),
            semantics.dimensionStrategy(),
            DIMENSION_CODE,
            semantics.overrideSource(),
            false
        );
        PackageModel dimension = new PackageModel(
            "model.pjm.budget_dimension",
            "budget_dimension",
            "预算维度",
            "models/dwd/budget_dimension.sql",
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            source.columns(),
            source.tests(),
            List.of(),
            dimensionSemantics,
            source.conversion()
        );
        PackageModel fact = new PackageModel(
            source.dbtUniqueId(),
            source.name(),
            source.description(),
            source.resourcePath(),
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            source.columns(),
            source.tests(),
            List.of(dimension.dbtUniqueId()),
            source.semantics(),
            source.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(fact, dimension),
                base.issues()
            )
        );
    }

    private static ModelPackage packageWithAdditionalColumn(ModelPackage base) {
        PackageModel source = base.models().getFirst();
        List<Column> columns = new java.util.ArrayList<>(source.columns());
        columns.add(new Column("budget_name", "预算名称", "varchar", "ATTRIBUTE", List.of()));
        PackageModel expanded = new PackageModel(
            source.dbtUniqueId(),
            source.name(),
            source.description(),
            source.resourcePath(),
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            columns,
            source.tests(),
            source.dependencies(),
            source.semantics(),
            source.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(expanded),
                base.issues()
            )
        );
    }

    private static ModelPackage dbtBackedPackage(String effectiveSql) {
        ModelPackage base = packageForPreview();
        PackageModel source = base.models().getFirst();
        String checksum = ModelPackageChecksum.sha256Text(effectiveSql);
        PackageModel dbtBacked = new PackageModel(
            source.dbtUniqueId(),
            source.name(),
            source.description(),
            source.resourcePath(),
            new ModelPackageContract.SqlArtifact(
                effectiveSql,
                checksum,
                null,
                null,
                effectiveSql,
                checksum,
                "PROJECT_FILE"
            ),
            source.materialization(),
            source.config(),
            source.tags(),
            source.columns(),
            source.tests(),
            source.dependencies(),
            source.semantics(),
            new ConversionResult(ConversionMode.DBT_BACKED, List.of("DBT_RUNTIME_REQUIRED"))
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(dbtBacked),
                base.issues()
            )
        );
    }

    private static ModelPackage dimensionPackage() {
        ModelPackage base = packageForPreview();
        PackageModel source = base.models().getFirst();
        SemanticMetadata semantics = source.semantics();
        SemanticMetadata dimensionSemantics = new SemanticMetadata(
            "DIMENSION",
            "DWD",
            semantics.grain(),
            null,
            null,
            semantics.domainCode(),
            semantics.sourceRefs(),
            semantics.consumptionScenarios(),
            semantics.fieldRoles(),
            semantics.dimensionStrategy(),
            DIMENSION_CODE,
            semantics.overrideSource(),
            false
        );
        PackageModel dimension = new PackageModel(
            source.dbtUniqueId(),
            source.name(),
            source.description(),
            source.resourcePath(),
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            source.columns(),
            source.tests(),
            source.dependencies(),
            dimensionSemantics,
            source.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(dimension),
                base.issues()
            )
        );
    }

    private static ModelPackage dimensionPackageWithBlueprint() {
        ModelPackage base = dimensionPackage();
        PackageModel source = base.models().getFirst();
        SemanticMetadata semantics = source.semantics();
        ModelPackageContract.Column sourceColumn = source.columns().getFirst();
        var portableColumns = List.of(
            new ModelPackageContract.Column(
                sourceColumn.name(),
                sourceColumn.description(),
                sourceColumn.dataType(),
                sourceColumn.role(),
                "STATUS_CODE",
                sourceColumn.tests()
            )
        );
        var blueprint = new ModelPackageContract.DimensionDefinitionBlueprint(
            "测试状态维度",
            "测试状态",
            "用于验证可移植导入契约的中性状态维度。",
            List.of(
                new ModelPackageContract.DimensionAttributeBlueprint(
                    "STATUS_CODE",
                    "状态编码",
                    "跨系统稳定的状态编码。",
                    true,
                    null,
                    null,
                    1
                )
            )
        );
        SemanticMetadata portableSemantics = new SemanticMetadata(
            semantics.modelType(),
            semantics.layer(),
            semantics.grain(),
            semantics.factShape(),
            semantics.timeSemantics(),
            semantics.domainCode(),
            semantics.sourceRefs(),
            semantics.consumptionScenarios(),
            semantics.fieldRoles(),
            semantics.dimensionStrategy(),
            semantics.dimensionDefinitionCode(),
            blueprint,
            semantics.overrideSource(),
            semantics.technicalOnly()
        );
        PackageModel portable = new PackageModel(
            source.dbtUniqueId(),
            source.name(),
            source.description(),
            source.resourcePath(),
            source.sql(),
            source.materialization(),
            source.config(),
            source.tags(),
            portableColumns,
            source.tests(),
            source.dependencies(),
            portableSemantics,
            source.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                base.technicalNodes(),
                List.of(portable),
                base.issues()
            )
        );
    }

    private static ModelOwnershipSnapshot existingOwnership(
        PersistedItem created,
        com.fasterxml.jackson.databind.JsonNode implementation
    ) {
        return existingOwnership(created, implementation, "DRAFT");
    }

    private static ModelOwnershipSnapshot existingOwnership(
        PersistedItem created,
        com.fasterxml.jackson.databind.JsonNode implementation,
        String status
    ) {
        return new ModelOwnershipSnapshot(
            UUID.fromString("50000000-0000-0000-0000-000000000070"),
            UUID.fromString("60000000-0000-0000-0000-000000000070"),
            PLAN_ID,
            1,
            created.modelSpecChecksum(),
            1,
            implementation.path("implementationChecksum").asText(),
            "DBT_MANAGED",
            "pjm",
            "model.pjm.budget",
            1,
            created.modelSpecChecksum(),
            "FACT",
            status,
            null,
            null,
            implementation.path("effectiveSqlChecksum").asText()
        );
    }

    private void stubReconciliationBase(
        PersistedItem created,
        com.fasterxml.jackson.databind.JsonNode implementation
    ) {
        UUID modelSpecId = UUID.fromString("60000000-0000-0000-0000-000000000070");
        String implementationChecksum = implementation.path("implementationChecksum").asText();
        when(repository.findCurrentModelSpecSnapshot(TENANT, modelSpecId, 1)).thenReturn(
            Optional.of(readTree(created.proposedModelSpecJson()))
        );
        when(
            repository.findLatestMergeCheckpoint(
                TENANT,
                PLAN_ID,
                modelSpecId,
                UUID.fromString("50000000-0000-0000-0000-000000000070"),
                "pjm",
                "model.pjm.budget"
            )
        ).thenReturn(
            Optional.of(
                new MergeCheckpoint(
                    TENANT,
                    "pjm",
                    "model.pjm.budget",
                    implementationChecksum,
                    1,
                    implementationChecksum,
                    1,
                    created.modelSpecChecksum()
                )
            )
        );
    }

    private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
