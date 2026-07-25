package com.yuzhi.dts.platform.service.modeling.imports.apply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyIssue;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginCommand;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Severity;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL control-plane store for immutable apply attempts and per-candidate outcomes. */
@Repository
public class ModelSpecImportApplyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ModelSpecImportApplyRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Claims one client idempotency key. The advisory lock closes the read/insert race before the
     * database uniqueness constraints become the final guard.
     */
    @Transactional
    public BeginResult begin(BeginCommand command) {
        Objects.requireNonNull(command, "command is required");
        lock("model-spec-import-apply:idempotency:" + command.tenantId() + ":" + command.idempotencyKey());
        lock("model-spec-import-apply:run:" + command.tenantId() + ":" + command.runId());
        recoverExpiredRunningLocked(command.tenantId(), command.runId(), command.actorId());
        Optional<Attempt> existing = findByIdempotencyKey(command.tenantId(), command.idempotencyKey());
        if (existing.isPresent()) {
            Attempt attempt = existing.orElseThrow();
            requireReplayCompatible(command, attempt);
            return new BeginResult(
                attempt.status() == AttemptStatus.RUNNING ? BeginDisposition.RUNNING : BeginDisposition.REPLAY,
                attempt
            );
        }

        Optional<Attempt> running = findRunning(command.tenantId(), command.runId());
        requireNoCompetingRunning(running.orElse(null));
        requireRetrySource(command);
        int attemptNo = nextAttemptNo(command.tenantId(), command.runId());
        String ownerToken = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
            insert into modeling_model_spec_import_apply_attempt (
                id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                preview_hash, selected_unique_ids, selected_closure_json,
                idempotency_key, request_hash, status, summary_json,
                owner_token, lease_expires_at,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (
                ?, ?, ?, ?, ?, ?,
                ?, cast(? as jsonb), cast(? as jsonb),
                ?, ?, 'RUNNING', cast(? as jsonb),
                ?, CURRENT_TIMESTAMP + INTERVAL '5 minutes',
                ?, ?, ?, ?
            )
            """,
            command.attemptId(),
            command.runId(),
            command.tenantId(),
            command.planId(),
            attemptNo,
            command.retrySourceAttemptId(),
            command.previewHash(),
            json(command.selectedUniqueIds()),
            json(command.selectedClosure()),
            command.idempotencyKey(),
            command.requestHash(),
            json(ApplySummary.EMPTY),
            ownerToken,
            command.actorId(),
            Timestamp.from(command.startedAt()),
            command.actorId(),
            Timestamp.from(command.startedAt())
        );
        return new BeginResult(
            BeginDisposition.STARTED,
            find(command.tenantId(), command.attemptId()).orElseThrow(() ->
                new IllegalStateException("Started model import apply attempt cannot be reloaded")
            ),
            ownerToken
        );
    }

    @Transactional
    public Optional<Attempt> recoverExpiredRunning(String tenantId, UUID runId, String actorId) {
        lock("model-spec-import-apply:run:" + tenantId + ":" + runId);
        return recoverExpiredRunningLocked(tenantId, runId, actorId);
    }

    static void requireNoCompetingRunning(Attempt running) {
        if (running != null) {
            throw conflict(
                "MODEL_IMPORT_APPLY_ALREADY_RUNNING",
                "Another model import apply attempt is already running for this preview",
                running.id()
            );
        }
    }

    @Transactional
    public CandidateResult recordSuccess(
        String tenantId,
        UUID runId,
        UUID attemptId,
        String ownerToken,
        CandidateResult result
    ) {
        if (result == null || !result.successful()) {
            throw new IllegalArgumentException("A successful candidate result is required");
        }
        return record(tenantId, runId, attemptId, ownerToken, result);
    }

    @Transactional
    public CandidateResult recordFailure(
        String tenantId,
        UUID runId,
        UUID attemptId,
        String ownerToken,
        CandidateResult result
    ) {
        if (result == null || result.status() != ResultStatus.FAILED) {
            throw new IllegalArgumentException("A FAILED candidate result is required");
        }
        return record(tenantId, runId, attemptId, ownerToken, result);
    }

    @Transactional
    public CandidateResult recordBlocked(
        String tenantId,
        UUID runId,
        UUID attemptId,
        String ownerToken,
        CandidateResult result
    ) {
        if (result == null || result.status() != ResultStatus.BLOCKED) {
            throw new IllegalArgumentException("A BLOCKED candidate result is required");
        }
        return record(tenantId, runId, attemptId, ownerToken, result);
    }

    @Transactional
    public void renewLease(String tenantId, UUID runId, UUID attemptId, String ownerToken) {
        int changed = jdbcTemplate.update(
            """
            update modeling_model_spec_import_apply_attempt
               set lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '5 minutes',
                   last_modified_date = CURRENT_TIMESTAMP
             where tenant_id = ? and run_id = ? and id = ?
               and status = 'RUNNING'
               and owner_token = ?
               and lease_expires_at > CURRENT_TIMESTAMP
            """,
            tenantId,
            runId,
            attemptId,
            ownerToken
        );
        if (changed != 1) {
            throw leaseLost(attemptId);
        }
    }

    /**
     * Finalizes from persisted item facts instead of trusting caller totals. Repeated finalize calls
     * replay the stored terminal attempt.
     */
    @Transactional
    public Attempt finalizeAttempt(
        String tenantId,
        UUID runId,
        UUID attemptId,
        String ownerToken,
        String actorId,
        Instant completedAt
    ) {
        Objects.requireNonNull(completedAt, "completedAt is required");
        Attempt current = lockOwnedAttempt(tenantId, runId, attemptId, ownerToken);
        List<CandidateResult> results = findResults(attemptId);
        ApplySummary summary = summarize(results);
        requireCompleteResults(current, results);
        AttemptStatus status = terminalStatus(summary);
        int changed = jdbcTemplate.update(
            """
            update modeling_model_spec_import_apply_attempt
               set status = ?, summary_json = cast(? as jsonb), completed_at = ?,
                   owner_token = null, lease_expires_at = null,
                   last_modified_by = ?, last_modified_date = ?
             where tenant_id = ? and run_id = ? and id = ? and status = 'RUNNING'
               and owner_token = ?
               and lease_expires_at > CURRENT_TIMESTAMP
            """,
            status.name(),
            json(summary),
            Timestamp.from(completedAt),
            actorId,
            Timestamp.from(completedAt),
            tenantId,
            runId,
            attemptId,
            ownerToken
        );
        if (changed != 1) {
            throw conflict(
                "MODEL_IMPORT_APPLY_ATTEMPT_CONFLICT",
                "Model import apply attempt was finalized concurrently",
                attemptId
            );
        }
        return find(tenantId, attemptId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<Attempt> find(String tenantId, UUID attemptId) {
        return findHead(
            """
            select id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                   preview_hash, selected_unique_ids::text as selected_unique_ids,
                   selected_closure_json::text as selected_closure_json,
                   idempotency_key, request_hash, status, summary_json::text as summary_json,
                   created_by, created_date, completed_at
              from modeling_model_spec_import_apply_attempt
             where tenant_id = ? and id = ?
            """,
            tenantId,
            attemptId
        );
    }

    @Transactional(readOnly = true)
    public Optional<Attempt> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return findHead(
            """
            select id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                   preview_hash, selected_unique_ids::text as selected_unique_ids,
                   selected_closure_json::text as selected_closure_json,
                   idempotency_key, request_hash, status, summary_json::text as summary_json,
                   created_by, created_date, completed_at
              from modeling_model_spec_import_apply_attempt
             where tenant_id = ? and idempotency_key = ?
            """,
            tenantId,
            idempotencyKey
        );
    }

    @Transactional(readOnly = true)
    public Optional<Attempt> findLatest(String tenantId, UUID runId) {
        return findHead(
            """
            select id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                   preview_hash, selected_unique_ids::text as selected_unique_ids,
                   selected_closure_json::text as selected_closure_json,
                   idempotency_key, request_hash, status, summary_json::text as summary_json,
                   created_by, created_date, completed_at
              from modeling_model_spec_import_apply_attempt
             where tenant_id = ? and run_id = ?
             order by attempt_no desc
             limit 1
            """,
            tenantId,
            runId
        );
    }

    @Transactional(readOnly = true)
    public Optional<Attempt> findRunning(String tenantId, UUID runId) {
        return findHead(
            """
            select id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                   preview_hash, selected_unique_ids::text as selected_unique_ids,
                   selected_closure_json::text as selected_closure_json,
                   idempotency_key, request_hash, status, summary_json::text as summary_json,
                   created_by, created_date, completed_at
              from modeling_model_spec_import_apply_attempt
             where tenant_id = ? and run_id = ? and status = 'RUNNING'
             limit 1
            """,
            tenantId,
            runId
        );
    }

    /**
     * Returns the latest terminal attempt with unresolved candidates. Its selected closure is the
     * immutable source for retry; callers must not accept a replacement closure from the client.
     */
    @Transactional(readOnly = true)
    public Optional<Attempt> findRetrySource(String tenantId, UUID runId) {
        return findHead(
            """
            with latest as (
                select candidate.*
                  from modeling_model_spec_import_apply_attempt candidate
                 where candidate.tenant_id = ? and candidate.run_id = ?
                 order by candidate.attempt_no desc
                 limit 1
            )
            select attempt.id, attempt.run_id, attempt.tenant_id, attempt.plan_id, attempt.attempt_no,
                   attempt.retry_source_attempt_id, attempt.preview_hash,
                   attempt.selected_unique_ids::text as selected_unique_ids,
                   attempt.selected_closure_json::text as selected_closure_json,
                   attempt.idempotency_key, attempt.request_hash, attempt.status,
                   attempt.summary_json::text as summary_json,
                   attempt.created_by, attempt.created_date, attempt.completed_at
              from latest attempt
             where attempt.status in ('PARTIAL', 'FAILED')
               and exists (
                   select 1
                     from modeling_model_spec_import_apply_result result
                    where result.attempt_id = attempt.id
                      and result.status in ('FAILED', 'BLOCKED')
               )
            """,
            tenantId,
            runId
        );
    }

    static BeginDisposition replayDisposition(BeginCommand command, Attempt existing) {
        requireReplayCompatible(command, existing);
        return existing.status() == AttemptStatus.RUNNING ? BeginDisposition.RUNNING : BeginDisposition.REPLAY;
    }

    static ApplySummary summarize(List<CandidateResult> results) {
        List<CandidateResult> safeResults = results == null ? List.of() : results;
        return new ApplySummary(
            safeResults.size(),
            count(safeResults, ResultStatus.CREATED),
            count(safeResults, ResultStatus.UPDATED),
            count(safeResults, ResultStatus.SKIPPED),
            count(safeResults, ResultStatus.REPLAYED),
            count(safeResults, ResultStatus.FAILED),
            count(safeResults, ResultStatus.BLOCKED)
        );
    }

    static AttemptStatus terminalStatus(ApplySummary summary) {
        if (summary.total() == 0 || summary.failed() + summary.blocked() == summary.total()) {
            return AttemptStatus.FAILED;
        }
        if (summary.failed() > 0 || summary.blocked() > 0) {
            return AttemptStatus.PARTIAL;
        }
        return AttemptStatus.SUCCEEDED;
    }

    static void requireCompleteResults(Attempt attempt, List<CandidateResult> results) {
        java.util.Set<String> expected = new java.util.TreeSet<>(attempt.selectedClosure());
        java.util.Set<String> actual = new java.util.TreeSet<>(
            results == null ? List.of() : results.stream().map(CandidateResult::dbtUniqueId).toList()
        );
        if (!expected.equals(actual)) {
            throw conflict(
                "MODEL_IMPORT_APPLY_RESULTS_INCOMPLETE",
                "Persisted candidate outcomes must exactly match the frozen selection closure",
                attempt.id()
            );
        }
    }

    static void validateRetrySource(BeginCommand command, Attempt source) {
        if (
            source == null ||
            !Objects.equals(source.id(), command.retrySourceAttemptId()) ||
            !Objects.equals(source.tenantId(), command.tenantId()) ||
            !Objects.equals(source.runId(), command.runId()) ||
            !Objects.equals(source.planId(), command.planId()) ||
            !Objects.equals(source.previewHash(), command.previewHash()) ||
            (source.status() != AttemptStatus.PARTIAL && source.status() != AttemptStatus.FAILED) ||
            source.results().stream().noneMatch(item ->
                item.status() == ResultStatus.FAILED || item.status() == ResultStatus.BLOCKED
            ) ||
            !source.selectedClosure().containsAll(command.selectedClosure())
        ) {
            throw conflict(
                "MODEL_IMPORT_RETRY_SOURCE_STALE",
                "Retry source is not the latest unresolved attempt for this frozen preview",
                command.retrySourceAttemptId()
            );
        }
    }

    private CandidateResult record(
        String tenantId,
        UUID runId,
        UUID attemptId,
        String ownerToken,
        CandidateResult result
    ) {
        lock("model-spec-import-apply:result-key:" + tenantId + ":" + attemptId + ":" + result.candidateIdempotencyKey());
        lock("model-spec-import-apply:result-node:" + tenantId + ":" + attemptId + ":" + result.dbtUniqueId());
        Attempt attempt = lockOwnedAttempt(tenantId, runId, attemptId, ownerToken);
        if (!attempt.selectedClosure().contains(result.dbtUniqueId())) {
            throw conflict(
                "MODEL_IMPORT_APPLY_CANDIDATE_NOT_SELECTED",
                "Candidate does not belong to the frozen selection closure",
                result.dbtUniqueId()
            );
        }
        Optional<CandidateResult> keyed = findResultByCandidateKey(attemptId, result.candidateIdempotencyKey());
        if (
            keyed.isPresent() &&
            (
                !Objects.equals(keyed.orElseThrow().dbtUniqueId(), result.dbtUniqueId()) ||
                !Objects.equals(keyed.orElseThrow().candidateRequestHash(), result.candidateRequestHash())
            )
        ) {
            throw conflict(
                "MODEL_IMPORT_IDEMPOTENCY_CONFLICT",
                "Candidate idempotency key belongs to another candidate or payload",
                result.candidateIdempotencyKey()
            );
        }
        Optional<CandidateResult> existing = findResult(attemptId, result.dbtUniqueId());
        if (existing.isPresent()) {
            CandidateResult stored = existing.orElseThrow();
            if (
                !Objects.equals(stored.candidateIdempotencyKey(), result.candidateIdempotencyKey()) ||
                !Objects.equals(stored.candidateRequestHash(), result.candidateRequestHash())
            ) {
                throw conflict(
                    "MODEL_IMPORT_IDEMPOTENCY_CONFLICT",
                    "Candidate idempotency key belongs to a different payload",
                    result.dbtUniqueId()
                );
            }
            return stored;
        }
        jdbcTemplate.update(
            """
            insert into modeling_model_spec_import_apply_result (
                id, attempt_id, run_id, seq, dbt_unique_id,
                candidate_idempotency_key, candidate_request_hash, status,
                model_spec_id, model_revision, model_checksum,
                implementation_revision, implementation_checksum,
                artifact_count, issues_json, created_date, last_modified_date
            ) values (
                ?, ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, cast(? as jsonb), ?, ?
            )
            """,
            result.resultId(),
            attemptId,
            attempt.runId(),
            result.sequence(),
            result.dbtUniqueId(),
            result.candidateIdempotencyKey(),
            result.candidateRequestHash(),
            result.status().name(),
            result.modelSpecId(),
            result.revision(),
            result.modelChecksum(),
            result.implementationRevision(),
            result.implementationChecksum(),
            result.artifactCount(),
            json(result.issues()),
            Timestamp.from(result.recordedAt()),
            Timestamp.from(result.recordedAt())
        );
        return findResult(attemptId, result.dbtUniqueId()).orElseThrow();
    }

    private Optional<Attempt> recoverExpiredRunningLocked(String tenantId, UUID runId, String actorId) {
        Optional<Attempt> expired = jdbcTemplate
            .query(
                """
                select id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                       preview_hash, selected_unique_ids::text as selected_unique_ids,
                       selected_closure_json::text as selected_closure_json,
                       idempotency_key, request_hash, status, summary_json::text as summary_json,
                       created_by, created_date, completed_at
                  from modeling_model_spec_import_apply_attempt
                 where tenant_id = ? and run_id = ? and status = 'RUNNING'
                   and (owner_token is null or lease_expires_at is null or lease_expires_at <= CURRENT_TIMESTAMP)
                 for update
                """,
                (row, rowNumber) -> attempt(row),
                tenantId,
                runId
            )
            .stream()
            .findFirst()
            .map(head -> withResults(head, findResults(head.id())));
        if (expired.isEmpty()) {
            return Optional.empty();
        }

        Attempt attempt = expired.orElseThrow();
        Instant recoveredAt = Instant.now();
        java.util.Set<String> recorded = new java.util.HashSet<>(
            attempt.results().stream().map(CandidateResult::dbtUniqueId).toList()
        );
        for (int sequence = 0; sequence < attempt.selectedClosure().size(); sequence++) {
            String dbtUniqueId = attempt.selectedClosure().get(sequence);
            if (recorded.contains(dbtUniqueId)) {
                continue;
            }
            CandidateResult interrupted = interruptedResult(attempt, sequence, dbtUniqueId, recoveredAt);
            jdbcTemplate.update(
                """
                insert into modeling_model_spec_import_apply_result (
                    id, attempt_id, run_id, seq, dbt_unique_id,
                    candidate_idempotency_key, candidate_request_hash, status,
                    model_spec_id, model_revision, model_checksum,
                    implementation_revision, implementation_checksum,
                    artifact_count, issues_json, created_date, last_modified_date
                ) values (
                    ?, ?, ?, ?, ?,
                    ?, ?, 'FAILED',
                    null, null, null,
                    null, null,
                    0, cast(? as jsonb), ?, ?
                )
                on conflict (attempt_id, dbt_unique_id) do nothing
                """,
                interrupted.resultId(),
                attempt.id(),
                attempt.runId(),
                interrupted.sequence(),
                interrupted.dbtUniqueId(),
                interrupted.candidateIdempotencyKey(),
                interrupted.candidateRequestHash(),
                json(interrupted.issues()),
                Timestamp.from(recoveredAt),
                Timestamp.from(recoveredAt)
            );
        }

        List<CandidateResult> results = findResults(attempt.id());
        requireCompleteResults(attempt, results);
        ApplySummary summary = summarize(results);
        AttemptStatus terminal = terminalStatus(summary);
        String recoveryActor = actorId == null || actorId.isBlank() ? "model-import-lease-recovery" : actorId;
        int changed = jdbcTemplate.update(
            """
            update modeling_model_spec_import_apply_attempt
               set status = ?, summary_json = cast(? as jsonb), completed_at = ?,
                   owner_token = null, lease_expires_at = null,
                   last_modified_by = ?, last_modified_date = ?
             where tenant_id = ? and run_id = ? and id = ? and status = 'RUNNING'
               and (owner_token is null or lease_expires_at is null or lease_expires_at <= CURRENT_TIMESTAMP)
            """,
            terminal.name(),
            json(summary),
            Timestamp.from(recoveredAt),
            recoveryActor,
            Timestamp.from(recoveredAt),
            tenantId,
            runId,
            attempt.id()
        );
        if (changed != 1) {
            throw conflict(
                "MODEL_IMPORT_APPLY_LEASE_RECOVERY_CONFLICT",
                "Expired model import apply attempt could not be recovered atomically",
                attempt.id()
            );
        }
        return find(tenantId, attempt.id());
    }

    private CandidateResult interruptedResult(
        Attempt attempt,
        int sequence,
        String dbtUniqueId,
        Instant recoveredAt
    ) {
        String stableIdentity = attempt.id() + "\n" + dbtUniqueId;
        String stableHash = ModelPackageChecksum.sha256Text("model-import-interrupted\n" + stableIdentity);
        return new CandidateResult(
            UUID.nameUUIDFromBytes(("model-import-interrupted-result\n" + stableIdentity).getBytes(StandardCharsets.UTF_8)),
            sequence,
            dbtUniqueId,
            "model-import-interrupted:" + stableHash,
            stableHash,
            ResultStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            0,
            List.of(
                new ApplyIssue(
                    "MODEL_IMPORT_APPLY_INTERRUPTED",
                    Severity.ERROR,
                    "$.items[" + sequence + "]",
                    dbtUniqueId,
                    "The apply worker lease expired before this candidate produced a durable result",
                    "Retry the unresolved candidates from this recovered attempt"
                )
            ),
            recoveredAt
        );
    }

    private Attempt lockOwnedAttempt(
        String tenantId,
        UUID runId,
        UUID attemptId,
        String ownerToken
    ) {
        return jdbcTemplate
            .query(
                """
                select id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                       preview_hash, selected_unique_ids::text as selected_unique_ids,
                       selected_closure_json::text as selected_closure_json,
                       idempotency_key, request_hash, status, summary_json::text as summary_json,
                       created_by, created_date, completed_at
                  from modeling_model_spec_import_apply_attempt
                 where tenant_id = ? and run_id = ? and id = ?
                   and status = 'RUNNING'
                   and owner_token = ?
                   and lease_expires_at > CURRENT_TIMESTAMP
                 for update
                """,
                (row, rowNumber) -> attempt(row),
                tenantId,
                runId,
                attemptId,
                ownerToken
            )
            .stream()
            .findFirst()
            .map(head -> withResults(head, findResults(head.id())))
            .orElseThrow(() -> leaseLost(attemptId));
    }

    private Attempt lockAttempt(String tenantId, UUID attemptId) {
        return jdbcTemplate
            .query(
                """
                select id, run_id, tenant_id, plan_id, attempt_no, retry_source_attempt_id,
                       preview_hash, selected_unique_ids::text as selected_unique_ids,
                       selected_closure_json::text as selected_closure_json,
                       idempotency_key, request_hash, status, summary_json::text as summary_json,
                       created_by, created_date, completed_at
                  from modeling_model_spec_import_apply_attempt
                 where tenant_id = ? and id = ?
                 for update
                """,
                (row, rowNumber) -> attempt(row),
                tenantId,
                attemptId
            )
            .stream()
            .findFirst()
            .map(head -> withResults(head, findResults(head.id())))
            .orElseThrow(() ->
                new ModelSpecImportApplyException(
                    "MODEL_IMPORT_APPLY_ATTEMPT_NOT_FOUND",
                    "Model import apply attempt was not found",
                    Kind.NOT_FOUND,
                    attemptId
                )
            );
    }

    private Optional<Attempt> findHead(String sql, Object... arguments) {
        return jdbcTemplate
            .query(sql, (row, rowNumber) -> attempt(row), arguments)
            .stream()
            .findFirst()
            .map(head -> withResults(head, findResults(head.id())));
    }

    private Attempt attempt(java.sql.ResultSet row) throws java.sql.SQLException {
        return new Attempt(
            row.getObject("id", UUID.class),
            row.getObject("run_id", UUID.class),
            row.getObject("plan_id", UUID.class),
            row.getObject("retry_source_attempt_id", UUID.class),
            row.getString("tenant_id"),
            row.getInt("attempt_no"),
            row.getString("preview_hash"),
            readStrings(row.getString("selected_unique_ids")),
            readStrings(row.getString("selected_closure_json")),
            row.getString("idempotency_key"),
            row.getString("request_hash"),
            AttemptStatus.valueOf(row.getString("status")),
            readSummary(row.getString("summary_json")),
            row.getString("created_by"),
            instant(row.getTimestamp("created_date")),
            instant(row.getTimestamp("completed_at")),
            List.of()
        );
    }

    private Attempt withResults(Attempt head, List<CandidateResult> results) {
        return new Attempt(
            head.id(),
            head.runId(),
            head.planId(),
            head.retrySourceAttemptId(),
            head.tenantId(),
            head.attemptNo(),
            head.previewHash(),
            head.selectedUniqueIds(),
            head.selectedClosure(),
            head.idempotencyKey(),
            head.requestHash(),
            head.status(),
            head.summary(),
            head.actorId(),
            head.startedAt(),
            head.completedAt(),
            results
        );
    }

    private List<CandidateResult> findResults(UUID attemptId) {
        return jdbcTemplate.query(
            """
            select id, seq, dbt_unique_id, candidate_idempotency_key, candidate_request_hash,
                   status, model_spec_id, model_revision, model_checksum,
                   implementation_revision, implementation_checksum, artifact_count,
                   issues_json::text as issues_json, created_date
              from modeling_model_spec_import_apply_result
             where attempt_id = ?
             order by seq, dbt_unique_id
            """,
            (row, rowNumber) ->
                new CandidateResult(
                    row.getObject("id", UUID.class),
                    row.getInt("seq"),
                    row.getString("dbt_unique_id"),
                    row.getString("candidate_idempotency_key"),
                    row.getString("candidate_request_hash"),
                    ResultStatus.valueOf(row.getString("status")),
                    row.getObject("model_spec_id", UUID.class),
                    (Integer) row.getObject("model_revision"),
                    row.getString("model_checksum"),
                    (Integer) row.getObject("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getInt("artifact_count"),
                    readIssues(row.getString("issues_json")),
                    instant(row.getTimestamp("created_date"))
                ),
            attemptId
        );
    }

    private Optional<CandidateResult> findResult(UUID attemptId, String dbtUniqueId) {
        return findResults(attemptId).stream().filter(item -> item.dbtUniqueId().equals(dbtUniqueId)).findFirst();
    }

    private Optional<CandidateResult> findResultByCandidateKey(UUID attemptId, String candidateIdempotencyKey) {
        return findResults(attemptId)
            .stream()
            .filter(item -> item.candidateIdempotencyKey().equals(candidateIdempotencyKey))
            .findFirst();
    }

    private void requireRetrySource(BeginCommand command) {
        if (command.retrySourceAttemptId() == null) {
            return;
        }
        Attempt source = findRetrySource(command.tenantId(), command.runId()).orElse(null);
        validateRetrySource(command, source);
    }

    private int nextAttemptNo(String tenantId, UUID runId) {
        Integer value = jdbcTemplate.queryForObject(
            """
            select coalesce(max(attempt_no), 0) + 1
              from modeling_model_spec_import_apply_attempt
             where tenant_id = ? and run_id = ?
            """,
            Integer.class,
            tenantId,
            runId
        );
        return value == null ? 1 : value;
    }

    private void lock(String key) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            (org.springframework.jdbc.core.ResultSetExtractor<Void>) resultSet -> {
                resultSet.next();
                return null;
            },
            key
        );
    }

    private static void requireReplayCompatible(BeginCommand command, Attempt existing) {
        if (
            !Objects.equals(existing.requestHash(), command.requestHash()) ||
            !Objects.equals(existing.runId(), command.runId()) ||
            !Objects.equals(existing.planId(), command.planId()) ||
            !Objects.equals(existing.retrySourceAttemptId(), command.retrySourceAttemptId()) ||
            !Objects.equals(existing.previewHash(), command.previewHash()) ||
            !Objects.equals(existing.selectedUniqueIds(), command.selectedUniqueIds()) ||
            !Objects.equals(existing.selectedClosure(), command.selectedClosure())
        ) {
            throw conflict(
                "MODEL_IMPORT_IDEMPOTENCY_CONFLICT",
                "Apply idempotency key belongs to a different request hash or frozen selection",
                command.idempotencyKey()
            );
        }
    }

    private static int count(List<CandidateResult> results, ResultStatus status) {
        return (int) results.stream().filter(item -> item.status() == status).count();
    }

    private ApplySummary readSummary(String json) {
        try {
            return objectMapper.readValue(json, ApplySummary.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model import apply summary is invalid", exception);
        }
    }

    private List<String> readStrings(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model import selection is invalid", exception);
        }
    }

    private List<ApplyIssue> readIssues(String json) {
        try {
            return objectMapper.readerForListOf(ApplyIssue.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored model import apply issues are invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Model import apply control-plane value cannot be serialized", exception);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static ModelSpecImportApplyException conflict(String code, String message, Object details) {
        return new ModelSpecImportApplyException(code, message, Kind.CONFLICT, details);
    }

    private static ModelSpecImportApplyException leaseLost(UUID attemptId) {
        return conflict(
            "MODEL_IMPORT_APPLY_LEASE_LOST",
            "Model import apply ownership lease is no longer valid",
            attemptId
        );
    }
}
