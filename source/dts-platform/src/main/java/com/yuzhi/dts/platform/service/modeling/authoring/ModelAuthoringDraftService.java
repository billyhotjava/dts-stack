package com.yuzhi.dts.platform.service.modeling.authoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelAuthoringModelValidator;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringContextView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringProjection;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringProvenance;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CommitAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CommitAuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CreateAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.DraftIntent;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.SaveAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.SaveAuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.ValidateAuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringOrigin;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringSeed;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.Diagnostic;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftService;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.AuthoringDraftState;
import com.yuzhi.dts.platform.service.modeling.representation.ModelVisualizationCapabilityEvaluator;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One public command boundary for visual and code authoring over the same canonical draft. */
@Service
public class ModelAuthoringDraftService {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelLifecycleService lifecycle;
    private final DbtImplementationDraftService drafts;
    private final ModelSpecPlanWriteAccessPort writeAccess;
    private final ModelAuthoringProjectionService projections;
    private final ModelVisualizationCapabilityEvaluator capabilities;
    private final ModelAuthoringModelValidator modelValidator;
    private final ObjectMapper objectMapper;
    private final ModelAuthoringSnapshotDecoder snapshotDecoder;
    private final ModelAuthoringSnapshotFactory snapshotFactory;

    public ModelAuthoringDraftService(
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        DbtImplementationDraftService drafts,
        ModelSpecPlanWriteAccessPort writeAccess,
        ModelAuthoringProjectionService projections,
        ModelVisualizationCapabilityEvaluator capabilities,
        ModelAuthoringModelValidator modelValidator,
        ObjectMapper objectMapper
    ) {
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.drafts = drafts;
        this.writeAccess = writeAccess;
        this.projections = projections;
        this.capabilities = capabilities;
        this.modelValidator = modelValidator;
        this.objectMapper = objectMapper;
        this.snapshotDecoder = new ModelAuthoringSnapshotDecoder(objectMapper);
        this.snapshotFactory = new ModelAuthoringSnapshotFactory(objectMapper);
    }

    @Transactional(readOnly = true)
    public AuthoringContextView context(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        Integer modelRevision,
        Integer implementationRevision,
        boolean technicalAuthorized
    ) {
        ModelSpecView current = modelSpecs.get(tenantId, modelSpecId);
        ModelSpecView model = modelRevision == null || modelRevision == current.revision()
            ? current
            : modelSpecs.revision(tenantId, new ModelRevisionRef(modelSpecId, modelRevision));
        TimelineView timeline = lifecycle.timeline(tenantId, modelSpecId);
        ImplementationView implementation = exactImplementation(timeline, implementationRevision);
        Optional<DraftView> open = model.id().equals(current.id()) && model.revision() == current.revision()
            ? drafts.findOpenAuthoring(tenantId, actorId, modelSpecId)
            : Optional.empty();
        AuthoringProjection projection = open
            .map(this::project)
            .orElseGet(() -> AuthoringProjection.unknown("MODEL_AUTHORING_PROJECTION_NOT_PREPARED"));
        boolean maintainer = writeAccess.canEdit(tenantId, model.id(), actorId);
        var allowed = capabilities
            .authoringActions(
                model.status(),
                technicalAuthorized,
                maintainer,
                open.map(ModelAuthoringDraftService::draftState).orElse(AuthoringDraftState.NONE),
                projection.coverage()
            )
            .allowedActions();
        return new AuthoringContextView(
            model,
            implementation,
            provenance(model, open.orElse(null)),
            projection,
            open.orElse(null),
            allowed,
            model.status() == ModelStatus.PUBLISHED
        );
    }

    @Transactional
    public AuthoringDraftView create(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        CreateAuthoringDraftRequest request
    ) {
        if (request == null || request.intent() == null) {
            throw error("MODEL_AUTHORING_REQUEST_INVALID", "Authoring intent and base pins are required", ModelAuthoringException.Kind.BAD_REQUEST);
        }
        ModelSpecView current = modelSpecs.get(tenantId, modelSpecId);
        requireMaintainer(tenantId, actorId, current);
        String requestHash = requestHash(tenantId, actorId, modelSpecId, request);
        Optional<DraftView> replay = drafts.findAuthoringByIdempotency(
            tenantId,
            actorId,
            modelSpecId,
            current.planId(),
            request.idempotencyKey(),
            requestHash
        );
        if (replay.isPresent()) return authoringView(current, replay.orElseThrow(), true);

        requireModelPins(current, request.baseModelRevision(), request.baseModelChecksum());
        ImplementationView implementation = exactImplementation(lifecycle.timeline(tenantId, modelSpecId), request.baseImplementationRevision());
        requireImplementationPins(implementation, request.baseImplementationRevision(), request.baseImplementationChecksum());
        ModelSpecView editable = switch (current.status()) {
            case DRAFT -> {
                if (request.intent() != DraftIntent.EDIT_DRAFT) {
                    throw error(
                        "MODEL_AUTHORING_DRAFT_INTENT_INVALID",
                        "A DRAFT ModelSpec must be opened with EDIT_DRAFT",
                        ModelAuthoringException.Kind.CONFLICT
                    );
                }
                yield current;
            }
            case PUBLISHED -> {
                if (request.intent() != DraftIntent.FORK_PUBLISHED) {
                    throw error(
                        "MODEL_AUTHORING_PUBLISHED_FORK_REQUIRED",
                        "Create a new draft version before editing a published ModelSpec",
                        ModelAuthoringException.Kind.CONFLICT
                    );
                }
                yield modelSpecs.forkPublishedForAuthoring(
                    tenantId,
                    actorId,
                    modelSpecId,
                    new ExpectedVersion(modelSpecId, current.revision(), current.checksum())
                );
            }
            default -> throw error(
                "MODEL_AUTHORING_STATUS_READONLY",
                "Only DRAFT or PUBLISHED ModelSpecs can enter authoring",
                ModelAuthoringException.Kind.CONFLICT,
                Map.of("status", current.status())
            );
        };
        JsonNode snapshot = snapshotFactory.create(
            editable,
            implementation,
            "authoring-visual-" + UUID.randomUUID()
        );
        AuthoringOrigin origin = snapshot.hasNonNull("visualImplementation") || editable.implementationMode() == ImplementationMode.DESIGNER_GENERATED
            ? AuthoringOrigin.SYSTEM_GENERATED
            : AuthoringOrigin.UNKNOWN;
        AuthoringProjection pendingProjection = AuthoringProjection.unknown("MODEL_AUTHORING_PROJECTION_PENDING");
        DraftView draft = drafts.createAuthoring(
            tenantId,
            actorId,
            modelSpecId,
            new CreateDraftRequest(
                editable.planId(),
                editable.revision(),
                editable.checksum(),
                request.baseImplementationRevision(),
                request.baseImplementationChecksum(),
                request.targetPhysicalName(),
                request.idempotencyKey()
            ),
            new AuthoringSeed(snapshot, objectMapper.valueToTree(pendingProjection), origin, requestHash)
        );
        return authoringView(editable, draft, true);
    }

    @Transactional
    public SaveAuthoringDraftView save(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        SaveAuthoringDraftRequest request
    ) {
        if (request == null || request.modelSpecSnapshot() == null || !request.modelSpecSnapshot().isObject()) {
            throw error("MODEL_AUTHORING_SNAPSHOT_REQUIRED", "A ModelSpec authoring snapshot is required", ModelAuthoringException.Kind.BAD_REQUEST);
        }
        DraftView open = drafts
            .findOpenAuthoring(tenantId, actorId, modelSpecId)
            .filter(candidate -> candidate.draftId().equals(draftId))
            .orElseThrow(() -> error("MODEL_AUTHORING_DRAFT_NOT_FOUND", "The authoring draft was not found", ModelAuthoringException.Kind.NOT_FOUND));
        boolean visualView = request.activeView() == ModelAuthoringContract.ActiveView.VISUAL;
        JsonNode snapshot = ModelAuthoringSnapshotPolicy.forSave(open.modelSpecSnapshot(), request.modelSpecSnapshot(),
            visualView, ModelAuthoringSnapshotPolicy.filesChanged(open.sourceBundle(), request.files()));
        SourceBundleView submittedBundle = withFiles(open.sourceBundle(), request.files());
        AuthoringProjection projection = project(submittedBundle, snapshot);
        var saved = drafts.saveAuthoring(
            tenantId,
            actorId,
            modelSpecId,
            draftId,
            request.expectedEtag(),
            snapshot,
            objectMapper.valueToTree(projection),
            request.files(),
            request.activeView() == ModelAuthoringContract.ActiveView.VISUAL
        );
        List<FileInput> files = drafts.authoringFiles(tenantId, actorId, modelSpecId, draftId);
        DraftView savedDraft = drafts
            .findOpenAuthoring(tenantId, actorId, modelSpecId)
            .filter(candidate -> candidate.draftId().equals(saved.draftId()))
            .orElseThrow(() -> error("MODEL_AUTHORING_DRAFT_NOT_FOUND", "The saved authoring draft was not found", ModelAuthoringException.Kind.NOT_FOUND));
        AuthoringProjection savedProjection = project(withFiles(open.sourceBundle(), files), savedDraft.modelSpecSnapshot());
        return new SaveAuthoringDraftView(
            saved.draftId(),
            saved.etag(),
            savedDraft.modelSpecSnapshot(),
            savedProjection,
            saved.fileCount(),
            saved.totalBytes(),
            files
        );
    }

    @Transactional
    public ValidateAuthoringDraftView validate(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        String expectedEtag
    ) {
        DraftView open = drafts
            .findOpenAuthoring(tenantId, actorId, modelSpecId)
            .filter(candidate -> candidate.draftId().equals(draftId))
            .orElseThrow(() -> error("MODEL_AUTHORING_DRAFT_NOT_FOUND", "The authoring draft was not found", ModelAuthoringException.Kind.NOT_FOUND));
        var decoded = snapshotDecoder.decode(open.modelSpecSnapshot());
        if (!decoded.valid()) return new ValidateAuthoringDraftView(null, decoded.issues(), List.of());
        List<Diagnostic> projectionIssues = projections
            .project(open.sourceBundle(), decoded.modelSpec().fields())
            .reasons()
            .stream()
            .map(reason -> new Diagnostic(reason, "WARNING", null, null, "The implementation remains editable in the code view"))
            .toList();
        var validation = drafts.validate(
            tenantId,
            actorId,
            modelSpecId,
            draftId,
            new ValidateDraftRequest(expectedEtag)
        );
        return new ValidateAuthoringDraftView(validation, modelValidator.validate(decoded.modelSpec()), projectionIssues);
    }

    @Transactional
    public CommitAuthoringDraftView commit(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID draftId,
        CommitAuthoringDraftRequest request
    ) {
        if (request == null || request.implementation() == null) {
            throw error("MODEL_AUTHORING_COMMIT_INVALID", "A validated authoring commit is required", ModelAuthoringException.Kind.BAD_REQUEST);
        }
        DraftView open = drafts
            .findOpenAuthoring(tenantId, actorId, modelSpecId)
            .filter(candidate -> candidate.draftId().equals(draftId))
            .orElseThrow(() -> error("MODEL_AUTHORING_DRAFT_NOT_FOUND", "The authoring draft was not found", ModelAuthoringException.Kind.NOT_FOUND));
        var decoded = snapshotDecoder.decode(open.modelSpecSnapshot());
        if (!decoded.valid()) {
            throw error(
                "MODEL_AUTHORING_MODEL_INVALID",
                "The ModelSpec authoring snapshot must pass validation before commit",
                ModelAuthoringException.Kind.UNPROCESSABLE,
                Map.of("issues", decoded.issues())
            );
        }
        var receipt = drafts.commit(tenantId, actorId, modelSpecId, draftId, request.implementation());
        return new CommitAuthoringDraftView(receipt, open.authoringOrigin());
    }

    private AuthoringDraftView authoringView(ModelSpecView model, DraftView draft, boolean technicalAuthorized) {
        AuthoringProjection projection = project(draft);
        var allowed = capabilities
            .authoringActions(model.status(), technicalAuthorized, true, draftState(draft), projection.coverage())
            .allowedActions();
        return new AuthoringDraftView(model, draft, provenance(model, draft), projection, allowed);
    }

    private AuthoringProjection project(DraftView draft) {
        return project(draft.sourceBundle(), draft.modelSpecSnapshot());
    }

    private AuthoringProjection project(SourceBundleView source, JsonNode snapshot) {
        var decoded = snapshotDecoder.decode(snapshot);
        return projections.project(source, decoded.valid() ? decoded.modelSpec().fields() : null);
    }

    private String requestHash(String tenantId, String actorId, UUID modelSpecId, CreateAuthoringDraftRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("modelSpecId", modelSpecId);
        payload.put("request", request);
        try {
            return ModelPackageChecksum.sha256(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The authoring request could not be hashed", exception);
        }
    }

    private static void requireModelPins(ModelSpecView model, int revision, String checksum) {
        if (revision < 1 || checksum == null || !checksum.matches("^[0-9a-fA-F]{64}$")) {
            throw error("MODEL_AUTHORING_BASE_PIN_INVALID", "Strong ModelSpec base pins are required", ModelAuthoringException.Kind.BAD_REQUEST);
        }
        if (model.revision() != revision || !Objects.equals(model.checksum(), checksum.toLowerCase())) {
            throw error("MODEL_AUTHORING_BASE_MODEL_CONFLICT", "The ModelSpec changed before authoring started", ModelAuthoringException.Kind.CONFLICT);
        }
    }

    private static void requireImplementationPins(
        ImplementationView current,
        Integer revision,
        String checksum
    ) {
        if ((revision == null) != (checksum == null)) {
            throw error("MODEL_AUTHORING_IMPLEMENTATION_PIN_INVALID", "Implementation pins must be supplied together", ModelAuthoringException.Kind.BAD_REQUEST);
        }
        if (revision == null) {
            if (current != null) {
                throw error("MODEL_AUTHORING_IMPLEMENTATION_PIN_REQUIRED", "The current implementation pin is required", ModelAuthoringException.Kind.CONFLICT);
            }
            return;
        }
        if (
            current == null ||
            current.implementationRevision() != revision ||
            !Objects.equals(current.implementationChecksum(), checksum)
        ) {
            throw error("MODEL_AUTHORING_BASE_IMPLEMENTATION_CONFLICT", "The implementation changed before authoring started", ModelAuthoringException.Kind.CONFLICT);
        }
    }

    private static ImplementationView exactImplementation(TimelineView timeline, Integer requestedRevision) {
        ImplementationView current = timeline == null ? null : timeline.implementation();
        if (requestedRevision == null || current == null || current.implementationRevision() == requestedRevision) return current;
        throw error("MODEL_AUTHORING_IMPLEMENTATION_NOT_FOUND", "The requested implementation revision is not current", ModelAuthoringException.Kind.NOT_FOUND);
    }

    private static SourceBundleView withFiles(SourceBundleView source, List<FileInput> files) {
        if (source == null) throw error("MODEL_AUTHORING_SOURCE_BUNDLE_UNAVAILABLE", "The authoring source bundle is unavailable", ModelAuthoringException.Kind.CONFLICT);
        List<com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.BundleFileView> views = files
            .stream()
            .map(file -> {
                byte[] bytes = file.content().getBytes(StandardCharsets.UTF_8);
                return new com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.BundleFileView(
                    file.path(), file.content(), ModelPackageChecksum.sha256(bytes), bytes.length
                );
            })
            .toList();
        return new SourceBundleView(
            source.projectKey(), source.projectChecksum(), source.bundleChecksum(), source.sourceKind(), source.lossless(), views,
            source.dependencyChecksum(), source.dependencySnapshot(), source.managedDependencyAliases()
        );
    }

    private static AuthoringProvenance provenance(ModelSpecView model, DraftView draft) {
        AuthoringOrigin origin = draft == null ? AuthoringOrigin.UNKNOWN : draft.authoringOrigin();
        if (origin == AuthoringOrigin.UNKNOWN && model.implementationMode() == ImplementationMode.DESIGNER_GENERATED) {
            origin = AuthoringOrigin.SYSTEM_GENERATED;
        }
        SourceBundleView source = draft == null ? null : draft.sourceBundle();
        return new AuthoringProvenance(
            origin,
            source == null ? null : source.sourceKind(),
            source != null && source.lossless(),
            source == null ? null : source.bundleChecksum()
        );
    }

    private static AuthoringDraftState draftState(DraftView draft) {
        if (draft == null) return AuthoringDraftState.NONE;
        return switch (draft.state()) {
            case DRAFT -> AuthoringDraftState.DRAFT;
            case VALIDATED -> AuthoringDraftState.VALIDATED;
            case COMMITTING, COMMITTED -> AuthoringDraftState.COMMITTED;
        };
    }

    private void requireMaintainer(String tenantId, String actorId, ModelSpecView model) {
        writeAccess.requireEdit(tenantId, model.id(), actorId);
        if (actorId == null || actorId.isBlank() || !writeAccess.canEdit(tenantId, model.id(), actorId)) {
            throw error("MODEL_AUTHORING_MAINTAINER_FORBIDDEN", "The current actor cannot maintain this model", ModelAuthoringException.Kind.FORBIDDEN);
        }
    }

    private static ModelAuthoringException error(String code, String message, ModelAuthoringException.Kind kind) {
        return new ModelAuthoringException(code, message, kind);
    }

    private static ModelAuthoringException error(
        String code,
        String message,
        ModelAuthoringException.Kind kind,
        Map<String, Object> details
    ) {
        return new ModelAuthoringException(code, message, kind, details);
    }
}
