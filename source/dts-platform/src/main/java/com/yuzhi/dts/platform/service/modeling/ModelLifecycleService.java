package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ClaimImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.CompileView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStep;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStepView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReleaseView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RollbackCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RunCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TestEvidenceCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.Stage;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical ModelSpec lifecycle; all evidence is pinned to modelSpecId/revision/checksum. */
@Service
public class ModelLifecycleService {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecRepository modelSpecRepository;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecStageGateService stageGates;
    private final ModelSpecPlanWriteAccessPort writeAccess;
    private final ModelLifecycleCompilerPort compiler;
    private final ModelReleaseRegistrationPort registrations;
    private final ModelLifecycleTestEvidencePort testEvidence;
    private final ModelLifecyclePublicationService publication;
    private final ModelingVNextApplicationService runtime;
    private final Clock clock;

    @Autowired
    public ModelLifecycleService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycle,
        ModelSpecStageGateService stageGates,
        ModelSpecPlanWriteAccessPort writeAccess,
        ModelLifecycleCompilerPort compiler,
        ModelReleaseRegistrationPort registrations,
        ModelLifecycleTestEvidencePort testEvidence,
        ModelLifecyclePublicationService publication,
        ModelingVNextApplicationService runtime
    ) {
        this(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            stageGates,
            writeAccess,
            compiler,
            registrations,
            testEvidence,
            publication,
            runtime,
            Clock.systemUTC()
        );
    }

    ModelLifecycleService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycle,
        ModelSpecStageGateService stageGates,
        ModelSpecPlanWriteAccessPort writeAccess,
        ModelLifecycleCompilerPort compiler,
        ModelReleaseRegistrationPort registrations,
        ModelLifecycleTestEvidencePort testEvidence,
        ModelLifecyclePublicationService publication,
        ModelingVNextApplicationService runtime,
        Clock clock
    ) {
        this.modelSpecs = modelSpecs;
        this.modelSpecRepository = modelSpecRepository;
        this.lifecycle = lifecycle;
        this.stageGates = stageGates;
        this.writeAccess = writeAccess;
        this.compiler = compiler;
        this.registrations = registrations;
        this.testEvidence = testEvidence;
        this.publication = publication;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Transactional
    public ImplementationView claim(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ClaimImplementationCommand command
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_IMPLEMENTATION_IDEMPOTENCY_REQUIRED");
        requireText(command == null ? null : command.projectKey(), "MODEL_IMPLEMENTATION_PROJECT_REQUIRED");
        requireText(command == null ? null : command.dbtUniqueId(), "MODEL_IMPLEMENTATION_DBT_ID_REQUIRED");
        if (command == null || command.ownership() == null || command.ownership() != model.implementationMode()) {
            throw conflict("MODEL_IMPLEMENTATION_OWNERSHIP_MISMATCH", "Implementation ownership must match the canonical ModelSpec");
        }
        requireGate(tenantId, modelSpecId, Stage.IMPLEMENTATION_READY);
        try {
            int changed = lifecycle.claimImplementation(
                tenantId,
                actorId,
                model,
                command.ownership(),
                command.projectKey().trim(),
                command.dbtUniqueId().trim(),
                command.idempotencyKey().trim(),
                clock.instant()
            );
            if (changed == 0) {
                throw conflict("MODEL_IMPLEMENTATION_OWNERSHIP_CONFLICT", "Another implementation owner already controls this ModelSpec");
            }
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("MODEL_IMPLEMENTATION_DBT_CONFLICT", "The dbt node is already owned by another ModelSpec");
        }
        return lifecycle.findImplementation(tenantId, modelSpecId).orElseThrow();
    }

    @Transactional
    public CompileView compile(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        String idempotencyKey
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        requireText(idempotencyKey, "MODEL_COMPILE_IDEMPOTENCY_REQUIRED");
        requireGate(tenantId, modelSpecId, Stage.IMPLEMENTATION_READY);
        ImplementationView owner = requireCurrentOwner(tenantId, model);
        LifecycleEventView replay = lifecycle.findEvent(tenantId, modelSpecId, EventType.COMPILE, idempotencyKey.trim()).orElse(null);
        if (replay != null) return new CompileView(owner, replay, lifecycle.listArtifacts(tenantId, modelSpecId, model.revision()));
        List<ArtifactWrite> artifacts = compiler.compile(tenantId, model);
        lifecycle.saveArtifacts(tenantId, model, owner, idempotencyKey.trim(), artifacts, clock.instant());
        LifecycleEventView event = lifecycle.recordEvent(
            tenantId,
            actorId,
            model,
            EventType.COMPILE,
            "PASSED",
            idempotencyKey.trim(),
            null,
            owner.dbtUniqueId(),
            Map.of("artifactCount", artifacts.size(), "ownership", owner.ownership().name()),
            clock.instant()
        );
        return new CompileView(owner, event, lifecycle.listArtifacts(tenantId, modelSpecId, model.revision()));
    }

    @Transactional
    public LifecycleEventView recordTest(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        TestEvidenceCommand command
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        requireCurrentOwner(tenantId, model);
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_TEST_IDEMPOTENCY_REQUIRED");
        requireText(command.externalRunId(), "MODEL_TEST_EXTERNAL_RUN_REQUIRED");
        List<ArtifactView> artifacts = lifecycle.listArtifacts(tenantId, modelSpecId, model.revision());
        if (!hasArtifact(artifacts, "SQL") || !hasArtifact(artifacts, "SCHEMA") || !hasArtifact(artifacts, "TEST")) {
            throw conflict("MODEL_TEST_COMPILE_REQUIRED", "Current revision must be compiled before test evidence can be recorded");
        }
        ModelLifecycleTestEvidencePort.TestEvidence verified = testEvidence.verify(command.externalRunId());
        String requested = normalizeStatus(command.status());
        if (requested != null && !requested.equals(verified.status())) {
            throw conflict("MODEL_TEST_STATUS_MISMATCH", "Client status does not match the persisted dbt run");
        }
        return lifecycle.recordEvent(
            tenantId,
            actorId,
            model,
            EventType.TEST,
            verified.status(),
            command.idempotencyKey().trim(),
            command.comment() == null ? verified.message() : command.comment(),
            verified.externalRunId(),
            Map.of("artifactRevision", model.revision()),
            clock.instant()
        );
    }

    @Transactional
    public LifecycleEventView submitReview(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ReviewCommand command
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_REVIEW_IDEMPOTENCY_REQUIRED");
        requireGate(tenantId, modelSpecId, Stage.RELEASE_READY);
        return lifecycle.recordEvent(
            tenantId,
            actorId,
            model,
            EventType.REVIEW_SUBMITTED,
            "PENDING",
            command.idempotencyKey().trim(),
            command.comment(),
            null,
            Map.of(),
            clock.instant()
        );
    }

    @Transactional
    public LifecycleEventView approveReview(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ReviewCommand command
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_REVIEW_IDEMPOTENCY_REQUIRED");
        if (lifecycle.findLatestEvent(tenantId, modelSpecId, model.revision(), EventType.REVIEW_SUBMITTED, "PENDING").isEmpty()) {
            throw conflict("MODEL_REVIEW_SUBMISSION_REQUIRED", "Submit the current ModelSpec revision for review first");
        }
        return lifecycle.recordEvent(
            tenantId,
            actorId,
            model,
            EventType.REVIEW_APPROVED,
            "APPROVED",
            command.idempotencyKey().trim(),
            command.comment(),
            null,
            Map.of(),
            clock.instant()
        );
    }

    public ReleaseView publish(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        PublishCommand command
    ) {
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_RELEASE_IDEMPOTENCY_REQUIRED");
        ModelSpecView current = writableCurrent(tenantId, actorId, modelSpecId, expected);
        LifecycleEventView replay = lifecycle
            .findEvent(tenantId, modelSpecId, EventType.RELEASE, command.idempotencyKey().trim())
            .orElse(null);
        if (replay != null) return releaseView(replay);
        if (current.status() != ModelStatus.DRAFT) {
            throw conflict("MODEL_RELEASE_DRAFT_REQUIRED", "Only a reviewed draft can be published");
        }
        requireGate(tenantId, modelSpecId, Stage.RELEASE_READY);
        if (lifecycle.findLatestEvent(tenantId, modelSpecId, current.revision(), EventType.REVIEW_APPROVED, "APPROVED").isEmpty()) {
            throw conflict("MODEL_REVIEW_APPROVAL_REQUIRED", "Current ModelSpec revision has not been approved");
        }
        requireCurrentOwner(tenantId, current);
        Instant now = clock.instant();
        ModelLifecyclePublicationService.Publication committed = publication.publish(tenantId, actorId, current, command, now);
        return register(committed.release(), tenantId, actorId, committed.model());
    }

    public ReleaseView retryRegistration(String tenantId, String actorId, UUID modelSpecId, UUID releaseEventId) {
        LifecycleEventView release = lifecycle.findEvent(tenantId, releaseEventId).orElseThrow(() -> notFound("Release event was not found"));
        if (!release.modelSpecId().equals(modelSpecId) || !release.eventType().equals(EventType.RELEASE)) {
            throw conflict("MODEL_RELEASE_EVENT_MISMATCH", "Release event does not belong to this ModelSpec");
        }
        ModelSpecView model = modelSpecs.get(tenantId, modelSpecId);
        requireWrite(tenantId, actorId, model);
        if (model.revision() != release.revision() || !Objects.equals(model.checksum(), release.modelChecksum())) {
            throw conflict("MODEL_RELEASE_EVIDENCE_STALE", "Release registration belongs to an older ModelSpec revision");
        }
        return register(release, tenantId, actorId, model);
    }

    public LifecycleEventView rollback(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        RollbackCommand command
    ) {
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_ROLLBACK_IDEMPOTENCY_REQUIRED");
        ModelSpecView current = writableCurrent(tenantId, actorId, modelSpecId, expected);
        LifecycleEventView replay = lifecycle
            .findEvent(tenantId, modelSpecId, EventType.ROLLBACK, command.idempotencyKey().trim())
            .orElse(null);
        if (replay != null) return replay;
        if (current.status() != ModelStatus.PUBLISHED) {
            throw conflict("MODEL_ROLLBACK_PUBLISHED_REQUIRED", "Only a published ModelSpec can be rolled back");
        }
        return publication.rollback(tenantId, actorId, current, command, clock.instant());
    }

    @Transactional
    public ModelingVNextApplicationService.RunView run(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        RunCommand command
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        if (model.status() != ModelStatus.PUBLISHED) {
            throw conflict("MODEL_RUN_PUBLISHED_REQUIRED", "Only a published ModelSpec can run");
        }
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_RUN_IDEMPOTENCY_REQUIRED");
        ModelingRunRequestContract.RunRequest request = new ModelingRunRequestContract.RunRequest(
            model.id().toString(),
            model.revision(),
            command.idempotencyKey().trim(),
            new ModelingRunRequestContract.ExternalContext(
                command.sourceBatchId(),
                command.addaxTaskId(),
                command.airflowDagId(),
                command.airflowRunId(),
                command.dbtRunId(),
                command.dbtSelector(),
                command.targetTable()
            )
        );
        ModelingVNextApplicationService.RunView result = runtime.createRun(tenantId, request);
        lifecycle.recordEvent(
            tenantId,
            actorId,
            model,
            EventType.RUN,
            result.state(),
            command.idempotencyKey().trim(),
            result.message(),
            result.id(),
            runDetails(result),
            clock.instant()
        );
        return result;
    }

    @Transactional(readOnly = true)
    public TimelineView timeline(String tenantId, UUID modelSpecId) {
        modelSpecs.get(tenantId, modelSpecId);
        return new TimelineView(
            lifecycle.findImplementation(tenantId, modelSpecId).orElse(null),
            lifecycle.listArtifacts(tenantId, modelSpecId, null),
            lifecycle.listEvents(tenantId, modelSpecId)
        );
    }

    private ReleaseView register(LifecycleEventView release, String tenantId, String actorId, ModelSpecView model) {
        List<ArtifactView> artifacts = lifecycle.listArtifacts(tenantId, model.id(), model.revision());
        Map<RegistrationStep, RegistrationStepView> existing = new LinkedHashMap<>();
        lifecycle.listRegistrations(release.id()).forEach(item -> existing.put(item.step(), item));
        for (RegistrationStep step : RegistrationStep.values()) {
            RegistrationStepView previous = existing.get(step);
            if (previous != null && "SUCCEEDED".equals(previous.status())) continue;
            RegistrationStepView attempt = lifecycle.startRegistration(release.id(), step, clock.instant());
            try {
                ModelReleaseRegistrationPort.RegistrationResult result = registrations.register(
                    step,
                    tenantId,
                    actorId,
                    release.id(),
                    model,
                    artifacts,
                    attempt.externalRef()
                );
                lifecycle.completeRegistration(release.id(), step, "SUCCEEDED", result.externalRef(), null, clock.instant());
            } catch (RuntimeException failure) {
                lifecycle.completeRegistration(
                    release.id(),
                    step,
                    "FAILED",
                    attempt.externalRef(),
                    safeMessage(failure),
                    clock.instant()
                );
            }
        }
        List<RegistrationStepView> steps = lifecycle.listRegistrations(release.id());
        String status = steps.size() == RegistrationStep.values().length && steps.stream().allMatch(item -> "SUCCEEDED".equals(item.status()))
            ? "PUBLISHED"
            : "PARTIAL";
        lifecycle.updateEventStatus(release.id(), status, Map.of("registrationCount", steps.size()));
        LifecycleEventView updated = lifecycle.findEvent(release.id()).orElseThrow();
        return new ReleaseView(updated, status, model.revision(), model.checksum(), steps);
    }

    private static Map<String, Object> runDetails(ModelingVNextApplicationService.RunView result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("runId", result.id());
        if (result.repairPath() != null) details.put("repairPath", result.repairPath());
        if (result.planId() != null) details.put("planId", result.planId());
        return details;
    }

    private ReleaseView releaseView(LifecycleEventView release) {
        return new ReleaseView(
            release,
            release.status(),
            release.revision(),
            release.modelChecksum(),
            lifecycle.listRegistrations(release.id())
        );
    }

    private ModelSpecView writableCurrent(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected
    ) {
        if (expected == null || !modelSpecId.equals(expected.modelSpecId())) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_INVALID",
                "A strong ModelSpec revision precondition is required",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        ModelSpecView model = modelSpecs.get(tenantId, modelSpecId);
        if (model.revision() != expected.revision() || !Objects.equals(model.checksum(), expected.checksum())) {
            throw conflict("MODEL_SPEC_REVISION_CONFLICT", "ModelSpec revision changed; refresh before continuing");
        }
        requireWrite(tenantId, actorId, model);
        return model;
    }

    private void requireWrite(String tenantId, String actorId, ModelSpecView model) {
        if (actorId == null || actorId.isBlank()) {
            throw new ModelSpecException("MODEL_SPEC_ACTOR_REQUIRED", "Authenticated actor is required", ModelSpecException.Kind.FORBIDDEN);
        }
        PlanState plan = modelSpecRepository.lockPlan(tenantId, model.planId()).orElseThrow(() -> notFound("Warehouse plan was not found"));
        if ("ARCHIVED".equals(plan.lifecycleStatus()) || !writeAccess.canMaintain(tenantId, model.planId(), actorId)) {
            throw new ModelSpecException(
                "MODEL_SPEC_PLAN_FORBIDDEN",
                "Warehouse plan is not available for lifecycle maintenance",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private void requireGate(String tenantId, UUID modelSpecId, Stage stage) {
        ModelSpecStageGateService.GateView gate = stageGates
            .evaluateAll(tenantId, modelSpecId)
            .stream()
            .filter(item -> item.stage() == stage)
            .findFirst()
            .orElseThrow();
        if (gate.status() != GateStatus.READY) {
            throw new ModelSpecException(
                "MODEL_LIFECYCLE_GATE_BLOCKED",
                "ModelSpec lifecycle gate is blocked",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of("stage", stage.name(), "blockers", gate.blockers())
            );
        }
    }

    private ImplementationView requireCurrentOwner(String tenantId, ModelSpecView model) {
        ImplementationView owner = lifecycle.findImplementation(tenantId, model.id()).orElseThrow(() ->
            conflict("MODEL_IMPLEMENTATION_OWNER_REQUIRED", "Claim an implementation owner before continuing")
        );
        if (
            owner.revision() != model.revision() ||
            !Objects.equals(owner.modelChecksum(), model.checksum()) ||
            owner.ownership() != model.implementationMode()
        ) {
            throw conflict("MODEL_IMPLEMENTATION_EVIDENCE_STALE", "Implementation ownership belongs to another ModelSpec revision");
        }
        return owner;
    }

    private static boolean hasArtifact(List<ArtifactView> artifacts, String type) {
        return artifacts.stream().anyMatch(item -> type.equals(item.artifactType()) && "COMPILED".equals(item.status()));
    }

    private static String normalizeStatus(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static void requireText(String value, String code) {
        if (value == null || value.isBlank()) throw unprocessable(code, "Required lifecycle input is missing");
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static ModelSpecException conflict(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.CONFLICT);
    }

    private static ModelSpecException unprocessable(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.UNPROCESSABLE);
    }

    private static ModelSpecException notFound(String message) {
        return new ModelSpecException("MODEL_LIFECYCLE_NOT_FOUND", message, ModelSpecException.Kind.NOT_FOUND);
    }
}
