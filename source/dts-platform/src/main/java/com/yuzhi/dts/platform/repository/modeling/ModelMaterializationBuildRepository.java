package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationClaimKey;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateRetryDriftGate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL adapter for the durable side of START_BUILD.
 *
 * <p>The candidate state transition is performed immediately before this adapter in the same
 * outer transaction. Any snapshot, claim or run failure therefore rolls the candidate command
 * and append-only receipt back to DRAFT.
 */
@Repository
public class ModelMaterializationBuildRepository
    implements ModelReleaseCandidateRetryDriftGate {

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern DBT_UNIQUE_ID = Pattern.compile(
        "^model\\.[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*$"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ModelMaterializationProperties properties;

    public ModelMaterializationBuildRepository(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        ModelMaterializationProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public QueuedBuildGroup createQueuedBuild(CandidateView candidate, Instant now) {
        if (candidate == null || candidate.status() != com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus.BUILDING) {
            throw failure(
                "MODEL_MATERIALIZATION_BUILDING_REQUIRED",
                "A BUILDING release candidate is required before queueing materialization",
                Kind.CONFLICT,
                Map.of()
            );
        }
        if (now == null) throw new IllegalArgumentException("now is required");
        ExecutionTarget target = executionTarget();
        List<BuildEntryRow> rows = lockCurrentBuildEntries(candidate);
        if (rows.size() != candidate.entries().size()) {
            throw failure(
                "MODEL_IMPLEMENTATION_CURRENT_REQUIRED",
                "Every candidate entry requires one current active implementation",
                Kind.UNPROCESSABLE,
                Map.of("candidateId", candidate.id(), "expectedEntries", candidate.entries().size(), "resolvedEntries", rows.size())
            );
        }

        Map<UUID, BuildEntryRow> candidateModels = new LinkedHashMap<>();
        rows.forEach(row -> candidateModels.put(row.modelSpecId(), row));
        List<PreparedEntry> prepared = rows
            .stream()
            .map(row -> prepareEntry(candidate, row, candidateModels))
            .sorted(Comparator.comparing(entry -> entry.row().modelSpecId()))
            .toList();
        String candidateArtifactChecksum = digest(
            prepared
                .stream()
                .flatMap(entry ->
                    List.of(
                        entry.row().modelSpecId().toString(),
                        entry.artifactBundleChecksum(),
                        entry.dependencySnapshotChecksum()
                    )
                        .stream()
                )
                .toList()
        );
        UUID groupId = stableUuid(
            "release-build-group:" + candidate.id() + ":v" + candidate.version() + ":a1"
        );
        UUID invocationId = stableUuid(
            "dbt-invocation:" + candidate.id() + ":v" + candidate.version() + ":a1"
        );
        String dagRunId =
            "dts_rc_" +
            candidate.id().toString().replace("-", "") +
            "_v" +
            candidate.version() +
            "_a1";

        int headerUpdated = jdbcTemplate.update(
            """
            update modeling_model_release_candidate
               set execution_target_key = ?, adapter = ?, profile_key = ?, target_name = ?
             where tenant_id = ? and id = ? and version = ? and status = 'BUILDING'
               and execution_target_key is null and adapter is null
               and profile_key is null and target_name is null
            """,
            target.executionTargetKey(),
            target.adapter(),
            target.profileKey(),
            target.targetName(),
            candidate.tenantId(),
            candidate.id(),
            candidate.version()
        );
        if (headerUpdated != 1) {
            throw failure(
                "MODEL_EXECUTION_TARGET_SNAPSHOT_CONFLICT",
                "Candidate execution target snapshot could not be locked",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "candidateVersion", candidate.version())
            );
        }

        releaseTerminalClaims(candidate, prepared);

        List<QueuedBuildRun> runs = new ArrayList<>();
        try {
            for (PreparedEntry entry : prepared) {
                persistEntrySnapshot(candidate, entry);
                runs.add(
                    insertQueuedRun(
                        candidate,
                        entry,
                        candidateArtifactChecksum,
                        target,
                        groupId,
                        invocationId,
                        dagRunId,
                        now
                    )
                );
            }
        } catch (DataIntegrityViolationException conflict) {
            String message = String.valueOf(conflict.getMostSpecificCause().getMessage());
            if (message.contains("uk_model_release_candidate_entry_active_claim")) {
                throw failure(
                    "MODEL_ACTIVE_CANDIDATE_CLAIM_CONFLICT",
                    "The model is already locked by another active release candidate",
                    Kind.CONFLICT,
                    Map.of("candidateId", candidate.id())
                );
            }
            throw conflict;
        }
        if (runs.size() != candidate.entries().size()) {
            throw failure(
                "MODEL_PIPELINE_RUN_ATOMICITY_FAILED",
                "Candidate entry and pipeline run counts do not match",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id())
            );
        }
        insertDispatch(
            candidate,
            target,
            groupId,
            dagRunId,
            candidateArtifactChecksum,
            now
        );
        return new QueuedBuildGroup(
            candidate.id(),
            candidate.version(),
            groupId,
            invocationId,
            target.executionTargetKey(),
            target.releaseBuildDagId(),
            dagRunId,
            candidateArtifactChecksum,
            List.copyOf(runs)
        );
    }

    private void releaseTerminalClaims(
        CandidateView candidate,
        List<PreparedEntry> prepared
    ) {
        prepared
            .stream()
            .map(PreparedEntry::activeClaimKey)
            .distinct()
            .forEach(activeClaimKey ->
                jdbcTemplate.update(
                    """
                    update modeling_model_release_candidate_entry e
                       set active_claim_key = null
                      from modeling_model_release_candidate c
                     where c.tenant_id = e.tenant_id
                       and c.id = e.candidate_id
                       and e.tenant_id = ?
                       and e.candidate_id <> ?
                       and e.active_claim_key = ?
                       and c.status in ('REJECTED', 'ROLLED_BACK', 'STALE', 'CANCELLED', 'PUBLISHED')
                       and e.status = c.status
                    """,
                    candidate.tenantId(),
                    candidate.id(),
                    activeClaimKey
                )
            );
    }

    @Transactional
    public QueuedBuildGroup createRetryQueuedBuild(
        CandidateView candidate,
        Instant now
    ) {
        return createSubsequentQueuedBuild(
            candidate,
            now,
            Set.of("FAILED", "BLOCKED"),
            "retry"
        );
    }

    @Transactional
    public QueuedBuildGroup createRematerializationQueuedBuild(
        CandidateView candidate,
        Instant now
    ) {
        List<UUID> fullScope = candidate == null
            ? List.of()
            : candidate.entries().stream().map(EntryView::modelSpecId).toList();
        return createRematerializationQueuedBuild(candidate, now, fullScope);
    }

    @Transactional
    public QueuedBuildGroup createRematerializationQueuedBuild(
        CandidateView candidate,
        Instant now,
        List<UUID> buildModelSpecIds
    ) {
        requireBuildingCandidate(candidate, now);
        List<UUID> requested = buildModelSpecIds == null
            ? List.of()
            : buildModelSpecIds.stream().filter(Objects::nonNull).distinct().toList();
        if (
            buildModelSpecIds == null ||
            requested.isEmpty() ||
            requested.size() != buildModelSpecIds.size() ||
            requested.size() > candidate.entries().size()
        ) {
            throw failure(
                "MODEL_REMATERIALIZATION_BUILD_SCOPE_INVALID",
                "Rematerialization requires a non-empty unique subset of the immutable candidate scope",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "requestedScope", requested)
            );
        }
        RetryDispatch previous = lockLatestRetryDispatch(candidate);
        if (!"COMPLETED".equals(previous.status()) || previous.candidateVersion() >= candidate.version()) {
            throw subsequentAttemptNotReady(candidate, previous.status(), "rematerialization");
        }
        Map<UUID, RetryEntryRow> available = lockRematerializationEntries(candidate)
            .stream()
            .collect(java.util.stream.Collectors.toMap(RetryEntryRow::modelSpecId, entry -> entry));
        List<RetryEntryRow> entries = requested.stream().map(available::get).filter(Objects::nonNull).toList();
        if (entries.size() != requested.size()) {
            throw subsequentAttemptNotReady(candidate, "INCOMPLETE_VERIFIED_BUILD_SCOPE", "rematerialization");
        }
        requireRematerializationSnapshotCurrent(candidate, previous, entries);
        String aggregate = digest(
            entries
                .stream()
                .sorted(Comparator.comparing(RetryEntryRow::modelSpecId))
                .flatMap(entry ->
                    List.of(
                        entry.modelSpecId().toString(),
                        entry.artifactBundleChecksum(),
                        entry.dependencySnapshotChecksum()
                    ).stream()
                )
                .toList()
        );
        int attempt = previous.attempt() + 1;
        UUID groupId = stableUuid("release-build-group:" + candidate.id() + ":a" + attempt);
        UUID invocationId = stableUuid("dbt-invocation:" + candidate.id() + ":a" + attempt);
        String dagRunId = "dts_rc_" + candidate.id().toString().replace("-", "") + "_a" + attempt;
        List<QueuedBuildRun> runs = entries
            .stream()
            .map(entry ->
                insertRetryQueuedRun(
                    candidate,
                    entry,
                    previous,
                    groupId,
                    invocationId,
                    dagRunId,
                    attempt,
                    aggregate,
                    "Release candidate rematerialization queued",
                    now
                )
            )
            .toList();
        insertRetryDispatch(candidate, previous, groupId, dagRunId, attempt, aggregate, now);
        return new QueuedBuildGroup(
            candidate.id(),
            candidate.version(),
            groupId,
            invocationId,
            previous.executionTargetKey(),
            previous.airflowDagId(),
            dagRunId,
            aggregate,
            runs
        );
    }

    private QueuedBuildGroup createSubsequentQueuedBuild(
        CandidateView candidate,
        Instant now,
        Set<String> acceptedPreviousStatuses,
        String operation
    ) {
        requireBuildingCandidate(candidate, now);
        RetryDispatch previous = lockLatestRetryDispatch(candidate);
        if (
            !acceptedPreviousStatuses.contains(previous.status()) ||
            previous.candidateVersion() >= candidate.version()
        ) {
            throw subsequentAttemptNotReady(candidate, previous.status(), operation);
        }
        List<RetryEntryRow> entries = lockRetryEntries(
            candidate,
            previous
        );
        Set<UUID> immutableScope = candidate.entries().stream().map(EntryView::modelSpecId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> retryScope = entries.stream().map(RetryEntryRow::modelSpecId).collect(java.util.stream.Collectors.toSet());
        if (entries.isEmpty() || retryScope.size() != entries.size() || !immutableScope.containsAll(retryScope)) {
            throw subsequentAttemptNotReady(
                candidate,
                "INCOMPLETE_TERMINAL_SCOPE",
                operation
            );
        }
        requireRetrySnapshotCurrent(candidate, previous, entries);
        int attempt = previous.attempt() + 1;
        UUID groupId = stableUuid(
            "release-build-group:" + candidate.id() + ":a" + attempt
        );
        UUID invocationId = stableUuid(
            "dbt-invocation:" + candidate.id() + ":a" + attempt
        );
        String dagRunId =
            "dts_rc_" +
            candidate.id().toString().replace("-", "") +
            "_a" +
            attempt;
        List<QueuedBuildRun> runs = entries
            .stream()
            .map(entry ->
                insertRetryQueuedRun(
                    candidate,
                    entry,
                    previous,
                    groupId,
                    invocationId,
                    dagRunId,
                    attempt,
                    previous.artifactBundleChecksum(),
                    "Release candidate build retry queued",
                    now
                )
            )
            .toList();
        insertRetryDispatch(
            candidate,
            previous,
            groupId,
            dagRunId,
            attempt,
            previous.artifactBundleChecksum(),
            now
        );
        return new QueuedBuildGroup(
            candidate.id(),
            candidate.version(),
            groupId,
            invocationId,
            previous.executionTargetKey(),
            previous.airflowDagId(),
            dagRunId,
            previous.artifactBundleChecksum(),
            runs
        );
    }

    @Override
    @Transactional
    public List<DriftReasonView> detect(CandidateView candidate) {
        return detect(candidate, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriftReasonView> detectForRead(CandidateView candidate) {
        return detect(candidate, false);
    }

    private List<DriftReasonView> detect(
        CandidateView candidate,
        boolean lockRows
    ) {
        if (
            candidate == null ||
            candidate.status() ==
            com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus.DRAFT ||
            (
                candidate.status() ==
                    com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus.BUILDING &&
                candidate.executionTargetKey() == null
            )
        ) {
            return List.of();
        }
        ExecutionTarget currentTarget;
        try {
            currentTarget = executionTarget();
        } catch (ModelReleaseCandidateException unavailable) {
            return retryDriftReasons(
                candidate,
                "The current execution target is unavailable or changed"
            );
        }
        List<RetryDriftRow> rows = lockRows
            ? lockCurrentRetryEntries(candidate)
            : readCurrentSnapshotEntries(candidate);
        Map<UUID, RetryDriftRow> rowsByModel = new LinkedHashMap<>();
        rows.forEach(row -> rowsByModel.put(row.current().modelSpecId(), row));
        Map<UUID, BuildEntryRow> currentModels = new LinkedHashMap<>();
        rows.forEach(row ->
            currentModels.put(
                row.current().modelSpecId(),
                row.current()
            )
        );
        boolean targetMatches =
            Objects.equals(
                candidate.executionTargetKey(),
                currentTarget.executionTargetKey()
            ) &&
            Objects.equals(candidate.adapter(), currentTarget.adapter()) &&
            Objects.equals(
                candidate.profileKey(),
                currentTarget.profileKey()
            ) &&
            Objects.equals(candidate.targetName(), currentTarget.targetName());

        List<DriftReasonView> drift = new ArrayList<>();
        for (var entry : candidate.entries()) {
            RetryDriftRow row = rowsByModel.get(entry.modelSpecId());
            if (row == null) {
                drift.add(
                    retryDriftReason(
                        entry.modelSpecId(),
                        entry.revision(),
                        entry.checksum(),
                        "The current active implementation is missing"
                    )
                );
                continue;
            }
            PreparedEntry prepared;
            try {
                prepared = prepareEntry(
                    candidate,
                    row.current(),
                    currentModels
                );
            } catch (ModelReleaseCandidateException changed) {
                drift.add(
                    retryDriftReason(
                        entry.modelSpecId(),
                        entry.revision(),
                        entry.checksum(),
                        "The current implementation or runnable artifact is no longer valid"
                    )
                );
                continue;
            }
            if (
                !targetMatches ||
                !row.matches(prepared)
            ) {
                drift.add(
                    retryDriftReason(
                        entry.modelSpecId(),
                        entry.revision(),
                        entry.checksum(),
                        "The current implementation, artifact, dependency or execution target changed after the previous attempt"
                    )
                );
            }
        }
        return List.copyOf(drift);
    }

    private List<RetryDriftRow> lockCurrentRetryEntries(
        CandidateView candidate
    ) {
        return currentSnapshotEntries(candidate, true);
    }

    private List<RetryDriftRow> readCurrentSnapshotEntries(
        CandidateView candidate
    ) {
        return currentSnapshotEntries(candidate, false);
    }

    private List<RetryDriftRow> currentSnapshotEntries(
        CandidateView candidate,
        boolean lockRows
    ) {
        String query =
            """
            select e.id as entry_id, e.model_spec_id,
                   e.revision as model_revision,
                   e.checksum as model_checksum,
                   e.implementation_mode,
                   e.implementation_id as locked_implementation_id,
                   e.implementation_revision as locked_implementation_revision,
                   e.implementation_checksum as locked_implementation_checksum,
                   e.dbt_unique_id as locked_dbt_unique_id,
                   e.target_identifier as locked_target_identifier,
                   e.artifact_bundle_checksum as locked_artifact_bundle_checksum,
                   e.dependency_snapshot_checksum as locked_dependency_snapshot_checksum,
                   e.active_claim_key as locked_active_claim_key,
                   i.id as current_implementation_id,
                   i.implementation_revision as current_implementation_revision,
                   i.current_implementation_checksum as current_implementation_checksum,
                   i.ownership, i.project_key, i.dbt_unique_id,
                   i.input_mode, i.inputs_json::text as inputs_json,
                   i.settings_json::text as settings_json,
                   i.materialization
              from modeling_model_release_candidate c
              join modeling_model_release_candidate_entry e
                on e.tenant_id = c.tenant_id and e.candidate_id = c.id
              join modeling_model_implementation i
                on i.tenant_id = e.tenant_id
               and i.plan_id = e.plan_id
               and i.model_spec_id = e.model_spec_id
               and i.model_revision = e.revision
               and i.model_checksum = e.checksum
               and i.ownership = e.implementation_mode
               and i.status = 'ACTIVE'
             where c.tenant_id = ? and c.id = ? and c.version = ?
               and c.status = ?
               and e.status = ?
             order by e.sort_order, e.id
            """ +
            (lockRows ? " for update of c, e, i" : "");
        return jdbcTemplate.query(
            query,
            (row, rowNumber) -> {
                BuildEntryRow current = new BuildEntryRow(
                    row.getObject("entry_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getString("implementation_mode"),
                    row.getObject(
                        "current_implementation_id",
                        UUID.class
                    ),
                    row.getInt("current_implementation_revision"),
                    row.getString("current_implementation_checksum"),
                    row.getString("ownership"),
                    row.getString("project_key"),
                    row.getString("dbt_unique_id"),
                    row.getString("input_mode"),
                    row.getString("inputs_json"),
                    row.getString("settings_json"),
                    row.getString("materialization")
                );
                return new RetryDriftRow(
                    row.getObject(
                        "locked_implementation_id",
                        UUID.class
                    ),
                    row.getObject(
                        "locked_implementation_revision",
                        Integer.class
                    ),
                    row.getString("locked_implementation_checksum"),
                    row.getString("locked_dbt_unique_id"),
                    row.getString("locked_target_identifier"),
                    row.getString("locked_artifact_bundle_checksum"),
                    row.getString(
                        "locked_dependency_snapshot_checksum"
                    ),
                    row.getString("locked_active_claim_key"),
                    current
                );
            },
            candidate.tenantId(),
            candidate.id(),
            candidate.version(),
            candidate.status().name(),
            candidate.status().name()
        );
    }

    private static List<DriftReasonView> retryDriftReasons(
        CandidateView candidate,
        String message
    ) {
        return candidate
            .entries()
            .stream()
            .map(entry ->
                retryDriftReason(
                    entry.modelSpecId(),
                    entry.revision(),
                    entry.checksum(),
                    message
                )
            )
            .toList();
    }

    private static DriftReasonView retryDriftReason(
        UUID modelSpecId,
        int revision,
        String checksum,
        String message
    ) {
        return new DriftReasonView(
            modelSpecId,
            revision,
            checksum,
            revision,
            checksum,
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            message
        );
    }

    @Transactional(readOnly = true)
    public QueuedBuildGroup requireQueuedBuild(CandidateView candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        List<QueuedBuildRun> runs = jdbcTemplate.query(
            """
            select id, release_candidate_entry_id, model_spec_id, pipeline_run_group_id,
                   dbt_invocation_id, airflow_dag_id, airflow_run_id, dbt_selector, target,
                   status, attempt, artifact_bundle_checksum
              from modeling_pipeline_run
             where tenant_id = ? and release_candidate_id = ? and release_candidate_version = ?
               and run_purpose = 'RELEASE_BUILD'
             order by model_spec_id, id
            """,
            (row, rowNumber) ->
                new QueuedBuildRun(
                    row.getObject("id", UUID.class),
                    row.getObject("release_candidate_entry_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getObject("pipeline_run_group_id", UUID.class),
                    row.getObject("dbt_invocation_id", UUID.class),
                    row.getString("airflow_dag_id"),
                    row.getString("airflow_run_id"),
                    row.getString("dbt_selector"),
                    row.getString("target"),
                    row.getString("status"),
                    row.getInt("attempt"),
                    row.getString("artifact_bundle_checksum")
                ),
            candidate.tenantId(),
            candidate.id(),
            candidate.version()
        );
        Set<UUID> candidateScope = candidate.entries().stream().map(EntryView::modelSpecId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> queuedScope = runs.stream().map(QueuedBuildRun::modelSpecId).collect(java.util.stream.Collectors.toSet());
        if (runs.isEmpty() || queuedScope.size() != runs.size() || !candidateScope.containsAll(queuedScope)) {
            throw failure(
                "MODEL_PIPELINE_RUN_INVARIANT_BROKEN",
                "A committed BUILDING candidate must have one durable run per planned BUILD entry",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "candidateVersion", candidate.version(), "runCount", runs.size())
            );
        }
        QueuedBuildRun first = runs.getFirst();
        boolean oneGroup = runs
            .stream()
            .allMatch(run ->
                first.pipelineRunGroupId().equals(run.pipelineRunGroupId()) &&
                first.dbtInvocationId().equals(run.dbtInvocationId()) &&
                first.airflowDagId().equals(run.airflowDagId()) &&
                first.airflowRunId().equals(run.airflowRunId()) &&
                first.artifactBundleChecksum().equals(run.artifactBundleChecksum())
            );
        if (!oneGroup) {
            throw failure(
                "MODEL_PIPELINE_RUN_GROUP_CONFLICT",
                "Candidate pipeline rows do not identify one release build invocation",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "candidateVersion", candidate.version())
            );
        }
        return new QueuedBuildGroup(
            candidate.id(),
            candidate.version(),
            first.pipelineRunGroupId(),
            first.dbtInvocationId(),
            candidate.executionTargetKey(),
            first.airflowDagId(),
            first.airflowRunId(),
            first.artifactBundleChecksum(),
            List.copyOf(runs)
        );
    }

    private static void requireBuildingCandidate(
        CandidateView candidate,
        Instant now
    ) {
        if (
            candidate == null ||
            candidate.status() !=
            com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus.BUILDING
        ) {
            throw failure(
                "MODEL_MATERIALIZATION_BUILDING_REQUIRED",
                "A BUILDING release candidate is required before queueing materialization",
                Kind.CONFLICT,
                Map.of()
            );
        }
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
    }

    private RetryDispatch lockLatestRetryDispatch(
        CandidateView candidate
    ) {
        return jdbcTemplate
            .query(
                """
                select candidate_version, attempt, execution_target_key,
                       airflow_dag_id, artifact_bundle_checksum, status
                  from modeling_materialization_dispatch
                 where tenant_id = ? and candidate_id = ?
                 order by attempt desc
                 limit 1
                 for update
                """,
                (row, rowNumber) ->
                    new RetryDispatch(
                        row.getInt("candidate_version"),
                        row.getInt("attempt"),
                        row.getString("execution_target_key"),
                        row.getString("airflow_dag_id"),
                        row.getString("artifact_bundle_checksum"),
                        row.getString("status")
                    ),
                candidate.tenantId(),
                candidate.id()
            )
            .stream()
            .findFirst()
            .orElseThrow(() ->
                retryReconciliationRequired(
                    candidate,
                    "MISSING_PREVIOUS_ATTEMPT"
                )
            );
    }

    private List<RetryEntryRow> lockRetryEntries(
        CandidateView candidate,
        RetryDispatch previous
    ) {
        return jdbcTemplate.query(
            """
            select e.id as entry_id, e.model_spec_id,
                   e.revision as model_revision,
                   e.checksum as model_checksum,
                   e.implementation_revision,
                   e.implementation_checksum,
                   e.dbt_unique_id,
                   e.target_identifier as entry_target_identifier,
                   e.artifact_bundle_checksum,
                   e.dependency_snapshot_checksum,
                   e.active_claim_key,
                   pr.model_revision as previous_model_revision,
                   pr.model_checksum as previous_model_checksum,
                   pr.implementation_revision as previous_implementation_revision,
                   pr.implementation_checksum as previous_implementation_checksum,
                   pr.dbt_selector,
                   pr.target as previous_target_identifier,
                   pr.artifact_bundle_checksum as previous_group_artifact_checksum,
                   pr.status
              from modeling_model_release_candidate_entry e
              join modeling_pipeline_run pr
                on pr.tenant_id = e.tenant_id
               and pr.release_candidate_id = e.candidate_id
               and pr.release_candidate_entry_id = e.id
               and pr.attempt = ?
               and pr.run_purpose = 'RELEASE_BUILD'
             where e.tenant_id = ? and e.candidate_id = ?
               and e.status = 'BUILDING'
               and e.implementation_revision is not null
               and e.implementation_checksum is not null
               and e.dbt_unique_id is not null
               and e.target_identifier is not null
               and e.artifact_bundle_checksum is not null
               and e.dependency_snapshot_checksum is not null
               and e.active_claim_key is not null
               and pr.status in (
                    'FAILED', 'BLOCKED', 'BUILT', 'DBT_SUCCEEDED',
                    'SKIPPED_DEPENDENCY_FAILED'
               )
             order by e.sort_order, e.id
             for update of e, pr
            """,
            (row, rowNumber) ->
                new RetryEntryRow(
                    row.getObject("entry_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getString("dbt_unique_id"),
                    row.getString("dbt_selector"),
                    row.getString("entry_target_identifier"),
                    row.getString("artifact_bundle_checksum"),
                    row.getString("dependency_snapshot_checksum"),
                    row.getString("active_claim_key"),
                    row.getInt("previous_model_revision"),
                    row.getString("previous_model_checksum"),
                    row.getInt("previous_implementation_revision"),
                    row.getString("previous_implementation_checksum"),
                    row.getString("previous_target_identifier"),
                    row.getString("previous_group_artifact_checksum")
                ),
            previous.attempt(),
            candidate.tenantId(),
            candidate.id()
        );
    }

    private List<RetryEntryRow> lockRematerializationEntries(CandidateView candidate) {
        return jdbcTemplate.query(
            """
            select e.id as entry_id, e.model_spec_id,
                   e.revision as model_revision,
                   e.checksum as model_checksum,
                   e.implementation_revision,
                   e.implementation_checksum,
                   e.dbt_unique_id,
                   e.target_identifier as entry_target_identifier,
                   e.artifact_bundle_checksum,
                   e.dependency_snapshot_checksum,
                   e.active_claim_key,
                   previous.model_revision as previous_model_revision,
                   previous.model_checksum as previous_model_checksum,
                   previous.implementation_revision as previous_implementation_revision,
                   previous.implementation_checksum as previous_implementation_checksum,
                   previous.dbt_selector,
                   previous.target as previous_target_identifier,
                   previous.artifact_bundle_checksum as previous_group_artifact_checksum
              from modeling_model_release_candidate_entry e
              join lateral (
                    select pr.model_revision, pr.model_checksum,
                           pr.implementation_revision, pr.implementation_checksum,
                           pr.dbt_selector, pr.target, pr.artifact_bundle_checksum
                      from modeling_pipeline_run pr
                      join modeling_materialization_dispatch d
                        on d.tenant_id = pr.tenant_id
                       and d.id = pr.pipeline_run_group_id
                       and d.candidate_id = pr.release_candidate_id
                     where pr.tenant_id = e.tenant_id
                       and pr.release_candidate_id = e.candidate_id
                       and pr.release_candidate_entry_id = e.id
                       and pr.run_purpose = 'RELEASE_BUILD'
                       and pr.status = 'BUILT'
                       and d.status = 'COMPLETED'
                     order by d.attempt desc, pr.id desc
                     limit 1
              ) previous on true
             where e.tenant_id = ? and e.candidate_id = ?
               and e.status = 'BUILDING'
               and e.implementation_revision is not null
               and e.implementation_checksum is not null
               and e.dbt_unique_id is not null
               and e.target_identifier is not null
               and e.artifact_bundle_checksum is not null
               and e.dependency_snapshot_checksum is not null
               and e.active_claim_key is not null
             order by e.sort_order, e.id
             for update of e
            """,
            (row, rowNumber) ->
                new RetryEntryRow(
                    row.getObject("entry_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getString("dbt_unique_id"),
                    row.getString("dbt_selector"),
                    row.getString("entry_target_identifier"),
                    row.getString("artifact_bundle_checksum"),
                    row.getString("dependency_snapshot_checksum"),
                    row.getString("active_claim_key"),
                    row.getInt("previous_model_revision"),
                    row.getString("previous_model_checksum"),
                    row.getInt("previous_implementation_revision"),
                    row.getString("previous_implementation_checksum"),
                    row.getString("previous_target_identifier"),
                    row.getString("previous_group_artifact_checksum")
                ),
            candidate.tenantId(),
            candidate.id()
        );
    }

    private static void requireRetrySnapshotCurrent(
        CandidateView candidate,
        RetryDispatch previous,
        List<RetryEntryRow> entries
    ) {
        boolean identityMatches =
            Objects.equals(
                candidate.executionTargetKey(),
                previous.executionTargetKey()
            ) &&
            entries
                .stream()
                .allMatch(entry ->
                    entry.modelRevision() ==
                    entry.previousModelRevision() &&
                    Objects.equals(
                        entry.modelChecksum(),
                        entry.previousModelChecksum()
                    ) &&
                    entry.implementationRevision() ==
                    entry.previousImplementationRevision() &&
                    Objects.equals(
                        entry.implementationChecksum(),
                        entry.previousImplementationChecksum()
                    ) &&
                    Objects.equals(
                        entry.selector(),
                        dbtSelector(entry.dbtUniqueId())
                    ) &&
                    Objects.equals(
                        entry.targetIdentifier(),
                        entry.previousTargetIdentifier()
                    ) &&
                    Objects.equals(
                        previous.artifactBundleChecksum(),
                        entry.previousGroupArtifactChecksum()
                    ) &&
                    Objects.equals(
                        entry.activeClaimKey(),
                        ModelMaterializationClaimKey.derive(
                            candidate.tenantId(),
                            candidate.environment(),
                            entry.modelSpecId()
                        )
                    )
                );
        String aggregate = digest(
            entries
                .stream()
                .sorted(
                    Comparator.comparing(RetryEntryRow::modelSpecId)
                )
                .flatMap(entry ->
                    List.of(
                        entry.modelSpecId().toString(),
                        entry.artifactBundleChecksum(),
                        entry.dependencySnapshotChecksum()
                    )
                        .stream()
                )
                .toList()
        );
        if (
            !identityMatches ||
            !previous.artifactBundleChecksum().equals(aggregate)
        ) {
            throw failure(
                "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
                "The immutable materialization snapshot changed after the previous attempt",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "attempt",
                    previous.attempt()
                )
            );
        }
    }

    private static void requireRematerializationSnapshotCurrent(
        CandidateView candidate,
        RetryDispatch previous,
        List<RetryEntryRow> entries
    ) {
        boolean identityMatches =
            Objects.equals(candidate.executionTargetKey(), previous.executionTargetKey()) &&
            entries
                .stream()
                .allMatch(entry ->
                    entry.modelRevision() == entry.previousModelRevision() &&
                    Objects.equals(entry.modelChecksum(), entry.previousModelChecksum()) &&
                    entry.implementationRevision() == entry.previousImplementationRevision() &&
                    Objects.equals(entry.implementationChecksum(), entry.previousImplementationChecksum()) &&
                    Objects.equals(entry.selector(), dbtSelector(entry.dbtUniqueId())) &&
                    Objects.equals(entry.targetIdentifier(), entry.previousTargetIdentifier()) &&
                    Objects.equals(
                        entry.activeClaimKey(),
                        ModelMaterializationClaimKey.derive(
                            candidate.tenantId(),
                            candidate.environment(),
                            entry.modelSpecId()
                        )
                    )
                );
        if (!identityMatches) {
            throw failure(
                "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
                "The immutable materialization snapshot changed after the previous successful attempt",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "attempt", previous.attempt())
            );
        }
    }

    private QueuedBuildRun insertRetryQueuedRun(
        CandidateView candidate,
        RetryEntryRow entry,
        RetryDispatch previous,
        UUID groupId,
        UUID invocationId,
        String dagRunId,
        int attempt,
        String artifactBundleChecksum,
        String message,
        Instant now
    ) {
        UUID runId = stableUuid(
            "pipeline-run:" +
            candidate.id() +
            ":a" +
            attempt +
            ":" +
            entry.entryId()
        );
        String idempotencyKey =
            "release-build:" +
            candidate.id() +
            ":a" +
            attempt +
            ":" +
            entry.entryId();
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_pipeline_run (
                id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                repair_path, idempotency_key, status, airflow_dag_id, airflow_run_id,
                dbt_selector, target, message, version, created_date, last_modified_date,
                release_candidate_id, release_candidate_entry_id, release_candidate_version,
                pipeline_run_group_id, implementation_revision, implementation_checksum,
                environment, run_purpose, attempt, artifact_bundle_checksum,
                scoped_bundle_checksum, dbt_invocation_id
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', ?, ?, ?, ?, ?, 1, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, 'RELEASE_BUILD', ?, ?, null, ?
            )
            """,
            runId,
            candidate.tenantId(),
            entry.modelSpecId(),
            candidate.planId(),
            entry.modelRevision(),
            entry.modelChecksum(),
            "/modeling/models/" +
            entry.modelSpecId() +
            "?tab=implementation&planId=" +
            candidate.planId(),
            idempotencyKey,
            previous.airflowDagId(),
            dagRunId,
            entry.selector(),
            entry.targetIdentifier(),
            message,
            Timestamp.from(now),
            Timestamp.from(now),
            candidate.id(),
            entry.entryId(),
            candidate.version(),
            groupId,
            entry.implementationRevision(),
            entry.implementationChecksum(),
            candidate.environment(),
            attempt,
            artifactBundleChecksum,
            invocationId
        );
        if (inserted != 1) {
            throw failure(
                "MODEL_PIPELINE_RUN_CREATE_FAILED",
                "Durable release build retry could not be created",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "modelSpecId",
                    entry.modelSpecId(),
                    "attempt",
                    attempt
                )
            );
        }
        return new QueuedBuildRun(
            runId,
            entry.entryId(),
            entry.modelSpecId(),
            groupId,
            invocationId,
            previous.airflowDagId(),
            dagRunId,
            entry.selector(),
            entry.targetIdentifier(),
            "QUEUED",
            attempt,
            artifactBundleChecksum
        );
    }

    private void insertRetryDispatch(
        CandidateView candidate,
        RetryDispatch previous,
        UUID groupId,
        String dagRunId,
        int attempt,
        String artifactBundleChecksum,
        Instant now
    ) {
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_materialization_dispatch (
                id, tenant_id, candidate_id, candidate_version, attempt,
                execution_target_key, airflow_dag_id, airflow_run_id,
                artifact_bundle_checksum, status, dispatch_attempts,
                next_attempt_at, recovered, created_at, last_modified_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, false, ?, ?)
            """,
            groupId,
            candidate.tenantId(),
            candidate.id(),
            candidate.version(),
            attempt,
            previous.executionTargetKey(),
            previous.airflowDagId(),
            dagRunId,
            artifactBundleChecksum,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        if (inserted != 1) {
            throw failure(
                "MODEL_MATERIALIZATION_DISPATCH_CREATE_FAILED",
                "Durable materialization retry dispatch could not be created",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "attempt",
                    attempt
                )
            );
        }
    }

    private static ModelReleaseCandidateException retryReconciliationRequired(
        CandidateView candidate,
        String status
    ) {
        return failure(
            "MODEL_MATERIALIZATION_RETRY_RECONCILIATION_REQUIRED",
            "The previous materialization attempt must be reconciled as failed before retry",
            Kind.CONFLICT,
            Map.of(
                "candidateId",
                candidate.id(),
                "previousStatus",
                status
            )
        );
    }

    private static ModelReleaseCandidateException subsequentAttemptNotReady(
        CandidateView candidate,
        String status,
        String operation
    ) {
        if ("retry".equals(operation)) return retryReconciliationRequired(candidate, status);
        return failure(
            "MODEL_REMATERIALIZATION_PREVIOUS_ATTEMPT_NOT_COMPLETED",
            "The previous materialization attempt must complete before rematerialization",
            Kind.CONFLICT,
            Map.of("candidateId", candidate.id(), "previousStatus", status)
        );
    }

    @Transactional(readOnly = true)
    public CandidateBuildScope loadCandidateBuildScope(
        String tenantId,
        UUID pipelineRunGroupId
    ) {
        if (
            tenantId == null ||
            tenantId.isBlank() ||
            pipelineRunGroupId == null
        ) {
            throw new IllegalArgumentException(
                "tenantId and pipelineRunGroupId are required"
            );
        }
        List<CandidateBuildRow> rows = jdbcTemplate.query(
            """
            select pr.id as pipeline_run_id,
                   pr.model_spec_id, pr.model_revision, pr.model_checksum,
                   pr.implementation_revision, pr.implementation_checksum,
                   pr.release_candidate_id, pr.release_candidate_version,
                   pr.pipeline_run_group_id, pr.artifact_bundle_checksum as group_artifact_checksum,
                   e.implementation_mode, e.dbt_unique_id,
                   e.target_identifier,
                   e.artifact_bundle_checksum as entry_artifact_checksum,
                   e.dependency_snapshot_checksum,
                   c.execution_target_key,
                   sr.snapshot_json -> 'fields' as model_fields
              from modeling_pipeline_run pr
              join modeling_model_release_candidate c
                on c.tenant_id = pr.tenant_id
               and c.id = pr.release_candidate_id
              join modeling_model_release_candidate_entry e
                on e.tenant_id = pr.tenant_id
               and e.id = pr.release_candidate_entry_id
              join modeling_model_spec_revision sr
                on sr.tenant_id = pr.tenant_id
               and sr.model_spec_id = pr.model_spec_id
               and sr.revision = pr.model_revision
               and sr.content_checksum = pr.model_checksum
             where pr.tenant_id = ?
               and pr.pipeline_run_group_id = ?
               and pr.run_purpose = 'RELEASE_BUILD'
             order by pr.model_spec_id, pr.id
            """,
            (row, rowNumber) ->
                new CandidateBuildRow(
                    row.getObject("pipeline_run_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getObject("release_candidate_id", UUID.class),
                    row.getInt("release_candidate_version"),
                    row.getObject("pipeline_run_group_id", UUID.class),
                    row.getString("group_artifact_checksum"),
                    row.getString("implementation_mode"),
                    row.getString("dbt_unique_id"),
                    row.getString("target_identifier"),
                    row.getString("entry_artifact_checksum"),
                    row.getString("dependency_snapshot_checksum"),
                    row.getString("execution_target_key"),
                    row.getString("model_fields")
                ),
            tenantId.trim(),
            pipelineRunGroupId
        );
        if (rows.isEmpty()) {
            throw failure(
                "MODEL_PIPELINE_RUN_GROUP_NOT_FOUND",
                "Candidate build group does not exist",
                Kind.CONFLICT,
                Map.of("pipelineRunGroupId", pipelineRunGroupId)
            );
        }
        CandidateBuildRow first = rows.getFirst();
        boolean sameGroup = rows
            .stream()
            .allMatch(row ->
                first.candidateId().equals(row.candidateId()) &&
                first.candidateVersion() == row.candidateVersion() &&
                first.pipelineRunGroupId().equals(
                    row.pipelineRunGroupId()
                ) &&
                first.groupArtifactChecksum().equals(
                    row.groupArtifactChecksum()
                ) &&
                first.executionTargetKey().equals(
                    row.executionTargetKey()
                )
            );
        if (!sameGroup) {
            throw failure(
                "MODEL_PIPELINE_RUN_GROUP_CONFLICT",
                "Candidate build rows disagree on immutable group identity",
                Kind.CONFLICT,
                Map.of("pipelineRunGroupId", pipelineRunGroupId)
            );
        }

        List<CandidateBuildEntry> entries = rows
            .stream()
            .map(row -> loadCandidateBuildEntry(tenantId.trim(), row))
            .toList();
        String aggregate = digest(
            rows
                .stream()
                .sorted(
                    Comparator.comparing(
                        CandidateBuildRow::modelSpecId
                    )
                )
                .flatMap(row ->
                    List.of(
                        row.modelSpecId().toString(),
                        row.entryArtifactChecksum(),
                        row.dependencySnapshotChecksum()
                    )
                        .stream()
                )
                .toList()
        );
        if (!aggregate.equals(first.groupArtifactChecksum())) {
            throw failure(
                "MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT",
                "Candidate artifact group checksum has drifted",
                Kind.CONFLICT,
                Map.of("pipelineRunGroupId", pipelineRunGroupId)
            );
        }
        return new CandidateBuildScope(
            tenantId.trim(),
            first.candidateId(),
            first.candidateVersion(),
            first.pipelineRunGroupId(),
            first.executionTargetKey(),
            first.groupArtifactChecksum(),
            entries
        );
    }

    /**
     * Resolves logical ModelSpec dependencies outside the current candidate to immutable,
     * revision-bound dbt proxy nodes. The latest observation in the same environment and
     * execution target must still represent the exact pinned model and implementation.
     */
    @Transactional(readOnly = true)
    public List<BuildArtifact> loadPinnedDependencyArtifacts(
        CandidateBuildScope scope
    ) {
        if (scope == null || scope.entries().isEmpty()) {
            throw new IllegalArgumentException("candidate build scope is required");
        }
        Map<UUID, CandidateBuildEntry> selected = new LinkedHashMap<>();
        Map<String, UUID> selectorOwners = new LinkedHashMap<>();
        for (CandidateBuildEntry entry : scope.entries()) {
            selected.put(entry.modelSpecId(), entry);
            selectorOwners.put(dbtSelector(entry.dbtUniqueId()), entry.modelSpecId());
        }

        Map<UUID, Integer> pinnedRevisions = new LinkedHashMap<>();
        for (CandidateBuildEntry entry : scope.entries()) {
            List<String> snapshots = jdbcTemplate.query(
                """
                select snapshot_json::text
                  from modeling_model_spec_revision
                 where tenant_id = ? and model_spec_id = ?
                   and revision = ? and content_checksum = ?
                """,
                (row, rowNumber) -> row.getString(1),
                scope.tenantId(),
                entry.modelSpecId(),
                entry.modelRevision(),
                entry.modelChecksum()
            );
            if (snapshots.size() != 1) {
                throw failure(
                    "MODEL_UPSTREAM_PIN_STALE",
                    "The selected model revision snapshot is unavailable",
                    Kind.CONFLICT,
                    Map.of("modelSpecId", entry.modelSpecId())
                );
            }
            JsonNode snapshot = json(
                snapshots.getFirst(),
                "model snapshot",
                entry.modelSpecId()
            );
            collectPinnedRevisionRefs(
                snapshot.path("dependsOn"),
                pinnedRevisions,
                entry.modelSpecId()
            );
            collectPinnedRevisionRefs(
                snapshot.path("dimensionRefs"),
                pinnedRevisions,
                entry.modelSpecId()
            );
        }

        List<BuildArtifact> artifacts = new ArrayList<>();
        pinnedRevisions.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(pin -> {
                CandidateBuildEntry selectedUpstream = selected.get(pin.getKey());
                if (selectedUpstream != null) {
                    if (selectedUpstream.modelRevision() != pin.getValue()) {
                        throw failure(
                            "MODEL_UPSTREAM_PIN_CONFLICT",
                            "A candidate model does not match its pinned upstream revision",
                            Kind.CONFLICT,
                            Map.of("modelSpecId", pin.getKey(), "revision", pin.getValue())
                        );
                    }
                    return;
                }
                PinnedDependencyRow dependency = loadPinnedDependency(
                    scope,
                    pin.getKey(),
                    pin.getValue()
                );
                String selector = dbtSelector(dependency.dbtUniqueId());
                UUID existingOwner = selectorOwners.putIfAbsent(
                    selector,
                    dependency.modelSpecId()
                );
                if (
                    existingOwner != null &&
                    !existingOwner.equals(dependency.modelSpecId())
                ) {
                    throw failure(
                        "MODEL_UPSTREAM_SELECTOR_CONFLICT",
                        "Pinned logical models resolve to the same dbt selector",
                        Kind.CONFLICT,
                        Map.of("selector", selector)
                    );
                }
                String content = pinnedDependencySql(dependency);
                artifacts.add(
                    new BuildArtifact(
                        "models/.dts-pinned-dependencies/" + selector + ".sql",
                        sha256(content),
                        content
                    )
                );
            });
        return List.copyOf(artifacts);
    }

    private void collectPinnedRevisionRefs(
        JsonNode refs,
        Map<UUID, Integer> pinnedRevisions,
        UUID downstreamModelSpecId
    ) {
        if (refs == null || refs.isMissingNode() || refs.isNull()) return;
        if (!refs.isArray()) {
            throw failure(
                "MODEL_UPSTREAM_PIN_INVALID",
                "Logical model dependencies are not a revision-ref array",
                Kind.CONFLICT,
                Map.of("modelSpecId", downstreamModelSpecId)
            );
        }
        refs.forEach(ref -> {
            UUID modelSpecId;
            int revision = ref.path("revision").asInt(0);
            try {
                modelSpecId = UUID.fromString(
                    ref.path("modelSpecId").asText("").trim()
                );
            } catch (RuntimeException invalid) {
                throw failure(
                    "MODEL_UPSTREAM_PIN_INVALID",
                    "Logical model dependency contains an invalid ModelSpec id",
                    Kind.CONFLICT,
                    Map.of("modelSpecId", downstreamModelSpecId)
                );
            }
            if (revision < 1) {
                throw failure(
                    "MODEL_UPSTREAM_PIN_INVALID",
                    "Logical model dependency contains an invalid revision",
                    Kind.CONFLICT,
                    Map.of("modelSpecId", downstreamModelSpecId)
                );
            }
            Integer existing = pinnedRevisions.putIfAbsent(
                modelSpecId,
                revision
            );
            if (existing != null && existing != revision) {
                throw failure(
                    "MODEL_UPSTREAM_PIN_CONFLICT",
                    "Candidate entries pin different revisions of the same upstream model",
                    Kind.CONFLICT,
                    Map.of("modelSpecId", modelSpecId)
                );
            }
        });
    }

    private PinnedDependencyRow loadPinnedDependency(
        CandidateBuildScope scope,
        UUID modelSpecId,
        int revision
    ) {
        List<PinnedDependencyRow> rows = jdbcTemplate.query(
            """
            select sr.model_spec_id, sr.revision, sr.content_checksum,
                   i.implementation_revision,
                   i.current_implementation_checksum,
                   i.dbt_unique_id,
                   observed.model_revision as observed_model_revision,
                   observed.model_checksum as observed_model_checksum,
                   observed.implementation_revision as observed_implementation_revision,
                   observed.implementation_checksum as observed_implementation_checksum,
                   observed.adapter as observed_adapter,
                   current_candidate.adapter as target_adapter,
                   observed.database_name, observed.schema_name,
                   observed.identifier, observed.verified,
                   observed.relation_exists
              from modeling_model_release_candidate current_candidate
              join modeling_model_spec_revision sr
                on sr.tenant_id = current_candidate.tenant_id
               and sr.model_spec_id = ? and sr.revision = ?
              join modeling_model_implementation i
                on i.tenant_id = sr.tenant_id
               and i.model_spec_id = sr.model_spec_id
               and i.model_revision = sr.revision
               and i.model_checksum = sr.content_checksum
               and i.status = 'ACTIVE'
              left join lateral (
                    select observation.*
                      from modeling_physical_relation_observation observation
                      join modeling_model_release_candidate observed_candidate
                        on observed_candidate.tenant_id = observation.tenant_id
                       and observed_candidate.id = observation.release_candidate_id
                     where observation.tenant_id = current_candidate.tenant_id
                       and observation.model_spec_id = sr.model_spec_id
                       and observation.run_purpose = 'RELEASE_BUILD'
                       and observed_candidate.environment = current_candidate.environment
                       and observed_candidate.execution_target_key = current_candidate.execution_target_key
                     order by observation.observed_at desc,
                              observation.created_date desc,
                              observation.id desc
                     limit 1
              ) observed on true
             where current_candidate.tenant_id = ?
               and current_candidate.id = ?
               and current_candidate.version = ?
               and current_candidate.execution_target_key = ?
            """,
            (row, rowNumber) ->
                new PinnedDependencyRow(
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("revision"),
                    row.getString("content_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("current_implementation_checksum"),
                    row.getString("dbt_unique_id"),
                    row.getObject("observed_model_revision", Integer.class),
                    row.getString("observed_model_checksum"),
                    row.getObject("observed_implementation_revision", Integer.class),
                    row.getString("observed_implementation_checksum"),
                    row.getString("observed_adapter"),
                    row.getString("target_adapter"),
                    row.getString("database_name"),
                    row.getString("schema_name"),
                    row.getString("identifier"),
                    row.getObject("verified", Boolean.class),
                    row.getObject("relation_exists", Boolean.class)
                ),
            modelSpecId,
            revision,
            scope.tenantId(),
            scope.candidateId(),
            scope.candidateVersion(),
            scope.executionTargetKey()
        );
        if (rows.size() != 1) {
            throw failure(
                "MODEL_UPSTREAM_PIN_STALE",
                "The pinned upstream revision has no current active implementation",
                Kind.CONFLICT,
                Map.of("modelSpecId", modelSpecId, "revision", revision)
            );
        }
        PinnedDependencyRow row = rows.getFirst();
        if (row.observedModelRevision() == null) {
            throw failure(
                "MODEL_UPSTREAM_MATERIALIZATION_REQUIRED",
                "The pinned upstream model has not been materialized in this environment",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", modelSpecId, "revision", revision)
            );
        }
        boolean exactObservation =
            row.modelRevision() == row.observedModelRevision() &&
            row.implementationRevision() == row.observedImplementationRevision() &&
            row.modelChecksum().equals(row.observedModelChecksum()) &&
            row.implementationChecksum().equals(row.observedImplementationChecksum()) &&
            Boolean.TRUE.equals(row.verified()) &&
            Boolean.TRUE.equals(row.relationExists()) &&
            Objects.equals(row.targetAdapter(), row.observedAdapter());
        if (!exactObservation) {
            throw failure(
                "MODEL_UPSTREAM_PIN_STALE",
                "The latest upstream relation does not match the pinned model and implementation",
                Kind.CONFLICT,
                Map.of("modelSpecId", modelSpecId, "revision", revision)
            );
        }
        if (
            !CHECKSUM.matcher(row.modelChecksum()).matches() ||
            !CHECKSUM.matcher(row.implementationChecksum()).matches() ||
            row.dbtUniqueId() == null ||
            !DBT_UNIQUE_ID.matcher(row.dbtUniqueId()).matches() ||
            !IDENTIFIER.matcher(Objects.toString(row.databaseName(), "")).matches() ||
            !IDENTIFIER.matcher(Objects.toString(row.schemaName(), "")).matches() ||
            !IDENTIFIER.matcher(Objects.toString(row.identifier(), "")).matches()
        ) {
            throw failure(
                "MODEL_UPSTREAM_RELATION_INVALID",
                "The pinned upstream relation identity is invalid",
                Kind.CONFLICT,
                Map.of("modelSpecId", modelSpecId)
            );
        }
        return row;
    }

    private static String pinnedDependencySql(PinnedDependencyRow row) {
        return (
            "{{ config(materialized='ephemeral', tags=['dts-pinned-dependency'], " +
            "meta={'modelSpecId': '%s', 'modelRevision': %d, 'modelChecksum': '%s', " +
            "'implementationRevision': %d, 'implementationChecksum': '%s'}) }}\n" +
            "select * from \"%s\".\"%s\".\"%s\"\n"
        ).formatted(
            row.modelSpecId(),
            row.modelRevision(),
            row.modelChecksum(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.databaseName(),
            row.schemaName(),
            row.identifier()
        );
    }

    private List<BuildEntryRow> lockCurrentBuildEntries(CandidateView candidate) {
        return jdbcTemplate.query(
            """
            select e.id as entry_id, e.model_spec_id, e.revision as model_revision,
                   e.checksum as model_checksum, e.implementation_mode,
                   i.id as implementation_id, i.implementation_revision,
                   i.current_implementation_checksum as implementation_checksum,
                   i.ownership, i.project_key, i.dbt_unique_id, i.status as implementation_status,
                   i.input_mode, i.inputs_json::text as inputs_json,
                   i.settings_json::text as settings_json, i.materialization
              from modeling_model_release_candidate c
              join modeling_model_release_candidate_entry e
                on e.tenant_id = c.tenant_id and e.candidate_id = c.id
              join modeling_model_implementation i
                on i.tenant_id = e.tenant_id
               and i.plan_id = e.plan_id
               and i.model_spec_id = e.model_spec_id
               and i.model_revision = e.revision
               and i.model_checksum = e.checksum
               and i.ownership = e.implementation_mode
             where c.tenant_id = ? and c.id = ? and c.version = ? and c.status = 'BUILDING'
               and e.status = 'BUILDING'
               and i.status = 'ACTIVE'
             order by e.sort_order, e.id
             for update of c, e, i
            """,
            (row, rowNumber) ->
                new BuildEntryRow(
                    row.getObject("entry_id", UUID.class),
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getString("implementation_mode"),
                    row.getObject("implementation_id", UUID.class),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getString("ownership"),
                    row.getString("project_key"),
                    row.getString("dbt_unique_id"),
                    row.getString("input_mode"),
                    row.getString("inputs_json"),
                    row.getString("settings_json"),
                    row.getString("materialization")
                ),
            candidate.tenantId(),
            candidate.id(),
            candidate.version()
        );
    }

    private PreparedEntry prepareEntry(
        CandidateView candidate,
        BuildEntryRow row,
        Map<UUID, BuildEntryRow> candidateModels
    ) {
        requireChecksum(row.modelChecksum(), "model checksum", row.modelSpecId());
        requireChecksum(row.implementationChecksum(), "implementation checksum", row.modelSpecId());
        if (
            row.implementationRevision() < 1 ||
            !row.implementationMode().equals(row.ownership()) ||
            row.dbtUniqueId() == null ||
            !DBT_UNIQUE_ID.matcher(row.dbtUniqueId()).matches() ||
            row.projectKey() == null ||
            !row.projectKey().equals(row.dbtUniqueId().split("\\.", 3)[1]) ||
            !List.of("table", "view", "incremental").contains(row.materialization()) ||
            !List.of("PHYSICAL_ASSET", "UPSTREAM_MODEL", "GENERATED").contains(row.inputMode())
        ) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Current implementation identity is incomplete or inconsistent",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        JsonNode settings = json(row.settingsJson(), "settings", row.modelSpecId());
        String targetIdentifier = resolveTargetIdentifier(row, settings);
        if (!IDENTIFIER.matcher(targetIdentifier).matches()) {
            throw failure(
                "IMPLEMENTATION_TARGET_REQUIRED",
                "Current implementation requires a valid targetPhysicalName",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        List<ArtifactRow> artifacts = loadArtifacts(candidate.tenantId(), row);
        if (
            artifacts
                .stream()
                .noneMatch(artifact ->
                    "SQL".equals(artifact.artifactType()) &&
                    row.dbtUniqueId().equals(artifact.dbtUniqueId())
                )
        ) {
            throw failure(
                "MATERIALIZATION_ARTIFACT_MISSING",
                "Current implementation has no selected COMPILED SQL artifact",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", row.modelSpecId(), "dbtUniqueId", row.dbtUniqueId())
            );
        }
        String artifactBundleChecksum = digest(
            artifacts
                .stream()
                .flatMap(artifact ->
                    List.of(
                        artifact.artifactKey(),
                        artifact.path(),
                        artifact.contentChecksum()
                    )
                        .stream()
                )
                .toList()
        );
        validateInputs(candidate, row, candidateModels);
        String dependencySnapshotChecksum = digest(
            List.of(
                row.inputMode(),
                canonicalJson(row.inputsJson(), "inputs", row.modelSpecId()),
                canonicalJson(row.settingsJson(), "settings", row.modelSpecId()),
                artifactBundleChecksum
            )
        );
        String activeClaimKey = ModelMaterializationClaimKey.derive(
            candidate.tenantId(),
            candidate.environment(),
            row.modelSpecId()
        );
        return new PreparedEntry(
            row,
            dbtSelector(row.dbtUniqueId()),
            targetIdentifier,
            artifactBundleChecksum,
            dependencySnapshotChecksum,
            activeClaimKey
        );
    }

    private String resolveTargetIdentifier(BuildEntryRow row, JsonNode settings) {
        String configuredTarget = settings.path("targetPhysicalName").asText("").trim();
        JsonNode inputs = json(row.inputsJson(), "inputs", row.modelSpecId());
        List<String> visualTargets = new ArrayList<>();
        if (inputs.isArray()) {
            inputs.forEach(input -> {
                String visualTarget = input
                    .path("config")
                    .path("visualImplementation")
                    .path("settings")
                    .path("targetPhysicalName")
                    .asText("")
                    .trim();
                if (!visualTarget.isBlank() && !visualTargets.contains(visualTarget)) {
                    visualTargets.add(visualTarget);
                }
            });
        }
        if (visualTargets.isEmpty()) return configuredTarget;
        if (visualTargets.size() > 1) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Current implementation contains conflicting visual targetPhysicalName values",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        String visualTarget = visualTargets.getFirst();
        if (
            configuredTarget.equals(visualTarget) ||
            configuredTarget.equals(dbtSelector(row.dbtUniqueId()))
        ) {
            return visualTarget;
        }
        throw failure(
            "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
            "Current implementation targetPhysicalName conflicts with its visual authoring snapshot",
            Kind.UNPROCESSABLE,
            Map.of("modelSpecId", row.modelSpecId())
        );
    }

    private List<ArtifactRow> loadArtifacts(String tenantId, BuildEntryRow row) {
        List<ArtifactRow> artifacts = jdbcTemplate.query(
            """
            select a.artifact_key, a.path, a.content_checksum, a.content,
                   a.artifact_type, a.node_kind, a.dbt_unique_id
              from modeling_dbt_artifact a
              join modeling_model_spec s on s.id = a.model_spec_id
             where s.tenant_id = ? and a.model_spec_id = ? and a.revision = ?
               and a.model_checksum = ? and a.implementation_revision = ?
               and a.ownership = ?
               and (
                    a.status = 'COMPILED'
                    or (
                        a.status = 'IMPORTED'
                        and a.node_kind in ('STG', 'EPHEMERAL')
                        and lower(a.path) like 'models/%.sql'
                    )
               )
               and (
                    a.artifact_type in ('SQL', 'TEST', 'STG_SQL')
                    or (
                        a.artifact_type = 'SCHEMA'
                        and (
                            lower(a.path) like '%.yml'
                            or lower(a.path) like '%.yaml'
                        )
                    )
               )
             order by a.artifact_key, a.id
            """,
            (result, rowNumber) ->
                new ArtifactRow(
                    result.getString("artifact_key"),
                    result.getString("path"),
                    result.getString("content_checksum"),
                    result.getString("content"),
                    result.getString("artifact_type"),
                    result.getString("node_kind"),
                    result.getString("dbt_unique_id")
                ),
            tenantId,
            row.modelSpecId(),
            row.modelRevision(),
            row.modelChecksum(),
            row.implementationRevision(),
            row.ownership()
        );
        if (artifacts.isEmpty()) {
            throw failure(
                "MATERIALIZATION_ARTIFACT_MISSING",
                "No current COMPILED lifecycle artifact exists",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        for (ArtifactRow artifact : artifacts) {
            if (
                artifact.artifactKey() == null ||
                artifact.path() == null ||
                !CHECKSUM.matcher(String.valueOf(artifact.contentChecksum())).matches() ||
                artifact.content() == null ||
                !artifact.contentChecksum().equals(sha256(artifact.content()))
            ) {
                throw failure(
                    "MATERIALIZATION_ARTIFACT_STALE",
                    "Lifecycle artifact content does not match its checksum",
                    Kind.UNPROCESSABLE,
                    Map.of("modelSpecId", row.modelSpecId())
                );
            }
        }
        return List.copyOf(artifacts);
    }

    private void validateInputs(
        CandidateView candidate,
        BuildEntryRow row,
        Map<UUID, BuildEntryRow> candidateModels
    ) {
        JsonNode inputs = json(row.inputsJson(), "inputs", row.modelSpecId());
        if (!inputs.isArray() || inputs.isEmpty()) {
            throw failure(
                "MODEL_IMPLEMENTATION_INPUT_REQUIRED",
                "Current implementation inputs are empty",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        if (!"UPSTREAM_MODEL".equals(row.inputMode())) return;
        for (JsonNode input : inputs) {
            UUID upstreamId;
            try {
                upstreamId = UUID.fromString(input.path("modelSpecId").asText());
            } catch (RuntimeException invalid) {
                throw failure(
                    "MODEL_UPSTREAM_PIN_INVALID",
                    "Upstream model pin is invalid",
                    Kind.UNPROCESSABLE,
                    Map.of("modelSpecId", row.modelSpecId())
                );
            }
            int revision = input.path("revision").asInt(0);
            String checksum = input.path("checksum").asText("");
            BuildEntryRow sameCandidate = candidateModels.get(upstreamId);
            if (sameCandidate != null) {
                if (
                    sameCandidate.modelRevision() != revision ||
                    !sameCandidate.modelChecksum().equals(checksum)
                ) {
                    throw failure(
                        "MODEL_UPSTREAM_PIN_STALE",
                        "Upstream pin does not match the model in this candidate",
                        Kind.UNPROCESSABLE,
                        Map.of("modelSpecId", row.modelSpecId(), "upstreamModelSpecId", upstreamId)
                    );
                }
                continue;
            }
            Boolean published = jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                      from modeling_model_spec s
                      join modeling_model_spec_revision revision
                        on revision.model_spec_id = s.id
                       and revision.revision = ?
                       and revision.content_checksum = ?
                     where s.tenant_id = ? and s.id = ?
                       and s.contract_version = 2
                       and exists (
                           select 1
                             from modeling_model_lifecycle_event event
                            where event.tenant_id = s.tenant_id
                              and event.model_spec_id = s.id
                              and event.model_revision = revision.revision
                              and event.model_checksum = revision.content_checksum
                              and event.event_type = 'RELEASE'
                              and event.status = 'PUBLISHED'
                              and event.details_json ->> 'executionTargetKey' = ?
                              and upper(event.details_json ->> 'environment') = upper(?)
                       )
                )
                """,
                Boolean.class,
                revision,
                checksum,
                candidate.tenantId(),
                upstreamId,
                candidate.executionTargetKey(),
                candidate.environment()
            );
            if (!Boolean.TRUE.equals(published)) {
                throw failure(
                    "MODEL_UPSTREAM_NOT_PUBLISHED",
                    "Upstream model must be current and published or included in the same candidate",
                    Kind.UNPROCESSABLE,
                    Map.of("modelSpecId", row.modelSpecId(), "upstreamModelSpecId", upstreamId)
                );
            }
        }
    }

    private void persistEntrySnapshot(CandidateView candidate, PreparedEntry entry) {
        BuildEntryRow row = entry.row();
        int updated = jdbcTemplate.update(
            """
            update modeling_model_release_candidate_entry
               set implementation_id = ?, implementation_revision = ?,
                   implementation_checksum = ?, dbt_unique_id = ?, target_identifier = ?,
                   artifact_bundle_checksum = ?, dependency_snapshot_checksum = ?,
                   active_claim_key = ?
             where tenant_id = ? and candidate_id = ? and id = ? and status = 'BUILDING'
               and implementation_revision is null and implementation_checksum is null
               and (active_claim_key is null or active_claim_key = ?)
            """,
            row.implementationId(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.dbtUniqueId(),
            entry.targetIdentifier(),
            entry.artifactBundleChecksum(),
            entry.dependencySnapshotChecksum(),
            entry.activeClaimKey(),
            candidate.tenantId(),
            candidate.id(),
            row.entryId(),
            entry.activeClaimKey()
        );
        if (updated != 1) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_CONFLICT",
                "Candidate entry snapshot could not be locked",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "modelSpecId", row.modelSpecId())
            );
        }
    }

    private QueuedBuildRun insertQueuedRun(
        CandidateView candidate,
        PreparedEntry entry,
        String candidateArtifactChecksum,
        ExecutionTarget executionTarget,
        UUID groupId,
        UUID invocationId,
        String dagRunId,
        Instant now
    ) {
        BuildEntryRow row = entry.row();
        UUID runId = stableUuid(
            "pipeline-run:" +
            candidate.id() +
            ":v" +
            candidate.version() +
            ":a1:" +
            row.entryId()
        );
        String idempotencyKey =
            "release-build:" +
            candidate.id() +
            ":v" +
            candidate.version() +
            ":a1:" +
            row.entryId();
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_pipeline_run (
                id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                repair_path, idempotency_key, status, airflow_dag_id, airflow_run_id,
                dbt_selector, target, message, version, created_date, last_modified_date,
                release_candidate_id, release_candidate_entry_id, release_candidate_version,
                pipeline_run_group_id, implementation_revision, implementation_checksum,
                environment, run_purpose, attempt, artifact_bundle_checksum,
                scoped_bundle_checksum, dbt_invocation_id
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', ?, ?, ?, ?, ?, 1, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, 'RELEASE_BUILD', 1, ?, null, ?
            )
            """,
            runId,
            candidate.tenantId(),
            row.modelSpecId(),
            candidate.planId(),
            row.modelRevision(),
            row.modelChecksum(),
            "/modeling/models/" +
            row.modelSpecId() +
            "?tab=implementation&planId=" +
            candidate.planId(),
            idempotencyKey,
            executionTarget.releaseBuildDagId(),
            dagRunId,
            entry.selector(),
            entry.targetIdentifier(),
            "Release candidate build queued",
            Timestamp.from(now),
            Timestamp.from(now),
            candidate.id(),
            row.entryId(),
            candidate.version(),
            groupId,
            row.implementationRevision(),
            row.implementationChecksum(),
            candidate.environment(),
            candidateArtifactChecksum,
            invocationId
        );
        if (inserted != 1) {
            throw failure(
                "MODEL_PIPELINE_RUN_CREATE_FAILED",
                "Durable release build run could not be created",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "modelSpecId", row.modelSpecId())
            );
        }
        return new QueuedBuildRun(
            runId,
            row.entryId(),
            row.modelSpecId(),
            groupId,
            invocationId,
            executionTarget.releaseBuildDagId(),
            dagRunId,
            entry.selector(),
            entry.targetIdentifier(),
            "QUEUED",
            1,
            candidateArtifactChecksum
        );
    }

    private void insertDispatch(
        CandidateView candidate,
        ExecutionTarget target,
        UUID groupId,
        String dagRunId,
        String artifactBundleChecksum,
        Instant now
    ) {
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_materialization_dispatch (
                id, tenant_id, candidate_id, candidate_version, attempt,
                execution_target_key, airflow_dag_id, airflow_run_id,
                artifact_bundle_checksum, status, dispatch_attempts,
                next_attempt_at, recovered, created_at, last_modified_at
            ) values (?, ?, ?, ?, 1, ?, ?, ?, ?, 'PENDING', 0, ?, false, ?, ?)
            """,
            groupId,
            candidate.tenantId(),
            candidate.id(),
            candidate.version(),
            target.executionTargetKey(),
            target.releaseBuildDagId(),
            dagRunId,
            artifactBundleChecksum,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        if (inserted != 1) {
            throw failure(
                "MODEL_MATERIALIZATION_DISPATCH_CREATE_FAILED",
                "Durable materialization dispatch could not be created",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id())
            );
        }
    }

    private CandidateBuildEntry loadCandidateBuildEntry(
        String tenantId,
        CandidateBuildRow row
    ) {
        List<ArtifactRow> artifacts = jdbcTemplate.query(
            """
            select artifact_key, path, content_checksum, content,
                   artifact_type, node_kind, dbt_unique_id
              from modeling_dbt_artifact
             where model_spec_id = ? and revision = ?
               and model_checksum = ? and implementation_revision = ?
               and ownership = ?
               and (
                    status = 'COMPILED'
                    or (
                        status = 'IMPORTED'
                        and node_kind in ('STG', 'EPHEMERAL')
                        and lower(path) like 'models/%.sql'
                    )
               )
               and (
                    artifact_type in ('SQL', 'TEST', 'STG_SQL')
                    or (
                        artifact_type = 'SCHEMA'
                        and (
                            lower(path) like '%.yml'
                            or lower(path) like '%.yaml'
                        )
                    )
               )
             order by artifact_key, id
            """,
            (result, rowNumber) ->
                new ArtifactRow(
                    result.getString("artifact_key"),
                    result.getString("path"),
                    result.getString("content_checksum"),
                    result.getString("content"),
                    result.getString("artifact_type"),
                    result.getString("node_kind"),
                    result.getString("dbt_unique_id")
                ),
            row.modelSpecId(),
            row.modelRevision(),
            row.modelChecksum(),
            row.implementationRevision(),
            row.implementationMode()
        );
        if (artifacts.isEmpty()) {
            throw failure(
                "MATERIALIZATION_ARTIFACT_MISSING",
                "Candidate build artifact is unavailable",
                Kind.CONFLICT,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        for (ArtifactRow artifact : artifacts) {
            if (
                artifact.content() == null ||
                artifact.contentChecksum() == null ||
                !artifact.contentChecksum().equals(
                    sha256(artifact.content())
                )
            ) {
                throw failure(
                    "MATERIALIZATION_ARTIFACT_STALE",
                    "Candidate build artifact content has drifted",
                    Kind.CONFLICT,
                    Map.of("modelSpecId", row.modelSpecId())
                );
            }
        }
        String actualArtifactChecksum = digest(
            artifacts
                .stream()
                .flatMap(artifact ->
                    List.of(
                        artifact.artifactKey(),
                        artifact.path(),
                        artifact.contentChecksum()
                    )
                        .stream()
                )
                .toList()
        );
        if (!actualArtifactChecksum.equals(row.entryArtifactChecksum())) {
            throw failure(
                "MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT",
                "Candidate entry artifact checksum has drifted",
                Kind.CONFLICT,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        FrozenBundleSnapshot frozenBundle = loadFrozenBundleSnapshot(tenantId, row);
        List<BuildArtifact> executableArtifacts = FrozenDbtBundleArtifactRecovery.recover(
            objectMapper,
            row.modelSpecId(),
            row.dbtUniqueId(),
            frozenBundle.inputsJson(),
            frozenBundle.evidence(),
            artifacts
                .stream()
                .map(artifact ->
                    new BuildArtifact(
                        artifact.path(),
                        artifact.contentChecksum(),
                        artifact.content()
                    )
                )
                .toList()
        );
        return new CandidateBuildEntry(
            row.pipelineRunId(),
            row.modelSpecId(),
            row.modelRevision(),
            row.modelChecksum(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.implementationMode(),
            row.dbtUniqueId(),
            row.targetIdentifier(),
            executableArtifacts,
            pinnedModelColumns(
                row.modelFieldsJson(),
                row.modelSpecId()
            )
        );
    }

    private FrozenBundleSnapshot loadFrozenBundleSnapshot(
        String tenantId,
        CandidateBuildRow row
    ) {
        List<FrozenBundleSnapshot> bundles = jdbcTemplate.query(
            """
            select ir.inputs_json,
                   a.project_key as artifact_project_key,
                   a.dbt_unique_id as artifact_dbt_unique_id,
                   a.path as artifact_path,
                   a.content_checksum as artifact_content_checksum,
                   a.content as artifact_content
              from modeling_model_release_candidate_entry e
              join modeling_model_implementation_revision ir
                on ir.tenant_id = e.tenant_id
               and ir.implementation_id = e.implementation_id
               and ir.revision = e.implementation_revision
               and ir.content_checksum = e.implementation_checksum
               and ir.ownership = e.implementation_mode
              left join modeling_dbt_artifact a
                on a.model_spec_id = e.model_spec_id
               and a.revision = e.revision
               and a.model_checksum = e.checksum
               and a.implementation_revision = e.implementation_revision
               and a.ownership = e.implementation_mode
               and a.dbt_unique_id = e.dbt_unique_id
               and a.artifact_type = 'CONFIG'
               and a.path = '.dts/dbt-project-bundle.json'
               and a.status in ('COMPILED', 'IMPORTED')
             where e.tenant_id = ?
               and e.candidate_id = ?
               and e.model_spec_id = ?
               and e.revision = ?
               and e.checksum = ?
               and e.implementation_revision = ?
               and e.implementation_checksum = ?
               and e.implementation_mode = ?
               and e.dbt_unique_id = ?
            """,
            (result, rowNumber) -> {
                String artifactPath = result.getString("artifact_path");
                return new FrozenBundleSnapshot(
                    result.getString("inputs_json"),
                    artifactPath == null
                        ? null
                        : new FrozenDbtBundleArtifactRecovery.FrozenBundleEvidence(
                            result.getString("artifact_project_key"),
                            result.getString("artifact_dbt_unique_id"),
                            artifactPath,
                            result.getString("artifact_content_checksum"),
                            result.getString("artifact_content")
                        )
                );
            },
            tenantId,
            row.candidateId(),
            row.modelSpecId(),
            row.modelRevision(),
            row.modelChecksum(),
            row.implementationRevision(),
            row.implementationChecksum(),
            row.implementationMode(),
            row.dbtUniqueId()
        );
        if (bundles.size() != 1) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Candidate implementation bundle snapshot is unavailable or ambiguous",
                Kind.CONFLICT,
                Map.of("modelSpecId", row.modelSpecId())
            );
        }
        return bundles.getFirst();
    }

    private ExecutionTarget executionTarget() {
        if (!properties.isEnabled()) {
            throw failure(
                "MODEL_MATERIALIZATION_DISABLED",
                "Model materialization is disabled",
                Kind.CONFLICT,
                Map.of()
            );
        }
        String executionTargetKey = requiredProperty(
            properties.getExecutionTargetKey(),
            "execution target key"
        );
        String adapter = requiredProperty(properties.getAdapter(), "adapter");
        String profileKey = requiredProperty(properties.getProfileKey(), "profile key");
        String targetName = requiredProperty(properties.getTargetName(), "target name");
        String dagId = requiredProperty(properties.getReleaseBuildDagId(), "release build DAG id");
        if (!"postgres".equals(adapter)) {
            throw failure(
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                "Only the verified PostgreSQL execution target is enabled",
                Kind.UNPROCESSABLE,
                Map.of("adapter", adapter)
            );
        }
        return new ExecutionTarget(
            executionTargetKey,
            adapter,
            profileKey,
            targetName,
            dagId
        );
    }

    private JsonNode json(String value, String name, UUID modelSpecId) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception invalid) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Current implementation " + name + " are invalid",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", modelSpecId)
            );
        }
    }

    private List<String> pinnedModelColumns(
        String value,
        UUID modelSpecId
    ) {
        JsonNode fields = json(value, "fields", modelSpecId);
        if (!fields.isArray() || fields.isEmpty()) {
            throw failure(
                "MODEL_FIELD_CONTRACT_INVALID",
                "Pinned model revision has no field contract",
                Kind.CONFLICT,
                Map.of("modelSpecId", modelSpecId)
            );
        }
        List<String> columns = new ArrayList<>();
        fields.forEach(field -> {
            String name = field.path("name").asText("").trim();
            if (!IDENTIFIER.matcher(name).matches()) {
                throw failure(
                    "MODEL_FIELD_CONTRACT_INVALID",
                    "Pinned model revision contains an invalid field name",
                    Kind.CONFLICT,
                    Map.of("modelSpecId", modelSpecId)
                );
            }
            columns.add(name);
        });
        if (
            columns.size() > 1000 ||
            Set.copyOf(columns).size() != columns.size()
        ) {
            throw failure(
                "MODEL_FIELD_CONTRACT_INVALID",
                "Pinned model revision field contract is invalid",
                Kind.CONFLICT,
                Map.of("modelSpecId", modelSpecId)
            );
        }
        return List.copyOf(columns);
    }

    private String canonicalJson(String value, String name, UUID modelSpecId) {
        try {
            return objectMapper.writeValueAsString(json(value, name, modelSpecId));
        } catch (Exception invalid) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Current implementation " + name + " cannot be canonicalized",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", modelSpecId)
            );
        }
    }

    private static String dbtSelector(String dbtUniqueId) {
        return dbtUniqueId.substring(dbtUniqueId.lastIndexOf('.') + 1);
    }

    private static void requireChecksum(String value, String name, UUID modelSpecId) {
        if (value == null || !CHECKSUM.matcher(value).matches()) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Current " + name + " is invalid",
                Kind.UNPROCESSABLE,
                Map.of("modelSpecId", modelSpecId)
            );
        }
    }

    private static String requiredProperty(String value, String name) {
        if (value == null || value.isBlank()) {
            throw failure(
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                "Server " + name + " is not configured",
                Kind.UNPROCESSABLE,
                Map.of()
            );
        }
        return value.trim();
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(List<String> values) {
        MessageDigest digest = messageDigest();
        for (String value : values) {
            digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(String value) {
        MessageDigest digest = messageDigest();
        return HexFormat.of().formatHex(
            digest.digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ModelReleaseCandidateException failure(
        String code,
        String message,
        Kind kind,
        Map<String, Object> details
    ) {
        return details == null || details.isEmpty()
            ? new ModelReleaseCandidateException(code, message, kind)
            : new ModelReleaseCandidateException(code, message, kind, details);
    }

    private record BuildEntryRow(
        UUID entryId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        String implementationMode,
        UUID implementationId,
        int implementationRevision,
        String implementationChecksum,
        String ownership,
        String projectKey,
        String dbtUniqueId,
        String inputMode,
        String inputsJson,
        String settingsJson,
        String materialization
    ) {}

    private record ArtifactRow(
        String artifactKey,
        String path,
        String contentChecksum,
        String content,
        String artifactType,
        String nodeKind,
        String dbtUniqueId
    ) {}

    private record FrozenBundleSnapshot(
        String inputsJson,
        FrozenDbtBundleArtifactRecovery.FrozenBundleEvidence evidence
    ) {}

    private record PreparedEntry(
        BuildEntryRow row,
        String selector,
        String targetIdentifier,
        String artifactBundleChecksum,
        String dependencySnapshotChecksum,
        String activeClaimKey
    ) {}

    private record CandidateBuildRow(
        UUID pipelineRunId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID candidateId,
        int candidateVersion,
        UUID pipelineRunGroupId,
        String groupArtifactChecksum,
        String implementationMode,
        String dbtUniqueId,
        String targetIdentifier,
        String entryArtifactChecksum,
        String dependencySnapshotChecksum,
        String executionTargetKey,
        String modelFieldsJson
    ) {}

    private record PinnedDependencyRow(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        Integer observedModelRevision,
        String observedModelChecksum,
        Integer observedImplementationRevision,
        String observedImplementationChecksum,
        String observedAdapter,
        String targetAdapter,
        String databaseName,
        String schemaName,
        String identifier,
        Boolean verified,
        Boolean relationExists
    ) {}

    private record ExecutionTarget(
        String executionTargetKey,
        String adapter,
        String profileKey,
        String targetName,
        String releaseBuildDagId
    ) {}

    private record RetryDispatch(
        int candidateVersion,
        int attempt,
        String executionTargetKey,
        String airflowDagId,
        String artifactBundleChecksum,
        String status
    ) {}

    private record RetryEntryRow(
        UUID entryId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        String selector,
        String targetIdentifier,
        String artifactBundleChecksum,
        String dependencySnapshotChecksum,
        String activeClaimKey,
        int previousModelRevision,
        String previousModelChecksum,
        int previousImplementationRevision,
        String previousImplementationChecksum,
        String previousTargetIdentifier,
        String previousGroupArtifactChecksum
    ) {}

    private record RetryDriftRow(
        UUID lockedImplementationId,
        Integer lockedImplementationRevision,
        String lockedImplementationChecksum,
        String lockedDbtUniqueId,
        String lockedTargetIdentifier,
        String lockedArtifactBundleChecksum,
        String lockedDependencySnapshotChecksum,
        String lockedActiveClaimKey,
        BuildEntryRow current
    ) {
        private boolean matches(PreparedEntry prepared) {
            return (
                Objects.equals(
                    lockedImplementationId,
                    current.implementationId()
                ) &&
                Objects.equals(
                    lockedImplementationRevision,
                    current.implementationRevision()
                ) &&
                Objects.equals(
                    lockedImplementationChecksum,
                    current.implementationChecksum()
                ) &&
                Objects.equals(
                    lockedDbtUniqueId,
                    current.dbtUniqueId()
                ) &&
                Objects.equals(
                    lockedTargetIdentifier,
                    prepared.targetIdentifier()
                ) &&
                Objects.equals(
                    lockedArtifactBundleChecksum,
                    prepared.artifactBundleChecksum()
                ) &&
                Objects.equals(
                    lockedDependencySnapshotChecksum,
                    prepared.dependencySnapshotChecksum()
                ) &&
                Objects.equals(
                    lockedActiveClaimKey,
                    prepared.activeClaimKey()
                )
            );
        }
    }

    public record QueuedBuildRun(
        UUID id,
        UUID candidateEntryId,
        UUID modelSpecId,
        UUID pipelineRunGroupId,
        UUID dbtInvocationId,
        String airflowDagId,
        String airflowRunId,
        String dbtSelector,
        String targetIdentifier,
        String status,
        int attempt,
        String artifactBundleChecksum
    ) {}

    public record QueuedBuildGroup(
        UUID candidateId,
        int candidateVersion,
        UUID pipelineRunGroupId,
        UUID dbtInvocationId,
        String executionTargetKey,
        String airflowDagId,
        String airflowRunId,
        String artifactBundleChecksum,
        List<QueuedBuildRun> runs
    ) {}

    public record BuildArtifact(
        String path,
        String contentChecksum,
        String content
    ) {}

    public record CandidateBuildEntry(
        UUID pipelineRunId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String implementationMode,
        String dbtUniqueId,
        String targetIdentifier,
        List<BuildArtifact> artifacts,
        List<String> expectedColumns
    ) {
        public CandidateBuildEntry(
            UUID pipelineRunId,
            UUID modelSpecId,
            int modelRevision,
            String modelChecksum,
            int implementationRevision,
            String implementationChecksum,
            String implementationMode,
            String dbtUniqueId,
            String targetIdentifier,
            List<BuildArtifact> artifacts
        ) {
            this(
                pipelineRunId,
                modelSpecId,
                modelRevision,
                modelChecksum,
                implementationRevision,
                implementationChecksum,
                implementationMode,
                dbtUniqueId,
                targetIdentifier,
                artifacts,
                List.of()
            );
        }

        public CandidateBuildEntry {
            if (
                implementationMode == null ||
                implementationMode.isBlank()
            ) {
                throw new IllegalArgumentException(
                    "implementationMode is required"
                );
            }
            implementationMode = implementationMode.trim();
            artifacts = artifacts == null
                ? List.of()
                : List.copyOf(artifacts);
            expectedColumns = expectedColumns == null
                ? List.of()
                : List.copyOf(expectedColumns);
        }
    }

    public record CandidateBuildScope(
        String tenantId,
        UUID candidateId,
        int candidateVersion,
        UUID pipelineRunGroupId,
        String executionTargetKey,
        String artifactBundleChecksum,
        List<CandidateBuildEntry> entries
    ) {
        public CandidateBuildScope {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
