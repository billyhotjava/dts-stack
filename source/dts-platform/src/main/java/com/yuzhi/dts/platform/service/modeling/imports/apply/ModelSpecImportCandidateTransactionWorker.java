package com.yuzhi.dts.platform.service.modeling.imports.apply;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportCommand;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyCommandCodec.DecodedCandidate;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.MergeCheckpoint;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Applies one candidate and its successful control-plane result in one independent transaction. */
@Service
public class ModelSpecImportCandidateTransactionWorker {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelLifecycleService lifecycle;
    private final ModelingDbtArtifactImportService artifactImports;
    private final ModelSpecImportApplyCommandCodec commandCodec;
    private final ModelSpecImportApplyRepository applyRepository;

    public ModelSpecImportCandidateTransactionWorker(
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        ModelingDbtArtifactImportService artifactImports,
        ModelSpecImportApplyCommandCodec commandCodec,
        ModelSpecImportApplyRepository applyRepository
    ) {
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.artifactImports = artifactImports;
        this.commandCodec = commandCodec;
        this.applyRepository = applyRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateResult execute(Execution command) {
        Candidate candidate = command.candidate();
        DecodedCandidate decoded = commandCodec.decode(
            candidate,
            command.applyPayloadJson(),
            command.ownedTechnicalNodeIds()
        );
        ResultStatus resultStatus;
        ModelSpecView model;
        if ("SKIP".equals(candidate.action()) || "CANCEL".equals(candidate.action())) {
            model = modelSpecs.get(command.tenantId(), candidate.targetModelSpecId());
            resultStatus = ResultStatus.SKIPPED;
        } else if ("CREATE".equals(candidate.action())) {
            ModelSpecApplicationService.CreateResult created = modelSpecs.createImported(
                command.tenantId(),
                command.actorId(),
                candidate.targetModelSpecId(),
                decoded.createCommand()
            );
            model = created.modelSpec();
            resultStatus = created.replayed() ? ResultStatus.SKIPPED : ResultStatus.CREATED;
        } else if ("UPDATE".equals(candidate.action())) {
            model = modelSpecs.update(
                command.tenantId(),
                command.actorId(),
                candidate.targetModelSpecId(),
                new ExpectedVersion(
                    candidate.targetModelSpecId(),
                    candidate.expectedModelRevision(),
                    candidate.expectedModelChecksum()
                ),
                decoded.updateCommand()
            );
            resultStatus = ResultStatus.UPDATED;
        } else {
            throw new IllegalArgumentException("Candidate action cannot be applied: " + candidate.action());
        }
        requireExpectedModel(candidate, model);

        int artifactCount = 0;
        int implementationRevision = candidate.expectedImplementationRevision();
        String implementationChecksum = candidate.expectedImplementationChecksum();
        var implementationCommand = implementationForCandidate(
            decoded.implementationCommand(),
            command.candidateIdempotencyKey()
        );
        boolean importedDbt = implementationCommand.ownership() == ImplementationMode.DBT_MANAGED;
        if (!"SKIP".equals(candidate.action()) && !"CANCEL".equals(candidate.action())) {
            ExpectedVersion modelVersion = new ExpectedVersion(model.id(), model.revision(), model.checksum());
            ExpectedImplementationVersion expectedImplementation = new ExpectedImplementationVersion(
                model.id(),
                candidate.expectedImplementationRevision(),
                candidate.expectedImplementationChecksum()
            );
            ImplementationView implementation;
            if (importedDbt) {
                implementation = lifecycle.saveImportedDbtImplementation(
                    command.tenantId(),
                    command.actorId(),
                    model.id(),
                    modelVersion,
                    ModelStatus.DRAFT,
                    expectedImplementation,
                    decoded.projectKey(),
                    candidate.dbtUniqueId(),
                    implementationCommand
                );
            } else {
                implementation = lifecycle.saveImplementation(
                    command.tenantId(),
                    command.actorId(),
                    model.id(),
                    modelVersion,
                    expectedImplementation,
                    decoded.projectKey(),
                    candidate.dbtUniqueId(),
                    implementationCommand
                );
            }
            requireExpectedImplementation(candidate, implementation);
            implementationRevision = implementation.implementationRevision();
            implementationChecksum = implementation.implementationChecksum();
            if (importedDbt) {
                ModelingDbtArtifactImportService.ImportResult imported = artifactImports.importArtifacts(
                    new ImportCommand(
                        command.tenantId(),
                        model.id(),
                        model.planId(),
                        model.revision(),
                        model.checksum(),
                        ModelStatus.DRAFT,
                        implementation.id(),
                        implementation.implementationRevision(),
                        implementation.implementationChecksum(),
                        decoded.projectKey(),
                        candidate.dbtUniqueId(),
                        command.candidateIdempotencyKey(),
                        decoded.artifacts()
                    )
                );
                artifactCount = imported.artifactCount();
            }
        }

        RevisionPins preAttemptPins = candidate.expectedModelRevision() > 0 &&
            candidate.expectedModelChecksum() != null &&
            candidate.expectedImplementationRevision() > 0 &&
            candidate.expectedImplementationChecksum() != null
            ? new RevisionPins(
                candidate.expectedModelRevision(),
                candidate.expectedModelChecksum(),
                candidate.expectedImplementationRevision(),
                candidate.expectedImplementationChecksum()
            )
            : null;
        MergeCheckpoint checkpoint = "CANCEL".equals(candidate.action())
            ? null
            : new MergeCheckpoint(
                command.tenantId(),
                decoded.projectKey(),
                candidate.dbtUniqueId(),
                candidate.incomingExternalChecksum(),
                implementationRevision,
                implementationChecksum,
                model.revision(),
                model.checksum()
            );
        CandidateResult result = new CandidateResult(
            UUID.randomUUID(),
            command.sequence(),
            candidate.dbtUniqueId(),
            command.candidateIdempotencyKey(),
            command.candidateRequestHash(),
            resultStatus,
            model.id(),
            model.revision(),
            model.checksum(),
            implementationRevision,
            implementationChecksum,
            artifactCount,
            List.of(),
            Instant.now(),
            candidate.action(),
            decoded.projectKey(),
            checkpoint,
            preAttemptPins,
            candidate.dependencyUniqueIds()
        );
        return applyRepository.recordSuccess(
            command.tenantId(),
            command.runId(),
            command.attemptId(),
            command.ownerToken(),
            result
        );
    }

    private static SaveImplementationCommand implementationForCandidate(
        SaveImplementationCommand command,
        String candidateIdempotencyKey
    ) {
        return new SaveImplementationCommand(
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            command.settings(),
            command.ownership(),
            command.materialization(),
            candidateIdempotencyKey + ":implementation"
        );
    }

    private static void requireExpectedModel(Candidate candidate, ModelSpecView model) {
        boolean retainCurrent = "SKIP".equals(candidate.action()) || "CANCEL".equals(candidate.action());
        boolean statusMismatch = retainCurrent
            ? !Objects.equals(model.status().name(), candidate.expectedModelStatus())
            : model.status() != ModelStatus.DRAFT;
        if (
            !Objects.equals(model.id(), candidate.targetModelSpecId()) ||
            model.revision() != candidate.targetRevision() ||
            !Objects.equals(model.checksum(), candidate.proposedModelSpecChecksum()) ||
            statusMismatch
        ) {
            throw new IllegalStateException("Applied ModelSpec does not match the frozen preview pins");
        }
    }

    private static void requireExpectedImplementation(Candidate candidate, ImplementationView implementation) {
        if (
            implementation.implementationRevision() != candidate.targetImplementationRevision() ||
            !Objects.equals(implementation.implementationChecksum(), candidate.proposedImplementationChecksum())
        ) {
            throw new IllegalStateException("Applied implementation does not match the frozen preview pins");
        }
    }

    public record Execution(
        String tenantId,
        String actorId,
        UUID runId,
        UUID attemptId,
        int sequence,
        Candidate candidate,
        String applyPayloadJson,
        String candidateIdempotencyKey,
        String candidateRequestHash,
        String ownerToken,
        Set<String> ownedTechnicalNodeIds
    ) {
        public Execution {
            ownedTechnicalNodeIds = ownedTechnicalNodeIds == null ? Set.of() : Set.copyOf(ownedTechnicalNodeIds);
        }

        public Execution(
            String tenantId,
            String actorId,
            UUID attemptId,
            int sequence,
            Candidate candidate,
            String applyPayloadJson,
            String candidateIdempotencyKey,
            String candidateRequestHash
        ) {
            this(
                tenantId,
                actorId,
                null,
                attemptId,
                sequence,
                candidate,
                applyPayloadJson,
                candidateIdempotencyKey,
                candidateRequestHash,
                null,
                Set.of()
            );
        }
    }
}
