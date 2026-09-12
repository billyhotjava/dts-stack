package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.DraftRow;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.FileRow;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.NewDraft;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.Resolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSource;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Reconciliation;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleCompilerPort;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtCompiler.CompileException;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringSnapshotDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ArtifactType;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportResult;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportedArtifact;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.NodeKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringOrigin;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringSeed;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.BundleFileView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.Diagnostic;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DependencyValidationView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ErrorKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ProposedNode;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtCanonicalProjectReconstructor.CanonicalProject;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidationView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleFile;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleSnapshot;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.RestoredBundle;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.StaticValidationException;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RepresentationEvidence;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for static-only advanced dbt draft editing and immutable commit. */
@Service
public class DbtImplementationDraftService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtImplementationDraftService.class);
    private static final Duration DRAFT_TTL = Duration.ofHours(24);
    private static final Pattern VERSIONED_COMPILER_ARTIFACT = Pattern.compile(
        "^(models/[A-Za-z_][A-Za-z0-9_]*/[A-Za-z_][A-Za-z0-9_]*)/v[1-9][0-9]*/i[1-9][0-9]*/([A-Za-z_][A-Za-z0-9_]*\\.(?:sql|yml))$"
    );
    private static final String AUDIT_CREATE = "MODELING_DBT_DRAFT_CREATE";
    private static final String AUDIT_SAVE = "MODELING_DBT_DRAFT_SAVE";
    private static final String AUDIT_VALIDATE = "MODELING_DBT_DRAFT_VALIDATE";
    private static final String AUDIT_COMMIT = "MODELING_DBT_DRAFT_COMMIT";

    private final DbtImplementationDraftRepository repository;
    private final ModelSpecApplicationService modelSpecs;
    private com.yuzhi.dts.platform.service.modeling.ModelSourceFieldsService sourceFields;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSourceFields(com.yuzhi.dts.platform.service.modeling.ModelSourceFieldsService sourceFields) { this.sourceFields = sourceFields; }

    private final ModelLifecycleService lifecycle;
    private final ModelSpecPlanWriteAccessPort writeAccess;
    private final AdvancedDbtDraftStaticValidator validator;
    private final ModelingDbtArtifactImportService artifactImports;
    private final ModelRepresentationEvidencePort representationEvidence;
    private final DbtImplementationDraftAuditRecorder auditRecorder;
    private final ModelImplementationDependencyService dependencies;
    private final ObjectMapper objectMapper;
    private final ModelAuthoringSnapshotDecoder snapshotDecoder;
    private final ModelSpecSnapshotCodec snapshotCodec;
    private final ModelImplementationChecksumCodec implementationChecksums;
    private final DbtCanonicalProjectReconstructor canonicalProjects;
    private final ModelLifecycleCompilerPort visualCompiler;
    private final Clock clock;

    @Autowired
    public DbtImplementationDraftService(
        DbtImplementationDraftRepository repository,
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        ModelSpecPlanWriteAccessPort writeAccess,
        AdvancedDbtDraftStaticValidator validator,
        ModelingDbtArtifactImportService artifactImports,
        ModelRepresentationEvidencePort representationEvidence,
        DbtImplementationDraftAuditRecorder auditRecorder,
        ObjectMapper objectMapper,
        ModelImplementationDependencyService dependencies,
        ModelLifecycleCompilerPort visualCompiler
    ) {
        this(
            repository,
            modelSpecs,
            lifecycle,
            writeAccess,
            validator,
            artifactImports,
            representationEvidence,
            auditRecorder,
            objectMapper,
            dependencies,
            visualCompiler,
            Clock.systemUTC()
        );
    }

    DbtImplementationDraftService(
        DbtImplementationDraftRepository repository,
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        ModelSpecPlanWriteAccessPort writeAccess,
        AdvancedDbtDraftStaticValidator validator,
        ModelingDbtArtifactImportService artifactImports,
        ModelRepresentationEvidencePort representationEvidence,
        DbtImplementationDraftAuditRecorder auditRecorder,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this(
            repository,
            modelSpecs,
            lifecycle,
            writeAccess,
            validator,
            artifactImports,
            representationEvidence,
            auditRecorder,
            objectMapper,
            null,
            null,
            clock
        );
    }

    DbtImplementationDraftService(
        DbtImplementationDraftRepository repository,
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        ModelSpecPlanWriteAccessPort writeAccess,
        AdvancedDbtDraftStaticValidator validator,
        ModelingDbtArtifactImportService artifactImports,
        ModelRepresentationEvidencePort representationEvidence,
        DbtImplementationDraftAuditRecorder auditRecorder,
        ObjectMapper objectMapper,
        ModelImplementationDependencyService dependencies,
        ModelLifecycleCompilerPort visualCompiler,
        Clock clock
    ) {
        this.repository = repository;
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.writeAccess = writeAccess;
        this.validator = validator;
        this.artifactImports = artifactImports;
        this.representationEvidence = representationEvidence;
        this.auditRecorder = auditRecorder;
        this.dependencies = dependencies;
        this.objectMapper = objectMapper;
        this.snapshotDecoder = new ModelAuthoringSnapshotDecoder(objectMapper);
        this.snapshotCodec = new ModelSpecSnapshotCodec(objectMapper);
        this.implementationChecksums = new ModelImplementationChecksumCodec(objectMapper);
        this.canonicalProjects = new DbtCanonicalProjectReconstructor(objectMapper);
        this.visualCompiler = visualCompiler;
        this.clock = clock;
    }

    @Transactional
    public DraftView create(String tenantId, String actorId, UUID modelSpecId, CreateDraftRequest request) {
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        try {
            return createInternal(tenantId, actorId, modelSpecId, request, null, correlationId);
        } catch (RuntimeException failure) {
            throw auditedFailure(AUDIT_CREATE, tenantId, actorId, modelSpecId, null, request, correlationId, failure);
        }
    }

    @Transactional
    public DraftView createAuthoring(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        CreateDraftRequest request,
        AuthoringSeed seed
    ) {
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        try {
            if (seed == null || seed.modelSpecSnapshot() == null || !seed.modelSpecSnapshot().isObject()) {
                throw DbtImplementationDraftContract.badRequest(
                    "MODEL_AUTHORING_SNAPSHOT_REQUIRED",
                    "A canonical ModelSpec authoring snapshot is required"
                );
            }
            return createInternal(tenantId, actorId, modelSpecId, request, seed, correlationId);
        } catch (RuntimeException failure) {
            throw auditedFailure(AUDIT_CREATE, tenantId, actorId, modelSpecId, null, request, correlationId, failure);
        }
    }

    private DraftView createInternal(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        CreateDraftRequest request,
        AuthoringSeed seed,
        String correlationId
    ) {
        requireIdentity(tenantId, actorId, modelSpecId);
        if (request == null || request.planId() == null || request.baseModelRevision() < 1) {
            throw DbtImplementationDraftContract.badRequest("DBT_DRAFT_REQUEST_INVALID", "Draft model pins are required");
        }
        String modelChecksum = DbtImplementationDraftContract.requiredChecksum(
            request.baseModelChecksum(),
            "baseModelChecksum"
        );
        Integer implementationRevision = request.baseImplementationRevision();
        String implementationChecksum = request.baseImplementationChecksum();
        if ((implementationRevision == null) != (implementationChecksum == null)) {
            throw DbtImplementationDraftContract.badRequest(
                "DBT_DRAFT_IMPLEMENTATION_PIN_INVALID",
                "Base implementation revision and checksum must be provided together"
            );
        }
        if (implementationRevision != null) {
            if (implementationRevision < 1) {
                throw DbtImplementationDraftContract.badRequest(
                    "DBT_DRAFT_IMPLEMENTATION_PIN_INVALID",
                    "Base implementation revision must be positive"
                );
            }
            implementationChecksum = DbtImplementationDraftContract.requiredChecksum(
                implementationChecksum,
                "baseImplementationChecksum"
            );
        }
        String idempotencyKey = DbtImplementationDraftContract.requiredText(
            request.idempotencyKey(),
            "idempotencyKey",
            128
        );
        String targetPhysicalName = normalizedTargetPhysicalName(request.targetPhysicalName());
        String requestHash = requestHash(
            tenantId,
            actorId,
            request.planId(),
            modelSpecId,
            request.baseModelRevision(),
            modelChecksum,
            implementationRevision,
            implementationChecksum,
            targetPhysicalName
        );
        if (seed != null) {
            requestHash = DbtImplementationDraftContract.requiredChecksum(seed.requestHash(), "requestHash");
        }
        writeAccess.requireEdit(tenantId, modelSpecId, actorId);
        if (!writeAccess.canMaintain(tenantId, request.planId(), actorId)) {
            throw DbtImplementationDraftContract.forbidden(
                "DBT_DRAFT_MAINTAINER_FORBIDDEN",
                "The current actor cannot maintain this warehouse plan"
            );
        }
        Instant now = clock.instant();
        Optional<DraftRow> replay = repository.findByIdempotency(
            tenantId,
            request.planId(),
            modelSpecId,
            actorId,
            idempotencyKey
        );
        if (replay.isPresent()) return createdDraft(replay.orElseThrow(), requestHash, now, correlationId);

        ModelSpecView model = requireEditableModel(tenantId, actorId, modelSpecId, request.planId(), seed != null);
        if (model.revision() != request.baseModelRevision() || !Objects.equals(model.checksum(), modelChecksum)) {
            throw DbtImplementationDraftContract.conflict(
                "DBT_DRAFT_BASE_MODEL_CONFLICT",
                "The ModelSpec revision changed before draft creation"
            );
        }
        ImplementationView implementation = lifecycle.timeline(tenantId, modelSpecId).implementation();
        boolean unifiedAuthoring = seed != null;
        requireImplementationPin(
            modelSpecId,
            implementation,
            implementationRevision,
            implementationChecksum,
            unifiedAuthoring
        );
        if (implementation == null && targetPhysicalName == null) {
            throw DbtImplementationDraftContract.badRequest(
                "DBT_DRAFT_TARGET_PHYSICAL_NAME_REQUIRED",
                "The first dbt implementation requires a target physical name"
            );
        }
        if (implementation != null && targetPhysicalName != null) {
            throw DbtImplementationDraftContract.badRequest(
                "DBT_DRAFT_TARGET_PHYSICAL_NAME_NOT_ALLOWED",
                "An existing dbt implementation owns its target physical name"
            );
        }
        SourceBundleView sourceBundle = sourceBundle(
            tenantId,
            request.planId(),
            modelSpecId,
            request.baseModelRevision(),
            modelChecksum,
            implementationRevision,
            implementationChecksum,
            implementation,
            model,
            targetPhysicalName,
            unifiedAuthoring
        );

        String sourceBundleSnapshot = sourceBundleSnapshot(sourceBundle);
        DraftRow row = repository.create(
            new NewDraft(
                UUID.randomUUID(),
                tenantId,
                request.planId(),
                modelSpecId,
                actorId,
                request.baseModelRevision(),
                modelChecksum,
                implementationRevision,
                implementationChecksum,
                idempotencyKey,
                requestHash,
                sourceBundleSnapshot,
                seed == null ? null : jsonSnapshot(seed.modelSpecSnapshot(), "ModelSpec authoring snapshot"),
                seed == null || seed.projectionSummary() == null
                    ? null
                    : jsonSnapshot(seed.projectionSummary(), "Model projection summary"),
                seed == null ? null : seed.origin().name(),
                nextEtag(),
                now.plus(DRAFT_TTL),
                now
            )
        );
        return createdDraft(row, requestHash, now, correlationId);
    }

    private String jsonSnapshot(JsonNode value, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(label + " could not be serialized", exception);
        }
    }

    private DraftView createdDraft(DraftRow row, String requestHash, Instant now, String correlationId) {
        if (!Objects.equals(row.requestHash(), requestHash)) {
            throw DbtImplementationDraftContract.conflict(
                "DBT_DRAFT_IDEMPOTENCY_CONFLICT",
                "The draft idempotency key belongs to different base pins"
            );
        }
        requireNotExpired(row, now);
        SourceBundleView sourceBundle = sourceBundleSnapshot(row.sourceBundleSnapshot());
        audit(
            AUDIT_CREATE,
            row,
            sourceBundle == null ? 0 : sourceBundle.files().size(),
            null,
            null,
            null,
            sourceBundle == null ? null : sourceBundle.bundleChecksum(),
            correlationId
        );
        return view(row, sourceBundle);
    }

    @Transactional(readOnly = true)
    public Optional<DraftView> findAuthoringByIdempotency(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID planId,
        String idempotencyKey,
        String requestHash
    ) {
        requireIdentity(tenantId, actorId, modelSpecId);
        String key = DbtImplementationDraftContract.requiredText(idempotencyKey, "idempotencyKey", 128);
        String expectedHash = DbtImplementationDraftContract.requiredChecksum(requestHash, "requestHash");
        Optional<DraftRow> found = repository.findByIdempotency(tenantId, planId, modelSpecId, actorId, key);
        if (found.isEmpty()) return Optional.empty();
        DraftRow row = found.orElseThrow();
        if (!Objects.equals(row.requestHash(), expectedHash)) {
            throw DbtImplementationDraftContract.conflict(
                "MODEL_AUTHORING_IDEMPOTENCY_CONFLICT",
                "The authoring idempotency key belongs to another request"
            );
        }
        requireNotExpired(row, clock.instant());
        if (row.modelSpecSnapshot() == null || row.modelSpecSnapshot().isBlank()) return Optional.empty();
        return Optional.of(view(row, workingSourceBundle(row)));
    }

    @Transactional(readOnly = true)
    public Optional<DraftView> findOpenAuthoring(String tenantId, String actorId, UUID modelSpecId) {
        requireIdentity(tenantId, actorId, modelSpecId);
        Optional<DraftRow> found = repository.findOpenForActor(tenantId, modelSpecId, actorId, clock.instant());
        if (found.isEmpty()) return Optional.empty();
        DraftRow row = found.orElseThrow();
        if (row.modelSpecSnapshot() == null || row.modelSpecSnapshot().isBlank()) return Optional.empty();
        if (!writeAccess.canEdit(tenantId, modelSpecId, actorId)) return Optional.empty();
        return Optional.of(view(row, workingSourceBundle(row)));
    }

    @Transactional(readOnly = true)
    public List<FileInput> authoringFiles(String tenantId, String actorId, UUID modelSpecId, UUID draftId) {
        DraftRow current = requireDraft(tenantId, actorId, modelSpecId, draftId);
        requireNotExpired(current, clock.instant());
        return List.copyOf(currentWorkingFiles(current).values());
    }

    private String sourceBundleSnapshot(SourceBundleView sourceBundle) {
        try {
            return objectMapper.writeValueAsString(sourceBundle);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The dbt source bundle snapshot could not be serialized", exception);
        }
    }

    private SourceBundleView sourceBundleSnapshot(String snapshot) {
        try {
            SourceBundleView source = objectMapper.readValue(snapshot, SourceBundleView.class);
            DbtImplementationDraftContract.requiredText(source.projectKey(), "projectKey", 128);
            DbtImplementationDraftContract.requiredChecksum(source.projectChecksum(), "projectChecksum");
            DbtImplementationDraftContract.requiredChecksum(source.bundleChecksum(), "bundleChecksum");
            List<FileInput> normalized = DbtImplementationDraftContract.normalizeFiles(
                source.files().stream().map(file -> new FileInput(file.path(), file.content())).toList()
            );
            if (normalized.size() != source.files().size()) throw sourceBundleUnavailable();
            for (int index = 0; index < normalized.size(); index++) {
                FileInput file = normalized.get(index);
                BundleFileView snapshotFile = source.files().get(index);
                byte[] bytes = file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (
                    !Objects.equals(file.path(), snapshotFile.path()) ||
                    !Objects.equals(ModelPackageChecksum.sha256(bytes), snapshotFile.checksum()) ||
                    bytes.length != snapshotFile.byteSize()
                ) {
                    throw sourceBundleUnavailable();
                }
            }
            if (source.files().stream().noneMatch(file -> "dbt_project.yml".equals(file.path()))) {
                throw sourceBundleUnavailable();
            }
            if ((source.dependencyChecksum() == null) != (source.dependencySnapshot() == null)) {
                throw sourceBundleUnavailable();
            }
            if (source.dependencySnapshot() != null) {
                DbtImplementationDraftContract.requiredChecksum(
                    source.dependencyChecksum(),
                    "dependencyChecksum"
                );
                if (!Objects.equals(source.dependencyChecksum(), source.dependencySnapshot().dependencyChecksum())) {
                    throw sourceBundleUnavailable();
                }
                source.managedDependencyAliases().forEach((alias, dependency) -> {
                    if (
                        alias == null ||
                        alias.isBlank() ||
                        dependency == null ||
                        dependency.isBlank() ||
                        !alias.startsWith("model.") ||
                        !dependency.startsWith("model.")
                    ) {
                        throw sourceBundleUnavailable();
                    }
                });
            } else if (!source.managedDependencyAliases().isEmpty()) {
                throw sourceBundleUnavailable();
            }
            return source;
        } catch (DraftException failure) {
            throw failure;
        } catch (RuntimeException | JsonProcessingException failure) {
            throw sourceBundleUnavailable();
        }
    }

    /** Validates and restores a frozen source bundle for the controlled authoring migration lane. */
    public SourceBundleView restoreSourceBundleSnapshot(String snapshot) {
        return sourceBundleSnapshot(snapshot);
    }

    private SourceBundleView workingSourceBundle(DraftRow row) {
        SourceBundleView frozen = sourceBundleSnapshot(row.sourceBundleSnapshot());
        List<BundleFileView> workingFiles = currentWorkingFiles(row)
            .values()
            .stream()
            .map(file -> {
                byte[] bytes = file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new BundleFileView(file.path(), file.content(), ModelPackageChecksum.sha256(bytes), bytes.length);
            })
            .toList();
        if (workingFiles.isEmpty()) return frozen;
        return new SourceBundleView(
            frozen.projectKey(),
            frozen.projectChecksum(),
            frozen.bundleChecksum(),
            frozen.sourceKind(),
            frozen.lossless(),
            workingFiles,
            frozen.dependencyChecksum(),
            frozen.dependencySnapshot(),
            frozen.managedDependencyAliases()
        );
    }

    @Transactional
    public SaveFilesView saveFiles(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        SaveFilesRequest request
    ) {
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        try {
            return saveFilesInternal(tenantId, actorId, modelSpecId, draftId, request, correlationId);
        } catch (RuntimeException failure) {
            throw auditedFailure(AUDIT_SAVE, tenantId, actorId, modelSpecId, draftId, request, correlationId, failure);
        }
    }

    @Transactional
    public SaveFilesView saveAuthoring(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        String expectedEtag,
        JsonNode modelSpecSnapshot,
        JsonNode projectionSummary,
        List<FileInput> requestedFiles,
        boolean visualView
    ) {
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        try {
            DraftRow current = requireDraft(tenantId, actorId, modelSpecId, draftId);
            if (modelSpecSnapshot == null || !modelSpecSnapshot.isObject()) {
                throw DbtImplementationDraftContract.badRequest(
                    "MODEL_AUTHORING_SNAPSHOT_REQUIRED",
                    "A canonical ModelSpec authoring snapshot is required"
                );
            }
            String requiredEtag = expectedEtag(expectedEtag);
            if (!Objects.equals(current.etag(), requiredEtag)) throw etagConflict(current, requiredEtag);
            List<FileInput> files = DbtImplementationDraftContract.normalizeFiles(requestedFiles);
            Instant now = clock.instant();
            requireNotExpired(current, now);
            if (current.state() == DraftState.COMMITTED || current.state() == DraftState.COMMITTING) {
                throw DbtImplementationDraftContract.conflict(
                    "MODEL_AUTHORING_DRAFT_NOT_EDITABLE",
                    "A committing or committed authoring draft cannot be edited"
                );
            }
            if (visualView) requireUnmanagedFilesUnchanged(current, files, projectionSummary);
            DraftRow aligned = alignAuthoringBase(tenantId, actorId, modelSpecId, current, now, modelSpecSnapshot);
            if (visualView) {
                var decoded = snapshotDecoder.decode(modelSpecSnapshot);
                boolean structuredVisual = decoded.valid() && decoded.visualImplementation() != null;
                if (structuredVisual) {
                    try {
                    SaveImplementationCommand pinned = lifecycle.prepareAuthoringInputs(
                        tenantId,
                        actorId,
                        modelSpecId,
                        new ExpectedVersion(modelSpecId, aligned.baseModelRevision(), aligned.baseModelChecksum()),
                        decoded.visualImplementation().command()
                    );
                    ObjectNode pinnedSnapshot = modelSpecSnapshot.deepCopy();
                    ((ObjectNode) pinnedSnapshot.path("visualImplementation")).set("inputs", objectMapper.valueToTree(pinned.inputs()));
                    modelSpecSnapshot = pinnedSnapshot;
                    decoded = snapshotDecoder.decode(pinnedSnapshot);
                    files = recompileVisualAuthoring(
                        tenantId,
                        actorId,
                        modelSpecId,
                        aligned,
                        files,
                        decoded,
                        projectionSummary
                    );
                    } catch (RuntimeException failure) {
                        String code = failure instanceof ModelSpecException modelFailure ? modelFailure.code()
                            : failure instanceof DraftException draftFailure ? draftFailure.code() : "";
                        if (!java.util.Set.of("MODEL_IMPLEMENTATION_INPUT_STALE", "MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
                            "DBT_DRAFT_DEPENDENCY_PIN_STALE", "IMPLEMENTATION_JOIN_INVALID", "IMPLEMENTATION_JOIN_REQUIRED",
                            "IMPLEMENTATION_JOIN_COVERAGE_INVALID", "MULTI_SOURCE_MAPPING_MUST_BE_QUALIFIED", "MULTI_SOURCE_MAPPING_REQUIRED",
                            "SINGLE_SOURCE_JOIN_NOT_ALLOWED", "SOURCE_REQUIRED",
                            "IMPLEMENTATION_FILTER_INVALID", "IMPLEMENTATION_AGGREGATION_INVALID").contains(code)) throw failure;
                        ObjectNode summary = projectionSummary != null && projectionSummary.isObject()
                            ? ((ObjectNode) projectionSummary).deepCopy() : objectMapper.createObjectNode();
                        summary.put("inputDiagnostic", "来源或字段配置尚未完成，草稿已保留；请修复后再提交");
                        projectionSummary = summary;
                    }
                }
            }
            requireManagedFilesUnchanged(aligned, files);
            DraftRow saved = repository
                .replaceAuthoringContent(
                    tenantId,
                    modelSpecId,
                    draftId,
                    actorId,
                    aligned.etag(),
                    nextEtag(),
                    jsonSnapshot(modelSpecSnapshot, "ModelSpec authoring snapshot"),
                    projectionSummary == null ? null : jsonSnapshot(projectionSummary, "Model projection summary"),
                    files,
                    now
                )
                .orElseThrow(() -> etagConflict(aligned, aligned.etag()));
            long totalBytes = files
                .stream()
                .mapToLong(file -> file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .sum();
            audit(AUDIT_SAVE, saved, files.size(), null, null, null, null, correlationId);
            return new SaveFilesView(saved.id(), saved.etag(), saved.expiresAt(), files.size(), totalBytes);
        } catch (RuntimeException failure) {
            throw auditedFailure(AUDIT_SAVE, tenantId, actorId, modelSpecId, draftId, requestedFiles, correlationId, failure);
        }
    }

    private SaveFilesView saveFilesInternal(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        SaveFilesRequest request,
        String correlationId
    ) {
        DraftRow current = requireDraft(tenantId, actorId, modelSpecId, draftId);
        String expectedEtag = expectedEtag(request == null ? null : request.expectedEtag());
        List<FileInput> files = DbtImplementationDraftContract.normalizeFiles(request == null ? null : request.files());
        Instant now = clock.instant();
        requireNotExpired(current, now);
        if (current.state() == DraftState.COMMITTED || current.state() == DraftState.COMMITTING) {
            throw DbtImplementationDraftContract.conflict(
                "DBT_DRAFT_NOT_EDITABLE",
                "A committing or committed draft cannot be edited"
            );
        }
        requireManagedFilesUnchanged(current, files);
        DraftRow saved = repository
            .replaceFiles(tenantId, modelSpecId, draftId, actorId, expectedEtag, nextEtag(), files, now)
            .orElseThrow(() -> etagConflict(current, expectedEtag));
        long totalBytes = files.stream().mapToLong(file -> file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
        audit(AUDIT_SAVE, saved, files.size(), null, null, null, null, correlationId);
        return new SaveFilesView(saved.id(), saved.etag(), saved.expiresAt(), files.size(), totalBytes);
    }

    private void requireManagedFilesUnchanged(DraftRow current, List<FileInput> files) {
        if (dependencies == null || current.sourceBundleSnapshot() == null) return;
        SourceBundleView source = sourceBundleSnapshot(current.sourceBundleSnapshot());
        if (source.dependencySnapshot() == null) return;
        Map<String, FileInput> submitted = new LinkedHashMap<>();
        files.forEach(file -> submitted.put(file.path(), file));
        for (BundleFileView managed : source.files()) {
            if (!managed.path().startsWith("models/.dts_dependencies/")) continue;
            FileInput actual = submitted.get(managed.path());
            if (actual == null || !Objects.equals(actual.content(), managed.content())) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_MANAGED_DEPENDENCY_IMMUTABLE",
                    "System-managed dbt dependency files cannot be removed, renamed or edited"
                );
            }
        }
    }

    private void requireUnmanagedFilesUnchanged(
        DraftRow current,
        List<FileInput> files,
        JsonNode projectionSummary
    ) {
        java.util.Set<String> managedPaths = managedPaths(current, projectionSummary);
        Map<String, FileInput> submitted = new LinkedHashMap<>();
        files.forEach(file -> submitted.put(file.path(), file));
        Map<String, FileInput> persisted = currentWorkingFiles(current);
        for (FileInput original : persisted.values()) {
            if (
                managedPaths.contains(original.path()) ||
                original.path().startsWith("models/.dts_dependencies/") ||
                "dbt_project.yml".equals(original.path())
            ) {
                continue;
            }
            FileInput actual = submitted.get(original.path());
            if (actual == null || !Objects.equals(actual.content(), original.content())) {
                throw DbtImplementationDraftContract.conflict(
                    "MODEL_AUTHORING_UNMANAGED_FILE_CHANGED",
                    "Visual editing cannot remove, rename or overwrite an unmanaged bundle file"
                );
            }
        }
        for (FileInput actual : submitted.values()) {
            if (
                persisted.containsKey(actual.path()) ||
                managedPaths.contains(actual.path()) ||
                "dbt_project.yml".equals(actual.path())
            ) {
                continue;
            }
            throw DbtImplementationDraftContract.conflict(
                "MODEL_AUTHORING_UNMANAGED_FILE_CHANGED",
                "Visual editing cannot add an unmanaged bundle file"
            );
        }
    }

    private List<FileInput> recompileVisualAuthoring(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        DraftRow draft,
        List<FileInput> submittedFiles,
        ModelAuthoringSnapshotDecoder.DecodeResult decoded,
        JsonNode projectionSummary
    ) {
        if (visualCompiler == null || decoded.visualImplementation() == null) throw sourceBundleUnavailable();
        ModelSpecView currentModel = requireEditableModel(tenantId, actorId, modelSpecId, draft.planId(), true);
        requireModelPins(draft, currentModel);
        ImplementationView currentImplementation = lifecycle.timeline(tenantId, modelSpecId).implementation();
        requireImplementationPin(
            modelSpecId,
            currentImplementation,
            draft.baseImplementationRevision(),
            draft.baseImplementationChecksum(),
            true
        );
        java.util.Set<String> compilerOwnedPaths = compilerOwnedPaths(
            tenantId,
            modelSpecId,
            draft,
            currentModel,
            currentImplementation
        );
        UpdateModelSpecCommand visualModelCommand = withImplementationMode(
            decoded.modelSpec(),
            ImplementationMode.DESIGNER_GENERATED
        );
        ModelSpecView visualModel = snapshotCodec.toUpdatedView(
            currentModel,
            visualModelCommand,
            currentModel.revision(),
            clock.instant()
        );
        ImplementationView visualImplementation = visualImplementation(
            modelSpecId,
            visualModel,
            currentImplementation,
            decoded.visualImplementation()
        );
        LinkedHashMap<String, String> generated = compiledFiles(tenantId, visualModel, visualImplementation);
        Map<String, String> assembled = CanonicalDbtProjectBundleAssembler.assemble(
            visualImplementation.projectKey(),
            visualImplementation.materialization(),
            generated
        );

        java.util.Set<String> managedPaths = managedPaths(draft, projectionSummary);
        java.util.Set<String> initializationPaths = new java.util.LinkedHashSet<>();
        Map<String, FileInput> submittedByPath = new LinkedHashMap<>();
        submittedFiles.forEach(file -> submittedByPath.put(file.path(), file));
        addMatchingInitializationFiles(initializationPaths, submittedByPath, draft, assembled);
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        submittedFiles.forEach(file -> merged.put(file.path(), file.content()));
        merged.remove("dbt_project.yml");
        managedPaths.forEach(merged::remove);
        compilerOwnedPaths.forEach(merged::remove);
        initializationPaths.forEach(merged::remove);
        for (Map.Entry<String, String> generatedFile : assembled.entrySet()) {
            String path = generatedFile.getKey();
            if (
                merged.containsKey(path) &&
                !path.startsWith("models/.dts_dependencies/") &&
                !Objects.equals(merged.get(path), generatedFile.getValue())
            ) {
                throw DbtImplementationDraftContract.conflict(
                    "MODEL_AUTHORING_UNMANAGED_FILE_CHANGED",
                    "Visual compilation cannot overwrite an unmanaged bundle file"
                );
            }
            merged.put(path, generatedFile.getValue());
        }
        return DbtImplementationDraftContract.normalizeFiles(
            merged.entrySet().stream().map(entry -> new FileInput(entry.getKey(), entry.getValue())).toList()
        );
    }

    /**
     * Reconstructs compiler ownership from generated-content or immutable frozen-snapshot
     * evidence instead of treating an entire output directory as managed. The current
     * implementation covers a newly opened draft; the saved authoring snapshot covers subsequent
     * visual saves before implementation commit.
     */
    private java.util.Set<String> compilerOwnedPaths(
        String tenantId,
        UUID modelSpecId,
        DraftRow draft,
        ModelSpecView currentModel,
        ImplementationView currentImplementation
    ) {
        Map<String, FileInput> persisted = currentWorkingFiles(draft);
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        Map<String, String> currentCompilerFiles = compilerBundleEvidence(tenantId, currentModel, currentImplementation);
        addMatchingCompilerFiles(
            paths,
            persisted,
            currentCompilerFiles
        );
        addMatchingFrozenCompilerFiles(paths, persisted, draft, currentCompilerFiles.keySet());
        addMatchingLegacyFrozenSchemaFiles(paths, persisted, draft, currentImplementation, currentCompilerFiles);
        addMatchingHistoricalFrozenCompilerFiles(paths, persisted, draft, currentCompilerFiles.keySet());

        Map<String, String> savedCompilerFiles = authoringCompilerEvidence(
            tenantId,
            modelSpecId,
            draft,
            currentModel,
            currentImplementation
        );
        addMatchingCompilerFiles(paths, persisted, savedCompilerFiles);
        addMatchingFrozenCompilerFiles(paths, persisted, draft, savedCompilerFiles.keySet());
        addMatchingLegacyFrozenSchemaFiles(paths, persisted, draft, currentImplementation, savedCompilerFiles);
        addMatchingHistoricalFrozenCompilerFiles(paths, persisted, draft, savedCompilerFiles.keySet());
        // A definition save can advance the base while the working files still carry the old
        // revision path. Prove their ownership by recompiling the saved snapshot at that revision.
        var decoded = snapshotDecoder.decode(jsonNode(draft.modelSpecSnapshot()));
        if (decoded.valid() && decoded.visualImplementation() != null) {
            java.util.Set<Integer> priorRevisions = new java.util.TreeSet<>();
            Pattern revisionPath = Pattern.compile("/v([1-9][0-9]*)/i[1-9][0-9]*/");
            for (String path : persisted.keySet()) {
                if (compilerArtifactSignature(path) == null) continue;
                Matcher match = revisionPath.matcher(path);
                if (!match.find()) continue;
                try {
                    int revision = Integer.parseInt(match.group(1));
                    if (revision < currentModel.revision()) priorRevisions.add(revision);
                } catch (NumberFormatException ignored) {
                    // An unrepresentable revision is not compiler ownership evidence.
                }
            }
            for (int revision : priorRevisions) {
                ModelSpecView historical = snapshotCodec.toUpdatedView(currentModel, decoded.modelSpec(), revision, clock.instant());
                addMatchingCompilerFiles(paths, persisted,
                    authoringCompilerEvidence(tenantId, modelSpecId, draft, historical, currentImplementation));
            }
        }
        return java.util.Set.copyOf(paths);
    }

    private Map<String, String> authoringCompilerEvidence(
        String tenantId,
        UUID modelSpecId,
        DraftRow draft,
        ModelSpecView currentModel,
        ImplementationView currentImplementation
    ) {
        JsonNode savedSnapshot = jsonNode(draft.modelSpecSnapshot());
        var decoded = snapshotDecoder.decode(savedSnapshot);
        if (!decoded.valid() || decoded.visualImplementation() == null) return Map.of();
        try {
            ModelSpecView savedModel = snapshotCodec.toUpdatedView(
                currentModel,
                withImplementationMode(decoded.modelSpec(), ImplementationMode.DESIGNER_GENERATED),
                currentModel.revision(),
                clock.instant()
            );
            ImplementationView savedImplementation = visualImplementation(
                modelSpecId,
                savedModel,
                currentImplementation,
                decoded.visualImplementation()
            );
            return compilerBundleEvidence(tenantId, savedModel, savedImplementation);
        } catch (RuntimeException ignored) {
            // Missing reconstruction evidence must keep the file unmanaged and protected.
            return Map.of();
        }
    }

    private Map<String, String> compilerBundleEvidence(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation
    ) {
        if (
            model == null ||
            implementation == null ||
            implementation.ownership() != ImplementationMode.DESIGNER_GENERATED
        ) {
            return Map.of();
        }
        try {
            String materialization = implementation.materialization() == null
                ? model.materialization()
                : implementation.materialization();
            return CanonicalDbtProjectBundleAssembler.assemble(
                implementation.projectKey(),
                materialization,
                compiledFiles(tenantId, model, implementation)
            );
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static void addMatchingCompilerFiles(
        java.util.Set<String> ownedPaths,
        Map<String, FileInput> persisted,
        Map<String, String> expected
    ) {
        expected.forEach((path, content) -> {
            if ("dbt_project.yml".equals(path) || path.startsWith("models/.dts_dependencies/")) return;
            FileInput actual = persisted.get(path);
            if (actual != null && (
                Objects.equals(actual.content(), content) ||
                (compilerArtifactSignature(path) != null &&
                    (Objects.equals(stableCompilerContent(actual.content()), stableCompilerContent(content)) ||
                        Objects.equals(stableCompilerContent(actual.content()), stableCompilerContent(legacyCompilerRefs(content, expected)))))
            )) ownedPaths.add(path);
        });
    }

    private static String stableCompilerContent(String content) {
        // Older visual drafts hashed JSON insertion order and the request idempotency key.
        // Only that generated metadata value may differ; every other byte remains protected.
        if (!content.startsWith("{{ config(") || !content.contains("\n-- generated by DTS modeling compiler, revision ")) {
            return content;
        }
        return content.replaceFirst("'implementationChecksum':'[0-9a-f]{64}'", "'implementationChecksum':'<content-checksum>'");
    }

    private static String legacyCompilerRefs(String content, Map<String, String> expected) {
        String result = content;
        Pattern identity = Pattern.compile("-- Managed dependency proxy for model\\.[A-Za-z0-9_]+\\.([A-Za-z0-9_]+)\\. Do not rename or delete\\.");
        for (var file : expected.entrySet()) {
            if (!file.getKey().startsWith("models/.dts_dependencies/dts_ref_") || !file.getKey().endsWith(".sql")) continue;
            Matcher match = identity.matcher(file.getValue());
            if (!match.find()) continue;
            String proxy = file.getKey().substring(file.getKey().lastIndexOf('/') + 1, file.getKey().length() - 4);
            result = result.replace("{{ ref('" + proxy + "') }}", "{{ ref('" + match.group(1) + "') }}");
        }
        return result;
    }

    /**
     * A compiler upgrade can legitimately change generated content between opening and saving an
     * older draft. In that case the immutable source snapshot proves ownership, but only for exact
     * compiler-reserved paths whose working content has not been edited since the draft opened.
     */
    private void addMatchingFrozenCompilerFiles(
        java.util.Set<String> ownedPaths,
        Map<String, FileInput> persisted,
        DraftRow draft,
        java.util.Set<String> expectedPaths
    ) {
        if (draft.sourceBundleSnapshot() == null || expectedPaths.isEmpty()) return;
        SourceBundleView frozen = sourceBundleSnapshot(draft.sourceBundleSnapshot());
        if (frozen.sourceKind() == SourceBundleKind.FROZEN_SOURCE_BUNDLE) return;
        Map<String, String> frozenFiles = new LinkedHashMap<>();
        frozen.files().forEach(file -> frozenFiles.put(file.path(), file.content()));
        expectedPaths.forEach(path -> {
            if ("dbt_project.yml".equals(path) || path.startsWith("models/.dts_dependencies/")) return;
            FileInput actual = persisted.get(path);
            if (actual != null && Objects.equals(actual.content(), frozenFiles.get(path))) ownedPaths.add(path);
        });
    }

    private void addMatchingHistoricalFrozenCompilerFiles(
        java.util.Set<String> ownedPaths,
        Map<String, FileInput> persisted,
        DraftRow draft,
        java.util.Set<String> expectedPaths
    ) {
        if (draft.sourceBundleSnapshot() == null || expectedPaths.isEmpty()) return;
        SourceBundleView frozen = sourceBundleSnapshot(draft.sourceBundleSnapshot());
        Map<String, String> frozenFiles = new LinkedHashMap<>();
        frozen.files().forEach(file -> frozenFiles.put(file.path(), file.content()));
        Map<String, String> expectedBySignature = new LinkedHashMap<>();
        expectedPaths.forEach(path -> {
            String signature = compilerArtifactSignature(path);
            if (signature != null && persisted.containsKey(path)) expectedBySignature.put(signature, path);
        });
        frozenFiles.forEach((path, content) -> {
            String signature = compilerArtifactSignature(path);
            String currentPath = signature == null ? null : expectedBySignature.get(signature);
            FileInput actual = persisted.get(path);
            if (
                currentPath != null &&
                !Objects.equals(path, currentPath) &&
                actual != null &&
                Objects.equals(actual.content(), content)
            ) {
                ownedPaths.add(path);
            }
        });
    }

    /**
     * Upgrades an older generated schema at the current artifact path only when its frozen
     * predecessor proves the exact legacy column-description mapping. This is deliberately not
     * used by historical-file cleanup: the current artifact must remain available for replacement.
     */
    private void addMatchingLegacyFrozenSchemaFiles(
        java.util.Set<String> ownedPaths,
        Map<String, FileInput> persisted,
        DraftRow draft,
        ImplementationView currentImplementation,
        Map<String, String> expected
    ) {
        if (draft.sourceBundleSnapshot() == null || expected.isEmpty()) return;
        SourceBundleView frozen = sourceBundleSnapshot(draft.sourceBundleSnapshot());
        if (
            frozen.sourceKind() == SourceBundleKind.FROZEN_SOURCE_BUNDLE &&
            !trustedFrozenVisualSource(draft, currentImplementation)
        ) return;
        Map<String, String> frozenFiles = new LinkedHashMap<>();
        frozen.files().forEach(file -> frozenFiles.put(file.path(), file.content()));
        expected.forEach((currentPath, expectedContent) -> {
            if (!isVersionedSchemaPath(currentPath)) return;
            FileInput actual = persisted.get(currentPath);
            if (actual == null) return;
            String signature = compilerArtifactSignature(currentPath);
            if (signature == null) return;
            frozenFiles.forEach((frozenPath, frozenContent) -> {
                if (Objects.equals(frozenPath, currentPath) || !Objects.equals(signature, compilerArtifactSignature(frozenPath))) return;
                Map<String, String> descriptions = legacyColumnDescriptions(frozenContent);
                String normalizedActual = stripProvenLegacyColumnDescriptions(actual.content(), descriptions);
                if (normalizedActual != null && normalizedActual.equals(expectedContent)) ownedPaths.add(currentPath);
            });
        });
    }

    private boolean trustedFrozenVisualSource(DraftRow draft, ImplementationView currentImplementation) {
        if (
            currentImplementation == null ||
            (currentImplementation.ownership() != ImplementationMode.DBT_MANAGED &&
                currentImplementation.ownership() != ImplementationMode.DESIGNER_GENERATED) ||
            currentImplementation.inputMode() != InputMode.GENERATED ||
            !Objects.equals(draft.baseImplementationRevision(), currentImplementation.implementationRevision()) ||
            !Objects.equals(draft.baseImplementationChecksum(), currentImplementation.implementationChecksum())
        ) return false;
        ModelAuthoringSnapshotDecoder.DecodeResult decoded = snapshotDecoder.decode(jsonNode(draft.modelSpecSnapshot()));
        return decoded.valid() && decoded.visualImplementation() != null;
    }

    private static boolean isVersionedSchemaPath(String path) {
        Matcher matcher = path == null ? null : VERSIONED_COMPILER_ARTIFACT.matcher(path);
        return matcher != null && matcher.matches() && matcher.group(2).endsWith(".yml");
    }

    private static Map<String, String> legacyColumnDescriptions(String yaml) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        String currentColumn = null;
        if (yaml == null) return Map.of();
        for (String line : yaml.split("\\n", -1)) {
            if (line.startsWith("      - name: ")) {
                currentColumn = line.substring("      - name: ".length()).trim();
                continue;
            }
            if (currentColumn != null && line.startsWith("        description: \"") && line.endsWith("\"")) {
                String value = line.substring("        description: \"".length(), line.length() - 1);
                if (!("模型字段".equals(value) || value.startsWith("数据标准 ")) || descriptions.putIfAbsent(currentColumn, value) != null) {
                    return Map.of();
                }
            }
        }
        return descriptions;
    }

    private static String stripProvenLegacyColumnDescriptions(String yaml, Map<String, String> descriptions) {
        if (descriptions == null || descriptions.isEmpty() || yaml == null) return null;
        StringBuilder normalized = new StringBuilder();
        String currentColumn = null;
        String[] lines = yaml.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.startsWith("      - name: ")) currentColumn = line.substring("      - name: ".length()).trim();
            if (currentColumn != null && line.startsWith("        description: \"") && line.endsWith("\"")) {
                String value = line.substring("        description: \"".length(), line.length() - 1);
                if (!Objects.equals(value, descriptions.get(currentColumn))) return null;
                continue;
            }
            normalized.append(line);
            if (index < lines.length - 1) normalized.append("\n");
        }
        return normalized.toString();
    }

    private static String compilerArtifactSignature(String path) {
        Matcher matcher = path == null ? null : VERSIONED_COMPILER_ARTIFACT.matcher(path);
        return matcher != null && matcher.matches() ? matcher.group(1) + "/" + matcher.group(2) : null;
    }

    private LinkedHashMap<String, String> compiledFiles(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation
    ) {
        try {
            List<ArtifactWrite> artifacts = visualCompiler.compile(tenantId, model, implementation);
            LinkedHashMap<String, String> files = new LinkedHashMap<>();
            for (ArtifactWrite artifact : artifacts == null ? List.<ArtifactWrite>of() : artifacts) {
                if (artifact == null || artifact.path() == null || artifact.content() == null) {
                    throw sourceBundleUnavailable();
                }
                String previous = files.putIfAbsent(artifact.path(), artifact.content());
                if (previous != null && !Objects.equals(previous, artifact.content())) throw sourceBundleUnavailable();
            }
            if (files.isEmpty()) throw sourceBundleUnavailable();
            if (implementation.inputMode() == InputMode.UPSTREAM_MODEL && dependencies != null) {
                String targetName = implementation.dbtUniqueId().substring(implementation.dbtUniqueId().lastIndexOf('.') + 1);
                Resolution resolution = resolveDependencies(tenantId, model, implementation, implementation.projectKey(), targetName);
                if (resolution != null) {
                    var canonical = canonicalProjects.initialize(model.id(), implementation.materialization(), targetName, resolution);
                    Map<String, String> refs = new LinkedHashMap<>();
                    for (var input : resolution.snapshot().modelInputs()) {
                        String name = input.dbtUniqueId().substring(input.dbtUniqueId().lastIndexOf('.') + 1);
                        addDependencyAlias(refs, name, DbtCanonicalProjectReconstructor.modelProxyName(input.modelSpecId()));
                    }
                    files.replaceAll((path, content) -> {
                        if (!path.endsWith(".sql")) return content;
                        String bound = content;
                        for (var ref : refs.entrySet()) {
                            bound = bound.replace("{{ ref('" + ref.getKey() + "') }}", "{{ ref('" + ref.getValue() + "') }}");
                        }
                        return bound;
                    });
                    canonical.files().forEach((path, content) -> {
                        if (path.startsWith("models/.dts_dependencies/")) files.put(path, content);
                    });
                }
            }
            return files;
        } catch (DraftException failure) {
            throw failure;
        } catch (ModelSpecException failure) {
            throw failure;
        } catch (CompileException failure) {
            throw DbtImplementationDraftContract.unprocessable(
                failure.getMessage(),
                "The visual implementation cannot be compiled under the current model contract"
            );
        } catch (RuntimeException failure) {
            throw sourceBundleUnavailable();
        }
    }

    private ImplementationView visualImplementation(
        UUID modelSpecId,
        ModelSpecView model,
        ImplementationView current,
        ModelAuthoringSnapshotDecoder.VisualImplementationSnapshot snapshot
    ) {
        SaveImplementationCommand command = snapshot.command();
        String checksum = implementationChecksums.contentChecksum(command);
        int implementationRevision = current == null ? 1 : current.implementationRevision();
        return new ImplementationView(
            current == null
                ? UUID.nameUUIDFromBytes((modelSpecId + ":visual-authoring").getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : current.id(),
            modelSpecId,
            model.planId(),
            model.revision(),
            model.checksum(),
            ImplementationMode.DESIGNER_GENERATED,
            snapshot.projectKey(),
            snapshot.dbtUniqueId(),
            // This is an in-memory compilation candidate, not the persisted draft lifecycle state.
            current == null || current.status() == null ? "ACTIVE" : current.status(),
            implementationRevision,
            checksum,
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            command.settings(),
            command.materialization()
        );
    }

    private Map<String, FileInput> currentWorkingFiles(DraftRow current) {
        LinkedHashMap<String, FileInput> files = new LinkedHashMap<>();
        List<FileRow> persisted = repository.listFiles(current.id());
        if (persisted != null && !persisted.isEmpty()) {
            persisted.forEach(file -> files.put(file.path(), new FileInput(file.path(), file.content())));
            return files;
        }
        if (current.sourceBundleSnapshot() == null) return files;
        SourceBundleView source = sourceBundleSnapshot(current.sourceBundleSnapshot());
        source.files().forEach(file -> files.put(file.path(), new FileInput(file.path(), file.content())));
        return files;
    }

    private java.util.Set<String> managedPaths(DraftRow current, JsonNode submittedProjection) {
        JsonNode projection = submittedProjection != null && submittedProjection.isObject()
            ? submittedProjection
            : jsonNode(current.projectionSummary());
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        if (projection != null && projection.path("managedPaths").isArray()) {
            projection.path("managedPaths").forEach(path -> {
                String value = path.asText("").trim();
                if (!value.isEmpty()) paths.add(value);
            });
        }
        return paths;
    }

    @Transactional
    public ValidationView validate(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        ValidateDraftRequest request
    ) {
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        try {
            return validateInternal(tenantId, actorId, modelSpecId, draftId, request, correlationId);
        } catch (RuntimeException failure) {
            throw auditedFailure(AUDIT_VALIDATE, tenantId, actorId, modelSpecId, draftId, request, correlationId, failure);
        }
    }

    private ValidationView validateInternal(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        ValidateDraftRequest request,
        String correlationId
    ) {
        DraftRow current = requireDraft(tenantId, actorId, modelSpecId, draftId);
        String expectedEtag = expectedEtag(request == null ? null : request.expectedEtag());
        Instant now = clock.instant();
        requireNotExpired(current, now);
        if (current.state() == DraftState.COMMITTED || current.state() == DraftState.COMMITTING) {
            throw DbtImplementationDraftContract.conflict("DBT_DRAFT_NOT_VALIDATABLE", "The draft is no longer validatable");
        }
        if (!Objects.equals(current.etag(), expectedEtag)) throw etagConflict(current, expectedEtag);
        requireValidModelSnapshot(current);
        current = alignAuthoringBase(tenantId, actorId, modelSpecId, current, now);
        List<FileRow> files = repository.listFiles(draftId);
        if (files.isEmpty()) {
            throw DbtImplementationDraftContract.unprocessable(
                "DBT_DRAFT_FILES_REQUIRED",
                "Save the isolated dbt project before validation"
            );
        }
        ValidationDraftContent repaired = repairHistoricalCompilerFiles(
            tenantId,
            actorId,
            modelSpecId,
            current,
            files,
            now
        );
        current = repaired.draft();
        files = repaired.files();
        String validationEtag = current.etag();
        DraftRow validationDraft = current;
        ValidatedProject validated = staticValidate(files);
        BundleSnapshot bundle = freezeBundle(files, validated);
        List<Diagnostic> diagnostics = diagnostics(validated);
        List<ProposedNode> structure = structure(validated);
        DependencyValidationView dependencyValidation = validateDependencies(
            tenantId,
            actorId,
            modelSpecId,
            current,
            validated
        );
        DraftRow saved = repository
            .markValidated(
                tenantId,
                modelSpecId,
                draftId,
                actorId,
                validationEtag,
                nextEtag(),
                validated.validatedChecksum(),
                bundle.projectChecksum(),
                bundle.bundleChecksum(),
                bundle.manifest(),
                validationSummary(diagnostics, structure, dependencyValidation),
                now
            )
            .orElseThrow(() -> etagConflict(validationDraft, validationEtag));
        audit(
            AUDIT_VALIDATE,
            saved,
            files.size(),
            validated.validatedChecksum(),
            diagnostics.size(),
            structure.size(),
            bundle.bundleChecksum(),
            correlationId
        );
        return new ValidationView(
            saved.id(),
            saved.state(),
            saved.etag(),
            saved.expiresAt(),
            validated.validatedChecksum(),
            diagnostics,
            structure,
            dependencyValidation
        );
    }

    private ValidationDraftContent repairHistoricalCompilerFiles(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        DraftRow draft,
        List<FileRow> files,
        Instant now
    ) {
        if (visualCompiler == null || draft.modelSpecSnapshot() == null || draft.sourceBundleSnapshot() == null) {
            return new ValidationDraftContent(draft, files);
        }
        try {
            ModelSpecView currentModel = requireEditableModel(tenantId, actorId, modelSpecId, draft.planId(), true);
            requireModelPins(draft, currentModel);
            ImplementationView currentImplementation = lifecycle.timeline(tenantId, modelSpecId).implementation();
            requireImplementationPin(
                modelSpecId,
                currentImplementation,
                draft.baseImplementationRevision(),
                draft.baseImplementationChecksum(),
                true
            );
            Map<String, FileInput> persisted = new LinkedHashMap<>();
            files.forEach(file -> persisted.put(file.path(), new FileInput(file.path(), file.content())));
            Map<String, String> expected = authoringCompilerEvidence(
                tenantId,
                modelSpecId,
                draft,
                currentModel,
                currentImplementation
            );
            java.util.Set<String> historical = new java.util.LinkedHashSet<>();
            addMatchingHistoricalFrozenCompilerFiles(historical, persisted, draft, expected.keySet());
            Map<String, String> presentReplacements = new LinkedHashMap<>();
            expected.forEach((path, content) -> {
                FileInput actual = persisted.get(path);
                if (actual != null && Objects.equals(actual.content(), content)) presentReplacements.put(path, content);
            });
            addMatchingInitializationFiles(historical, persisted, draft, presentReplacements);
            if (historical.isEmpty()) return new ValidationDraftContent(draft, files);
            List<FileInput> retained = DbtImplementationDraftContract.normalizeFiles(
                files
                    .stream()
                    .filter(file -> !historical.contains(file.path()))
                    .map(file -> new FileInput(file.path(), file.content()))
                    .toList()
            );
            DraftRow repaired = repository
                .replaceFiles(
                    tenantId,
                    modelSpecId,
                    draft.id(),
                    actorId,
                    draft.etag(),
                    nextEtag(),
                    retained,
                    now
                )
                .orElseThrow(() -> etagConflict(draft, draft.etag()));
            return new ValidationDraftContent(repaired, repository.listFiles(draft.id()));
        } catch (DraftException failure) {
            throw failure;
        } catch (RuntimeException ignored) {
            return new ValidationDraftContent(draft, files);
        }
    }

    /** Only immutable, unedited initialization SQL can be superseded by visual compiler output. */
    private void addMatchingInitializationFiles(
        java.util.Set<String> ownedPaths,
        Map<String, FileInput> working,
        DraftRow draft,
        Map<String, String> replacements
    ) {
        if (draft.sourceBundleSnapshot() == null || replacements.isEmpty()) return;
        SourceBundleView frozen = sourceBundleSnapshot(draft.sourceBundleSnapshot());
        if (frozen.sourceKind() != SourceBundleKind.CANONICAL_INITIALIZATION) return;
        boolean hasCompiledSql = replacements.keySet().stream()
            .anyMatch(path -> path.endsWith(".sql") && compilerArtifactSignature(path) != null);
        if (!hasCompiledSql) return;
        for (BundleFileView file : frozen.files()) {
            if (!file.path().matches("models/[a-z][a-z0-9_]{0,62}\\.sql") || replacements.containsKey(file.path())) continue;
            FileInput actual = working.get(file.path());
            if (actual != null && Objects.equals(actual.content(), file.content())) ownedPaths.add(file.path());
        }
    }

    private record ValidationDraftContent(DraftRow draft, List<FileRow> files) {}

    private DependencyValidationView validateDependencies(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        DraftRow draft,
        ValidatedProject validated
    ) {
        if (dependencies == null || draft.sourceBundleSnapshot() == null) return null;
        SourceBundleView source = sourceBundleSnapshot(draft.sourceBundleSnapshot());
        boolean unifiedAuthoring = draft.modelSpecSnapshot() != null;
        // A repairable authoring draft can start without a resolved dependency baseline. It must
        // still validate its saved intent; an absent baseline is never proof of valid dependencies.
        if (source.dependencySnapshot() == null && !unifiedAuthoring) return null;
        ModelSpecView model = requireEditableModel(
            tenantId,
            actorId,
            modelSpecId,
            draft.planId(),
            unifiedAuthoring
        );
        requireModelPins(draft, model);
        ImplementationView implementation = lifecycle.timeline(tenantId, modelSpecId).implementation();
        requireImplementationPin(
            modelSpecId,
            implementation,
            draft.baseImplementationRevision(),
            draft.baseImplementationChecksum(),
            unifiedAuthoring
        );
        ValidatedNode ownedTarget = draftTarget(validated, draft, implementation);
        var decoded = unifiedAuthoring ? snapshotDecoder.decode(jsonNode(draft.modelSpecSnapshot())) : null;
        boolean structuredVisual = decoded != null && decoded.valid() && decoded.visualImplementation() != null;
        Resolution current;
        if (structuredVisual) {
            // Owner/implementation CAS above protects the base. Explicit saved input pins are the new intent;
            // requiring obsolete base dependency pins here would make updating a stale reference impossible.
            ModelSpecView authoredModel = snapshotCodec.toUpdatedView(model, requireValidModelSnapshot(draft), model.revision(), clock.instant());
            if (sourceFields != null) sourceFields.requireValid(tenantId, authoredModel, decoded.visualImplementation().command());
            ImplementationView authoredImplementation = visualImplementation(modelSpecId, authoredModel, implementation, decoded.visualImplementation());
            if (com.yuzhi.dts.platform.service.modeling.ModelSchemaOnlySupport.isSchemaOnly(authoredImplementation) &&
                !"dts_schema_only".equals(ownedTarget.materialization())) {
                throw DbtImplementationDraftContract.unprocessable("MODEL_SCHEMA_ONLY_SQL_MISMATCH",
                    "Structure-only authoring must retain the registered empty-table materialization");
            }
            current = resolveDependencies(tenantId, authoredModel, authoredImplementation, validated.projectKey(), ownedTarget.name());
        } else {
            current = resolveDependencies(tenantId, model, implementation, validated.projectKey(), ownedTarget.name());
            if (source.dependencySnapshot() != null &&
                !Objects.equals(source.dependencyChecksum(), current.snapshot().dependencyChecksum())) {
                throw dependencyFailure("MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE", "ModelSpec dependencies changed after the dbt draft was created",
                    Map.of("expectedDependencyChecksum", source.dependencyChecksum(), "currentDependencyChecksum", current.snapshot().dependencyChecksum()));
            }
            if (unifiedAuthoring) {
                ModelSpecView authoredModel = snapshotCodec.toUpdatedView(model, requireValidModelSnapshot(draft), model.revision(), clock.instant());
                current = resolveDependencies(tenantId, authoredModel, null, validated.projectKey(), ownedTarget.name());
            }
        }
        List<String> parsed = externalDependencies(
            validated,
            ownedTarget,
            dependencyAliases(validated.projectKey(), current, source.managedDependencyAliases())
        );
        Reconciliation reconciliation;
        try {
            reconciliation = dependencies.reconcile(current.snapshot(), parsed);
        } catch (ModelSpecException failure) {
            throw dependencyFailure(failure.code(), failure.getMessage(), failure.details());
        }
        var physicalReferences = current.physicalSourceFacts().values().stream()
            .filter(PhysicalSourceFact::current).map(PhysicalSourceFact::executableRef).filter(Objects::nonNull).toList();
        for (var node : validated.nodes()) {
            com.yuzhi.dts.platform.service.modeling.ModelingSqlReadSetGuard.requireDeclared(node.sql(), physicalReferences);
        }
        return new DependencyValidationView(
            current.snapshot().dependencyChecksum(),
            reconciliation.matched(),
            reconciliation.missing(),
            reconciliation.undeclared()
        );
    }

    /**
     * Reconciles the compiler's executable source name with the stable source identity pinned in
     * the dependency snapshot. The alias is derived only from the current confirmed binding fact;
     * arbitrary source calls therefore remain undeclared.
     */
    static Map<String, String> dependencyAliases(
        String projectKey,
        Resolution dependencies,
        Map<String, String> managedAliases
    ) {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        (managedAliases == null ? Map.<String, String>of() : managedAliases)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> addDependencyAlias(aliases, entry.getKey(), entry.getValue()));
        if (dependencies == null || dependencies.snapshot() == null) return Map.copyOf(aliases);

        String normalizedProject = dbtSegment(projectKey);
        for (var input : dependencies.snapshot().modelInputs()) {
            addDependencyAlias(aliases, "model." + normalizedProject + "." +
                DbtCanonicalProjectReconstructor.modelProxyName(input.modelSpecId()), input.dbtUniqueId());
        }
        for (PhysicalSource source : dependencies.snapshot().physicalSources()) {
            PhysicalSourceFact fact = dependencies.physicalSourceFacts().get(source.sourceBindingId());
            if (
                fact == null ||
                !fact.current() ||
                !Objects.equals(source.resolvedVersion(), fact.resolvedVersion()) ||
                fact.executableRef() == null ||
                fact.executableRef().isBlank() ||
                "DBT_NODE".equalsIgnoreCase(fact.sourceType())
            ) {
                continue;
            }
            String executableRef = fact.executableRef().trim();
            int separator = executableRef.indexOf('.');
            String namespace = separator > 0 ? executableRef.substring(0, separator) : "ods";
            String name = separator > 0 ? executableRef.substring(separator + 1) : executableRef;
            String compilerIdentity =
                "source." + normalizedProject + "." + dbtSegment(namespace) + "." + dbtSegment(name);
            addDependencyAlias(aliases, compilerIdentity, source.dbtSourceUniqueId());
        }
        return Map.copyOf(aliases);
    }

    private static void addDependencyAlias(Map<String, String> aliases, String alias, String dependency) {
        if (alias == null || alias.isBlank() || dependency == null || dependency.isBlank()) {
            throw sourceBundleUnavailable();
        }
        String previous = aliases.putIfAbsent(alias, dependency);
        if (previous != null && !Objects.equals(previous, dependency)) throw sourceBundleUnavailable();
    }

    /** Mirrors the canonical identifier normalization used by the shared static dbt adapter. */
    private static String dbtSegment(String value) {
        String normalized = Normalizer
            .normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_")
            .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) throw sourceBundleUnavailable();
        return normalized;
    }

    static List<String> externalDependencies(
        ValidatedProject project,
        ValidatedNode target,
        Map<String, String> managedAliases
    ) {
        LinkedHashMap<String, ValidatedNode> internalNodes = new LinkedHashMap<>();
        if (project != null && project.nodes() != null) {
            project.nodes().stream().filter(Objects::nonNull).forEach(node -> internalNodes.put(node.dbtUniqueId(), node));
        }
        java.util.Set<String> external = new java.util.TreeSet<>();
        java.util.Set<String> visiting = new java.util.LinkedHashSet<>();
        Map<String, String> aliases = managedAliases == null ? Map.of() : managedAliases;
        for (String dependency : target == null || target.dependencies() == null ? List.<String>of() : target.dependencies()) {
            collectExternalDependency(dependency, internalNodes, aliases, visiting, external);
        }
        return List.copyOf(external);
    }

    private static void collectExternalDependency(
        String dependency,
        Map<String, ValidatedNode> internalNodes,
        Map<String, String> aliases,
        java.util.Set<String> visiting,
        java.util.Set<String> external
    ) {
        if (dependency == null || dependency.isBlank()) return;
        String aliased = aliases.get(dependency);
        if (aliased != null && !aliased.isBlank()) {
            external.add(aliased);
            return;
        }
        ValidatedNode internal = internalNodes.get(dependency);
        if (internal == null) {
            external.add(dependency);
            return;
        }
        if (!visiting.add(dependency)) return;
        for (String nested : internal.dependencies() == null ? List.<String>of() : internal.dependencies()) {
            collectExternalDependency(nested, internalNodes, aliases, visiting, external);
        }
        visiting.remove(dependency);
    }

    @Transactional
    public CommitView commit(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        CommitDraftRequest request
    ) {
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        try {
            return commitInternal(tenantId, actorId, modelSpecId, draftId, request, correlationId);
        } catch (RuntimeException failure) {
            throw auditedFailure(AUDIT_COMMIT, tenantId, actorId, modelSpecId, draftId, request, correlationId, failure);
        }
    }

    private CommitView commitInternal(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        CommitDraftRequest request,
        String correlationId
    ) {
        DraftRow current = requireDraft(tenantId, actorId, modelSpecId, draftId);
        String idempotencyKey = DbtImplementationDraftContract.requiredText(
            request == null ? null : request.idempotencyKey(),
            "idempotencyKey",
            128
        );
        String validatedChecksum = DbtImplementationDraftContract.requiredChecksum(
            request == null ? null : request.validatedChecksum(),
            "validatedChecksum"
        );
        SourceBundleView frozenSource = current.sourceBundleSnapshot() == null
            ? null
            : sourceBundleSnapshot(current.sourceBundleSnapshot());
        String dependencyChecksum = commitDependencyChecksum(current, frozenSource);
        if (dependencyChecksum != null) {
            String suppliedDependencyChecksum = DbtImplementationDraftContract.requiredChecksum(
                request == null ? null : request.dependencyChecksum(),
                "dependencyChecksum"
            );
            if (!Objects.equals(dependencyChecksum, suppliedDependencyChecksum)) {
                throw DbtImplementationDraftContract.precondition(
                    "DBT_DRAFT_DEPENDENCY_PIN_STALE",
                    "The supplied dependency checksum is not current"
                );
            }
        }
        String frozenBundleChecksum = requireFrozenChecksum(current.bundleChecksum());
        String derivedCommitKey = commitKey(
            idempotencyKey,
            validatedChecksum,
            frozenBundleChecksum,
            dependencyChecksum
        );
        if (current.state() == DraftState.COMMITTED) {
            if (
                Objects.equals(current.commitIdempotencyKey(), derivedCommitKey) &&
                Objects.equals(current.validatedChecksum(), validatedChecksum)
            ) {
                audit(
                    AUDIT_COMMIT,
                    current,
                    0,
                    validatedChecksum,
                    null,
                    current.artifactCount(),
                    frozenBundleChecksum,
                    correlationId
                );
                return committedView(current, dependencyChecksum);
            }
            throw DbtImplementationDraftContract.conflict(
                "DBT_DRAFT_COMMIT_IDEMPOTENCY_CONFLICT",
                "The draft was already committed with another idempotency payload"
            );
        }
        String expectedEtag = expectedEtag(request.expectedEtag());
        Instant now = clock.instant();
        requireNotExpired(current, now);
        if (current.state() != DraftState.VALIDATED) {
            throw DbtImplementationDraftContract.conflict(
                "DBT_DRAFT_VALIDATION_REQUIRED",
                "Only a VALIDATED draft can be committed"
            );
        }
        if (!Objects.equals(current.etag(), expectedEtag)) throw etagConflict(current, expectedEtag);
        if (!Objects.equals(current.validatedChecksum(), validatedChecksum)) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_VALIDATED_CHECKSUM_CONFLICT",
                "The supplied validated checksum is not current"
            );
        }

        List<FileRow> files = repository.listFiles(draftId);
        ValidatedProject validated = staticValidate(files);
        BundleSnapshot bundle = freezeBundle(files, validated);
        DependencyValidationView dependencyValidation = validateDependencies(
            tenantId,
            actorId,
            modelSpecId,
            current,
            validated
        );
        if (dependencyChecksum != null && (dependencyValidation == null ||
            !Objects.equals(dependencyChecksum, dependencyValidation.dependencyChecksum()))) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_DEPENDENCY_PIN_STALE",
                "Dependencies changed since validation; validate the draft again before committing"
            );
        }
        if (!Objects.equals(validated.validatedChecksum(), validatedChecksum)) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_VALIDATED_CONTENT_CONFLICT",
                "Draft content no longer matches the validated checksum"
            );
        }
        requireFrozenBundle(current, bundle);
        boolean unifiedAuthoring = current.modelSpecSnapshot() != null;
        ModelSpecView model = requireEditableModel(tenantId, actorId, modelSpecId, current.planId(), unifiedAuthoring);
        requireModelPins(current, model);
        TimelineView timeline = lifecycle.timeline(tenantId, modelSpecId);
        ImplementationView baseImplementation = timeline.implementation();
        requireImplementationPin(
            modelSpecId,
            baseImplementation,
            current.baseImplementationRevision(),
            current.baseImplementationChecksum(),
            unifiedAuthoring
        );
        ValidatedNode target = draftTarget(validated, current, baseImplementation);
        var committedSnapshot = unifiedAuthoring ? snapshotDecoder.decode(jsonNode(current.modelSpecSnapshot())) : null;
        var visualCommand = committedSnapshot != null && committedSnapshot.valid() && committedSnapshot.visualImplementation() != null
            ? committedSnapshot.visualImplementation().command() : null;
        boolean schemaOnly = visualCommand != null &&
            com.yuzhi.dts.platform.service.modeling.ModelSchemaOnlySupport.isSchemaOnly(visualCommand.inputMode(), visualCommand.inputs()) &&
            "dts_schema_only".equals(target.materialization());
        UpdateModelSpecCommand authoringSnapshot = unifiedAuthoring ? requireValidModelSnapshot(current) : null;
        // Pins above guard external changes; the validated bundle implements this saved draft definition.
        ModelSpecView authoredModel = authoringSnapshot == null
            ? model
            : snapshotCodec.toUpdatedView(model, authoringSnapshot, model.revision(), now);
        requireMaterialization(authoredModel, target, schemaOnly);
        List<ModelField> projectedFields = DbtModelFieldProjector.project(
            objectMapper,
            target.schema(),
            authoringSnapshot == null ? model.fields() : authoringSnapshot.fields()
        );

        Optional<DraftRow> claim = repository.claimCommit(
                tenantId,
                modelSpecId,
                draftId,
                actorId,
                expectedEtag,
                validatedChecksum,
                derivedCommitKey,
                nextEtag(),
                now
            );
        if (claim.isEmpty()) {
            DraftRow winner = requireDraft(tenantId, actorId, modelSpecId, draftId);
            if (
                winner.state() == DraftState.COMMITTED &&
                Objects.equals(winner.commitIdempotencyKey(), derivedCommitKey) &&
                Objects.equals(winner.validatedChecksum(), validatedChecksum)
            ) {
                audit(
                    AUDIT_COMMIT,
                    winner,
                    files.size(),
                    validatedChecksum,
                    diagnostics(validated).size(),
                    winner.artifactCount(),
                    bundle.bundleChecksum(),
                    correlationId
                );
                return committedView(winner, dependencyChecksum);
            }
            throw etagConflict(winner, expectedEtag);
        }
        DraftRow claimed = claim.orElseThrow();
        if (unifiedAuthoring) {
            model = modelSpecs.synchronizeAuthoringDraft(
                tenantId,
                actorId,
                modelSpecId,
                new ExpectedVersion(modelSpecId, model.revision(), model.checksum()),
                withProjectedFields(authoringSnapshot, projectedFields)
            );
        } else if (!Objects.equals(model.fields(), projectedFields)) {
            model = modelSpecs.synchronizeDbtManagedFields(
                tenantId,
                actorId,
                modelSpecId,
                new ExpectedVersion(modelSpecId, model.revision(), model.checksum()),
                projectedFields
            );
        }
        ImplementationView committedVisual = committedSnapshot != null && committedSnapshot.valid() && committedSnapshot.visualImplementation() != null
            ? visualImplementation(modelSpecId, model, null, committedSnapshot.visualImplementation()) : null;
        Resolution committedDependencies = dependencyValidation == null
            ? null
            : resolveDependencies(
                tenantId,
                model,
                committedVisual,
                validated.projectKey(),
                target.name()
            );
        ExpectedImplementationVersion expectedImplementation = new ExpectedImplementationVersion(
            modelSpecId,
            current.baseImplementationRevision() == null ? 0 : current.baseImplementationRevision(),
            current.baseImplementationChecksum()
        );
        LinkedHashMap<String, Object> generatedConfig = new LinkedHashMap<>();
        generatedConfig.put("projectKey", validated.projectKey());
        generatedConfig.put("dbtUniqueId", target.dbtUniqueId());
        generatedConfig.put("projectChecksum", bundle.projectChecksum());
        generatedConfig.put("bundleChecksum", bundle.bundleChecksum());
        if (schemaOnly) generatedConfig.put("buildMode", "SCHEMA_ONLY");
        Map<String, Object> visualImplementation = unifiedAuthoring
            ? visualImplementationConfig(current)
            : null;
        if (visualImplementation != null) generatedConfig.put("visualImplementation", visualImplementation);
        if (committedDependencies != null) {
            generatedConfig.put("dependencyChecksum", committedDependencies.snapshot().dependencyChecksum());
            generatedConfig.put(
                "dependencySnapshot",
                objectMapper.convertValue(
                    committedDependencies.snapshot(),
                    new TypeReference<Map<String, Object>>() {}
                )
            );
            generatedConfig.put(
                "managedDependencyAliases",
                dependencyAliases(validated.projectKey(), committedDependencies,
                    frozenSource == null ? Map.of() : frozenSource.managedDependencyAliases())
            );
        }
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.copyOf(generatedConfig))),
            List.of(),
            Map.of(
                "targetPhysicalName",
                committedTargetPhysicalName(visualImplementation, target.name()),
                "loadStrategy",
                "incremental".equalsIgnoreCase(model.materialization()) ? "INCREMENTAL" : "FULL",
                "partitionFields",
                List.of()
            ),
            ImplementationMode.DBT_MANAGED,
            model.materialization(),
            derivedCommitKey
        );
        ImplementationView implementation = lifecycle.saveImportedDbtImplementation(
            tenantId,
            actorId,
            modelSpecId,
            new ExpectedVersion(modelSpecId, model.revision(), model.checksum()),
            model.status(),
            expectedImplementation,
            validated.projectKey(),
            target.dbtUniqueId(),
            command
        );
        List<ImportedArtifact> artifacts = artifacts(
            validated,
            target,
            model.materialization(),
            bundle,
            files
        );
        ImportResult imported = artifactImports.importArtifacts(
            new ImportCommand(
                tenantId,
                modelSpecId,
                model.planId(),
                model.revision(),
                model.checksum(),
                model.status(),
                implementation.id(),
                implementation.implementationRevision(),
                implementation.implementationChecksum(),
                validated.projectKey(),
                target.dbtUniqueId(),
                derivedCommitKey,
                artifacts
            )
        );
        DraftRow committed = repository.completeCommit(
            tenantId,
            modelSpecId,
            draftId,
            actorId,
            claimed.etag(),
            nextEtag(),
            implementation.id(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            imported.artifactCount(),
            clock.instant()
        );
        audit(
            AUDIT_COMMIT,
            committed,
            files.size(),
            validatedChecksum,
            diagnostics(validated).size(),
            imported.artifactCount(),
            bundle.bundleChecksum(),
            correlationId
        );
        return committedView(committed, model, dependencyChecksum);
    }

    private UpdateModelSpecCommand requireValidModelSnapshot(DraftRow row) {
        if (row.modelSpecSnapshot() == null || row.modelSpecSnapshot().isBlank()) return null;
        var decoded = snapshotDecoder.decode(jsonNode(row.modelSpecSnapshot()));
        if (!decoded.valid()) {
            throw DbtImplementationDraftContract.unprocessable(
                "MODEL_AUTHORING_MODEL_INVALID",
                "The ModelSpec authoring snapshot must pass validation before implementation commit"
            );
        }
        return decoded.modelSpec();
    }

    private Map<String, Object> visualImplementationConfig(DraftRow row) {
        JsonNode snapshot = jsonNode(row.modelSpecSnapshot());
        JsonNode visual = snapshot == null ? null : snapshot.path("visualImplementation");
        if (visual == null || !visual.isObject()) return null;
        return objectMapper.convertValue(visual, new TypeReference<Map<String, Object>>() {});
    }

    private static String committedTargetPhysicalName(
        Map<String, Object> visualImplementation,
        String fallback
    ) {
        Object rawSettings = visualImplementation == null
            ? null
            : visualImplementation.get("settings");
        if (!(rawSettings instanceof Map<?, ?> settings)) {
            return fallback;
        }
        String configured = settings.get("targetPhysicalName") instanceof String value
            ? normalizedTargetPhysicalName(value)
            : null;
        return configured == null ? fallback : configured;
    }

    private static UpdateModelSpecCommand withProjectedFields(
        UpdateModelSpecCommand command,
        List<ModelField> fields
    ) {
        return new UpdateModelSpecCommand(
            command.planId(), command.domainId(), command.modelType(), command.layer(), command.name(),
            command.description(), command.implementationMode(), command.materialization(), command.businessActivityRef(),
            command.consumptionScenario(), command.grain(), command.factShape(), command.timeSemantics(), fields,
            command.sourceRefs(), command.dependsOn(), command.dimensionRefs(), command.metricRefs(),
            command.standardBindings(), command.generationStrategy(), command.dimensionProfile(), command.dataMartId(),
            command.variantCode(), command.implementationPolicy(), command.warehouseLayerCode(),
            command.businessProcessId(), command.subjectDomainId()
        );
    }

    private static UpdateModelSpecCommand withImplementationMode(
        UpdateModelSpecCommand command,
        ImplementationMode implementationMode
    ) {
        return new UpdateModelSpecCommand(
            command.planId(), command.domainId(), command.modelType(), command.layer(), command.name(),
            command.description(), implementationMode, command.materialization(), command.businessActivityRef(),
            command.consumptionScenario(), command.grain(), command.factShape(), command.timeSemantics(),
            command.fields(), command.sourceRefs(), command.dependsOn(), command.dimensionRefs(), command.metricRefs(),
            command.standardBindings(), command.generationStrategy(), command.dimensionProfile(), command.dataMartId(),
            command.variantCode(), command.implementationPolicy(), command.warehouseLayerCode(),
            command.businessProcessId(), command.subjectDomainId()
        );
    }

    private ModelSpecView requireEditableModel(String tenantId, String actorId, UUID modelSpecId, UUID planId) {
        return requireEditableModel(tenantId, actorId, modelSpecId, planId, false);
    }

    private ModelSpecView requireEditableModel(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID planId,
        boolean sourceNeutralAuthoring
    ) {
        ModelSpecView model = modelSpecs.get(tenantId, modelSpecId);
        if (!Objects.equals(model.planId(), planId)) {
            throw DbtImplementationDraftContract.forbidden(
                "DBT_DRAFT_PLAN_FORBIDDEN",
                "The requested plan does not own this ModelSpec"
            );
        }
        writeAccess.requireEdit(tenantId, modelSpecId, actorId);
        if (!writeAccess.canMaintain(tenantId, planId, actorId)) {
            throw DbtImplementationDraftContract.forbidden(
                "DBT_DRAFT_MAINTAINER_FORBIDDEN",
                "The current actor cannot maintain this warehouse plan"
            );
        }
        if (!sourceNeutralAuthoring && model.implementationMode() != ImplementationMode.DBT_MANAGED) {
            throw DbtImplementationDraftContract.unprocessable(
                "DBT_DRAFT_OWNERSHIP_UNSUPPORTED",
                "Advanced dbt drafts are available only for DBT_MANAGED models"
            );
        }
        return model;
    }

    private DraftRow requireDraft(String tenantId, String actorId, UUID modelSpecId, UUID draftId) {
        requireIdentity(tenantId, actorId, modelSpecId);
        if (draftId == null) {
            throw new DraftException("DBT_DRAFT_NOT_FOUND", "Draft was not found", ErrorKind.NOT_FOUND);
        }
        DraftRow row = repository.findForActor(tenantId, modelSpecId, draftId, actorId).orElse(null);
        if (row == null) {
            if (repository.existsForTenant(tenantId, modelSpecId, draftId)) {
                throw DbtImplementationDraftContract.forbidden(
                    "DBT_DRAFT_ACTOR_FORBIDDEN",
                    "The draft belongs to another actor"
                );
            }
            throw new DraftException("DBT_DRAFT_NOT_FOUND", "Draft was not found", ErrorKind.NOT_FOUND);
        }
        writeAccess.requireEdit(tenantId, modelSpecId, actorId);
        if (!writeAccess.canMaintain(tenantId, row.planId(), actorId)) {
            throw DbtImplementationDraftContract.forbidden(
                "DBT_DRAFT_MAINTAINER_FORBIDDEN",
                "The current actor can no longer maintain this warehouse plan"
            );
        }
        return row;
    }

    private static void requireIdentity(String tenantId, String actorId, UUID modelSpecId) {
        DbtImplementationDraftContract.requiredText(tenantId, "tenantId", 128);
        if (actorId == null || actorId.isBlank()) {
            throw DbtImplementationDraftContract.forbidden(
                "DBT_DRAFT_ACTOR_REQUIRED",
                "An authenticated modeling maintainer is required"
            );
        }
        if (modelSpecId == null) {
            throw DbtImplementationDraftContract.badRequest("DBT_DRAFT_MODEL_REQUIRED", "modelSpecId is required");
        }
    }

    private static void requireImplementationPin(
        UUID modelSpecId,
        ImplementationView implementation,
        Integer revision,
        String checksum,
        boolean sourceNeutralAuthoring
    ) {
        if (implementation == null) {
            if (revision == null && checksum == null) return;
        } else if (
            implementation.modelSpecId().equals(modelSpecId) &&
            (sourceNeutralAuthoring || implementation.ownership() == ImplementationMode.DBT_MANAGED) &&
            Objects.equals(revision, implementation.implementationRevision()) &&
            Objects.equals(checksum, implementation.implementationChecksum())
        ) {
            return;
        }
        throw DbtImplementationDraftContract.conflict(
            "DBT_DRAFT_BASE_IMPLEMENTATION_CONFLICT",
            "The implementation revision changed from the draft base"
        );
    }

    /**
     * Saving the definition can persist the exact content already held by the authoring draft.
     * A legacy save could also create the first designer implementation outside this draft.
     * Align equivalent persisted content under CAS, retaining every source/working file.
     * Different content remains a conflict, including a second implementation revision.
     */
    private DraftRow alignAuthoringBase(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        DraftRow draft,
        Instant now
    ) {
        return alignAuthoringBase(tenantId, actorId, modelSpecId, draft, now, null);
    }

    private DraftRow alignAuthoringBase(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        DraftRow draft,
        Instant now,
        JsonNode submittedSnapshot
    ) {
        if (draft.modelSpecSnapshot() == null || draft.sourceBundleSnapshot() == null) return draft;
        ModelSpecView model = requireEditableModel(tenantId, actorId, modelSpecId, draft.planId(), true);
        boolean modelChanged = model.revision() != draft.baseModelRevision() ||
            !Objects.equals(model.checksum(), draft.baseModelChecksum());
        if (modelChanged) {
            UpdateModelSpecCommand snapshot = requireValidModelSnapshot(draft);
            ModelSpecView originalModel = modelSpecs.revision(tenantId,
                new com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef(modelSpecId, draft.baseModelRevision()));
            requireModelPins(draft, originalModel);
            boolean matchesCurrent = snapshot != null && Objects.equals(model.checksum(),
                snapshotCodec.toUpdatedView(originalModel, snapshot, model.revision(), now).checksum());
            // Only an explicit save may acknowledge a separately persisted definition.
            // The submitted logical snapshot must equal the current server model exactly;
            // validation alone must never silently rebase stale SQL onto another definition.
            if (!matchesCurrent && submittedSnapshot != null) {
                var submitted = snapshotDecoder.decode(submittedSnapshot);
                matchesCurrent = submitted.valid() && Objects.equals(model.checksum(),
                    snapshotCodec.toUpdatedView(originalModel, submitted.modelSpec(), model.revision(), now).checksum());
            }
            if (!matchesCurrent || model.status() != ModelStatus.DRAFT || model.revision() <= draft.baseModelRevision()) {
                throw DbtImplementationDraftContract.conflict(
                    "DBT_DRAFT_BASE_MODEL_CONFLICT",
                    "模型定义已变更，且与当前创作草稿不一致。草稿及 SQL 已保留，请核对模型设计后再提交。"
                );
            }
        }
        ImplementationView implementation = lifecycle.timeline(tenantId, modelSpecId).implementation();
        Integer implementationRevision = implementation == null ? null : implementation.implementationRevision();
        String implementationChecksum = implementation == null ? null : implementation.implementationChecksum();
        boolean implementationChanged = !Objects.equals(draft.baseImplementationRevision(), implementationRevision) ||
            !Objects.equals(draft.baseImplementationChecksum(), implementationChecksum);
        if (!implementationChanged || !equivalentInitialImplementation(draft, model, implementation)) {
            requireImplementationPin(modelSpecId, implementation, draft.baseImplementationRevision(),
                draft.baseImplementationChecksum(), true);
        }
        if (!modelChanged && !implementationChanged) return draft;
        SourceBundleView frozen = sourceBundleSnapshot(draft.sourceBundleSnapshot());
        Resolution resolution = dependencies == null ? null
            : resolveDependencies(tenantId, model, implementation, frozen.projectKey(), null);
        SourceBundleView alignedSource = resolution == null ? frozen
            : withDependencies(frozen, resolution, frozen.managedDependencyAliases());
        DraftRow aligned = repository.alignAuthoringBase(
            tenantId, modelSpecId, draft.id(), actorId, draft.etag(),
            draft.baseModelRevision(), draft.baseModelChecksum(), model.revision(), model.checksum(),
            draft.baseImplementationRevision(), draft.baseImplementationChecksum(), implementationRevision, implementationChecksum,
            sourceBundleSnapshot(alignedSource), nextEtag(), now
        ).orElseThrow(() -> etagConflict(draft, draft.etag()));
        audit("MODELING_DBT_DRAFT_BASE_SYNC", aligned, 0, null, null, null, null,
            DbtImplementationDraftCorrelation.currentOrCreate());
        return aligned;
    }

    /** Compare the saved snapshot, never newly submitted edits, before accepting a legacy first save. */
    private boolean equivalentInitialImplementation(DraftRow draft, ModelSpecView model, ImplementationView implementation) {
        if (draft.baseImplementationRevision() != null || draft.baseImplementationChecksum() != null ||
            implementation == null || implementation.implementationRevision() != 1 ||
            implementation.ownership() != ImplementationMode.DESIGNER_GENERATED ||
            !"ACTIVE".equals(implementation.status()) || model.status() != ModelStatus.DRAFT ||
            !Objects.equals(model.id(), implementation.modelSpecId()) || !Objects.equals(model.planId(), implementation.planId()) ||
            model.revision() != implementation.revision() || !Objects.equals(model.checksum(), implementation.modelChecksum()) ||
            sourceBundleSnapshot(draft.sourceBundleSnapshot()).sourceKind() != SourceBundleKind.CANONICAL_INITIALIZATION) return false;
        var decoded = snapshotDecoder.decode(jsonNode(draft.modelSpecSnapshot()));
        if (!decoded.valid() || decoded.visualImplementation() == null) return false;
        SaveImplementationCommand saved = decoded.visualImplementation().command();
        // Legacy and authoring saves assign different SQL identities to the same configuration.
        // The frozen authoring project is retained; lifecycle transition still uses strict CAS.
        return saved.ownership() == implementation.ownership() && saved.inputMode() == implementation.inputMode() &&
            Objects.equals(saved.inputs(), implementation.inputs()) &&
            Objects.equals(saved.fieldMappings(), implementation.fieldMappings()) &&
            Objects.equals(saved.settings(), implementation.settings()) &&
            Objects.equals(saved.materialization(), implementation.materialization());
    }

    private ValidatedNode draftTarget(ValidatedProject validated, DraftRow draft, ImplementationView implementation) {
        if (draft.modelSpecSnapshot() != null && draft.sourceBundleSnapshot() != null && implementation != null &&
            implementation.ownership() == ImplementationMode.DESIGNER_GENERATED) {
            SourceBundleView frozen = sourceBundleSnapshot(draft.sourceBundleSnapshot());
            if (frozen.sourceKind() == SourceBundleKind.CANONICAL_INITIALIZATION) {
                if (!Objects.equals(frozen.projectKey(), validated.projectKey())) {
                    throw DbtImplementationDraftContract.unprocessable("DBT_DRAFT_PROJECT_IDENTITY_UNSUPPORTED",
                        "The initialized authoring project identity cannot be changed");
                }
                List<String> initialTargets = frozen.files().stream().map(BundleFileView::path)
                    .filter(path -> path.matches("models/[a-z][a-z0-9_]{0,62}\\.sql"))
                    .map(path -> "model." + frozen.projectKey() + "." + path.substring(7, path.length() - 4)).toList();
                if (initialTargets.size() == 1) {
                    return validated.nodes().stream().filter(node -> "MODEL".equals(node.nodeKind()) &&
                        Objects.equals(initialTargets.get(0), node.dbtUniqueId())).findFirst().orElseThrow(() ->
                        DbtImplementationDraftContract.unprocessable("DBT_DRAFT_NODE_IDENTITY_UNSUPPORTED",
                            "The initialized authoring model cannot be removed or renamed"));
                }
                throw sourceBundleUnavailable();
            }
        }
        return target(validated, implementation);
    }

    /** The immutable initialization dependency snapshot is a base pin, not the validated commit pin. */
    private String commitDependencyChecksum(DraftRow draft, SourceBundleView frozen) {
        if (draft.modelSpecSnapshot() == null) return frozen == null ? null : frozen.dependencyChecksum();
        JsonNode summary = jsonNode(draft.validationSummary());
        String validated = summary == null ? null
            : summary.path("dependencyValidation").path("dependencyChecksum").asText(null);
        if (validated != null) return DbtImplementationDraftContract.requiredChecksum(validated, "dependencyChecksum");
        if (frozen != null && frozen.dependencySnapshot() != null) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_VALIDATION_REQUIRED",
                "请重新校验当前草稿后提交，实现提交需要本次校验的依赖版本。"
            );
        }
        return null;
    }

    private static void requireModelPins(DraftRow draft, ModelSpecView model) {
        if (
            model.revision() != draft.baseModelRevision() ||
            !Objects.equals(model.checksum(), draft.baseModelChecksum())
        ) {
            throw DbtImplementationDraftContract.conflict(
                "DBT_DRAFT_BASE_MODEL_CONFLICT",
                "The ModelSpec revision changed from the draft base"
            );
        }
    }

    private static ValidatedNode target(ValidatedProject validated, ImplementationView base) {
        if (base != null) {
            if (!Objects.equals(base.projectKey(), validated.projectKey())) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_PROJECT_IDENTITY_UNSUPPORTED",
                    "Advanced editing cannot change the dbt project identity"
                );
            }
            return validated
                .nodes()
                .stream()
                .filter(node -> Objects.equals(node.dbtUniqueId(), base.dbtUniqueId()))
                .findFirst()
                .orElseThrow(() ->
                    DbtImplementationDraftContract.unprocessable(
                        "DBT_DRAFT_NODE_IDENTITY_UNSUPPORTED",
                        "Advanced editing cannot remove or rename the owned dbt model"
                    )
                );
        }
        List<ValidatedNode> models = validated
            .nodes()
            .stream()
            .filter(node -> "MODEL".equals(node.nodeKind()))
            .filter(node -> !managedDependencyPath(node.resourcePath()))
            .toList();
        if (models.size() != 1) {
            throw DbtImplementationDraftContract.unprocessable(
                "DBT_DRAFT_MODEL_SELECTION_UNSUPPORTED",
                "A first DBT_MANAGED implementation draft must contain exactly one editable model"
            );
        }
        return models.getFirst();
    }

    private static boolean managedDependencyPath(String path) {
        String normalized = Objects.toString(path, "").replace('\\', '/');
        return normalized.contains("/.dts_dependencies/") || normalized.contains("/dts_dependencies/");
    }

    private static void requireMaterialization(ModelSpecView model, ValidatedNode target, boolean schemaOnly) {
        if (schemaOnly && "table".equals(model.materialization()) && "dts_schema_only".equals(target.materialization())) return;
        if (
            model.materialization() == null ||
            model.materialization().isBlank() ||
            !model.materialization().equalsIgnoreCase(target.materialization())
        ) {
            throw DbtImplementationDraftContract.unprocessable(
                "DBT_DRAFT_MATERIALIZATION_UNSUPPORTED",
                "The dbt materialization must match the canonical ModelSpec"
            );
        }
    }

    private ValidatedProject staticValidate(List<FileRow> files) {
        LinkedHashMap<String, String> content = new LinkedHashMap<>();
        files.forEach(file -> content.put(file.path(), file.content()));
        try {
            return validator.validate(Map.copyOf(content));
        } catch (StaticValidationException exception) {
            throw DbtImplementationDraftContract.unprocessable(exception.code(), exception.getMessage());
        }
    }

    private static List<ImportedArtifact> artifacts(
        ValidatedProject validated,
        ValidatedNode target,
        String materialization,
        BundleSnapshot bundle,
        List<FileRow> files
    ) {
        List<ImportedArtifact> artifacts = new java.util.ArrayList<>();
        artifacts.add(
            new ImportedArtifact(
                target.dbtUniqueId(),
                NodeKind.MODEL,
                ArtifactType.SQL,
                target.resourcePath(),
                target.sqlChecksum(),
                target.sql(),
                materialization
            )
        );
        artifacts.add(
            new ImportedArtifact(
                target.dbtUniqueId(),
                NodeKind.MODEL,
                ArtifactType.SCHEMA,
                target.resourcePath() + "#schema",
                target.schemaChecksum(),
                target.schema(),
                materialization
            )
        );
        artifacts.add(
            new ImportedArtifact(
                target.dbtUniqueId(),
                NodeKind.MODEL,
                ArtifactType.CONFIG,
                ".dts/dbt-project-bundle.json",
                bundle.bundleChecksum(),
                bundle.manifest(),
                materialization
            )
        );
        dependencyNodes(validated, target).forEach(node -> {
            String nodeMaterialization = node.materialization().toLowerCase(Locale.ROOT);
            NodeKind nodeKind = "ephemeral".equals(nodeMaterialization) ? NodeKind.EPHEMERAL : NodeKind.STG;
            artifacts.add(
                new ImportedArtifact(
                    node.dbtUniqueId(),
                    nodeKind,
                    ArtifactType.SQL,
                    node.resourcePath(),
                    node.sqlChecksum(),
                    node.sql(),
                    nodeMaterialization
                )
            );
        });
        java.util.Set<String> importedPaths = artifacts
            .stream()
            .map(ImportedArtifact::path)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        java.util.Set<String> availableModelNames = importedPaths
            .stream()
            .filter(path -> path.startsWith("models/") && path.endsWith(".sql"))
            .map(DbtImplementationDraftService::modelName)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        DbtProjectBundleManifest.resolveLocalModelDependencies(
            bundleFiles(files),
            List.of(target.sql()),
            availableModelNames
        ).forEach(file -> {
            if (!importedPaths.add(file.path())) return;
            boolean ephemeral = file.content().toLowerCase(Locale.ROOT).matches(
                "(?s).*materialized\\s*=\\s*['\"]ephemeral['\"].*"
            );
            String nodeMaterialization = ephemeral ? "ephemeral" : "view";
            artifacts.add(
                new ImportedArtifact(
                    "model." + validated.projectKey() + "." + modelName(file.path()),
                    ephemeral ? NodeKind.EPHEMERAL : NodeKind.STG,
                    ArtifactType.SQL,
                    file.path(),
                    file.checksum(),
                    file.content(),
                    nodeMaterialization
                )
            );
        });
        return List.copyOf(artifacts);
    }

    private static String modelName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.substring(0, fileName.length() - ".sql".length());
    }

    private static List<ValidatedNode> dependencyNodes(ValidatedProject validated, ValidatedNode target) {
        Map<String, ValidatedNode> byUniqueId = new LinkedHashMap<>();
        validated.nodes().forEach(node -> byUniqueId.put(node.dbtUniqueId(), node));
        ArrayDeque<String> pending = new ArrayDeque<>(target.dependencies());
        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
        List<ValidatedNode> dependencies = new java.util.ArrayList<>();
        while (!pending.isEmpty()) {
            String uniqueId = pending.removeFirst();
            if (!visited.add(uniqueId)) continue;
            ValidatedNode dependency = byUniqueId.get(uniqueId);
            if (dependency == null || Objects.equals(dependency.dbtUniqueId(), target.dbtUniqueId())) continue;
            dependencies.add(dependency);
            pending.addAll(dependency.dependencies());
        }
        dependencies.sort(java.util.Comparator.comparing(ValidatedNode::dbtUniqueId));
        return List.copyOf(dependencies);
    }

    private BundleSnapshot freezeBundle(List<FileRow> files, ValidatedProject validated) {
        return DbtProjectBundleManifest.freeze(objectMapper, bundleFiles(files), validated);
    }

    private static List<BundleFile> bundleFiles(List<FileRow> files) {
        return files
            .stream()
            .map(file -> new BundleFile(file.path(), file.content(), file.checksum(), file.byteSize()))
            .toList();
    }

    private SourceBundleView sourceBundle(
        String tenantId,
        UUID planId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        Integer implementationRevision,
        String implementationChecksum,
        ImplementationView current,
        ModelSpecView model,
        String targetPhysicalName,
        boolean sourceNeutralAuthoring
    ) {
        if (current == null) {
            // Creating an editing container must not require an implementation of every original
            // design reference. The first save supplies explicit input pins; validation gates use
            // that saved intent before any implementation can be committed.
            Resolution dependencyResolution = dependencies == null || sourceNeutralAuthoring
                ? null
                : resolveDependencies(
                    tenantId,
                    model,
                    null,
                    initializedProjectKey(modelSpecId),
                    targetPhysicalName
                );
            return freezeCanonical(
                canonicalProjects.initialize(
                    modelSpecId,
                    model.materialization(),
                    targetPhysicalName,
                    dependencyResolution
                ),
                SourceBundleKind.CANONICAL_INITIALIZATION
            );
        }
        if (implementationRevision == null || implementationChecksum == null) throw sourceBundleUnavailable();
        Resolution dependencyResolution = dependencies == null
            ? null
            : resolveEditingBaseDependencies(
                tenantId,
                model,
                current,
                current.projectKey(),
                current.dbtUniqueId().substring(current.dbtUniqueId().lastIndexOf('.') + 1),
                sourceNeutralAuthoring
            );

        if (sourceNeutralAuthoring && current.ownership() == ImplementationMode.DESIGNER_GENERATED &&
            (dependencyResolution != null || dependencies == null)) {
            if (visualCompiler == null) throw sourceBundleUnavailable();
            return withDependencies(
                freezeCanonical(
                    compiledDesignerProject(tenantId, model, current, dependencyResolution),
                    SourceBundleKind.CANONICAL_ARTIFACT_RECONSTRUCTION
                ),
                dependencyResolution,
                Map.of()
            );
        }

        int sourceModelRevision = current.revision();
        String sourceModelChecksum = current.modelChecksum();
        RepresentationEvidence evidence = representationEvidence
            .findExact(tenantId, modelSpecId, sourceModelRevision, sourceModelChecksum, implementationRevision, true)
            .orElseThrow(DbtImplementationDraftService::sourceBundleUnavailable);
        ImplementationSnapshot snapshot = evidence.implementation();
        requireSourcePins(
            planId,
            modelSpecId,
            sourceModelRevision,
            sourceModelChecksum,
            implementationRevision,
            implementationChecksum,
            current,
            snapshot
        );

        JsonNode config = bundleConfig(snapshot.inputs(), false);
        if (config == null) {
            CanonicalProject canonical = canonicalProjects.reconstruct(
                snapshot,
                evidence.artifacts(),
                modelSpecId,
                sourceModelRevision,
                sourceModelChecksum,
                implementationRevision,
                implementationChecksum
            );
            return withDependencies(
                freezeCanonical(canonical, SourceBundleKind.CANONICAL_ARTIFACT_RECONSTRUCTION),
                dependencyResolution,
                Map.of()
            );
        }
        String configuredProjectKey = bundleText(config, "projectKey", 128);
        String configuredDbtUniqueId = bundleText(config, "dbtUniqueId", 512);
        if (
            !Objects.equals(configuredProjectKey, snapshot.projectKey()) ||
            !Objects.equals(configuredDbtUniqueId, snapshot.dbtUniqueId())
        ) {
            throw sourceBundleUnavailable();
        }
        String projectChecksum = bundleChecksum(config, "projectChecksum");
        String expectedBundleChecksum = bundleChecksum(config, "bundleChecksum");
        ArtifactEvidence manifest = bundleManifest(
            evidence,
            modelSpecId,
            sourceModelRevision,
            sourceModelChecksum,
            implementationRevision,
            implementationChecksum,
            expectedBundleChecksum
        );

        RestoredBundle restored;
        try {
            restored = DbtProjectBundleManifest.restore(
                objectMapper,
                manifest.artifactContent(),
                expectedBundleChecksum,
                projectChecksum
            );
        } catch (DraftException invalid) {
            throw sourceBundleUnavailable();
        }
        if (!Objects.equals(restored.projectKey(), snapshot.projectKey())) throw sourceBundleUnavailable();
        List<BundleFileView> files = restored
            .files()
            .stream()
            .map(file -> new BundleFileView(file.path(), file.content(), file.checksum(), file.byteSize()))
            .toList();
        if (files.stream().noneMatch(file -> "dbt_project.yml".equals(file.path()))) throw sourceBundleUnavailable();
        return withDependencies(
            new SourceBundleView(
                restored.projectKey(),
                restored.projectChecksum(),
                restored.bundleChecksum(),
                SourceBundleKind.FROZEN_SOURCE_BUNDLE,
                true,
                files
            ),
            dependencyResolution,
            Map.of()
        );
    }

    private Resolution resolveEditingBaseDependencies(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        String projectKey,
        String targetName,
        boolean sourceNeutralAuthoring
    ) {
        try {
            return resolveDependencies(tenantId, model, implementation, projectKey, targetName);
        } catch (DraftException failure) {
            if (!sourceNeutralAuthoring || !"DBT_DRAFT_DEPENDENCY_PIN_STALE".equals(failure.code())) throw failure;
            // Restore the exact persisted source evidence below instead of recompiling stale input.
            // Owner CAS, source checksum verification and live submit-time admission remain mandatory.
            return null;
        }
    }

    private CanonicalProject compiledDesignerProject(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        Resolution dependencyResolution
    ) {
        try {
            List<ArtifactWrite> artifacts = visualCompiler.compile(tenantId, model, implementation);
            LinkedHashMap<String, String> generated = new LinkedHashMap<>();
            for (ArtifactWrite artifact : artifacts == null ? List.<ArtifactWrite>of() : artifacts) {
                if (artifact == null || artifact.path() == null || artifact.content() == null) {
                    throw sourceBundleUnavailable();
                }
                String previous = generated.putIfAbsent(artifact.path(), artifact.content());
                if (previous != null && !Objects.equals(previous, artifact.content())) {
                    throw sourceBundleUnavailable();
                }
            }
            Map<String, String> files = CanonicalDbtProjectBundleAssembler.assemble(
                implementation.projectKey(),
                implementation.materialization() == null ? model.materialization() : implementation.materialization(),
                generated
            );
            return new CanonicalProject(
                implementation.projectKey(),
                files,
                Map.of(),
                dependencyResolution == null ? null : dependencyResolution.snapshot(),
                Map.of()
            );
        } catch (DraftException failure) {
            throw failure;
        } catch (ModelSpecException failure) {
            throw failure;
        } catch (CompileException failure) {
            throw DbtImplementationDraftContract.unprocessable(
                failure.getMessage(),
                "The visual implementation cannot be compiled under the current model contract"
            );
        } catch (RuntimeException failure) {
            throw sourceBundleUnavailable();
        }
    }

    private SourceBundleView freezeCanonical(CanonicalProject canonical, SourceBundleKind sourceKind) {
        ValidatedProject validated;
        try {
            validated = validator.validate(canonical.files());
        } catch (RuntimeException failure) {
            throw sourceBundleUnavailable();
        }
        if (!Objects.equals(validated.projectKey(), canonical.projectKey())) throw sourceBundleUnavailable();
        canonical.expectedDependencies().forEach((path, expected) -> {
            List<ValidatedNode> matches = validated.nodes().stream().filter(node -> Objects.equals(path, node.resourcePath())).toList();
            if (
                matches.size() != 1 ||
                !Objects.equals(
                    matches.getFirst().dependencies().stream().sorted().toList(),
                    expected.stream().sorted().toList()
                )
            ) {
                throw sourceBundleUnavailable();
            }
        });
        List<BundleFile> files = canonical
            .files()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                byte[] bytes = entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new BundleFile(
                    entry.getKey(),
                    entry.getValue(),
                    ModelPackageChecksum.sha256(bytes),
                    bytes.length
                );
            })
            .toList();
        BundleSnapshot bundle;
        try {
            bundle = DbtProjectBundleManifest.freeze(objectMapper, files, validated);
        } catch (RuntimeException failure) {
            throw sourceBundleUnavailable();
        }
        return new SourceBundleView(
            canonical.projectKey(),
            bundle.projectChecksum(),
            bundle.bundleChecksum(),
            sourceKind,
            false,
            files.stream().map(file -> new BundleFileView(file.path(), file.content(), file.checksum(), file.byteSize())).toList(),
            canonical.dependencySnapshot() == null ? null : canonical.dependencySnapshot().dependencyChecksum(),
            canonical.dependencySnapshot(),
            canonical.dependencyAliases()
        );
    }

    private static SourceBundleView withDependencies(
        SourceBundleView source,
        Resolution dependencies,
        Map<String, String> aliases
    ) {
        if (dependencies == null) return source;
        return new SourceBundleView(
            source.projectKey(),
            source.projectChecksum(),
            source.bundleChecksum(),
            source.sourceKind(),
            source.lossless(),
            source.files(),
            dependencies.snapshot().dependencyChecksum(),
            dependencies.snapshot(),
            aliases
        );
    }

    private static String initializedProjectKey(UUID modelSpecId) {
        return "dts_model_" + modelSpecId.toString().replace("-", "").substring(0, 12);
    }

    private Resolution resolveDependencies(
        String tenantId,
        ModelSpecView model,
        ImplementationView implementation,
        String projectKey,
        String targetName
    ) {
        try {
            return dependencies.resolveForDraft(
                tenantId,
                model,
                dependencyImplementation(model, implementation),
                projectKey,
                targetName
            );
        } catch (ModelSpecException failure) {
            throw dependencyFailure(failure.code(), failure.getMessage(), failure.details());
        }
    }

    /**
     * A new logical draft inherits the last active implementation as its editing base. Rebind only
     * the owner pins used to resolve the draft dependency snapshot; source artifact reconstruction
     * continues to use the immutable implementation's original model pins.
     */
    private static ImplementationView dependencyImplementation(
        ModelSpecView model,
        ImplementationView implementation
    ) {
        if (
            model == null ||
            implementation == null ||
            model.status() != ModelStatus.DRAFT ||
            !Objects.equals(model.id(), implementation.modelSpecId()) ||
            !Objects.equals(model.planId(), implementation.planId()) ||
            implementation.revision() >= model.revision()
        ) {
            return implementation;
        }
        return new ImplementationView(
            implementation.id(),
            implementation.modelSpecId(),
            implementation.planId(),
            model.revision(),
            model.checksum(),
            implementation.ownership(),
            implementation.projectKey(),
            implementation.dbtUniqueId(),
            implementation.status(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            implementation.inputMode(),
            implementation.inputs(),
            implementation.fieldMappings(),
            implementation.settings(),
            implementation.materialization()
        );
    }

    private static DraftException dependencyFailure(String sourceCode, String message, Object details) {
        String code = switch (sourceCode) {
            case "MODEL_IMPLEMENTATION_DEPENDENCY_UNDECLARED" -> "DBT_DRAFT_DEPENDENCY_UNDECLARED";
            case "MODEL_IMPLEMENTATION_DEPENDENCY_MISSING" -> "DBT_DRAFT_DEPENDENCY_MISSING";
            case "MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE" -> "DBT_DRAFT_DEPENDENCY_PIN_STALE";
            case "MODEL_SOURCE_BINDING_STALE" -> "DBT_DRAFT_SOURCE_BINDING_STALE";
            case "MODEL_IMPLEMENTATION_DEPENDENCY_CYCLE" -> "DBT_DRAFT_DEPENDENCY_CYCLE";
            default -> "DBT_DRAFT_DEPENDENCY_INVALID";
        };
        ErrorKind kind = switch (code) {
            case "DBT_DRAFT_DEPENDENCY_UNDECLARED",
                "DBT_DRAFT_DEPENDENCY_MISSING",
                "DBT_DRAFT_DEPENDENCY_CYCLE" -> ErrorKind.UNPROCESSABLE;
            default -> ErrorKind.PRECONDITION_FAILED;
        };
        Map<String, Object> projectedDetails;
        if (details instanceof Map<?, ?> values) {
            LinkedHashMap<String, Object> projected = new LinkedHashMap<>();
            values.forEach((key, value) -> projected.put(Objects.toString(key), value));
            projectedDetails = Map.copyOf(projected);
        } else {
            projectedDetails = details == null ? Map.of() : Map.of("dependency", details);
        }
        return new DraftException(code, message, kind, projectedDetails);
    }

    private static void requireSourcePins(
        UUID planId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        ImplementationView current,
        ImplementationSnapshot snapshot
    ) {
        if (
            snapshot == null ||
            !Objects.equals(snapshot.implementationId(), current.id()) ||
            !Objects.equals(current.modelSpecId(), modelSpecId) ||
            !Objects.equals(current.planId(), planId) ||
            current.revision() != modelRevision ||
            !Objects.equals(current.modelChecksum(), modelChecksum) ||
            current.implementationRevision() != implementationRevision ||
            !Objects.equals(current.implementationChecksum(), implementationChecksum) ||
            current.ownership() != ImplementationMode.DBT_MANAGED ||
            !Objects.equals(snapshot.modelSpecId(), modelSpecId) ||
            !Objects.equals(snapshot.planId(), planId) ||
            snapshot.modelRevision() != modelRevision ||
            !Objects.equals(snapshot.modelChecksum(), modelChecksum) ||
            snapshot.implementationRevision() != implementationRevision ||
            !Objects.equals(snapshot.implementationChecksum(), implementationChecksum) ||
            snapshot.ownership() != ImplementationMode.DBT_MANAGED ||
            !Objects.equals(snapshot.projectKey(), current.projectKey()) ||
            !Objects.equals(snapshot.dbtUniqueId(), current.dbtUniqueId())
        ) {
            throw sourceBundleUnavailable();
        }
    }

    private static JsonNode bundleConfig(JsonNode inputs, boolean required) {
        if (inputs == null || !inputs.isArray()) {
            if (required) throw sourceBundleUnavailable();
            return null;
        }
        JsonNode match = null;
        for (JsonNode input : inputs) {
            JsonNode config = input == null ? null : input.get("config");
            if (config == null || !config.isObject() || !config.hasNonNull("bundleChecksum")) continue;
            if (match != null) throw sourceBundleUnavailable();
            match = config;
        }
        if (match == null && required) throw sourceBundleUnavailable();
        return match;
    }

    private static String bundleChecksum(JsonNode config, String field) {
        JsonNode value = config == null ? null : config.get(field);
        try {
            return DbtImplementationDraftContract.requiredChecksum(
                value != null && value.isTextual() ? value.textValue() : null,
                field
            );
        } catch (DraftException invalid) {
            throw sourceBundleUnavailable();
        }
    }

    private static String bundleText(JsonNode config, String field, int maxLength) {
        JsonNode value = config == null ? null : config.get(field);
        try {
            return DbtImplementationDraftContract.requiredText(
                value != null && value.isTextual() ? value.textValue() : null,
                field,
                maxLength
            );
        } catch (DraftException invalid) {
            throw sourceBundleUnavailable();
        }
    }

    private static ArtifactEvidence bundleManifest(
        RepresentationEvidence evidence,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String bundleChecksum
    ) {
        List<ArtifactEvidence> manifests = evidence
            .artifacts()
            .stream()
            .filter(Objects::nonNull)
            .filter(artifact -> "CONFIG".equals(artifact.artifactType()))
            .filter(artifact -> ".dts/dbt-project-bundle.json".equals(artifact.artifactPath()))
            .toList();
        if (manifests.size() != 1) throw sourceBundleUnavailable();
        ArtifactEvidence manifest = manifests.getFirst();
        if (
            !Objects.equals(manifest.modelSpecId(), modelSpecId) ||
            manifest.modelRevision() != modelRevision ||
            !Objects.equals(manifest.modelChecksum(), modelChecksum) ||
            manifest.implementationRevision() != implementationRevision ||
            !Objects.equals(manifest.implementationChecksum(), implementationChecksum) ||
            !Objects.equals(manifest.artifactChecksum(), bundleChecksum) ||
            manifest.artifactContent() == null
        ) {
            throw sourceBundleUnavailable();
        }
        return manifest;
    }

    private static DraftException sourceBundleUnavailable() {
        return DbtImplementationDraftContract.precondition(
            "DBT_DRAFT_SOURCE_BUNDLE_UNAVAILABLE",
            "The exactly pinned frozen dbt project bundle is unavailable or invalid"
        );
    }

    private static String requireFrozenChecksum(String value) {
        if (value == null || value.isBlank()) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_BUNDLE_REQUIRED",
                "The validated draft does not contain a frozen project bundle"
            );
        }
        try {
            return DbtImplementationDraftContract.requiredChecksum(value, "bundleChecksum");
        } catch (DraftException invalid) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_BUNDLE_INVALID",
                "The validated draft bundle checksum is invalid"
            );
        }
    }

    private static void requireFrozenBundle(DraftRow draft, BundleSnapshot bundle) {
        if (
            !Objects.equals(draft.projectChecksum(), bundle.projectChecksum()) ||
            !Objects.equals(draft.bundleChecksum(), bundle.bundleChecksum()) ||
            !Objects.equals(draft.bundleManifest(), bundle.manifest())
        ) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_BUNDLE_CONFLICT",
                "The reproducible dbt project bundle no longer matches the validated checkpoint"
            );
        }
    }

    private static String commitKey(
        String idempotencyKey,
        String validatedChecksum,
        String bundleChecksum,
        String dependencyChecksum
    ) {
        if (dependencyChecksum != null) {
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
        return ModelPackageChecksum.sha256Text(
            String.join("\u0000", "dbt-draft-commit-v1", idempotencyKey, validatedChecksum, bundleChecksum)
        );
    }

    private static List<Diagnostic> diagnostics(ValidatedProject validated) {
        return validated
            .diagnostics()
            .stream()
            .map(item -> new Diagnostic(item.code(), item.severity(), item.path(), item.modelUniqueId(), item.message()))
            .toList();
    }

    private static List<ProposedNode> structure(ValidatedProject validated) {
        return validated
            .nodes()
            .stream()
            .map(node ->
                new ProposedNode(
                    node.dbtUniqueId(),
                    node.name(),
                    node.resourcePath(),
                    node.materialization(),
                    node.nodeKind(),
                    node.dependencies()
                )
            )
            .toList();
    }

    private String validationSummary(
        List<Diagnostic> diagnostics,
        List<ProposedNode> structure,
        DependencyValidationView dependencyValidation
    ) {
        try {
            LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
            summary.put("diagnostics", diagnostics);
            summary.put("proposedStructure", structure);
            if (dependencyValidation != null) summary.put("dependencyValidation", dependencyValidation);
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Static validation summary could not be serialized", exception);
        }
    }

    private static void requireNotExpired(DraftRow row, Instant now) {
        if (row.expiresAt() == null || !row.expiresAt().isAfter(now)) {
            throw new DraftException("DBT_DRAFT_EXPIRED", "The advanced dbt draft has expired", ErrorKind.GONE);
        }
    }

    private static String expectedEtag(String value) {
        return DbtImplementationDraftContract.requiredText(value, "expectedEtag", 64);
    }

    private static DraftException etagConflict(DraftRow current, String expectedEtag) {
        return new DraftException(
            "DBT_DRAFT_ETAG_CONFLICT",
            "The draft ETag changed; reload before continuing",
            ErrorKind.PRECONDITION_FAILED,
            Map.of("expectedEtag", expectedEtag, "currentEtag", current.etag())
        );
    }

    private DraftView view(DraftRow row, SourceBundleView sourceBundle) {
        return new DraftView(
            row.id(),
            row.planId(),
            row.modelSpecId(),
            row.baseModelRevision(),
            row.baseModelChecksum(),
            row.baseImplementationRevision(),
            row.baseImplementationChecksum(),
            row.state(),
            row.etag(),
            row.expiresAt(),
            sourceBundle,
            jsonNode(row.modelSpecSnapshot()),
            jsonNode(row.projectionSummary()),
            AuthoringOrigin.fromStorage(row.authoringOrigin())
        );
    }

    private JsonNode jsonNode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private static CommitView committedView(DraftRow row) {
        return committedView(row, row.baseModelRevision(), row.baseModelChecksum(), null);
    }

    private static CommitView committedView(DraftRow row, ModelSpecView model) {
        return committedView(row, model.revision(), model.checksum(), null);
    }

    private static CommitView committedView(DraftRow row, String dependencyChecksum) {
        return committedView(row, row.baseModelRevision(), row.baseModelChecksum(), dependencyChecksum);
    }

    private static CommitView committedView(
        DraftRow row,
        ModelSpecView model,
        String dependencyChecksum
    ) {
        return committedView(row, model.revision(), model.checksum(), dependencyChecksum);
    }

    private static CommitView committedView(
        DraftRow row,
        int modelRevision,
        String modelChecksum,
        String dependencyChecksum
    ) {
        if (row.implementationId() == null || row.implementationRevision() == null) {
            throw new IllegalStateException("Committed advanced dbt draft is missing its implementation receipt");
        }
        return new CommitView(
            row.id(),
            row.modelSpecId(),
            modelRevision,
            modelChecksum,
            row.implementationId(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.artifactCount(),
            row.etag(),
            dependencyChecksum
        );
    }

    private static String requestHash(
        String tenantId,
        String actorId,
        UUID planId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        Integer implementationRevision,
        String implementationChecksum,
        String targetPhysicalName
    ) {
        String base = String.join(
            "\u0000",
            tenantId,
            actorId.trim(),
            planId.toString(),
            modelSpecId.toString(),
            Integer.toString(modelRevision),
            modelChecksum,
            Objects.toString(implementationRevision, ""),
            Objects.toString(implementationChecksum, "")
        );
        return ModelPackageChecksum.sha256Text(
            targetPhysicalName == null ? base : base + "\u0000" + targetPhysicalName
        );
    }

    private static String normalizedTargetPhysicalName(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("^[a-z][a-z0-9_]{0,62}$")) {
            throw DbtImplementationDraftContract.badRequest(
                "DBT_DRAFT_TARGET_PHYSICAL_NAME_INVALID",
                "Target physical name must use lower-case snake_case and contain at most 63 characters"
            );
        }
        return normalized;
    }

    private static String nextEtag() {
        return UUID.randomUUID().toString();
    }

    private void audit(
        String action,
        DraftRow draft,
        int fileCount,
        String validatedChecksum,
        Integer diagnosticCount,
        Integer artifactCount,
        String bundleChecksum,
        String correlationId
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", correlationId);
        payload.put("result", "SUCCESS");
        payload.put("tenantId", draft.tenantId());
        payload.put("planId", draft.planId());
        payload.put("modelSpecId", draft.modelSpecId());
        payload.put("draftId", draft.id());
        payload.put("baseModelRevision", draft.baseModelRevision());
        payload.put("baseModelChecksum", draft.baseModelChecksum());
        if (draft.baseImplementationRevision() != null) {
            payload.put("baseImplementationRevision", draft.baseImplementationRevision());
            payload.put("baseImplementationChecksum", draft.baseImplementationChecksum());
        }
        payload.put("fileCount", fileCount);
        if (validatedChecksum != null) payload.put("validatedChecksum", validatedChecksum);
        if (bundleChecksum != null) payload.put("bundleChecksum", bundleChecksum);
        if (diagnosticCount != null) payload.put("diagnosticCount", diagnosticCount);
        if (artifactCount != null) payload.put("artifactCount", artifactCount);
        if (draft.implementationRevision() != null) {
            payload.put("implementationRevision", draft.implementationRevision());
            payload.put("implementationChecksum", draft.implementationChecksum());
        }
        auditRecorder.recordSuccess(action, draft.id().toString(), Map.copyOf(payload));
    }

    private DraftException auditedFailure(
        String action,
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        Object request,
        String correlationId,
        RuntimeException failure
    ) {
        DraftException safe = safeFailure(failure, correlationId);
        if (safe.kind() == ErrorKind.SYSTEM_ERROR) {
            Throwable rootFailure = rootCause(failure);
            LOG.error(
                "Advanced dbt draft operation failed correlationId={} action={} modelSpecId={} draftId={} " +
                "failureType={} failureLocation={} rootFailureType={} rootFailureLocation={}",
                correlationId,
                action,
                modelSpecId,
                draftId,
                failure.getClass().getName(),
                failureLocation(failure),
                rootFailure.getClass().getName(),
                failureLocation(rootFailure)
            );
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", correlationId);
        payload.put("result", "FAILED");
        payload.put("errorCode", safe.code());
        payload.put("errorKind", safe.kind().name());
        if (tenantId != null) payload.put("tenantId", tenantId);
        if (actorId != null) payload.put("actorId", actorId);
        if (modelSpecId != null) payload.put("modelSpecId", modelSpecId);
        if (draftId != null) payload.put("draftId", draftId);
        if (request instanceof CreateDraftRequest create) {
            if (create.planId() != null) payload.put("planId", create.planId());
            payload.put("baseModelRevision", create.baseModelRevision());
            if (create.baseModelChecksum() != null) payload.put("baseModelChecksum", create.baseModelChecksum());
            if (create.baseImplementationRevision() != null) {
                payload.put("baseImplementationRevision", create.baseImplementationRevision());
            }
            if (create.baseImplementationChecksum() != null) {
                payload.put("baseImplementationChecksum", create.baseImplementationChecksum());
            }
        } else if (request instanceof SaveFilesRequest save) {
            payload.put("fileCount", save.files() == null ? 0 : save.files().size());
        } else if (request instanceof CommitDraftRequest commit && commit.validatedChecksum() != null) {
            payload.put("validatedChecksum", commit.validatedChecksum());
        }
        String resourceId = draftId != null
            ? draftId.toString()
            : (modelSpecId == null ? "dbt-draft" : modelSpecId.toString());
        try {
            auditRecorder.recordFailure(action, resourceId, Map.copyOf(payload));
        } catch (RuntimeException auditFailure) {
            Throwable rootFailure = rootCause(auditFailure);
            LOG.error(
                "Advanced dbt draft failure audit failed correlationId={} action={} modelSpecId={} draftId={} " +
                "failureType={} failureLocation={} rootFailureType={} rootFailureLocation={}",
                correlationId,
                action,
                modelSpecId,
                draftId,
                auditFailure.getClass().getName(),
                failureLocation(auditFailure),
                rootFailure.getClass().getName(),
                failureLocation(rootFailure)
            );
            return DbtImplementationDraftContract.systemError(correlationId, auditFailure);
        }
        return safe;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root;
    }

    private static StackTraceElement failureLocation(Throwable failure) {
        StackTraceElement[] trace = failure.getStackTrace();
        for (StackTraceElement frame : trace) {
            if (frame.getClassName().startsWith("com.yuzhi.dts.")) return frame;
        }
        return trace.length == 0 ? null : trace[0];
    }

    private static DraftException safeFailure(RuntimeException failure, String correlationId) {
        if (failure instanceof DraftException draftFailure) {
            return DbtImplementationDraftContract.withCorrelation(draftFailure, correlationId);
        }
        if (failure instanceof ModelSpecException modelFailure) {
            ErrorKind kind = switch (modelFailure.kind()) {
                case BAD_REQUEST -> ErrorKind.BAD_REQUEST;
                case UNPROCESSABLE -> ErrorKind.UNPROCESSABLE;
                case FORBIDDEN -> ErrorKind.FORBIDDEN;
                case NOT_FOUND -> ErrorKind.NOT_FOUND;
                case CONFLICT -> ErrorKind.CONFLICT;
                case PRECONDITION_REQUIRED -> ErrorKind.PRECONDITION_FAILED;
            };
            LinkedHashMap<String, Object> details = new LinkedHashMap<>();
            if (modelFailure.details() instanceof Map<?, ?> values) values.forEach((key, value) -> details.put(Objects.toString(key), value));
            details.put("correlationId", correlationId);
            return new DraftException(modelFailure.code(), modelFailure.getMessage(), kind, details);
        }
        return DbtImplementationDraftContract.systemError(correlationId, failure);
    }
}
