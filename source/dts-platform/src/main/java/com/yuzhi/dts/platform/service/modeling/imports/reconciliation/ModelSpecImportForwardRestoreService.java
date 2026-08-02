package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationRepository.HistoricalImplementation;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationRepository.UndoEligibility;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Canonical atomic boundary for append-only UPDATE forward undo of imported DBT models. */
@Service
public class ModelSpecImportForwardRestoreService {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecRepository modelSpecRepository;
    private final ModelSpecSnapshotCodec modelSpecSnapshots;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecImportReconciliationRepository reconciliation;
    private final ModelImplementationChecksumCodec implementationChecksums;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public ModelSpecImportForwardRestoreService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelSpecSnapshotCodec modelSpecSnapshots,
        ModelLifecycleRepository lifecycle,
        ModelSpecImportReconciliationRepository reconciliation,
        ModelImplementationChecksumCodec implementationChecksums,
        AuditService audit
    ) {
        this(
            modelSpecs,
            modelSpecRepository,
            modelSpecSnapshots,
            lifecycle,
            reconciliation,
            implementationChecksums,
            audit,
            Clock.systemUTC()
        );
    }

    ModelSpecImportForwardRestoreService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelSpecSnapshotCodec modelSpecSnapshots,
        ModelLifecycleRepository lifecycle,
        ModelSpecImportReconciliationRepository reconciliation,
        ModelImplementationChecksumCodec implementationChecksums,
        AuditService audit,
        Clock clock
    ) {
        this.modelSpecs = modelSpecs;
        this.modelSpecRepository = modelSpecRepository;
        this.modelSpecSnapshots = modelSpecSnapshots;
        this.lifecycle = lifecycle;
        this.reconciliation = reconciliation;
        this.implementationChecksums = implementationChecksums;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Restores revision-pinned server-side history only. No historical JSON is accepted from the
     * caller, and any failure after the ModelSpec CAS marks the encompassing transaction rollback.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ForwardRestoreResult restoreImportedDbtRevision(
        String tenantId,
        String actorId,
        ForwardRestoreCommand command
    ) {
        requireCommand(command);
        UndoEligibility eligibility = reconciliation.evaluateUndoEligibility(
            tenantId,
            command.modelSpecId(),
            new RevisionPins(
                command.expectedCurrentModel().revision(),
                command.expectedCurrentModel().checksum(),
                command.expectedCurrentImplementation().revision(),
                command.expectedCurrentImplementation().checksum()
            ),
            command.restorePins()
        );
        if (!eligibility.allowed()) {
            return ForwardRestoreResult.blocked(eligibility.reasonCode());
        }
        ModelSpecView historicalModel = modelSpecs.revision(tenantId, command.restoreModel());
        if (
            historicalModel.status() != ModelStatus.DRAFT ||
            historicalModel.implementationMode() != ImplementationMode.DBT_MANAGED ||
            !Objects.equals(historicalModel.checksum(), command.restorePins().modelChecksum())
        ) {
            throw conflict(
                "MODEL_IMPORT_UNDO_MODEL_HISTORY_MISMATCH",
                "Historical ModelSpec revision does not match the forward-undo checkpoint"
            );
        }

        HistoricalImplementation historicalImplementation = reconciliation
            .findHistoricalImplementation(
                tenantId,
                command.modelSpecId(),
                command.restorePins(),
                command.idempotencyKey() + ":implementation"
            )
            .orElseThrow(() -> conflict(
                "MODEL_IMPORT_UNDO_IMPLEMENTATION_HISTORY_MISSING",
                "Historical implementation revision is unavailable"
            ));
        if (
            !Objects.equals(historicalImplementation.projectKey(), command.expectedProjectKey()) ||
            !Objects.equals(historicalImplementation.dbtUniqueId(), command.expectedDbtUniqueId()) ||
            !Objects.equals(
                implementationChecksums.contentChecksum(historicalImplementation.command()),
                command.restorePins().implementationChecksum()
            )
        ) {
            throw conflict(
                "MODEL_IMPORT_UNDO_IMPLEMENTATION_HISTORY_MISMATCH",
                "Historical implementation does not match the forward-undo checkpoint"
            );
        }

        ModelSpecView restoredModel = modelSpecs.update(
            tenantId,
            actorId,
            command.modelSpecId(),
            command.expectedCurrentModel(),
            update(historicalModel)
        );
        if (restoredModel.revision() == command.expectedCurrentModel().revision()) {
            ModelSpecView appended = modelSpecSnapshots.toUpdatedView(
                restoredModel,
                update(historicalModel),
                restoredModel.revision() + 1,
                clock.instant()
            );
            if (!Objects.equals(appended.checksum(), command.restorePins().modelChecksum())) {
                throw conflict(
                    "MODEL_IMPORT_UNDO_MODEL_RESTORE_MISMATCH",
                    "Restored ModelSpec does not match the forward-undo checkpoint"
                );
            }
            int appendedRevision = modelSpecRepository.appendUnchangedV2RevisionForForwardUndo(
                tenantId,
                actorId,
                restoredModel.revision(),
                restoredModel.checksum(),
                appended,
                modelSpecSnapshots.write(appended)
            );
            if (appendedRevision != 1) {
                throw conflict(
                    "MODEL_IMPORT_UNDO_CURRENT_PINS_CHANGED",
                    "Current ModelSpec changed during forward undo"
                );
            }
            restoredModel = appended;
        } else if (restoredModel.revision() != command.expectedCurrentModel().revision() + 1) {
            throw conflict(
                "MODEL_IMPORT_UNDO_MODEL_RESTORE_MISMATCH",
                "Restored ModelSpec revision is not the expected append-only successor"
            );
        }
        if (
            restoredModel.status() != ModelStatus.DRAFT ||
            restoredModel.implementationMode() != ImplementationMode.DBT_MANAGED ||
            !Objects.equals(restoredModel.checksum(), command.restorePins().modelChecksum())
        ) {
            throw conflict(
                "MODEL_IMPORT_UNDO_MODEL_RESTORE_MISMATCH",
                "Restored ModelSpec does not match the forward-undo checkpoint"
            );
        }

        int changed = lifecycle.restoreImportedDbtImplementation(
            tenantId,
            actorId,
            restoredModel,
            command.expectedProjectKey(),
            command.expectedDbtUniqueId(),
            historicalImplementation.command(),
            command.expectedCurrentModel().revision(),
            command.expectedCurrentModel().checksum(),
            command.expectedCurrentImplementation().revision(),
            command.expectedCurrentImplementation().checksum(),
            command.restorePins().implementationChecksum(),
            clock.instant()
        );
        if (changed != 1) {
            throw conflict(
                "MODEL_IMPORT_UNDO_CURRENT_PINS_CHANGED",
                "Current ModelSpec or implementation changed during forward undo"
            );
        }
        ImplementationView restoredImplementation = lifecycle
            .findImplementation(tenantId, command.modelSpecId())
            .orElseThrow(() -> conflict(
                "MODEL_IMPORT_UNDO_IMPLEMENTATION_RESTORE_MISSING",
                "Restored implementation head is unavailable"
            ));
        if (
            restoredImplementation.revision() != restoredModel.revision() ||
            !Objects.equals(restoredImplementation.modelChecksum(), restoredModel.checksum()) ||
            restoredImplementation.ownership() != ImplementationMode.DBT_MANAGED ||
            !Objects.equals(restoredImplementation.projectKey(), command.expectedProjectKey()) ||
            !Objects.equals(restoredImplementation.dbtUniqueId(), command.expectedDbtUniqueId()) ||
            !Objects.equals(
                restoredImplementation.implementationChecksum(),
                command.restorePins().implementationChecksum()
            )
        ) {
            throw conflict(
                "MODEL_IMPORT_UNDO_IMPLEMENTATION_RESTORE_MISMATCH",
                "Restored implementation head does not match the forward-undo checkpoint"
            );
        }
        auditRestoreStrict(tenantId, actorId, command, restoredModel, restoredImplementation);
        return ForwardRestoreResult.restored(restoredModel, restoredImplementation);
    }

    private void auditRestoreStrict(
        String tenantId,
        String actorId,
        ForwardRestoreCommand command,
        ModelSpecView restoredModel,
        ImplementationView restoredImplementation
    ) {
        String eventIdentity = command.idempotencyKey() + ":" + command.modelSpecId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventIdentity", eventIdentity);
        payload.put("actor", actorId);
        payload.put("tenantId", tenantId);
        payload.put("planId", restoredModel.planId());
        payload.put("modelSpecId", restoredModel.id());
        payload.put("restoredFromModelRevision", command.restorePins().modelRevision());
        payload.put("modelRevision", restoredModel.revision());
        payload.put("restoredFromImplementationRevision", command.restorePins().implementationRevision());
        payload.put("implementationRevision", restoredImplementation.implementationRevision());
        payload.put("ownership", restoredImplementation.ownership().name());
        payload.put("outcome", "RESTORED");
        audit.auditActionStrict(
            "MODELING_DBT_IMPORT_FORWARD_UNDO_ITEM",
            AuditStage.SUCCESS,
            eventIdentity,
            payload
        );
    }

    private static void requireCommand(ForwardRestoreCommand command) {
        if (
            command == null ||
            command.modelSpecId() == null ||
            command.expectedCurrentModel() == null ||
            !command.modelSpecId().equals(command.expectedCurrentModel().modelSpecId()) ||
            command.expectedCurrentImplementation() == null ||
            !command.modelSpecId().equals(command.expectedCurrentImplementation().modelSpecId()) ||
            command.restorePins() == null ||
            command.expectedProjectKey() == null ||
            command.expectedProjectKey().isBlank() ||
            command.expectedDbtUniqueId() == null ||
            command.expectedDbtUniqueId().isBlank() ||
            command.idempotencyKey() == null ||
            command.idempotencyKey().isBlank() ||
            command.idempotencyKey().length() > ModelSpecImportReconciliationContract.MAX_IDEMPOTENCY_KEY_LENGTH
        ) {
            throw new ModelSpecException(
                "MODEL_IMPORT_UNDO_COMMAND_INVALID",
                "Forward-undo command is invalid",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
    }

    private static UpdateModelSpecCommand update(ModelSpecView view) {
        return new UpdateModelSpecCommand(
            view.planId(),
            view.domainId(),
            view.modelType(),
            view.layer(),
            view.name(),
            view.description(),
            view.implementationMode(),
            view.materialization(),
            view.businessActivityRef(),
            view.consumptionScenario(),
            view.grain(),
            view.factShape(),
            view.timeSemantics(),
            view.fields(),
            view.sourceRefs(),
            view.dependsOn(),
            view.dimensionRefs(),
            view.metricRefs(),
            view.standardBindings(),
            view.generationStrategy(),
            view.dimensionProfile(),
            view.dataMartId(),
            view.variantCode(),
            view.implementationPolicy()
        );
    }

    private static ModelSpecException conflict(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.CONFLICT);
    }

    public record ForwardRestoreCommand(
        UUID modelSpecId,
        ExpectedVersion expectedCurrentModel,
        ExpectedImplementationVersion expectedCurrentImplementation,
        RevisionPins restorePins,
        String expectedProjectKey,
        String expectedDbtUniqueId,
        String idempotencyKey
    ) {
        ModelRevisionRef restoreModel() {
            return new ModelRevisionRef(modelSpecId, restorePins.modelRevision());
        }
    }

    public record ForwardRestoreResult(
        ModelSpecView modelSpec,
        ImplementationView implementation,
        String blockedReasonCode
    ) {
        static ForwardRestoreResult restored(ModelSpecView modelSpec, ImplementationView implementation) {
            return new ForwardRestoreResult(modelSpec, implementation, null);
        }

        static ForwardRestoreResult blocked(String reasonCode) {
            return new ForwardRestoreResult(null, null, reasonCode);
        }

        public boolean blocked() {
            return blockedReasonCode != null;
        }
    }
}
