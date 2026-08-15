package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleCommandReceiptRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleCommandReceiptRepository.Receipt;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ClaimImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.CompileView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TestEvidenceCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
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

    private static final String COMMAND_SAVE = "SAVE";
    private static final String COMMAND_IMPORT = "IMPORT";
    private static final String COMMAND_OWNERSHIP_CONVERT = "OWNERSHIP_CONVERT";
    private static final String COMMAND_CLAIM = "CLAIM";

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecRepository modelSpecRepository;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecStageGateService stageGates;
    private final ModelSpecPlanWriteAccessPort writeAccess;
    private final ModelLifecycleCompilerPort compiler;
    private final ModelLifecycleTestEvidencePort testEvidence;
    private final ModelImplementationInputPolicy inputPolicy;
    private final ModelLifecycleCommandReceiptRepository commandReceipts;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public ModelLifecycleService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycle,
        ModelSpecStageGateService stageGates,
        ModelSpecPlanWriteAccessPort writeAccess,
        ModelLifecycleCompilerPort compiler,
        ModelLifecycleTestEvidencePort testEvidence,
        ModelImplementationInputPolicy inputPolicy,
        ModelLifecycleCommandReceiptRepository commandReceipts,
        AuditService auditService
    ) {
        this(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            stageGates,
            writeAccess,
            compiler,
            testEvidence,
            inputPolicy,
            commandReceipts,
            auditService,
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
        ModelLifecycleTestEvidencePort testEvidence,
        Clock clock
    ) {
        this(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            stageGates,
            writeAccess,
            compiler,
            testEvidence,
            new ModelImplementationInputPolicy(modelSpecs, modelSpecRepository, lifecycle, null),
            null,
            null,
            clock
        );
    }

    ModelLifecycleService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycle,
        ModelSpecStageGateService stageGates,
        ModelSpecPlanWriteAccessPort writeAccess,
        ModelLifecycleCompilerPort compiler,
        ModelLifecycleTestEvidencePort testEvidence,
        ModelImplementationInputPolicy inputPolicy,
        Clock clock
    ) {
        this(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            stageGates,
            writeAccess,
            compiler,
            testEvidence,
            inputPolicy,
            null,
            null,
            clock
        );
    }

    ModelLifecycleService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycle,
        ModelSpecStageGateService stageGates,
        ModelSpecPlanWriteAccessPort writeAccess,
        ModelLifecycleCompilerPort compiler,
        ModelLifecycleTestEvidencePort testEvidence,
        ModelImplementationInputPolicy inputPolicy,
        ModelLifecycleCommandReceiptRepository commandReceipts,
        AuditService auditService,
        Clock clock
    ) {
        this.modelSpecs = modelSpecs;
        this.modelSpecRepository = modelSpecRepository;
        this.lifecycle = lifecycle;
        this.stageGates = stageGates;
        this.writeAccess = writeAccess;
        this.compiler = compiler;
        this.testEvidence = testEvidence;
        this.inputPolicy = inputPolicy;
        this.commandReceipts = commandReceipts;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public ImplementationValidationView validateImplementation(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        SaveImplementationCommand command
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        SaveImplementationCommand pinned = inputPolicy.pinCurrentUpstreamImplementations(tenantId, command);
        ModelImplementationInputPolicy.ValidationResult inputValidation =
            inputPolicy.validate(tenantId, model, pinned);
        if (!inputValidation.valid()) return validation(inputValidation);
        return validation(
            ModelImplementationExecutionPlanner.plan(
                model,
                pinned,
                ModelImplementationExecutionPlanner.systemManagedDbtUniqueId(model)
            )
        );
    }

    @Transactional
    public ImplementationView saveImplementation(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion expectedImplementation,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {
        ModelSpecView authorizedModel = authorizeImplementationCommand(tenantId, actorId, modelSpecId);
        String idempotencyKey = implementationIdempotencyKey(command);
        CommandAttempt commandAttempt = beginImplementationCommand(
            tenantId,
            actorId,
            modelSpecId,
            COMMAND_SAVE,
            idempotencyKey,
            implementationCommandPayload(COMMAND_SAVE, expected, null, expectedImplementation, null, null, command)
        );
        if (commandAttempt.replay() != null) return commandAttempt.replay();
        ModelSpecView model = requireExpectedAuthorizedModel(modelSpecId, expected, authorizedModel);
        SaveImplementationCommand pinned = inputPolicy.pinCurrentUpstreamImplementations(tenantId, command);
        String systemProjectKey = ModelImplementationExecutionPlanner.systemManagedDbtProjectKey(model);
        String systemDbtUniqueId = ModelImplementationExecutionPlanner.systemManagedDbtUniqueId(model);
        validateForWrite(tenantId, model, pinned, systemDbtUniqueId);
        ImplementationView current = lifecycle.findImplementation(tenantId, modelSpecId).orElse(null);
        requireImplementationPrecondition(modelSpecId, current, expectedImplementation);
        if (current != null && current.ownership() == ImplementationMode.DBT_MANAGED) {
            throw conflict("MODEL_IMPLEMENTATION_DBT_MANAGED", "DBT-managed implementations require explicit conversion before normal editing");
        }
        if (pinned.ownership() != model.implementationMode()) {
            throw conflict("MODEL_IMPLEMENTATION_OWNERSHIP_MISMATCH", "Implementation ownership must match the canonical ModelSpec");
        }
        if (
            lifecycle.saveImplementation(
                tenantId, actorId, model, systemProjectKey, systemDbtUniqueId, pinned,
                expectedImplementation.revision(), expectedImplementation.checksum(), clock.instant()
            ) == 0
        ) {
            throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation revision changed; refresh before saving");
        }
        ImplementationView saved = lifecycle.findImplementation(tenantId, modelSpecId).orElseThrow();
        auditImplementation("MODEL_IMPLEMENTATION_SAVE", tenantId, actorId, model, saved);
        completeImplementationCommand(tenantId, modelSpecId, COMMAND_SAVE, idempotencyKey, commandAttempt.payloadHash(), saved, actorId);
        return saved;
    }

    /**
     * F3 canonical-import writer for one DBT-backed implementation.
     *
     * <p>This boundary deliberately bypasses the normal visual-input compatibility adapter, which
     * continues to reject DBT generator inputs. In exchange it accepts only the converter-owned,
     * single-input DBT payload and requires both ModelSpec and implementation CAS pins.
     */
    @Transactional
    public ImplementationView saveImportedDbtImplementation(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expectedModel,
        ModelStatus expectedModelStatus,
        ExpectedImplementationVersion expectedImplementation,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {
        ModelSpecView authorizedModel = authorizeImplementationCommand(tenantId, actorId, modelSpecId);
        String idempotencyKey = implementationIdempotencyKey(command);
        CommandAttempt commandAttempt = beginImplementationCommand(
            tenantId,
            actorId,
            modelSpecId,
            COMMAND_IMPORT,
            idempotencyKey,
            implementationCommandPayload(
                COMMAND_IMPORT,
                expectedModel,
                expectedModelStatus == null ? null : expectedModelStatus.name(),
                expectedImplementation,
                projectKey,
                dbtUniqueId,
                command
            )
        );
        if (commandAttempt.replay() != null) return commandAttempt.replay();
        ModelSpecView model = requireExpectedAuthorizedModel(modelSpecId, expectedModel, authorizedModel);
        if (expectedModelStatus == null || model.status() != expectedModelStatus) {
            throw conflict("MODEL_SPEC_STATUS_CONFLICT", "ModelSpec status changed after import preview");
        }
        requireImportedDbtCommand(model, projectKey, dbtUniqueId, command);
        ImplementationView current = lifecycle.findImplementation(tenantId, modelSpecId).orElse(null);
        requireImplementationPrecondition(modelSpecId, current, expectedImplementation);
        ImplementationView saved;
        try {
            saved = lifecycle
                .saveImportedDbtImplementation(
                    tenantId,
                    actorId,
                    model,
                    expectedModelStatus,
                    projectKey.trim(),
                    dbtUniqueId.trim(),
                    command,
                    expectedImplementation.revision(),
                    expectedImplementation.checksum(),
                    clock.instant()
                )
                .orElse(null);
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("MODEL_IMPLEMENTATION_DBT_CONFLICT", "The dbt node is already owned by another ModelSpec");
        } catch (IllegalArgumentException invalid) {
            throw unprocessable("MODEL_IMPORT_IMPLEMENTATION_INVALID", "Imported DBT implementation payload is invalid");
        }
        if (saved != null) {
            auditImplementation("MODEL_IMPLEMENTATION_IMPORT", tenantId, actorId, model, saved);
            completeImplementationCommand(
                tenantId,
                modelSpecId,
                COMMAND_IMPORT,
                idempotencyKey,
                commandAttempt.payloadHash(),
                saved,
                actorId
            );
            return saved;
        }

        ModelSpecView refreshed = modelSpecs.get(tenantId, modelSpecId);
        if (
            refreshed.revision() != expectedModel.revision() ||
            !Objects.equals(refreshed.checksum(), expectedModel.checksum()) ||
            refreshed.status() != expectedModelStatus
        ) {
            throw conflict("MODEL_SPEC_REVISION_CONFLICT", "ModelSpec changed while the imported implementation was being saved");
        }
        throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation revision changed; refresh before importing");
    }

    @Transactional
    public ImplementationView convertToDesignerGenerated(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion expectedImplementation,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {
        ModelSpecView authorizedModel = authorizeImplementationCommand(tenantId, actorId, modelSpecId);
        String idempotencyKey = implementationIdempotencyKey(command);
        CommandAttempt commandAttempt = beginImplementationCommand(
            tenantId,
            actorId,
            modelSpecId,
            COMMAND_OWNERSHIP_CONVERT,
            idempotencyKey,
            implementationCommandPayload(
                COMMAND_OWNERSHIP_CONVERT,
                expected,
                null,
                expectedImplementation,
                null,
                null,
                command
            )
        );
        if (commandAttempt.replay() != null) return commandAttempt.replay();
        ModelSpecView model = requireExpectedAuthorizedModel(modelSpecId, expected, authorizedModel);
        SaveImplementationCommand pinned = inputPolicy.pinCurrentUpstreamImplementations(tenantId, command);
        String systemProjectKey = ModelImplementationExecutionPlanner.systemManagedDbtProjectKey(model);
        String systemDbtUniqueId = ModelImplementationExecutionPlanner.systemManagedDbtUniqueId(model);
        validateForWrite(tenantId, model, pinned, systemDbtUniqueId);
        if (model.implementationMode() != ImplementationMode.DESIGNER_GENERATED || pinned.ownership() != ImplementationMode.DESIGNER_GENERATED) {
            throw conflict("MODEL_IMPLEMENTATION_NORMAL_MODE_REQUIRED", "Convert the canonical ModelSpec to designer-generated mode before replacing DBT ownership");
        }
        ImplementationView current = lifecycle.findImplementation(tenantId, modelSpecId).orElse(null);
        requireImplementationPrecondition(modelSpecId, current, expectedImplementation);
        if (
            lifecycle.convertImplementationOwnership(
                tenantId, actorId, model, systemProjectKey, systemDbtUniqueId, pinned,
                expectedImplementation.revision(), expectedImplementation.checksum(), clock.instant()
            ) == 0
        ) {
            throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation revision changed or is no longer DBT-managed");
        }
        ImplementationView saved = lifecycle.findImplementation(tenantId, modelSpecId).orElseThrow();
        auditImplementation("MODEL_IMPLEMENTATION_OWNERSHIP_CONVERT", tenantId, actorId, model, saved);
        completeImplementationCommand(
            tenantId,
            modelSpecId,
            COMMAND_OWNERSHIP_CONVERT,
            idempotencyKey,
            commandAttempt.payloadHash(),
            saved,
            actorId
        );
        return saved;
    }

    @Transactional
    public ImplementationView claim(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion expectedImplementation,
        ClaimImplementationCommand command
    ) {
        ModelSpecView authorizedModel = authorizeImplementationCommand(tenantId, actorId, modelSpecId);
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_IMPLEMENTATION_IDEMPOTENCY_REQUIRED");
        String idempotencyKey = command.idempotencyKey().trim();
        CommandAttempt commandAttempt = beginImplementationCommand(
            tenantId,
            actorId,
            modelSpecId,
            COMMAND_CLAIM,
            idempotencyKey,
            claimCommandPayload(expected, expectedImplementation, command)
        );
        if (commandAttempt.replay() != null) return commandAttempt.replay();
        ModelSpecView model = requireExpectedAuthorizedModel(modelSpecId, expected, authorizedModel);
        requireText(command == null ? null : command.projectKey(), "MODEL_IMPLEMENTATION_PROJECT_REQUIRED");
        requireText(command == null ? null : command.dbtUniqueId(), "MODEL_IMPLEMENTATION_DBT_ID_REQUIRED");
        if (command == null || command.ownership() == null || command.ownership() != model.implementationMode()) {
            throw conflict("MODEL_IMPLEMENTATION_OWNERSHIP_MISMATCH", "Implementation ownership must match the canonical ModelSpec");
        }
        ImplementationView current = lifecycle.findImplementation(tenantId, modelSpecId).orElse(null);
        requireImplementationPrecondition(modelSpecId, current, expectedImplementation);
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
                expectedImplementation.revision(),
                expectedImplementation.checksum(),
                clock.instant()
            );
            if (changed == 0) {
                throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation revision changed; refresh before claiming");
            }
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("MODEL_IMPLEMENTATION_DBT_CONFLICT", "The dbt node is already owned by another ModelSpec");
        }
        ImplementationView saved = lifecycle.findImplementation(tenantId, modelSpecId).orElseThrow();
        auditImplementation("MODEL_IMPLEMENTATION_CLAIM", tenantId, actorId, model, saved);
        completeImplementationCommand(tenantId, modelSpecId, COMMAND_CLAIM, idempotencyKey, commandAttempt.payloadHash(), saved, actorId);
        return saved;
    }

    @Transactional
    public CompileView compile(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion expectedImplementation,
        String idempotencyKey
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        requireText(idempotencyKey, "MODEL_COMPILE_IDEMPOTENCY_REQUIRED");
        ImplementationView owner = requireCurrentOwner(tenantId, model);
        requireImplementationPrecondition(modelSpecId, owner, expectedImplementation);
        lockCurrentImplementation(tenantId, modelSpecId, owner);
        requireGate(tenantId, modelSpecId, Stage.IMPLEMENTATION_READY);
        LifecycleEventView replay = lifecycle.findEvent(tenantId, modelSpecId, EventType.COMPILE, idempotencyKey.trim()).orElse(null);
        if (replay != null) {
            requireEventImplementation(replay, owner);
            return new CompileView(owner, replay, lifecycle.listArtifacts(tenantId, modelSpecId, model.revision()));
        }
        List<ArtifactWrite> artifacts;
        int artifactCount;
        if (owner.ownership() == ImplementationMode.DBT_MANAGED) {
            java.util.Set<String> importedTypes = lifecycle.currentArtifactTypes(tenantId, modelSpecId, owner);
            java.util.Set<String> requiredTypes = java.util.Set.of("SQL", "SCHEMA");
            java.util.Set<String> supportedTypes = java.util.Set.of("SQL", "SCHEMA", "CONFIG", "DEPENDENCY");
            if (!importedTypes.containsAll(requiredTypes) || !supportedTypes.containsAll(importedTypes)) {
                throw conflict(
                    "MODEL_DBT_IMPORT_REQUIRED",
                    "Current DBT-managed SQL and schema artifacts must be imported before compilation can pass"
                );
            }
            artifacts = List.of();
            artifactCount = importedTypes.size();
            lifecycle.promoteImportedArtifactsToCompiled(tenantId, modelSpecId, owner, clock.instant());
        } else {
            artifacts = compiler.compile(tenantId, model, owner);
            lifecycle.saveArtifacts(tenantId, model, owner, idempotencyKey.trim(), artifacts, clock.instant());
            artifactCount = artifacts.size();
        }
        LifecycleEventView event = lifecycle.recordEvent(
            tenantId,
            actorId,
            model,
            EventType.COMPILE,
            "PASSED",
            idempotencyKey.trim(),
            null,
            owner.dbtUniqueId(),
            Map.of("artifactCount", artifactCount, "ownership", owner.ownership().name()),
            owner,
            clock.instant()
        );
        auditLifecycleEvent(
            "MODEL_IMPLEMENTATION_COMPILE",
            tenantId,
            actorId,
            model,
            owner,
            event,
            artifactCount
        );
        return new CompileView(owner, event, lifecycle.listArtifacts(tenantId, modelSpecId, model.revision()));
    }

    @Transactional
    public LifecycleEventView recordTest(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion expectedImplementation,
        TestEvidenceCommand command
    ) {
        ModelSpecView model = writableCurrent(tenantId, actorId, modelSpecId, expected);
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_TEST_IDEMPOTENCY_REQUIRED");
        requireText(command.externalRunId(), "MODEL_TEST_EXTERNAL_RUN_REQUIRED");
        ImplementationView owner = requireCurrentOwner(tenantId, model);
        requireImplementationPrecondition(modelSpecId, owner, expectedImplementation);
        lockCurrentImplementation(tenantId, modelSpecId, owner);
        LifecycleEventView replay = lifecycle
            .findEvent(tenantId, modelSpecId, EventType.TEST, command.idempotencyKey().trim())
            .orElse(null);
        if (replay != null) {
            requireEventImplementation(replay, owner);
            return replay;
        }
        List<ArtifactView> artifacts = lifecycle.listArtifacts(tenantId, modelSpecId, model.revision());
        if (
            !lifecycle.hasPassedEvidence(
                tenantId,
                modelSpecId,
                model.revision(),
                owner.implementationRevision(),
                owner.implementationChecksum(),
                EventType.COMPILE
            ) ||
            !hasArtifact(artifacts, owner, "SQL") ||
            !hasArtifact(artifacts, owner, "SCHEMA") ||
            (owner.ownership() != ImplementationMode.DBT_MANAGED && !hasArtifact(artifacts, owner, "TEST"))
        ) {
            throw conflict("MODEL_TEST_COMPILE_REQUIRED", "Current revision must be compiled before test evidence can be recorded");
        }
        ModelLifecycleTestEvidencePort.TestEvidence verified = testEvidence.verify(
            new ModelLifecycleTestEvidencePort.VerificationRequest(
                command.externalRunId(),
                tenantId,
                modelSpecId,
                owner.implementationRevision(),
                owner.implementationChecksum(),
                owner.projectKey(),
                owner.dbtUniqueId()
            )
        );
        String requested = normalizeStatus(command.status());
        if (requested != null && !requested.equals(verified.status())) {
            throw conflict("MODEL_TEST_STATUS_MISMATCH", "Client status does not match the persisted dbt run");
        }
        LifecycleEventView event = lifecycle.recordEvent(
            tenantId,
            actorId,
            model,
            EventType.TEST,
            verified.status(),
            command.idempotencyKey().trim(),
            command.comment() == null ? verified.message() : command.comment(),
            verified.externalRunId(),
            Map.of("artifactRevision", model.revision()),
            owner,
            clock.instant()
        );
        auditLifecycleEvent(
            "MODEL_IMPLEMENTATION_TEST_EVIDENCE_RECORD",
            tenantId,
            actorId,
            model,
            owner,
            event,
            artifacts.size()
        );
        return event;
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

    private ModelSpecView authorizeImplementationCommand(String tenantId, String actorId, UUID modelSpecId) {
        ModelSpecView model = modelSpecs.get(tenantId, modelSpecId);
        requireWrite(tenantId, actorId, model);
        return model;
    }

    private ModelSpecView requireExpectedAuthorizedModel(
        UUID modelSpecId,
        ExpectedVersion expected,
        ModelSpecView authorizedModel
    ) {
        if (expected == null || !modelSpecId.equals(expected.modelSpecId())) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_INVALID",
                "A strong ModelSpec revision precondition is required",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        if (
            authorizedModel.revision() != expected.revision() ||
            !Objects.equals(authorizedModel.checksum(), expected.checksum())
        ) {
            throw conflict("MODEL_SPEC_REVISION_CONFLICT", "ModelSpec revision changed; refresh before continuing");
        }
        return authorizedModel;
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

    private void lockCurrentImplementation(String tenantId, UUID modelSpecId, ImplementationView owner) {
        if (!lifecycle.lockImplementation(tenantId, modelSpecId, owner)) {
            throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation revision changed; refresh before continuing");
        }
    }

    private static void requireEventImplementation(LifecycleEventView event, ImplementationView owner) {
        if (!ModelLifecycleRepository.matchesImplementation(event, owner)) {
            throw conflict("MODEL_LIFECYCLE_IDEMPOTENCY_CONFLICT", "The idempotency key belongs to another implementation revision");
        }
    }

    private static void requireImplementationPrecondition(
        UUID modelSpecId,
        ImplementationView current,
        ExpectedImplementationVersion expected
    ) {
        if (expected == null || !modelSpecId.equals(expected.modelSpecId())) {
            throw new ModelSpecException(
                "MODEL_IMPLEMENTATION_IF_MATCH_REQUIRED",
                "A strong implementation If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        if (current == null) {
            if (expected.revision() == 0 && expected.checksum() == null) return;
            throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation head does not exist; create only with If-Match-Implementation: *");
        }
        if (
            expected.revision() != current.implementationRevision() ||
            !Objects.equals(expected.checksum(), current.implementationChecksum())
        ) {
            throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation revision changed; refresh before saving");
        }
    }

    private static boolean hasArtifact(List<ArtifactView> artifacts, ImplementationView implementation, String type) {
        return artifacts.stream().anyMatch(item ->
            type.equals(item.artifactType()) && "COMPILED".equals(item.status()) &&
            item.implementationRevision() == implementation.implementationRevision() &&
            Objects.equals(item.modelChecksum(), implementation.modelChecksum())
        );
    }

    private static String normalizeStatus(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private void validateForWrite(
        String tenantId,
        ModelSpecView model,
        SaveImplementationCommand command,
        String dbtUniqueId
    ) {
        if (command == null) throw unprocessable("MODEL_IMPLEMENTATION_INPUT_REQUIRED", "Implementation input is required");
        ModelImplementationInputPolicy.ValidationResult result = inputPolicy.validate(tenantId, model, command);
        if (!result.valid()) throw unprocessable(result.code(), "Implementation input does not satisfy the current ModelSpec");
        ModelImplementationExecutionPlanner.ValidationResult execution =
            ModelImplementationExecutionPlanner.plan(model, command, dbtUniqueId);
        if (!execution.valid()) {
            throw unprocessable(execution.code(), execution.blockers().getFirst().message());
        }
    }

    private static void requireImportedDbtCommand(
        ModelSpecView model,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {
        boolean executionSettingsValid = command != null && (
            command.settings().isEmpty() ||
            ModelImplementationExecutionPlanner.plan(model, command, dbtUniqueId).valid()
        );
        if (
            model.implementationMode() != ImplementationMode.DBT_MANAGED ||
            projectKey == null ||
            projectKey.isBlank() ||
            dbtUniqueId == null ||
            dbtUniqueId.isBlank() ||
            command == null ||
            command.ownership() != ImplementationMode.DBT_MANAGED ||
            command.inputMode() != ModelLifecycleContract.InputMode.GENERATED ||
            command.inputs().size() != 1 ||
            !(command.inputs().get(0) instanceof ModelLifecycleContract.GeneratedInput input) ||
            !"DBT".equals(input.generatorType()) ||
            !command.fieldMappings().isEmpty() ||
            !executionSettingsValid ||
            !Objects.equals(input.config().get("projectKey"), projectKey.trim()) ||
            !Objects.equals(input.config().get("dbtUniqueId"), dbtUniqueId.trim()) ||
            (
                model.materialization() != null &&
                !model.materialization().isBlank() &&
                !model.materialization().equalsIgnoreCase(command.materialization())
            )
        ) {
            throw unprocessable(
                "MODEL_IMPORT_IMPLEMENTATION_INVALID",
                "Imported DBT implementation must use the converter-owned generated input"
            );
        }
    }

    private static ImplementationValidationView validation(ModelImplementationInputPolicy.ValidationResult result) {
        if (result.valid()) {
            return new ImplementationValidationView(true, "MODEL_IMPLEMENTATION_VALID", List.of(), null);
        }
        return new ImplementationValidationView(
            false,
            result.code(),
            List.of(
                new ModelImplementationExecutionPlanner.Blocker(
                    result.code(),
                    "inputs",
                    "Implementation input does not satisfy the current ModelSpec",
                    "RESELECT_IMPLEMENTATION_INPUT"
                )
            ),
            null
        );
    }

    private static ImplementationValidationView validation(
        ModelImplementationExecutionPlanner.ValidationResult result
    ) {
        return new ImplementationValidationView(
            result.valid(),
            result.code(),
            result.blockers(),
            result.executionPlan()
        );
    }

    private String implementationIdempotencyKey(SaveImplementationCommand command) {
        requireText(command == null ? null : command.idempotencyKey(), "MODEL_IMPLEMENTATION_IDEMPOTENCY_REQUIRED");
        return command.idempotencyKey().trim();
    }

    private CommandAttempt beginImplementationCommand(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        String action,
        String idempotencyKey,
        Object payload
    ) {
        if (commandReceipts == null) {
            throw new IllegalStateException("Model lifecycle command receipt repository is required");
        }
        String payloadHash = commandReceipts.payloadHash(payload);
        commandReceipts.lockCommandKey(tenantId, modelSpecId, action, idempotencyKey);
        Receipt existing = commandReceipts.find(tenantId, modelSpecId, action, idempotencyKey).orElse(null);
        if (existing == null) return new CommandAttempt(payloadHash, null);
        requireReceiptReplayWrite(tenantId, actorId, existing.result());
        if (!Objects.equals(existing.payloadHash(), payloadHash)) {
            throw conflict(
                "MODEL_IMPLEMENTATION_IDEMPOTENCY_CONFLICT",
                "The implementation idempotency key belongs to a different payload"
            );
        }
        return new CommandAttempt(payloadHash, existing.result());
    }

    private void requireReceiptReplayWrite(String tenantId, String actorId, ImplementationView result) {
        if (actorId == null || actorId.isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_ACTOR_REQUIRED",
                "Authenticated actor is required",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
        PlanState plan = modelSpecRepository
            .lockPlan(tenantId, result.planId())
            .orElseThrow(() -> notFound("Warehouse plan was not found"));
        if ("ARCHIVED".equals(plan.lifecycleStatus()) || !writeAccess.canMaintain(tenantId, result.planId(), actorId)) {
            throw new ModelSpecException(
                "MODEL_SPEC_PLAN_FORBIDDEN",
                "Warehouse plan is not available for lifecycle maintenance",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private void completeImplementationCommand(
        String tenantId,
        UUID modelSpecId,
        String action,
        String idempotencyKey,
        String payloadHash,
        ImplementationView result,
        String actorId
    ) {
        commandReceipts.append(
            tenantId,
            modelSpecId,
            action,
            idempotencyKey,
            payloadHash,
            result,
            actorId,
            clock.instant()
        );
    }

    private static Map<String, Object> implementationCommandPayload(
        String action,
        ExpectedVersion expectedModel,
        String expectedModelStatus,
        ExpectedImplementationVersion expectedImplementation,
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {
        LinkedHashMap<String, Object> payload = baseCommandPayload(
            action,
            expectedModel,
            expectedModelStatus,
            expectedImplementation
        );
        payload.put("projectKey", normalized(projectKey));
        payload.put("dbtUniqueId", normalized(dbtUniqueId));
        payload.put("inputMode", command == null ? null : command.inputMode());
        payload.put("inputs", command == null ? null : command.inputs());
        payload.put("fieldMappings", command == null ? null : command.fieldMappings());
        payload.put("settings", command == null ? null : command.settings());
        payload.put("ownership", command == null ? null : command.ownership());
        payload.put("materialization", command == null ? null : command.materialization());
        return payload;
    }

    private static Map<String, Object> claimCommandPayload(
        ExpectedVersion expectedModel,
        ExpectedImplementationVersion expectedImplementation,
        ClaimImplementationCommand command
    ) {
        LinkedHashMap<String, Object> payload = baseCommandPayload(
            COMMAND_CLAIM,
            expectedModel,
            null,
            expectedImplementation
        );
        payload.put("ownership", command == null ? null : command.ownership());
        payload.put("projectKey", command == null ? null : normalized(command.projectKey()));
        payload.put("dbtUniqueId", command == null ? null : normalized(command.dbtUniqueId()));
        return payload;
    }

    private static LinkedHashMap<String, Object> baseCommandPayload(
        String action,
        ExpectedVersion expectedModel,
        String expectedModelStatus,
        ExpectedImplementationVersion expectedImplementation
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("expectedModelId", expectedModel == null ? null : expectedModel.modelSpecId());
        payload.put("expectedModelRevision", expectedModel == null ? null : expectedModel.revision());
        payload.put("expectedModelChecksum", expectedModel == null ? null : expectedModel.checksum());
        payload.put("expectedModelStatus", expectedModelStatus);
        payload.put(
            "expectedImplementationId",
            expectedImplementation == null ? null : expectedImplementation.modelSpecId()
        );
        payload.put(
            "expectedImplementationRevision",
            expectedImplementation == null ? null : expectedImplementation.revision()
        );
        payload.put(
            "expectedImplementationChecksum",
            expectedImplementation == null ? null : expectedImplementation.checksum()
        );
        return payload;
    }

    private static String normalized(String value) {
        return value == null ? null : value.trim();
    }

    private void auditImplementation(
        String actionCode,
        String tenantId,
        String actorId,
        ModelSpecView model,
        ImplementationView implementation
    ) {
        if (auditService == null) return;
        String eventIdentity = actionCode + ":" + model.id() + ":" + implementation.implementationRevision();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventIdentity", eventIdentity);
        payload.put("actor", actorId);
        payload.put("tenantId", tenantId);
        payload.put("planId", model.planId());
        payload.put("modelSpecId", model.id());
        payload.put("modelRevision", model.revision());
        payload.put("implementationId", implementation.id());
        payload.put("implementationRevision", implementation.implementationRevision());
        payload.put("inputMode", implementation.inputMode().name());
        payload.put("ownership", implementation.ownership().name());
        payload.put("materialization", implementation.materialization());
        payload.put("outcome", implementation.status());
        payload.put("inputCount", implementation.inputs().size());
        payload.put("fieldMappingCount", implementation.fieldMappings().size());
        payload.put("settingCount", implementation.settings().size());
        auditService.auditActionStrict(actionCode, AuditStage.SUCCESS, eventIdentity, payload);
    }

    private void auditLifecycleEvent(
        String actionCode,
        String tenantId,
        String actorId,
        ModelSpecView model,
        ImplementationView implementation,
        LifecycleEventView event,
        int artifactCount
    ) {
        if (auditService == null) return;
        String eventIdentity = actionCode + ":" + event.id();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventIdentity", eventIdentity);
        payload.put("actor", actorId);
        payload.put("tenantId", tenantId);
        payload.put("planId", model.planId());
        payload.put("modelSpecId", model.id());
        payload.put("modelRevision", model.revision());
        payload.put("implementationId", implementation.id());
        payload.put("implementationRevision", implementation.implementationRevision());
        payload.put("inputMode", implementation.inputMode().name());
        payload.put("ownership", implementation.ownership().name());
        payload.put("materialization", implementation.materialization());
        payload.put("lifecycleEventId", event.id());
        payload.put("outcome", event.status());
        payload.put("artifactCount", artifactCount);
        auditService.auditAction(actionCode, AuditStage.SUCCESS, eventIdentity, payload);
    }

    private static void requireText(String value, String code) {
        if (value == null || value.isBlank()) throw unprocessable(code, "Required lifecycle input is missing");
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

    private record CommandAttempt(String payloadHash, ImplementationView replay) {}

    public record ImplementationValidationView(
        boolean valid,
        String code,
        List<ModelImplementationExecutionPlanner.Blocker> blockers,
        ModelImplementationExecutionPlanner.ExecutionPlan executionPlan
    ) {
        public ImplementationValidationView(boolean valid, String code) {
            this(valid, code, List.of(), null);
        }

        public ImplementationValidationView {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    /** Explicit CAS token for implementation-input writers; revision zero represents a missing head. */
    public record ExpectedImplementationVersion(UUID modelSpecId, int revision, String checksum) {
        public ExpectedImplementationVersion {
            if (modelSpecId == null || revision < 0 || (revision == 0 && checksum != null) || (revision > 0 && (checksum == null || !checksum.matches("^[0-9a-f]{64}$")))) {
                throw new IllegalArgumentException("Invalid implementation precondition");
            }
        }
    }
}
