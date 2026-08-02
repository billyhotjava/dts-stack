package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.DraftRow;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.FileRow;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.NewDraft;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ArtifactType;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportResult;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportedArtifact;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.NodeKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.BundleFileView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.Diagnostic;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for static-only advanced dbt draft editing and immutable commit. */
@Service
public class DbtImplementationDraftService {

    private static final Duration DRAFT_TTL = Duration.ofHours(24);
    private static final String AUDIT_CREATE = "MODELING_DBT_DRAFT_CREATE";
    private static final String AUDIT_SAVE = "MODELING_DBT_DRAFT_SAVE";
    private static final String AUDIT_VALIDATE = "MODELING_DBT_DRAFT_VALIDATE";
    private static final String AUDIT_COMMIT = "MODELING_DBT_DRAFT_COMMIT";

    private final DbtImplementationDraftRepository repository;
    private final ModelSpecApplicationService modelSpecs;
    private final ModelLifecycleService lifecycle;
    private final ModelSpecPlanWriteAccessPort writeAccess;
    private final AdvancedDbtDraftStaticValidator validator;
    private final ModelingDbtArtifactImportService artifactImports;
    private final ModelRepresentationEvidencePort representationEvidence;
    private final DbtImplementationDraftAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final DbtCanonicalProjectReconstructor canonicalProjects;
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
        ObjectMapper objectMapper
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
        this.repository = repository;
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.writeAccess = writeAccess;
        this.validator = validator;
        this.artifactImports = artifactImports;
        this.representationEvidence = representationEvidence;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.canonicalProjects = new DbtCanonicalProjectReconstructor(objectMapper);
        this.clock = clock;
    }

    @Transactional
    public DraftView create(String tenantId, String actorId, UUID modelSpecId, CreateDraftRequest request) {
        String correlationId = DbtImplementationDraftCorrelation.currentOrCreate();
        try {
            return createInternal(tenantId, actorId, modelSpecId, request, correlationId);
        } catch (RuntimeException failure) {
            throw auditedFailure(AUDIT_CREATE, tenantId, actorId, modelSpecId, null, request, correlationId, failure);
        }
    }

    private DraftView createInternal(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        CreateDraftRequest request,
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
        String requestHash = requestHash(
            tenantId,
            actorId,
            request.planId(),
            modelSpecId,
            request.baseModelRevision(),
            modelChecksum,
            implementationRevision,
            implementationChecksum
        );
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

        ModelSpecView model = requireEditableModel(tenantId, actorId, modelSpecId, request.planId());
        if (model.revision() != request.baseModelRevision() || !Objects.equals(model.checksum(), modelChecksum)) {
            throw DbtImplementationDraftContract.conflict(
                "DBT_DRAFT_BASE_MODEL_CONFLICT",
                "The ModelSpec revision changed before draft creation"
            );
        }
        ImplementationView implementation = lifecycle.timeline(tenantId, modelSpecId).implementation();
        requireImplementationPin(modelSpecId, implementation, implementationRevision, implementationChecksum);
        SourceBundleView sourceBundle = sourceBundle(
            tenantId,
            request.planId(),
            modelSpecId,
            request.baseModelRevision(),
            modelChecksum,
            implementationRevision,
            implementationChecksum,
            implementation,
            model
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
                nextEtag(),
                now.plus(DRAFT_TTL),
                now
            )
        );
        return createdDraft(row, requestHash, now, correlationId);
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
            return source;
        } catch (DraftException failure) {
            throw failure;
        } catch (RuntimeException | JsonProcessingException failure) {
            throw sourceBundleUnavailable();
        }
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
        DraftRow saved = repository
            .replaceFiles(tenantId, modelSpecId, draftId, actorId, expectedEtag, nextEtag(), files, now)
            .orElseThrow(() -> etagConflict(current, expectedEtag));
        long totalBytes = files.stream().mapToLong(file -> file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
        audit(AUDIT_SAVE, saved, files.size(), null, null, null, null, correlationId);
        return new SaveFilesView(saved.id(), saved.etag(), saved.expiresAt(), files.size(), totalBytes);
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
        List<FileRow> files = repository.listFiles(draftId);
        if (files.isEmpty()) {
            throw DbtImplementationDraftContract.unprocessable(
                "DBT_DRAFT_FILES_REQUIRED",
                "Save the isolated dbt project before validation"
            );
        }
        ValidatedProject validated = staticValidate(files);
        BundleSnapshot bundle = freezeBundle(files, validated);
        List<Diagnostic> diagnostics = diagnostics(validated);
        List<ProposedNode> structure = structure(validated);
        DraftRow saved = repository
            .markValidated(
                tenantId,
                modelSpecId,
                draftId,
                actorId,
                expectedEtag,
                nextEtag(),
                validated.validatedChecksum(),
                bundle.projectChecksum(),
                bundle.bundleChecksum(),
                bundle.manifest(),
                validationSummary(diagnostics, structure),
                now
            )
            .orElseThrow(() -> etagConflict(current, expectedEtag));
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
            structure
        );
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
        String frozenBundleChecksum = requireFrozenChecksum(current.bundleChecksum());
        String derivedCommitKey = commitKey(idempotencyKey, validatedChecksum, frozenBundleChecksum);
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
                return committedView(current);
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
        if (!Objects.equals(validated.validatedChecksum(), validatedChecksum)) {
            throw DbtImplementationDraftContract.precondition(
                "DBT_DRAFT_VALIDATED_CONTENT_CONFLICT",
                "Draft content no longer matches the validated checksum"
            );
        }
        requireFrozenBundle(current, bundle);
        ModelSpecView model = requireEditableModel(tenantId, actorId, modelSpecId, current.planId());
        requireModelPins(current, model);
        TimelineView timeline = lifecycle.timeline(tenantId, modelSpecId);
        ImplementationView baseImplementation = timeline.implementation();
        requireImplementationPin(
            modelSpecId,
            baseImplementation,
            current.baseImplementationRevision(),
            current.baseImplementationChecksum()
        );
        ValidatedNode target = target(validated, baseImplementation);
        requireMaterialization(model, target);

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
                return committedView(winner);
            }
            throw etagConflict(winner, expectedEtag);
        }
        DraftRow claimed = claim.orElseThrow();
        ExpectedImplementationVersion expectedImplementation = new ExpectedImplementationVersion(
            modelSpecId,
            current.baseImplementationRevision() == null ? 0 : current.baseImplementationRevision(),
            current.baseImplementationChecksum()
        );
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(
                new GeneratedInput(
                    "DBT",
                    Map.of(
                        "projectKey",
                        validated.projectKey(),
                        "dbtUniqueId",
                        target.dbtUniqueId(),
                        "projectChecksum",
                        bundle.projectChecksum(),
                        "bundleChecksum",
                        bundle.bundleChecksum()
                    )
                )
            ),
            List.of(),
            Map.of(),
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
        List<ImportedArtifact> artifacts = artifacts(target, model.materialization(), bundle);
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
        return committedView(committed);
    }

    private ModelSpecView requireEditableModel(String tenantId, String actorId, UUID modelSpecId, UUID planId) {
        ModelSpecView model = modelSpecs.get(tenantId, modelSpecId);
        if (!Objects.equals(model.planId(), planId)) {
            throw DbtImplementationDraftContract.forbidden(
                "DBT_DRAFT_PLAN_FORBIDDEN",
                "The requested plan does not own this ModelSpec"
            );
        }
        if (!writeAccess.canMaintain(tenantId, planId, actorId)) {
            throw DbtImplementationDraftContract.forbidden(
                "DBT_DRAFT_MAINTAINER_FORBIDDEN",
                "The current actor cannot maintain this warehouse plan"
            );
        }
        if (model.implementationMode() != ImplementationMode.DBT_MANAGED) {
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
        String checksum
    ) {
        if (implementation == null) {
            if (revision == null && checksum == null) return;
        } else if (
            implementation.modelSpecId().equals(modelSpecId) &&
            implementation.ownership() == ImplementationMode.DBT_MANAGED &&
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
        List<ValidatedNode> models = validated.nodes().stream().filter(node -> "MODEL".equals(node.nodeKind())).toList();
        if (models.size() != 1) {
            throw DbtImplementationDraftContract.unprocessable(
                "DBT_DRAFT_MODEL_SELECTION_UNSUPPORTED",
                "A first DBT_MANAGED implementation draft must contain exactly one editable model"
            );
        }
        return models.getFirst();
    }

    private static void requireMaterialization(ModelSpecView model, ValidatedNode target) {
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
        ValidatedNode node,
        String materialization,
        BundleSnapshot bundle
    ) {
        return List.of(
            new ImportedArtifact(
                node.dbtUniqueId(),
                NodeKind.MODEL,
                ArtifactType.SQL,
                node.resourcePath(),
                node.sqlChecksum(),
                node.sql(),
                materialization
            ),
            new ImportedArtifact(
                node.dbtUniqueId(),
                NodeKind.MODEL,
                ArtifactType.SCHEMA,
                node.resourcePath() + "#schema",
                node.schemaChecksum(),
                node.schema(),
                materialization
            ),
            new ImportedArtifact(
                node.dbtUniqueId(),
                NodeKind.MODEL,
                ArtifactType.CONFIG,
                ".dts/dbt-project-bundle.json",
                bundle.bundleChecksum(),
                bundle.manifest(),
                materialization
            )
        );
    }

    private BundleSnapshot freezeBundle(List<FileRow> files, ValidatedProject validated) {
        List<BundleFile> bundleFiles = files
            .stream()
            .map(file -> new BundleFile(file.path(), file.content(), file.checksum(), file.byteSize()))
            .toList();
        return DbtProjectBundleManifest.freeze(objectMapper, bundleFiles, validated);
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
        ModelSpecView model
    ) {
        if (current == null) {
            return freezeCanonical(
                canonicalProjects.initialize(modelSpecId, model.materialization()),
                SourceBundleKind.CANONICAL_INITIALIZATION
            );
        }
        if (implementationRevision == null || implementationChecksum == null) throw sourceBundleUnavailable();

        RepresentationEvidence evidence = representationEvidence
            .findExact(tenantId, modelSpecId, modelRevision, modelChecksum, implementationRevision, true)
            .orElseThrow(DbtImplementationDraftService::sourceBundleUnavailable);
        ImplementationSnapshot snapshot = evidence.implementation();
        requireSourcePins(
            planId,
            modelSpecId,
            modelRevision,
            modelChecksum,
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
                modelRevision,
                modelChecksum,
                implementationRevision,
                implementationChecksum
            );
            return freezeCanonical(canonical, SourceBundleKind.CANONICAL_ARTIFACT_RECONSTRUCTION);
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
            modelRevision,
            modelChecksum,
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
        return new SourceBundleView(
            restored.projectKey(),
            restored.projectChecksum(),
            restored.bundleChecksum(),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            true,
            files
        );
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
            files.stream().map(file -> new BundleFileView(file.path(), file.content(), file.checksum(), file.byteSize())).toList()
        );
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

    private static String commitKey(String idempotencyKey, String validatedChecksum, String bundleChecksum) {
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

    private String validationSummary(List<Diagnostic> diagnostics, List<ProposedNode> structure) {
        try {
            return objectMapper.writeValueAsString(Map.of("diagnostics", diagnostics, "proposedStructure", structure));
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

    private static DraftView view(DraftRow row, SourceBundleView sourceBundle) {
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
            sourceBundle
        );
    }

    private static CommitView committedView(DraftRow row) {
        if (row.implementationId() == null || row.implementationRevision() == null) {
            throw new IllegalStateException("Committed advanced dbt draft is missing its implementation receipt");
        }
        return new CommitView(
            row.id(),
            row.modelSpecId(),
            row.baseModelRevision(),
            row.baseModelChecksum(),
            row.implementationId(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.artifactCount(),
            row.etag()
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
        String implementationChecksum
    ) {
        return ModelPackageChecksum.sha256Text(
            String.join(
                "\u0000",
                tenantId,
                actorId.trim(),
                planId.toString(),
                modelSpecId.toString(),
                Integer.toString(modelRevision),
                modelChecksum,
                Objects.toString(implementationRevision, ""),
                Objects.toString(implementationChecksum, "")
            )
        );
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
            return DbtImplementationDraftContract.systemError(correlationId, auditFailure);
        }
        return safe;
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
            return new DraftException(
                modelFailure.code(),
                modelFailure.getMessage(),
                kind,
                Map.of("correlationId", correlationId)
            );
        }
        return DbtImplementationDraftContract.systemError(correlationId, failure);
    }
}
