package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildEntry;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildScope;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationRunRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationRunRepository.RunGroupRecord;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository.ObservationWrite;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationSourceAvailabilityGuard.GenerationCheck;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationSourceAvailabilityGuard.GenerationDrift;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalRelationObservation;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.RelationLocator;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.TargetContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Synchronizes dbt runtime artifacts into durable pipeline truth.
 *
 * <p>dbt success and append-only probe evidence are persisted first. Verified pipeline rows
 * become BUILT during sync. Finalization completes the dispatch and promotes the candidate.
 * Data registration is an independent command that consumes this completed evidence.
 */
@Service
public class ModelMaterializationRunArtifactService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelMaterializationRunArtifactService.class);

    private static final long MAX_DBT_ARTIFACT_BYTES =
        25L * 1024L * 1024L;

    private final ModelMaterializationRunRepository runs;
    private final ModelMaterializationBuildRepository builds;
    private final ModelMaterializationSourceAvailabilityGuard sourceAvailability;
    private final DbtScopedProjectService scopedProjects;
    private final PhysicalRelationInspectorRegistry inspectors;
    private final PhysicalRelationObservationRepository observations;
    private final ModelReleaseCandidateService candidates;
    private final CandidateQualityAssetRegistrationService qualityAssets;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public ModelMaterializationRunArtifactService(
        ModelMaterializationRunRepository runs,
        ModelMaterializationBuildRepository builds,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        DbtScopedProjectService scopedProjects,
        PhysicalRelationInspectorRegistry inspectors,
        PhysicalRelationObservationRepository observations,
        ModelReleaseCandidateService candidates,
        CandidateQualityAssetRegistrationService qualityAssets,
        AuditService auditService,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this(
            runs,
            builds,
            sourceAvailability,
            scopedProjects,
            inspectors,
            observations,
            candidates,
            qualityAssets,
            auditService,
            objectMapper,
            Clock.systemUTC(),
            new TransactionTemplate(transactionManager)
        );
    }

    ModelMaterializationRunArtifactService(
        ModelMaterializationRunRepository runs,
        ModelMaterializationBuildRepository builds,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        DbtScopedProjectService scopedProjects,
        PhysicalRelationInspectorRegistry inspectors,
        PhysicalRelationObservationRepository observations,
        ModelReleaseCandidateService candidates,
        CandidateQualityAssetRegistrationService qualityAssets,
        AuditService auditService,
        ObjectMapper objectMapper,
        Clock clock,
        TransactionOperations transactions
    ) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.builds = Objects.requireNonNull(
            builds,
            "builds is required"
        );
        this.sourceAvailability = Objects.requireNonNull(
            sourceAvailability,
            "sourceAvailability is required"
        );
        this.scopedProjects = Objects.requireNonNull(
            scopedProjects,
            "scopedProjects is required"
        );
        this.inspectors = Objects.requireNonNull(
            inspectors,
            "inspectors is required"
        );
        this.observations = Objects.requireNonNull(
            observations,
            "observations is required"
        );
        this.candidates = Objects.requireNonNull(
            candidates,
            "candidates is required"
        );
        this.qualityAssets = Objects.requireNonNull(
            qualityAssets,
            "qualityAssets is required"
        );
        this.auditService = Objects.requireNonNull(
            auditService,
            "auditService is required"
        );
        this.objectMapper = Objects.requireNonNull(
            objectMapper,
            "objectMapper is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.transactions = Objects.requireNonNull(
            transactions,
            "transactions is required"
        );
    }

    public RunArtifactView syncAndProbe(
        UUID groupId,
        SyncProbeCommand command
    ) {
        if (groupId == null || command == null) {
            throw failure(
                "MODEL_DBT_RUN_REQUEST_INVALID",
                "Run group and sync command are required"
            );
        }
        RunGroupRecord group = requireGroup(groupId);
        rejectPersistedAvailabilityStale(group);
        boolean terminalStatePersisted = false;
        boolean dbtResultsPersisted = false;
        Map<UUID, String> perModelRunResults = Map.of();
        UUID runInvocationId = null;
        try {
            requireSyncIdentity(group, command);
            CandidateBuildScope scope =
                builds.loadCandidateBuildScope(
                    group.tenantId(),
                    groupId
                );
            requireCandidateIdentity(group, scope);
            Path project =
                scopedProjects.verifyCandidateProject(
                    command.projectBundleChecksum()
                );
            JsonNode manifest = readArtifact(
                project,
                "manifest.json"
            );
            JsonNode results = readArtifact(
                project,
                "run_results.json"
            );
            UUID invocationId = requireInvocationIdentity(
                manifest,
                results
            );
            runInvocationId = invocationId;
            ManifestValidation manifestValidation =
                validateManifest(scope, manifest);
            perModelRunResults = validateRunResults(
                scope,
                results,
                manifestValidation.runtimeUniqueIds()
            );
            if (perModelRunResults.values().stream().anyMatch(status -> !"DBT_SUCCEEDED".equals(status))) {
                throw failure(
                    "MODEL_DBT_BUILD_RESULT_FAILED",
                    "At least one candidate model did not build successfully"
                );
            }
            Instant now = clock.instant();
            List<ObservationWrite> evidence =
                observeRelations(
                    group,
                    scope,
                    invocationId,
                    manifestValidation.locators(),
                    now
                );
            boolean artifactBoundaryCurrent = Boolean.TRUE.equals(transactions.execute(status -> {
                GenerationCheck generation = sourceAvailability.checkPinnedCurrentForUpdate(groupId);
                if (!generation.current()) {
                    persistAvailabilityStale(group, generation, "ARTIFACT_SYNC", now);
                    return false;
                }
                runs.markDbtSucceeded(
                    groupId,
                    invocationId,
                    scope.entries().size(),
                    now
                );
                observations.appendAll(evidence);
                auditRun(
                    group,
                    "artifacts-synced:" + invocationId,
                    "MODEL_MATERIALIZATION_ARTIFACTS_SYNCED",
                    AuditStage.SUCCESS,
                    "ARTIFACTS_SYNCED",
                    invocationId,
                    scope.entries().size(),
                    null,
                    now
                );
                return true;
            }));
            if (!artifactBoundaryCurrent) {
                terminalStatePersisted = true;
                throw staleFailure();
            }
            dbtResultsPersisted = true;
            ObservationWrite failed = evidence
                .stream()
                .filter(observation ->
                    !observation.verified()
                )
                .findFirst()
                .orElse(null);
            if (failed != null) {
                throw failure(
                    failed.errorCode(),
                    "Physical relation verification failed"
                );
            }
            boolean relationBoundaryCurrent = Boolean.TRUE.equals(transactions.execute(status -> {
                GenerationCheck generation = sourceAvailability.checkPinnedCurrentForUpdate(groupId);
                if (!generation.current()) {
                    persistAvailabilityStale(group, generation, "RELATION_PUBLICATION", now);
                    return false;
                }
                runs.markRelationsVerified(
                    groupId,
                    scope.entries().size(),
                    now
                );
                auditRun(
                    group,
                    "relations-verified:" + invocationId,
                    "MODEL_MATERIALIZATION_RELATIONS_VERIFIED",
                    AuditStage.SUCCESS,
                    "BUILT",
                    invocationId,
                    scope.entries().size(),
                    null,
                    now
                );
                return true;
            }));
            if (!relationBoundaryCurrent) {
                terminalStatePersisted = true;
                throw staleFailure();
            }
            terminalStatePersisted = true;
            return new RunArtifactView(
                groupId,
                "BUILT",
                invocationId,
                scope.entries().size()
            );
        } catch (MachineAuditPersistenceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            ModelMaterializationRuntimeException stable =
                stableFailure(failure);
            LOG.warn("Materialization sync failed: runGroup={}, code={}", groupId, stable.code(), failure);
            if (!terminalStatePersisted) {
                try {
                    Instant failedAt = clock.instant();
                    Map<UUID, String> itemResults = dbtResultsPersisted ? Map.of() : perModelRunResults;
                    UUID failedInvocationId = runInvocationId;
                    transactions.executeWithoutResult(status -> {
                        if (!itemResults.isEmpty()) {
                            runs.recordDbtResults(groupId, failedInvocationId, itemResults, failedAt);
                        }
                        runs.markFailed(
                            groupId,
                            stable.code(),
                            failedAt
                        );
                        transitionCandidate(
                            group,
                            DeliveryStatus.BUILD_FAILED,
                            "materialization-failed-" + groupId,
                            stable.code()
                        );
                        auditRun(
                            group,
                            "failed:" + stable.code(),
                            "MODEL_MATERIALIZATION_RUN_FAILED",
                            AuditStage.FAIL,
                            "FAILED",
                            null,
                            null,
                            stable.code(),
                            failedAt
                        );
                    });
                } catch (RuntimeException failurePersistence) {
                    stable.addSuppressed(failurePersistence);
                }
            }
            throw stable;
        }
    }

    public RunArtifactView finalizeRun(
        UUID groupId,
        FinalizeCommand command
    ) {
        if (groupId == null || command == null) {
            throw failure(
                "MODEL_DBT_RUN_REQUEST_INVALID",
                "Run group and finalize command are required"
            );
        }
        RunGroupRecord group = requireGroup(groupId);
        rejectPersistedAvailabilityStale(group);
        String outcome = required(
            command.outcome(),
            "outcome"
        );
        Instant now = clock.instant();
        int modelCount;
        if ("SUCCEEDED".equals(outcome)) {
            try {
                SuccessBoundary success = transactions.execute(status -> {
                    GenerationCheck generation = sourceAvailability.checkPinnedCurrentForUpdate(groupId);
                    if (!generation.current()) {
                        persistAvailabilityStale(group, generation, "AIRFLOW_FINALIZE", now);
                        return new SuccessBoundary(false, 0);
                    }
                    int finalized = runs.finalizeSucceeded(
                        groupId,
                        now
                    );
                    if ("COMPLETED".equals(group.dispatchStatus())) {
                        return new SuccessBoundary(true, finalized);
                    }
                    CommandResult built = transitionCandidate(
                        group,
                        DeliveryStatus.BUILT,
                        "materialization-built-" + groupId,
                        "dbt build and physical relation evidence verified"
                    );
                    if (built == null || built.candidate() == null) {
                        throw failure(
                            "MODEL_SPEC_GOVERNANCE_ASSET_REGISTRATION_FAILED",
                            "Built candidate was not available for governance asset registration"
                        );
                    }
                    // Catalog registration is an independent data-module command; it must not roll back a verified model build.
                    auditRun(
                        group,
                        "finalized:succeeded",
                        "MODEL_MATERIALIZATION_RUN_FINALIZED",
                        AuditStage.SUCCESS,
                        "BUILT",
                        null,
                        finalized,
                        null,
                        now
                    );
                    return new SuccessBoundary(true, finalized);
                });
                if (success == null || !success.current()) {
                    throw staleFailure();
                }
                modelCount = success.modelCount();
            } catch (MachineAuditPersistenceException failure) {
                throw failure;
            } catch (RuntimeException inconsistent) {
                ModelMaterializationRuntimeException stable = inconsistent instanceof ModelMaterializationRuntimeException ||
                    inconsistent instanceof ModelReleaseCandidateException
                    ? stableFailure(inconsistent)
                    : failure("MODEL_DBT_FINALIZE_PRECONDITION_FAILED", "Run group cannot be finalized as successful");
                LOG.warn("Materialization finalize failed: runGroup={}, code={}", groupId, stable.code(), inconsistent);
                if (!isAvailabilityStaleReason(stable.code())) {
                    try {
                        transactions.executeWithoutResult(status -> {
                            runs.finalizeFailed(groupId, stable.code(), now);
                            transitionCandidate(
                                group, DeliveryStatus.BUILD_FAILED, "materialization-failed-" + groupId, stable.code()
                            );
                            auditRun(
                                group, "failed:" + stable.code(), "MODEL_MATERIALIZATION_RUN_FAILED",
                                AuditStage.FAIL, "FAILED", null, null, stable.code(), now
                            );
                        });
                    } catch (RuntimeException failurePersistence) {
                        stable.addSuppressed(failurePersistence);
                    }
                }
                throw stable;
            }
        } else if ("FAILED".equals(outcome)) {
            String errorCode = group.lastErrorCode() == null || group.lastErrorCode().isBlank()
                ? "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED" : group.lastErrorCode();
            modelCount = transactions.execute(status -> {
                int failed = runs.finalizeFailed(
                    groupId,
                    errorCode,
                    now
                );
                if (failed < 1) {
                    throw failure(
                        "MODEL_DBT_FINALIZE_PRECONDITION_FAILED",
                        "Run group cannot be finalized as failed"
                    );
                }
                if (!"BUILD_FAILED".equals(group.candidateCurrentStatus())) {
                    transitionCandidate(group, DeliveryStatus.BUILD_FAILED, "materialization-failed-" + groupId, errorCode);
                }
                auditRun(
                    group,
                    "finalized:failed",
                    "MODEL_MATERIALIZATION_RUN_FAILED",
                    AuditStage.FAIL,
                    "FAILED",
                    null,
                    failed,
                    errorCode,
                    now
                );
                return failed;
            });
        } else {
            throw failure(
                "MODEL_DBT_RUN_REQUEST_INVALID",
                "Finalize outcome is invalid"
            );
        }
        if (
            group.scopedBundleChecksum() != null &&
            group.scopedBundleChecksum().matches("^[0-9a-f]{64}$")
        ) {
            scopedProjects.releaseCandidateProject(
                group.scopedBundleChecksum()
            );
        }
        return new RunArtifactView(
            groupId,
            "SUCCEEDED".equals(outcome)
                ? "BUILT"
                : "FAILED",
            null,
            modelCount
        );
    }

    private RunGroupRecord requireGroup(UUID groupId) {
        return runs
            .findRunGroup(groupId)
            .orElseThrow(() ->
                failure(
                    "MODEL_DBT_RUN_GROUP_NOT_FOUND",
                    "Materialization run group does not exist"
                )
            );
    }

    private static void rejectPersistedAvailabilityStale(RunGroupRecord group) {
        if (isAvailabilityStaleReason(group.lastErrorCode())) {
            throw staleFailure();
        }
    }

    private void persistAvailabilityStale(
        RunGroupRecord group,
        GenerationCheck generation,
        String boundary,
        Instant occurredAt
    ) {
        String reasonCode = generation.reasonCode() == null
            ? ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            : generation.reasonCode();
        boolean first = runs.markAvailabilityStale(group.groupId(), reasonCode, occurredAt);
        if (!first) {
            return;
        }
        if (!DeliveryStatus.STALE.name().equals(group.candidateCurrentStatus())) {
            candidates.transition(
                group.tenantId(),
                "service:dts-airflow",
                group.candidateId(),
                new TransitionCommand(
                    group.candidateCurrentVersion(),
                    DeliveryStatus.STALE,
                    "materialization-availability-stale-" + group.groupId(),
                    reasonCode
                )
            );
        }
        auditAvailabilityStale(group, generation, boundary, reasonCode, occurredAt);
    }

    private void auditAvailabilityStale(
        RunGroupRecord group,
        GenerationCheck generation,
        String boundary,
        String reasonCode,
        Instant occurredAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenant", group.tenantId());
        payload.put("candidate", group.candidateId());
        payload.put("version", group.candidateVersion());
        payload.put("attempt", group.attempt());
        payload.put("dispatch", group.groupId());
        payload.put("status", "FAILED_STALE");
        payload.put("boundary", boundary);
        payload.put("reasonCode", reasonCode);
        payload.put("driftCount", generation.drift().size());
        payload.put(
            "generations",
            generation
                .drift()
                .stream()
                .limit(100)
                .map(ModelMaterializationRunArtifactService::safeGeneration)
                .toList()
        );
        try {
            auditService.auditActionAsStrict(
                "airflow",
                "model-materialization-run:" + group.groupId() + ":availability-stale",
                occurredAt,
                "MODEL_MATERIALIZATION_RUN_FAILED",
                AuditStage.FAIL,
                group.groupId().toString(),
                Map.copyOf(payload)
            );
        } catch (RuntimeException failure) {
            throw new MachineAuditPersistenceException(failure);
        }
    }

    private static Map<String, Object> safeGeneration(GenerationDrift drift) {
        Map<String, Object> generation = new LinkedHashMap<>();
        if (drift.sourceBindingId() != null) generation.put("sourceBindingId", drift.sourceBindingId());
        if (drift.assetType() != null) generation.put("assetType", drift.assetType());
        generation.put("pinnedEpoch", drift.pinnedEpoch());
        generation.put("pinnedSourceSequence", drift.pinnedSourceSequence());
        if (drift.pinnedEventId() != null) generation.put("pinnedEventId", drift.pinnedEventId());
        if (drift.currentStatus() != null) generation.put("currentStatus", drift.currentStatus());
        generation.put("currentEpoch", drift.currentEpoch());
        generation.put("currentSourceSequence", drift.currentSourceSequence());
        if (drift.currentEventId() != null) generation.put("currentEventId", drift.currentEventId());
        generation.put("reasonCode", drift.reasonCode());
        return Map.copyOf(generation);
    }

    private static void requireSyncIdentity(
        RunGroupRecord group,
        SyncProbeCommand command
    ) {
        if (
            !"RELEASE_BUILD".equals(command.runPurpose()) ||
            !Objects.equals(
                group.runPurpose(),
                command.runPurpose()
            ) ||
            group.scopedBundleChecksum() == null ||
            !group
                .scopedBundleChecksum()
                .equals(command.projectBundleChecksum()) ||
            !java.util.Set.of(
                "CLAIMED",
                "SUBMITTED",
                "UNKNOWN"
            ).contains(group.dispatchStatus())
            ||
            group.executionTargetKey() == null ||
            group.executionTargetKey().isBlank() ||
            group.adapter() == null ||
            group.adapter().isBlank() ||
            group.credentialVersionRef() == null ||
            !group
                .credentialVersionRef()
                .matches("^sha256:[0-9a-f]{64}$")
        ) {
            throw failure(
                "MODEL_DBT_RUN_IDENTITY_MISMATCH",
                "Runtime artifacts do not match the durable run group"
            );
        }
    }

    private static void requireCandidateIdentity(
        RunGroupRecord group,
        CandidateBuildScope scope
    ) {
        if (
            scope == null ||
            !group.groupId().equals(scope.pipelineRunGroupId()) ||
            !group.tenantId().equals(scope.tenantId()) ||
            !group.candidateId().equals(scope.candidateId()) ||
            group.candidateVersion() != scope.candidateVersion() ||
            scope.entries().isEmpty()
        ) {
            throw failure(
                "MODEL_DBT_RUN_IDENTITY_MISMATCH",
                "Candidate scope does not match the durable run group"
            );
        }
    }

    private JsonNode readArtifact(
        Path project,
        String fileName
    ) {
        if (project == null || fileName == null) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt runtime artifact is unavailable"
            );
        }
        Path normalizedProject = project.normalize();
        Path target = normalizedProject.resolve("target").normalize();
        Path artifact = target.resolve(fileName).normalize();
        if (
            !target.startsWith(normalizedProject) ||
            !artifact.startsWith(target) ||
            Files.isSymbolicLink(target) ||
            Files.isSymbolicLink(artifact) ||
            !Files.isRegularFile(
                artifact,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt runtime artifact is unavailable"
            );
        }
        try {
            long size = Files.size(artifact);
            if (size < 2 || size > MAX_DBT_ARTIFACT_BYTES) {
                throw failure(
                    "MODEL_DBT_ARTIFACT_INVALID",
                    "dbt runtime artifact size is invalid"
                );
            }
            JsonNode parsed = objectMapper.readTree(
                Files.readAllBytes(artifact)
            );
            if (parsed == null || !parsed.isObject()) {
                throw failure(
                    "MODEL_DBT_ARTIFACT_INVALID",
                    "dbt runtime artifact must be a JSON object"
                );
            }
            return parsed;
        } catch (IOException invalid) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt runtime artifact cannot be read"
            );
        }
    }

    private static UUID requireInvocationIdentity(
        JsonNode manifest,
        JsonNode runResults
    ) {
        String manifestInvocation = manifest
            .path("metadata")
            .path("invocation_id")
            .asText("");
        String resultsInvocation = runResults
            .path("metadata")
            .path("invocation_id")
            .asText("");
        if (
            manifestInvocation.isBlank() ||
            !manifestInvocation.equals(resultsInvocation)
        ) {
            throw failure(
                "MODEL_DBT_INVOCATION_MISMATCH",
                "dbt runtime artifacts disagree on invocation identity"
            );
        }
        try {
            return UUID.fromString(manifestInvocation);
        } catch (IllegalArgumentException invalid) {
            throw failure(
                "MODEL_DBT_INVOCATION_MISMATCH",
                "dbt invocation identity is invalid"
            );
        }
    }

    private static ManifestValidation validateManifest(
        CandidateBuildScope scope,
        JsonNode manifest
    ) {
        JsonNode nodes = manifest.path("nodes");
        if (!nodes.isObject()) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt manifest nodes are unavailable"
            );
        }
        Map<UUID, RelationLocator> locators =
            new LinkedHashMap<>();
        Map<UUID, String> runtimeUniqueIds =
            new LinkedHashMap<>();
        for (CandidateBuildEntry entry : scope.entries()) {
            RuntimeManifestNode runtimeNode = requireRuntimeManifestNode(
                nodes,
                entry
            );
            JsonNode node = runtimeNode.node();
            JsonNode meta = node.path("config").path("meta");
            if (
                node.isMissingNode() ||
                !entry
                    .modelSpecId()
                    .toString()
                    .equals(meta.path("modelSpecId").asText()) ||
                entry.modelRevision() !=
                meta.path("modelRevision").asInt(-1) ||
                !entry
                    .modelChecksum()
                    .equals(
                        meta.path("modelChecksum").asText()
                    ) ||
                entry.implementationRevision() !=
                meta
                    .path("implementationRevision")
                    .asInt(-1) ||
                !entry
                    .implementationChecksum()
                    .equals(
                        meta
                            .path("implementationChecksum")
                            .asText()
                    )
            ) {
                throw failure(
                    "MODEL_DBT_MANIFEST_IDENTITY_MISMATCH",
                    "dbt manifest does not match the immutable model implementation"
                );
            }
            String alias = node.path("alias").asText("");
            List<String> manifestColumns = expectedColumns(
                node.path("columns")
            );
            List<String> pinnedColumns = entry.expectedColumns().isEmpty()
                ? manifestColumns
                : entry.expectedColumns();
            if (
                pinnedColumns.isEmpty() ||
                (
                    !manifestColumns.isEmpty() &&
                    !Set.copyOf(manifestColumns).equals(
                        Set.copyOf(pinnedColumns)
                    )
                )
            ) {
                throw failure(
                    "MODEL_DBT_MANIFEST_COLUMNS_MISMATCH",
                    "dbt manifest columns do not match the pinned model revision"
                );
            }
            if (
                !entry.targetIdentifier().equals(alias) ||
                !"model".equals(
                    node.path("resource_type").asText()
                )
            ) {
                throw failure(
                    "MODEL_DBT_MANIFEST_LOCATOR_MISMATCH",
                    "dbt manifest relation locator does not match the candidate"
                );
            }
            RelationLocator locator;
            try {
                locator = new RelationLocator(
                    node.path("database").asText(""),
                    node.path("schema").asText(""),
                    alias,
                    expectedType(
                        node
                            .path("config")
                            .path("materialized")
                            .asText("")
                    ),
                    pinnedColumns,
                    expectedColumnTypes(
                        node.path("columns"),
                        entry.implementationMode()
                    )
                );
            } catch (IllegalArgumentException invalid) {
                throw failure(
                    "MODEL_DBT_MANIFEST_LOCATOR_INVALID",
                    "dbt manifest relation locator is invalid"
                );
            }
            locators.put(entry.modelSpecId(), locator);
            runtimeUniqueIds.put(
                entry.pipelineRunId(),
                runtimeNode.uniqueId()
            );
        }
        return new ManifestValidation(
            Map.copyOf(locators),
            Map.copyOf(runtimeUniqueIds)
        );
    }

    private static RuntimeManifestNode requireRuntimeManifestNode(
        JsonNode nodes,
        CandidateBuildEntry entry
    ) {
        List<RuntimeManifestNode> matches = new ArrayList<>();
        nodes.fields().forEachRemaining(candidate -> {
            JsonNode node = candidate.getValue();
            if (
                "model".equals(node.path("resource_type").asText()) &&
                entry
                    .modelSpecId()
                    .toString()
                    .equals(
                        node
                            .path("config")
                            .path("meta")
                            .path("modelSpecId")
                            .asText()
                    ) &&
                candidate
                    .getKey()
                    .equals(node.path("unique_id").asText())
            ) {
                matches.add(
                    new RuntimeManifestNode(
                        candidate.getKey(),
                        node
                    )
                );
            }
        });
        if (matches.size() != 1) {
            throw failure(
                "MODEL_DBT_MANIFEST_IDENTITY_MISMATCH",
                "dbt manifest does not contain exactly one immutable model identity"
            );
        }
        return matches.getFirst();
    }

    private List<ObservationWrite> observeRelations(
        RunGroupRecord group,
        CandidateBuildScope scope,
        UUID invocationId,
        Map<UUID, RelationLocator> locators,
        Instant createdAt
    ) {
        PhysicalRelationInspector inspector =
            inspectors.require(group.adapter());
        List<ObservationWrite> evidence =
            new ArrayList<>(scope.entries().size());
        for (CandidateBuildEntry entry : scope.entries()) {
            RelationLocator locator = locators.get(
                entry.modelSpecId()
            );
            TargetContext target = new TargetContext(
                group.executionTargetKey(),
                inspector.adapter(),
                locator.databaseName(),
                locator.schemaName(),
                group.credentialVersionRef()
            );
            PhysicalRelationObservation observed;
            try {
                observed = inspector.observe(target, locator);
            } catch (
                PhysicalRelationInspectionException inspectionFailure
            ) {
                observed = new PhysicalRelationObservation(
                    false,
                    null,
                    List.of(),
                    null,
                    clock.instant(),
                    inspectionFailure.code()
                );
            }
            String errorCode = verificationError(
                inspector,
                locator,
                observed
            );
            boolean verified = errorCode == null;
            String expectedColumnsChecksum = digest(
                expectedColumnContract(locator)
            );
            String metadataChecksum = observed.exists()
                ? digest(
                    List.of(
                        group.tenantId(),
                        group.candidateId().toString(),
                        group.candidateVersion() + "",
                        entry.modelSpecId().toString(),
                        entry.modelRevision() + "",
                        entry.modelChecksum(),
                        entry.implementationRevision() + "",
                        entry.implementationChecksum(),
                        invocationId.toString(),
                        group.scopedBundleChecksum(),
                        inspector.adapter(),
                        locator.databaseName(),
                        locator.schemaName(),
                        locator.identifier(),
                        locator.expectedType().name(),
                        observed.actualType().name(),
                        expectedColumnsChecksum,
                        observed.columnsChecksum()
                    )
                )
                : null;
            evidence.add(
                new ObservationWrite(
                    group.tenantId(),
                    group.candidateId(),
                    group.candidateVersion(),
                    group.groupId(),
                    entry.pipelineRunId(),
                    entry.modelSpecId(),
                    entry.modelRevision(),
                    entry.modelChecksum(),
                    entry.implementationRevision(),
                    entry.implementationChecksum(),
                    invocationId,
                    group.scopedBundleChecksum(),
                    inspector.adapter(),
                    group.credentialVersionRef(),
                    locator.databaseName(),
                    locator.schemaName(),
                    locator.identifier(),
                    locator.expectedType(),
                    observed.actualType(),
                    observed.exists(),
                    verified,
                    observed.columns(),
                    expectedColumnsChecksum,
                    observed.columnsChecksum(),
                    metadataChecksum,
                    errorCode,
                    observed.observedAt(),
                    createdAt
                )
            );
        }
        return List.copyOf(evidence);
    }

    private static String verificationError(
        PhysicalRelationInspector inspector,
        RelationLocator locator,
        PhysicalRelationObservation observed
    ) {
        if (!observed.exists()) {
            return observed.errorCode();
        }
        if (locator.expectedType() != observed.actualType()) {
            return "MODEL_PHYSICAL_RELATION_TYPE_MISMATCH";
        }
        List<PhysicalColumn> actualColumns = observed
            .columns()
            .stream()
            .sorted(
                java.util.Comparator.comparingInt(
                    PhysicalColumn::ordinalPosition
                )
            )
            .toList();
        List<String> actualColumnNames = actualColumns
            .stream()
            .map(PhysicalColumn::name)
            .toList();
        if (
            locator.expectedColumns().size() != actualColumnNames.size() ||
            !Set.copyOf(locator.expectedColumns()).equals(Set.copyOf(actualColumnNames))
        ) {
            return "MODEL_PHYSICAL_RELATION_COLUMNS_MISMATCH";
        }
        for (PhysicalColumn actual : actualColumns) {
            String expected = locator
                .expectedColumnTypes()
                .get(actual.name());
            if (
                expected != null &&
                !inspector.dataTypeMatches(
                    expected,
                    actual.dataType()
                )
            ) {
                return "MODEL_PHYSICAL_RELATION_COLUMN_TYPE_MISMATCH";
            }
        }
        return null;
    }

    private static ExpectedRelationType expectedType(
        String materialized
    ) {
        return switch (
            materialized == null
                ? ""
                : materialized
                    .trim()
                    .toLowerCase(Locale.ROOT)
        ) {
            case "table", "incremental", "dts_schema_only" ->
                ExpectedRelationType.TABLE;
            case "view" -> ExpectedRelationType.VIEW;
            case "materialized_view" ->
                ExpectedRelationType.MATERIALIZED_VIEW;
            default -> throw new IllegalArgumentException(
                "unsupported materialization"
            );
        };
    }

    private static List<String> expectedColumns(
        JsonNode columns
    ) {
        if (!columns.isObject()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        columns
            .fields()
            .forEachRemaining(entry -> {
                String name = entry
                    .getValue()
                    .path("name")
                    .asText(entry.getKey());
                names.add(name);
            });
        return List.copyOf(names);
    }

    private static Map<String, String> expectedColumnTypes(
        JsonNode columns,
        String implementationMode
    ) {
        if (!columns.isObject()) {
            return Map.of();
        }
        boolean designerGenerated =
            "DESIGNER_GENERATED".equals(implementationMode);
        boolean dbtManaged = "DBT_MANAGED".equals(
            implementationMode
        );
        if (!designerGenerated && !dbtManaged) {
            throw failure(
                "MODEL_DBT_MANIFEST_IMPLEMENTATION_MODE_INVALID",
                "Candidate implementation mode is invalid"
            );
        }
        Map<String, String> types = new LinkedHashMap<>();
        int columnCount = 0;
        var fields = columns.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            columnCount++;
            String name = entry
                .getValue()
                .path("name")
                .asText(entry.getKey());
            String declared = entry
                .getValue()
                .path("data_type")
                .asText("");
            if (declared.isBlank()) {
                if (designerGenerated) {
                    throw failure(
                        "MODEL_DBT_MANIFEST_COLUMN_TYPE_REQUIRED",
                        "Generated model manifest must declare every column type"
                    );
                }
                continue;
            }
            try {
                types.put(
                    name,
                    ModelFieldPhysicalTypeContract.canonicalPostgresType(
                        declared
                    )
                );
            } catch (IllegalArgumentException unsupported) {
                throw failure(
                    "MODEL_DBT_MANIFEST_COLUMN_TYPE_UNSUPPORTED",
                    "dbt manifest declares an unsupported column type"
                );
            }
        }
        if (
            dbtManaged &&
            !types.isEmpty() &&
            types.size() != columnCount
        ) {
            throw failure(
                "MODEL_DBT_MANIFEST_COLUMN_TYPE_PARTIAL",
                "dbt-managed manifest column types must be complete or omitted"
            );
        }
        return Map.copyOf(types);
    }

    private static List<String> expectedColumnContract(
        RelationLocator locator
    ) {
        return locator
            .expectedColumns()
            .stream()
            .map(column ->
                column +
                "\u0000" +
                locator
                    .expectedColumnTypes()
                    .getOrDefault(column, "")
            )
            .toList();
    }

    private CommandResult transitionCandidate(
        RunGroupRecord group,
        DeliveryStatus target,
        String idempotencyKey,
        String reason
    ) {
        return candidates.transition(
            group.tenantId(),
            "service:dts-airflow",
            group.candidateId(),
                new TransitionCommand(
                group.candidateCurrentVersion(),
                target,
                idempotencyKey,
                reason
            )
        );
    }

    private void auditRun(
        RunGroupRecord group,
        String eventSuffix,
        String actionCode,
        AuditStage stage,
        String outcome,
        UUID invocationId,
        Integer modelCount,
        String errorCode,
        Instant occurredAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenant", group.tenantId());
        payload.put("candidate", group.candidateId());
        payload.put("version", group.candidateVersion());
        payload.put("runGroup", group.groupId());
        payload.put("outcome", outcome);
        if (invocationId != null) payload.put("invocation", invocationId);
        if (modelCount != null) payload.put("modelCount", modelCount);
        if (errorCode != null) payload.put("errorCode", errorCode);
        try {
            auditService.auditActionAsStrict(
                "airflow",
                "model-materialization-run:" + group.groupId() + ":" + eventSuffix,
                occurredAt,
                actionCode,
                stage,
                group.groupId().toString(),
                payload
            );
        } catch (RuntimeException failure) {
            throw new MachineAuditPersistenceException(failure);
        }
    }

    private static Map<UUID, String> validateRunResults(
        CandidateBuildScope scope,
        JsonNode runResults,
        Map<UUID, String> runtimeUniqueIds
    ) {
        JsonNode resultNodes = runResults.path("results");
        if (!resultNodes.isArray()) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt run results are unavailable"
            );
        }
        Map<String, String> statuses = new HashMap<>();
        for (JsonNode result : resultNodes) {
            String uniqueId = result.path("unique_id").asText("");
            if (!uniqueId.isBlank()) {
                String previous = statuses.put(
                    uniqueId,
                    result.path("status").asText("")
                );
                if (previous != null) {
                    throw failure(
                        "MODEL_DBT_ARTIFACT_INVALID",
                        "dbt run results contain duplicate nodes"
                    );
                }
            }
        }
        Map<UUID, String> perModel = new LinkedHashMap<>();
        for (CandidateBuildEntry entry : scope.entries()) {
            String runtimeUniqueId = runtimeUniqueIds.get(
                entry.pipelineRunId()
            );
            String status = String.valueOf(
                statuses.get(runtimeUniqueId)
            ).toLowerCase(Locale.ROOT);
            String persisted = switch (status) {
                case "success" -> "DBT_SUCCEEDED";
                case "skipped" -> "SKIPPED_DEPENDENCY_FAILED";
                default -> "FAILED";
            };
            perModel.put(entry.pipelineRunId(), persisted);
        }
        return Map.copyOf(perModel);
    }

    private static ModelMaterializationRuntimeException stableFailure(
        RuntimeException failure
    ) {
        if (
            failure instanceof ModelMaterializationRuntimeException stable
        ) {
            return stable;
        }
        if (
            failure instanceof DbtScopedProjectService.ScopedProjectException scoped
        ) {
            return failure(
                scoped.code(),
                "Candidate dbt project verification failed"
            );
        }
        if (failure instanceof ModelReleaseCandidateException candidate) {
            return failure(candidate.code(), candidate.getMessage());
        }
        if (
            failure instanceof PhysicalRelationInspectionException inspection
        ) {
            return failure(
                inspection.code(),
                "Physical relation verification failed"
            );
        }
        return failure(
            "MODEL_DBT_ARTIFACT_SYNC_FAILED",
            "dbt runtime artifacts could not be synchronized"
        );
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw failure(
                "MODEL_DBT_RUN_REQUEST_INVALID",
                name + " is required"
            );
        }
        return value.trim();
    }

    private static ModelMaterializationRuntimeException failure(
        String code,
        String message
    ) {
        return new ModelMaterializationRuntimeException(code, message);
    }

    private static ModelMaterializationRuntimeException staleFailure() {
        return failure(
            ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE,
            "Materialization runtime source generation is stale"
        );
    }

    private static boolean isAvailabilityStaleReason(String reasonCode) {
        return ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE.equals(reasonCode) ||
        ModelMaterializationSourceAvailabilityGuard.SOURCE_PIN_MISSING.equals(reasonCode) ||
        ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE.equals(reasonCode);
    }

    private static String digest(List<String> values) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                impossible
            );
        }
        for (String value : values) {
            digest.update(
                value.getBytes(StandardCharsets.UTF_8)
            );
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public record SyncProbeCommand(
        String runPurpose,
        String projectBundleChecksum
    ) {}

    public record FinalizeCommand(String outcome) {}

    public record RunArtifactView(
        UUID pipelineRunGroupId,
        String status,
        UUID dbtInvocationId,
        int modelCount
    ) {}

    private record SuccessBoundary(boolean current, int modelCount) {}

    private record ManifestValidation(
        Map<UUID, RelationLocator> locators,
        Map<UUID, String> runtimeUniqueIds
    ) {}

    private record RuntimeManifestNode(
        String uniqueId,
        JsonNode node
    ) {}

    private static final class MachineAuditPersistenceException
        extends RuntimeException {

        private MachineAuditPersistenceException(RuntimeException cause) {
            super("Machine audit persistence failed", cause);
        }
    }
}
