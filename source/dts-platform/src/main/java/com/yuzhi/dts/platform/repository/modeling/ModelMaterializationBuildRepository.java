package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
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
public class ModelMaterializationBuildRepository {

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
        if (runs.size() != candidate.entries().size() || runs.isEmpty()) {
            throw failure(
                "MODEL_PIPELINE_RUN_INVARIANT_BROKEN",
                "A committed BUILDING candidate must have one durable run per entry",
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
        String targetIdentifier = settings.path("targetPhysicalName").asText("").trim();
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
        String activeClaimKey = digest(
            List.of(
                candidate.tenantId(),
                candidate.environment(),
                row.modelSpecId().toString()
            )
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

    private List<ArtifactRow> loadArtifacts(String tenantId, BuildEntryRow row) {
        List<ArtifactRow> artifacts = jdbcTemplate.query(
            """
            select a.artifact_key, a.path, a.content_checksum, a.content,
                   a.artifact_type, a.node_kind, a.dbt_unique_id
              from modeling_dbt_artifact a
              join modeling_model_spec s on s.id = a.model_spec_id
             where s.tenant_id = ? and a.model_spec_id = ? and a.revision = ?
               and a.model_checksum = ? and a.implementation_revision = ?
               and a.ownership = ? and a.status = 'COMPILED'
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
                     where s.tenant_id = ? and s.id = ? and s.plan_id = ?
                       and s.revision = ? and s.current_checksum = ?
                       and s.status = 'PUBLISHED' and s.contract_version = 2
                )
                """,
                Boolean.class,
                candidate.tenantId(),
                upstreamId,
                candidate.planId(),
                revision,
                checksum
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
               and active_claim_key is null
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
            row.entryId()
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

    private record PreparedEntry(
        BuildEntryRow row,
        String selector,
        String targetIdentifier,
        String artifactBundleChecksum,
        String dependencySnapshotChecksum,
        String activeClaimKey
    ) {}

    private record ExecutionTarget(
        String executionTargetKey,
        String adapter,
        String profileKey,
        String targetName,
        String releaseBuildDagId
    ) {}

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
}
