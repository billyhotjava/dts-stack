package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationClaimKey;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CurrentModelReference;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL persistence adapter for immutable release-candidate scope and mutable delivery state.
 *
 * <p>Header audit milestones are append-only for the first delivery cycle. REJECTED and ROLLED_BACK candidates are
 * historical records: a later delivery starts a new candidate. T03 may add an attempt ledger, but must never reset
 * this header's submitted, approved or published identity.
 */
@Repository
public class ModelReleaseCandidateRepository {

    private static final String HEADER_SELECTION = """
        select id, tenant_id, plan_id, environment, status, version, idempotency_key, request_hash,
               created_by, created_date, submitted_by, submitted_date, approved_by, approved_date,
               published_by, published_date, last_modified_by, last_modified_date, origin,
               execution_target_key, adapter, profile_key, target_name
          from modeling_model_release_candidate
        """;

    private final JdbcTemplate jdbcTemplate;

    public ModelReleaseCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Serializes active-candidate decisions on the canonical warehouse-plan row.
     *
     * <p>The row lock is transaction-scoped. Callers still rely on the active-candidate unique
     * constraint as the final database invariant.
     */
    public void lockPlanForCandidate(String tenantId, UUID planId) {
        requireTenantAndId(tenantId, planId);
        jdbcTemplate.query(
            """
            select id
              from modeling_warehouse_plan
             where tenant_id = ? and id = ?
             for update
            """,
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            tenantId.trim(),
            planId
        );
    }

    @Transactional
    public int insert(CandidateView candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        DeliveryAuditView audit = candidate.audit();
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_model_release_candidate (
                id, tenant_id, plan_id, environment, status, version, idempotency_key, request_hash,
                created_by, created_date, submitted_by, submitted_date, approved_by, approved_date,
                published_by, published_date, last_modified_by, last_modified_date, origin,
                execution_target_key, adapter, profile_key, target_name
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """,
            candidate.id(),
            candidate.tenantId(),
            candidate.planId(),
            candidate.environment(),
            candidate.status().name(),
            candidate.version(),
            candidate.idempotencyKey(),
            candidate.requestHash(),
            audit.createdBy(),
            Timestamp.from(audit.createdAt()),
            audit.submittedBy(),
            timestamp(audit.submittedAt()),
            audit.approvedBy(),
            timestamp(audit.approvedAt()),
            audit.publishedBy(),
            timestamp(audit.publishedAt()),
            candidate.lastModifiedBy(),
            Timestamp.from(candidate.lastModifiedAt()),
            candidate.origin().name(),
            candidate.executionTargetKey(),
            candidate.adapter(),
            candidate.profileKey(),
            candidate.targetName()
        );
        if (inserted == 0) return 0;
        for (EntryView entry : candidate.entries()) {
            insertEntry(entry);
        }
        return inserted;
    }

    public Optional<CandidateView> find(String tenantId, UUID id) {
        if (tenantId == null || tenantId.isBlank() || id == null) return Optional.empty();
        return queryCandidates(
            HEADER_SELECTION + " where tenant_id = ? and id = ?",
            tenantId.trim(),
            id
        )
            .stream()
            .findFirst();
    }

    public Optional<CandidateView> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (tenantId == null || tenantId.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return queryCandidates(
            HEADER_SELECTION + " where tenant_id = ? and idempotency_key = ?",
            tenantId.trim(),
            idempotencyKey.trim()
        )
            .stream()
            .findFirst();
    }

    /**
     * Moves a terminal candidate's model claims to its replacement DRAFT in one transaction.
     *
     * <p>The source is cleared before the replacement is reserved so PostgreSQL can enforce the
     * unique key. A competing reservation still sees one atomic transaction: any uniqueness
     * conflict rolls both updates back and leaves the source claim intact.
     */
    @Transactional
    public void transferActiveClaims(
        CandidateView source,
        CandidateView replacement
    ) {
        if (source == null || replacement == null) {
            throw new IllegalArgumentException(
                "source and replacement candidates are required"
            );
        }
        if (
            !source.tenantId().equals(replacement.tenantId()) ||
            !source.planId().equals(replacement.planId()) ||
            replacement.status() != DeliveryStatus.DRAFT ||
            !Set.of(
                DeliveryStatus.REJECTED,
                DeliveryStatus.ROLLED_BACK,
                DeliveryStatus.STALE,
                DeliveryStatus.CANCELLED
            )
                .contains(source.status())
        ) {
            throw claimTransferFailure(
                "MODEL_RELEASE_CANDIDATE_CLAIM_TRANSFER_INVALID",
                "Only a terminal candidate can transfer claims to its DRAFT replacement",
                source,
                replacement
            );
        }
        List<ClaimRow> sourceRows = jdbcTemplate.query(
            """
            select e.model_spec_id, e.active_claim_key
              from modeling_model_release_candidate c
              join modeling_model_release_candidate_entry e
                on e.tenant_id = c.tenant_id and e.candidate_id = c.id
             where c.tenant_id = ? and c.id = ? and c.version = ?
               and c.status = ? and e.status = ?
             order by e.model_spec_id, e.id
             for update of c, e
            """,
            (row, rowNumber) ->
                new ClaimRow(
                    row.getObject("model_spec_id", UUID.class),
                    row.getString("active_claim_key")
                ),
            source.tenantId(),
            source.id(),
            source.version(),
            source.status().name(),
            source.status().name()
        );
        if (sourceRows.isEmpty()) {
            if (source.entries().isEmpty()) return;
            throw claimTransferFailure(
                "MODEL_RELEASE_CANDIDATE_CLAIM_TRANSFER_CONFLICT",
                "The source candidate changed before claim transfer",
                source,
                replacement
            );
        }
        List<ClaimRow> claimed = sourceRows
            .stream()
            .filter(row -> row.activeClaimKey() != null)
            .toList();
        if (claimed.isEmpty()) return;
        if (claimed.size() != sourceRows.size()) {
            throw claimTransferFailure(
                "MODEL_RELEASE_CANDIDATE_CLAIM_INVARIANT_BROKEN",
                "The source candidate contains a partial model claim set",
                source,
                replacement
            );
        }
        Map<UUID, UUID> replacementEntries = jdbcTemplate.query(
            """
            select e.model_spec_id, e.id
              from modeling_model_release_candidate c
              join modeling_model_release_candidate_entry e
                on e.tenant_id = c.tenant_id and e.candidate_id = c.id
             where c.tenant_id = ? and c.id = ? and c.version = 1
               and c.status = 'DRAFT' and e.status = 'DRAFT'
             order by e.model_spec_id, e.id
             for update of c, e
            """,
            (row, rowNumber) ->
                Map.entry(
                    row.getObject("model_spec_id", UUID.class),
                    row.getObject("id", UUID.class)
                ),
            replacement.tenantId(),
            replacement.id()
        )
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (left, right) -> left,
                    LinkedHashMap::new
                )
            );
        Set<UUID> sourceModels = sourceRows
            .stream()
            .map(ClaimRow::modelSpecId)
            .collect(java.util.stream.Collectors.toSet());
        if (!sourceModels.equals(replacementEntries.keySet())) {
            throw claimTransferFailure(
                "MODEL_RELEASE_CANDIDATE_REPLACEMENT_SCOPE_MISMATCH",
                "A claimed replacement must retain the exact ModelSpec scope",
                source,
                replacement
            );
        }

        int released = jdbcTemplate.update(
            """
            update modeling_model_release_candidate_entry
               set active_claim_key = null
             where tenant_id = ? and candidate_id = ?
               and active_claim_key is not null
            """,
            source.tenantId(),
            source.id()
        );
        if (released != claimed.size()) {
            throw claimTransferFailure(
                "MODEL_RELEASE_CANDIDATE_CLAIM_TRANSFER_CONFLICT",
                "The source candidate claim set changed during transfer",
                source,
                replacement
            );
        }
        try {
            for (UUID modelSpecId : sourceModels) {
                int reserved = jdbcTemplate.update(
                    """
                    update modeling_model_release_candidate_entry
                       set active_claim_key = ?
                     where tenant_id = ? and candidate_id = ? and id = ?
                       and model_spec_id = ? and status = 'DRAFT'
                       and implementation_revision is null
                       and active_claim_key is null
                    """,
                    ModelMaterializationClaimKey.derive(
                        replacement.tenantId(),
                        replacement.environment(),
                        modelSpecId
                    ),
                    replacement.tenantId(),
                    replacement.id(),
                    replacementEntries.get(modelSpecId),
                    modelSpecId
                );
                if (reserved != 1) {
                    throw claimTransferFailure(
                        "MODEL_RELEASE_CANDIDATE_CLAIM_TRANSFER_CONFLICT",
                        "The replacement claim reservation could not be written",
                        source,
                        replacement
                    );
                }
            }
        } catch (DataIntegrityViolationException conflict) {
            throw claimTransferFailure(
                "MODEL_ACTIVE_CANDIDATE_CLAIM_CONFLICT",
                "Another candidate already owns a replacement model claim",
                source,
                replacement
            );
        }
    }

    private static ModelReleaseCandidateException claimTransferFailure(
        String code,
        String message,
        CandidateView source,
        CandidateView replacement
    ) {
        return new ModelReleaseCandidateException(
            code,
            message,
            Kind.CONFLICT,
            Map.of(
                "sourceCandidateId",
                source.id(),
                "replacementCandidateId",
                replacement.id()
            )
        );
    }

    public Optional<CommandEventView> findCommandByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (tenantId == null || tenantId.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate
            .query(
                """
                select id, tenant_id, candidate_id, plan_id, candidate_version, event_type,
                       from_status, to_status, actor_id, occurred_at, reason, idempotency_key,
                       request_hash, response_snapshot::text as response_snapshot
                  from modeling_model_release_candidate_command
                 where tenant_id = ? and idempotency_key = ?
                """,
                (row, rowNumber) -> mapCommand(row),
                tenantId.trim(),
                idempotencyKey.trim()
            )
            .stream()
            .findFirst();
    }

    /** Returns the newest immutable command snapshot that pinned engineering and governance quality evidence. */
    public Optional<CommandEventView> findLatestQualityEvidenceSnapshot(String tenantId, UUID candidateId) {
        if (tenantId == null || tenantId.isBlank() || candidateId == null) return Optional.empty();
        return jdbcTemplate
            .query(
                """
                select id, tenant_id, candidate_id, plan_id, candidate_version, event_type,
                       from_status, to_status, actor_id, occurred_at, reason, idempotency_key,
                       request_hash, response_snapshot::text as response_snapshot
                  from modeling_model_release_candidate_command
                 where tenant_id = ? and candidate_id = ?
                   and response_snapshot ->> 'combinedEvidenceChecksum' is not null
                 order by candidate_version desc, occurred_at desc, id desc
                 limit 1
                """,
                (row, rowNumber) -> mapCommand(row),
                tenantId.trim(),
                candidateId
            )
            .stream()
            .findFirst();
    }

    public Map<UUID, CurrentModelReference> findCurrentModelReferences(
        String tenantId,
        UUID planId,
        List<UUID> modelSpecIds
    ) {
        return findCurrentModelReferences(tenantId, planId, modelSpecIds, true);
    }

    public Map<UUID, CurrentModelReference> findCurrentModelReferencesForRead(
        String tenantId,
        UUID planId,
        List<UUID> modelSpecIds
    ) {
        return findCurrentModelReferences(tenantId, planId, modelSpecIds, false);
    }

    private Map<UUID, CurrentModelReference> findCurrentModelReferences(
        String tenantId,
        UUID planId,
        List<UUID> modelSpecIds,
        boolean lockRows
    ) {
        requireTenantAndId(tenantId, planId);
        List<UUID> ids = modelSpecIds == null
            ? List.of()
            : modelSpecIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(tenantId.trim());
        arguments.add(planId);
        arguments.addAll(ids);
        List<CurrentModelReference> references = jdbcTemplate.query(
            """
            select s.id, s.plan_id, s.revision, s.current_checksum, s.implementation_mode
              from modeling_model_spec s
              join modeling_model_spec_revision r
                on r.tenant_id = s.tenant_id
               and r.model_spec_id = s.id
               and r.revision = s.revision
               and r.content_checksum = s.current_checksum
               and r.contract_version = 2
             where s.tenant_id = ? and s.plan_id = ? and s.id in (%s)
               and s.contract_version = 2
               and s.status <> 'ARCHIVED'
               and s.revision > 0
               and s.current_checksum is not null
               and s.implementation_mode is not null
             %s
            """.formatted(placeholders, lockRows ? "for share of s, r" : ""),
            (row, rowNumber) ->
                new CurrentModelReference(
                    row.getObject("id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getInt("revision"),
                    row.getString("current_checksum"),
                    ImplementationMode.valueOf(row.getString("implementation_mode"))
                ),
            arguments.toArray()
        );
        LinkedHashMap<UUID, CurrentModelReference> byId = new LinkedHashMap<>();
        for (CurrentModelReference reference : references) {
            byId.put(reference.modelSpecId(), reference);
        }
        return Map.copyOf(byId);
    }

    public List<CandidateView> list(
        String tenantId,
        UUID planId,
        DeliveryStatus status,
        int offset,
        int limit
    ) {
        requireTenantAndId(tenantId, planId);
        if (offset < 0) throw new IllegalArgumentException("offset must be non-negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        String statusClause = status == null ? "" : " and status = ?";
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(tenantId.trim());
        arguments.add(planId);
        if (status != null) arguments.add(status.name());
        arguments.add(limit);
        arguments.add(offset);
        return queryCandidates(
            HEADER_SELECTION +
            " where tenant_id = ? and plan_id = ?" +
            statusClause +
            " order by last_modified_date desc, id limit ? offset ?",
            arguments.toArray()
        );
    }

    /**
     * Returns the current active candidate first, followed by recent terminal history.
     *
     * <p>Two active rows indicate pre-migration or externally corrupted state and are intentionally returned so the
     * application boundary can fail closed instead of silently choosing one.
     */
    public List<CandidateView> listForWorkbench(String tenantId, UUID planId) {
        requireTenantAndId(tenantId, planId);
        return queryCandidatesWithOrder(
            HEADER_SELECTION +
            """
             where tenant_id = ? and plan_id = ?
             order by case
                        when status not in ('REJECTED', 'ROLLED_BACK', 'CANCELLED', 'STALE') then 0
                        else 1
                      end,
                      last_modified_date desc,
                      id
             limit 2
            """,
            """
            case
              when c.status not in ('REJECTED', 'ROLLED_BACK', 'CANCELLED', 'STALE') then 0
              else 1
            end,
            c.last_modified_date desc,
            c.id
            """,
            tenantId.trim(),
            planId
        );
    }

    @Transactional
    int compareAndSetVersion(
        String tenantId,
        UUID id,
        int expectedVersion,
        DeliveryStatus replacementStatus,
        DeliveryAuditView audit,
        String lastModifiedBy,
        Instant lastModifiedAt
    ) {
        requireTenantAndId(tenantId, id);
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        if (replacementStatus == null) throw new IllegalArgumentException("replacementStatus is required");
        if (audit == null) throw new IllegalArgumentException("audit is required");
        String actor = requiredText(lastModifiedBy, "lastModifiedBy");
        if (lastModifiedAt == null) throw new IllegalArgumentException("lastModifiedAt is required");
        int updated = jdbcTemplate.update(
            """
            update modeling_model_release_candidate
               set status = ?,
                   version = version + 1,
                   submitted_by = ?,
                   submitted_date = ?,
                   approved_by = ?,
                   approved_date = ?,
                   published_by = ?,
                   published_date = ?,
                   last_modified_by = ?,
                   last_modified_date = ?
             where tenant_id = ?
               and id = ?
               and version = ?
               and created_by = ?
               and created_date = ?
               and last_modified_date <= ?
               and (
                    (submitted_by is null and submitted_date is null)
                    or (submitted_by is not distinct from ? and submitted_date is not distinct from ?)
               )
               and (
                    (approved_by is null and approved_date is null)
                    or (approved_by is not distinct from ? and approved_date is not distinct from ?)
               )
               and (
                    (published_by is null and published_date is null)
                    or (published_by is not distinct from ? and published_date is not distinct from ?)
               )
            """,
            replacementStatus.name(),
            audit.submittedBy(),
            timestamp(audit.submittedAt()),
            audit.approvedBy(),
            timestamp(audit.approvedAt()),
            audit.publishedBy(),
            timestamp(audit.publishedAt()),
            actor,
            Timestamp.from(lastModifiedAt),
            tenantId.trim(),
            id,
            expectedVersion,
            audit.createdBy(),
            Timestamp.from(audit.createdAt()),
            Timestamp.from(lastModifiedAt),
            audit.submittedBy(),
            timestamp(audit.submittedAt()),
            audit.approvedBy(),
            timestamp(audit.approvedAt()),
            audit.publishedBy(),
            timestamp(audit.publishedAt())
        );
        if (updated == 1) {
            jdbcTemplate.update(
                """
                update modeling_model_release_candidate_entry
                   set status = ?,
                       active_claim_key = case
                           when ? = 'CANCELLED' then null
                           else active_claim_key
                       end
                 where tenant_id = ? and candidate_id = ?
                """,
                replacementStatus.name(),
                replacementStatus.name(),
                tenantId.trim(),
                id
            );
        }
        return updated;
    }

    /**
     * Deletes only the locked DRAFT/version pair. Future evidence foreign keys remain RESTRICT and fail the
     * transaction.
     */
    @Transactional
    public int deleteDraft(String tenantId, UUID id, int expectedVersion) {
        requireTenantAndId(tenantId, id);
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        boolean deletable = !jdbcTemplate
            .query(
                """
                select id
                  from modeling_model_release_candidate
                 where tenant_id = ? and id = ? and status = 'DRAFT' and version = ?
                   for update
                """,
                (row, rowNumber) -> row.getObject("id", UUID.class),
                tenantId.trim(),
                id,
                expectedVersion
            )
            .isEmpty();
        if (!deletable) return 0;
        boolean hasCommandLedger = jdbcTemplate.queryForObject(
            """
            select exists(
                select 1
                  from modeling_model_release_candidate_command
                 where tenant_id = ? and candidate_id = ?
            )
            """,
            Boolean.class,
            tenantId.trim(),
            id
        );
        if (hasCommandLedger) return 0;
        jdbcTemplate.update(
            "delete from modeling_model_release_candidate_entry where tenant_id = ? and candidate_id = ?",
            tenantId.trim(),
            id
        );
        return jdbcTemplate.update(
            """
            delete from modeling_model_release_candidate
             where tenant_id = ? and id = ? and status = 'DRAFT' and version = ?
            """,
            tenantId.trim(),
            id,
            expectedVersion
        );
    }

    @Transactional
    public int replaceDraftScope(
        CandidateView current,
        int expectedVersion,
        List<EntryView> replacementEntries,
        String actorId,
        Instant occurredAt,
        CommandEventView command
    ) {
        if (current == null) throw new IllegalArgumentException("current candidate is required");
        if (replacementEntries == null) throw new IllegalArgumentException("replacementEntries are required");
        String actor = requiredText(actorId, "actorId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        int updated = jdbcTemplate.update(
            """
            update modeling_model_release_candidate
               set version = version + 1,
                   last_modified_by = ?,
                   last_modified_date = ?
             where tenant_id = ? and id = ? and plan_id = ?
               and status = 'DRAFT' and version = ?
            """,
            actor,
            Timestamp.from(occurredAt),
            current.tenantId(),
            current.id(),
            current.planId(),
            expectedVersion
        );
        if (updated == 0) return 0;
        jdbcTemplate.update(
            "delete from modeling_model_release_candidate_entry where tenant_id = ? and candidate_id = ?",
            current.tenantId(),
            current.id()
        );
        for (EntryView entry : replacementEntries) {
            insertEntry(entry);
        }
        appendCommandOrFail(command);
        return updated;
    }

    @Transactional
    public int transitionAndAppend(
        CandidateView current,
        int expectedVersion,
        DeliveryStatus replacementStatus,
        DeliveryAuditView audit,
        String actorId,
        Instant occurredAt,
        CommandEventView command
    ) {
        if (current == null) throw new IllegalArgumentException("current candidate is required");
        int updated = compareAndSetVersion(
            current.tenantId(),
            current.id(),
            expectedVersion,
            replacementStatus,
            audit,
            actorId,
            occurredAt
        );
        if (updated == 0) return 0;
        appendCommandOrFail(command);
        return updated;
    }

    public int appendCommand(CommandEventView command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        return jdbcTemplate.update(
            """
            insert into modeling_model_release_candidate_command (
                id, tenant_id, candidate_id, plan_id, candidate_version, event_type,
                from_status, to_status, actor_id, occurred_at, reason, idempotency_key,
                request_hash, response_snapshot
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            on conflict (tenant_id, idempotency_key) do nothing
            """,
            command.id(),
            command.tenantId(),
            command.candidateId(),
            command.planId(),
            command.candidateVersion(),
            command.eventType().name(),
            command.fromStatus() == null ? null : command.fromStatus().name(),
            command.toStatus().name(),
            command.actorId(),
            Timestamp.from(command.occurredAt()),
            command.reason(),
            command.idempotencyKey(),
            command.requestHash(),
            command.responseSnapshot()
        );
    }

    /**
     * Loads the paged candidate headers and their entries from one PostgreSQL statement. This keeps a coherent MVCC
     * snapshot while a concurrent command updates both header and entry status in the same transaction.
     */
    private List<CandidateView> queryCandidates(String headerSelection, Object... arguments) {
        return queryCandidatesWithOrder(headerSelection, "c.last_modified_date desc, c.id", arguments);
    }

    private List<CandidateView> queryCandidatesWithOrder(
        String headerSelection,
        String candidateOrder,
        Object... arguments
    ) {
        String sql = """
            with selected_candidate as (
                %s
            )
            select c.id as candidate_header_id,
                   c.tenant_id as candidate_tenant_id,
                   c.plan_id as candidate_plan_id,
                   c.environment as candidate_environment,
                   c.status as candidate_status,
                   c.version as candidate_version,
                   c.idempotency_key as candidate_idempotency_key,
                   c.request_hash as candidate_request_hash,
                   c.created_by as candidate_created_by,
                   c.created_date as candidate_created_date,
                   c.submitted_by as candidate_submitted_by,
                   c.submitted_date as candidate_submitted_date,
                   c.approved_by as candidate_approved_by,
                   c.approved_date as candidate_approved_date,
                   c.published_by as candidate_published_by,
                   c.published_date as candidate_published_date,
                   c.last_modified_by as candidate_last_modified_by,
                   c.last_modified_date as candidate_last_modified_date,
                   c.origin as candidate_origin,
                   c.execution_target_key as candidate_execution_target_key,
                   c.adapter as candidate_adapter,
                   c.profile_key as candidate_profile_key,
                   c.target_name as candidate_target_name,
                   e.id as entry_id,
                   e.tenant_id as entry_tenant_id,
                   e.candidate_id as entry_candidate_id,
                   e.plan_id as entry_plan_id,
                   e.model_spec_id as entry_model_spec_id,
                   e.revision as entry_revision,
                   e.checksum as entry_checksum,
                   e.implementation_id as entry_implementation_id,
                   e.implementation_mode as entry_implementation_mode,
                   e.status as entry_status,
                   e.sort_order as entry_sort_order,
                   e.selected_reason as entry_selected_reason
              from selected_candidate c
              left join modeling_model_release_candidate_entry e
                on e.tenant_id = c.tenant_id
               and e.candidate_id = c.id
             order by %s, e.sort_order, e.id
            """.formatted(headerSelection, candidateOrder);
        return jdbcTemplate.query(
            sql,
            row -> {
                LinkedHashMap<UUID, CandidateHeader> headers = new LinkedHashMap<>();
                LinkedHashMap<UUID, List<EntryView>> entries = new LinkedHashMap<>();
                while (row.next()) {
                    UUID candidateId = row.getObject("candidate_header_id", UUID.class);
                    headers.computeIfAbsent(candidateId, ignored -> mapJoinedHeader(row));
                    UUID entryId = row.getObject("entry_id", UUID.class);
                    if (entryId != null) {
                        entries.computeIfAbsent(candidateId, ignored -> new ArrayList<>()).add(mapJoinedEntry(row, entryId));
                    }
                }
                return headers
                    .values()
                    .stream()
                    .map(header -> toCandidate(header, entries.getOrDefault(header.id(), List.of())))
                    .toList();
            },
            arguments
        );
    }

    private static CandidateHeader mapJoinedHeader(ResultSet row) {
        try {
            return new CandidateHeader(
                row.getObject("candidate_header_id", UUID.class),
                row.getString("candidate_tenant_id"),
                row.getObject("candidate_plan_id", UUID.class),
                row.getString("candidate_environment"),
                DeliveryStatus.valueOf(row.getString("candidate_status")),
                row.getInt("candidate_version"),
                row.getString("candidate_idempotency_key"),
                row.getString("candidate_request_hash"),
                new DeliveryAuditView(
                    row.getString("candidate_created_by"),
                    instant(row, "candidate_created_date"),
                    row.getString("candidate_submitted_by"),
                    instant(row, "candidate_submitted_date"),
                    row.getString("candidate_approved_by"),
                    instant(row, "candidate_approved_date"),
                    row.getString("candidate_published_by"),
                    instant(row, "candidate_published_date")
                ),
                row.getString("candidate_last_modified_by"),
                instant(row, "candidate_last_modified_date"),
                CandidateOrigin.valueOf(row.getString("candidate_origin")),
                row.getString("candidate_execution_target_key"),
                row.getString("candidate_adapter"),
                row.getString("candidate_profile_key"),
                row.getString("candidate_target_name")
            );
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to map release candidate header", error);
        }
    }

    private static EntryView mapJoinedEntry(ResultSet row, UUID entryId) {
        try {
            return new EntryView(
                entryId,
                row.getString("entry_tenant_id"),
                row.getObject("entry_candidate_id", UUID.class),
                row.getObject("entry_plan_id", UUID.class),
                row.getObject("entry_model_spec_id", UUID.class),
                row.getInt("entry_revision"),
                row.getString("entry_checksum"),
                row.getObject("entry_implementation_id", UUID.class),
                ImplementationMode.valueOf(row.getString("entry_implementation_mode")),
                DeliveryStatus.valueOf(row.getString("entry_status")),
                row.getInt("entry_sort_order"),
                row.getString("entry_selected_reason")
            );
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to map release candidate entry", error);
        }
    }

    private CandidateView toCandidate(CandidateHeader header, List<EntryView> entries) {
        return new CandidateView(
            header.id(),
            header.tenantId(),
            header.planId(),
            header.environment(),
            header.status(),
            header.version(),
            header.idempotencyKey(),
            header.requestHash(),
            header.audit(),
            header.lastModifiedBy(),
            header.lastModifiedAt(),
            entries,
            header.origin(),
            header.executionTargetKey(),
            header.adapter(),
            header.profileKey(),
            header.targetName()
        );
    }

    private static CommandEventView mapCommand(ResultSet row) throws SQLException {
        String fromStatus = row.getString("from_status");
        return new CommandEventView(
            row.getObject("id", UUID.class),
            row.getString("tenant_id"),
            row.getObject("candidate_id", UUID.class),
            row.getObject("plan_id", UUID.class),
            row.getInt("candidate_version"),
            CommandEventType.valueOf(row.getString("event_type")),
            fromStatus == null ? null : DeliveryStatus.valueOf(fromStatus),
            DeliveryStatus.valueOf(row.getString("to_status")),
            row.getString("actor_id"),
            instant(row, "occurred_at"),
            row.getString("reason"),
            row.getString("idempotency_key"),
            row.getString("request_hash"),
            row.getString("response_snapshot")
        );
    }

    private void insertEntry(EntryView entry) {
        jdbcTemplate.update(
            """
            insert into modeling_model_release_candidate_entry (
                id, tenant_id, candidate_id, plan_id, model_spec_id, revision, checksum, implementation_id,
                implementation_mode, status, sort_order, selected_reason
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            entry.id(),
            entry.tenantId(),
            entry.candidateId(),
            entry.planId(),
            entry.modelSpecId(),
            entry.revision(),
            entry.checksum(),
            entry.implementationId(),
            entry.implementationMode().name(),
            entry.status().name(),
            entry.sortOrder(),
            entry.selectedReason()
        );
    }

    private void appendCommandOrFail(CommandEventView command) {
        if (appendCommand(command) == 0) {
            throw new IdempotencyCollisionException();
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void requireTenantAndId(String tenantId, UUID id) {
        requiredText(tenantId, "tenantId");
        if (id == null) throw new IllegalArgumentException("id is required");
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private record CandidateHeader(
        UUID id,
        String tenantId,
        UUID planId,
        String environment,
        DeliveryStatus status,
        int version,
        String idempotencyKey,
        String requestHash,
        DeliveryAuditView audit,
        String lastModifiedBy,
        Instant lastModifiedAt,
        CandidateOrigin origin,
        String executionTargetKey,
        String adapter,
        String profileKey,
        String targetName
    ) {}

    private record ClaimRow(UUID modelSpecId, String activeClaimKey) {}

    public static final class IdempotencyCollisionException extends RuntimeException {

        public IdempotencyCollisionException() {
            super("MODEL_RELEASE_CANDIDATE_IDEMPOTENCY_COLLISION");
        }
    }
}
