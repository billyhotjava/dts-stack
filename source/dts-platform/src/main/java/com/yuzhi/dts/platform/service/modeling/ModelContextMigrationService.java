package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Exact-match, reversible stable-context backfill for FACT and APPLICATION model heads/revisions. */
@Service
public class ModelContextMigrationService {

    private static final int MAX_BATCH_SIZE = 500;
    private final JdbcTemplate jdbc;

    public ModelContextMigrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Preview preview(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        List<PreviewRow> rows = jdbc.query(
            """
            WITH migration_scope AS (
                SELECT head.tenant_id, head.id AS model_spec_id, 0 AS revision_scope,
                       NULL::integer AS revision, head.model_type, head.domain_id,
                       head.business_activity_ref, head.data_mart_id,
                       head.business_process_id, head.subject_domain_id
                  FROM modeling_model_spec head
                 WHERE (head.model_type = 'FACT' AND head.business_process_id IS NULL)
                    OR (head.model_type = 'APPLICATION' AND head.subject_domain_id IS NULL)
                UNION ALL
                SELECT revision.tenant_id, revision.model_spec_id, revision.revision AS revision_scope,
                       revision.revision, revision.model_type, revision.domain_id,
                       revision.business_activity_ref, revision.data_mart_id,
                       revision.business_process_id, revision.subject_domain_id
                  FROM modeling_model_spec_revision revision
                 WHERE (revision.model_type = 'FACT' AND revision.business_process_id IS NULL)
                    OR (revision.model_type = 'APPLICATION' AND revision.subject_domain_id IS NULL)
            )
            SELECT scope.*,
                   CASE WHEN scope.model_type = 'FACT' THEN ARRAY(
                       SELECT process.id
                         FROM sprint64_business_process process
                        WHERE process.domain_id = scope.domain_id
                          AND process.confirmed = true
                          AND (lower(btrim(process.process_id)) = lower(btrim(scope.business_activity_ref))
                               OR lower(btrim(process.name)) = lower(btrim(scope.business_activity_ref)))
                        ORDER BY process.id
                   ) ELSE ARRAY[]::uuid[] END AS process_candidates,
                   CASE WHEN scope.model_type = 'APPLICATION' THEN ARRAY(
                       SELECT subject.id
                         FROM modeling_subject_domain subject
                        WHERE subject.tenant_id = scope.tenant_id
                          AND subject.mart_id = scope.data_mart_id
                          AND upper(subject.status) = 'CURRENT'
                        ORDER BY subject.id
                   ) ELSE ARRAY[]::uuid[] END AS subject_candidates
              FROM migration_scope scope
             ORDER BY scope.tenant_id, scope.model_spec_id, scope.revision_scope
             LIMIT ?
            """,
            (rs, rowNum) -> previewRow(rs),
            limit
        );
        long totalPending = countPending();
        return new Preview(hash(rows), rows, totalPending, rows.size() == limit && totalPending > rows.size());
    }

    @Transactional
    public ApplyResult apply(String expectedPreviewHash, int requestedLimit, String correlationId) {
        Preview preview = preview(requestedLimit);
        if (!StringUtils.hasText(expectedPreviewHash) || !expectedPreviewHash.equals(preview.previewHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_CONTEXT_PREVIEW_STALE");
        }
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        jdbc.update(
            """
            INSERT INTO modeling_model_context_migration_batch(
                id, preview_hash, status, previewed_count, applied_count, created_by,
                correlation_id, created_date, applied_date
            ) VALUES (?, ?, 'APPLIED', ?, 0, ?, ?, ?, ?)
            """,
            batchId,
            preview.previewHash(),
            preview.rows().size(),
            actor,
            trimToNull(correlationId),
            Timestamp.from(now),
            Timestamp.from(now)
        );

        int applied = 0;
        for (PreviewRow row : preview.rows()) {
            if (row.issueCode() != null) {
                persistIssue(row, now);
                continue;
            }
            jdbc.update(
                """
                INSERT INTO modeling_model_context_migration_evidence(
                    batch_id, tenant_id, model_spec_id, revision_scope,
                    previous_business_process_id, previous_subject_domain_id,
                    proposed_business_process_id, proposed_subject_domain_id,
                    item_checksum, created_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                batchId,
                row.tenantId(),
                row.modelSpecId(),
                row.revisionScope(),
                row.currentBusinessProcessId(),
                row.currentSubjectDomainId(),
                row.proposedBusinessProcessId(),
                row.proposedSubjectDomainId(),
                itemChecksum(row),
                Timestamp.from(now)
            );
            int changed = applyRow(row);
            if (changed != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_CONTEXT_ROW_DRIFT");
            }
            resolveIssue(row, batchId, actor, now);
            applied++;
        }
        jdbc.update(
            "UPDATE modeling_model_context_migration_batch SET applied_count = ? WHERE id = ?",
            applied,
            batchId
        );
        return new ApplyResult(batchId, preview.previewHash(), preview.rows().size(), applied, preview.rows().size() - applied, now);
    }

    @Transactional
    public RollbackResult rollback(UUID batchId) {
        Batch batch = batch(batchId);
        if (!"APPLIED".equals(batch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_CONTEXT_BATCH_NOT_APPLIED");
        }
        List<Evidence> evidence = jdbc.query(
            """
            SELECT tenant_id, model_spec_id, revision_scope,
                   previous_business_process_id, previous_subject_domain_id,
                   proposed_business_process_id, proposed_subject_domain_id
              FROM modeling_model_context_migration_evidence
             WHERE batch_id = ?
             ORDER BY tenant_id, model_spec_id, revision_scope
            """,
            (rs, rowNum) ->
                new Evidence(
                    rs.getString("tenant_id"),
                    rs.getObject("model_spec_id", UUID.class),
                    rs.getInt("revision_scope"),
                    rs.getObject("previous_business_process_id", UUID.class),
                    rs.getObject("previous_subject_domain_id", UUID.class),
                    rs.getObject("proposed_business_process_id", UUID.class),
                    rs.getObject("proposed_subject_domain_id", UUID.class)
                ),
            batchId
        );
        evidence.forEach(this::assertRollbackNotDrifted);
        int restored = 0;
        for (Evidence item : evidence) {
            restored += restore(item);
        }
        jdbc.update(
            """
            UPDATE modeling_model_context_migration_issue
               SET resolution_status = 'PENDING', resolved_reference_id = NULL,
                   resolution_note = NULL, last_modified_date = ?
             WHERE resolution_note LIKE ?
            """,
            Timestamp.from(Instant.now()),
            "batch:" + batchId + ":%"
        );
        Instant now = Instant.now();
        jdbc.update(
            """
            UPDATE modeling_model_context_migration_batch
               SET status = 'ROLLED_BACK', rolled_back_date = ?
             WHERE id = ? AND status = 'APPLIED'
            """,
            Timestamp.from(now),
            batchId
        );
        return new RollbackResult(batchId, restored, now);
    }

    private PreviewRow previewRow(ResultSet rs) throws SQLException {
        List<UUID> processCandidates = uuidArray(rs.getArray("process_candidates"));
        List<UUID> subjectCandidates = uuidArray(rs.getArray("subject_candidates"));
        String modelType = rs.getString("model_type");
        UUID proposedProcess = "FACT".equals(modelType) && processCandidates.size() == 1 ? processCandidates.get(0) : null;
        UUID proposedSubject = "APPLICATION".equals(modelType) && subjectCandidates.size() == 1
            ? subjectCandidates.get(0)
            : null;
        String issueCode = null;
        if ("FACT".equals(modelType) && processCandidates.size() != 1) {
            issueCode = processCandidates.isEmpty()
                ? "BUSINESS_PROCESS_MATCH_MISSING"
                : "BUSINESS_PROCESS_MATCH_AMBIGUOUS";
        } else if ("APPLICATION".equals(modelType) && subjectCandidates.size() != 1) {
            issueCode = subjectCandidates.isEmpty()
                ? "SUBJECT_DOMAIN_MATCH_MISSING"
                : "SUBJECT_DOMAIN_MATCH_AMBIGUOUS";
        }
        return new PreviewRow(
            rs.getString("tenant_id"),
            rs.getObject("model_spec_id", UUID.class),
            rs.getInt("revision_scope"),
            rs.getObject("revision") == null ? null : rs.getInt("revision"),
            modelType,
            rs.getObject("domain_id", UUID.class),
            rs.getString("business_activity_ref"),
            rs.getObject("data_mart_id", UUID.class),
            rs.getObject("business_process_id", UUID.class),
            rs.getObject("subject_domain_id", UUID.class),
            proposedProcess,
            proposedSubject,
            processCandidates,
            subjectCandidates,
            issueCode
        );
    }

    private int applyRow(PreviewRow row) {
        if (row.revisionScope() == 0) {
            return jdbc.update(
                """
                UPDATE modeling_model_spec
                   SET business_process_id = coalesce(business_process_id, ?),
                       subject_domain_id = coalesce(subject_domain_id, ?)
                 WHERE tenant_id = ? AND id = ?
                   AND business_process_id IS NOT DISTINCT FROM ?
                   AND subject_domain_id IS NOT DISTINCT FROM ?
                """,
                row.proposedBusinessProcessId(),
                row.proposedSubjectDomainId(),
                row.tenantId(),
                row.modelSpecId(),
                row.currentBusinessProcessId(),
                row.currentSubjectDomainId()
            );
        }
        return jdbc.update(
            """
            UPDATE modeling_model_spec_revision
               SET business_process_id = coalesce(business_process_id, ?),
                   subject_domain_id = coalesce(subject_domain_id, ?)
             WHERE tenant_id = ? AND model_spec_id = ? AND revision = ?
               AND business_process_id IS NOT DISTINCT FROM ?
               AND subject_domain_id IS NOT DISTINCT FROM ?
            """,
            row.proposedBusinessProcessId(),
            row.proposedSubjectDomainId(),
            row.tenantId(),
            row.modelSpecId(),
            row.revisionScope(),
            row.currentBusinessProcessId(),
            row.currentSubjectDomainId()
        );
    }

    private void persistIssue(PreviewRow row, Instant now) {
        jdbc.update(
            """
            INSERT INTO modeling_model_context_migration_issue(
                id, tenant_id, model_spec_id, revision, issue_code, legacy_value,
                candidate_refs, resolution_status, created_date, last_modified_date
            )
            SELECT ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?, ?
             WHERE NOT EXISTS (
                 SELECT 1 FROM modeling_model_context_migration_issue existing
                  WHERE existing.tenant_id = ? AND existing.model_spec_id = ?
                    AND coalesce(existing.revision, 0) = ? AND existing.issue_code = ?
             )
            """,
            UUID.randomUUID(),
            row.tenantId(),
            row.modelSpecId(),
            row.revision(),
            row.issueCode(),
            "FACT".equals(row.modelType()) ? row.businessActivityRef() : value(row.dataMartId()),
            jsonCandidates(row),
            Timestamp.from(now),
            Timestamp.from(now),
            row.tenantId(),
            row.modelSpecId(),
            row.revisionScope(),
            row.issueCode()
        );
    }

    private void resolveIssue(PreviewRow row, UUID batchId, String actor, Instant now) {
        UUID resolved = row.proposedBusinessProcessId() != null
            ? row.proposedBusinessProcessId()
            : row.proposedSubjectDomainId();
        jdbc.update(
            """
            UPDATE modeling_model_context_migration_issue
               SET resolution_status = 'RESOLVED', resolved_reference_id = ?,
                   resolution_note = ?, last_modified_date = ?
             WHERE tenant_id = ? AND model_spec_id = ?
               AND coalesce(revision, 0) = ? AND resolution_status = 'PENDING'
            """,
            resolved,
            "batch:" + batchId + ":" + actor,
            Timestamp.from(now),
            row.tenantId(),
            row.modelSpecId(),
            row.revisionScope()
        );
    }

    private void assertRollbackNotDrifted(Evidence evidence) {
        CurrentContext current = currentContext(evidence);
        if (
            !Objects.equals(current.businessProcessId(), evidence.proposedBusinessProcessId()) ||
            !Objects.equals(current.subjectDomainId(), evidence.proposedSubjectDomainId())
        ) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_CONTEXT_ROLLBACK_DRIFT");
        }
    }

    private CurrentContext currentContext(Evidence evidence) {
        String sql = evidence.revisionScope() == 0
            ? "SELECT business_process_id, subject_domain_id FROM modeling_model_spec WHERE tenant_id = ? AND id = ?"
            : "SELECT business_process_id, subject_domain_id FROM modeling_model_spec_revision WHERE tenant_id = ? AND model_spec_id = ? AND revision = ?";
        Object[] args = evidence.revisionScope() == 0
            ? new Object[] { evidence.tenantId(), evidence.modelSpecId() }
            : new Object[] { evidence.tenantId(), evidence.modelSpecId(), evidence.revisionScope() };
        List<CurrentContext> rows = jdbc.query(
            sql,
            (rs, rowNum) ->
                new CurrentContext(
                    rs.getObject("business_process_id", UUID.class),
                    rs.getObject("subject_domain_id", UUID.class)
                ),
            args
        );
        if (rows.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_CONTEXT_ROLLBACK_TARGET_MISSING");
        }
        return rows.get(0);
    }

    private int restore(Evidence evidence) {
        if (evidence.revisionScope() == 0) {
            return jdbc.update(
                """
                UPDATE modeling_model_spec
                   SET business_process_id = ?, subject_domain_id = ?
                 WHERE tenant_id = ? AND id = ?
                """,
                evidence.previousBusinessProcessId(),
                evidence.previousSubjectDomainId(),
                evidence.tenantId(),
                evidence.modelSpecId()
            );
        }
        return jdbc.update(
            """
            UPDATE modeling_model_spec_revision
               SET business_process_id = ?, subject_domain_id = ?
             WHERE tenant_id = ? AND model_spec_id = ? AND revision = ?
            """,
            evidence.previousBusinessProcessId(),
            evidence.previousSubjectDomainId(),
            evidence.tenantId(),
            evidence.modelSpecId(),
            evidence.revisionScope()
        );
    }

    private Batch batch(UUID batchId) {
        List<Batch> rows = jdbc.query(
            "SELECT id, status FROM modeling_model_context_migration_batch WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new Batch(rs.getObject("id", UUID.class), rs.getString("status")),
            batchId
        );
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MODEL_CONTEXT_BATCH_NOT_FOUND");
        return rows.get(0);
    }

    private long countPending() {
        Long value = jdbc.queryForObject(
            """
            SELECT (
                SELECT count(*) FROM modeling_model_spec
                 WHERE (model_type = 'FACT' AND business_process_id IS NULL)
                    OR (model_type = 'APPLICATION' AND subject_domain_id IS NULL)
            ) + (
                SELECT count(*) FROM modeling_model_spec_revision
                 WHERE (model_type = 'FACT' AND business_process_id IS NULL)
                    OR (model_type = 'APPLICATION' AND subject_domain_id IS NULL)
            )
            """,
            Long.class
        );
        return value == null ? 0 : value;
    }

    private String hash(List<PreviewRow> rows) {
        StringBuilder canonical = new StringBuilder();
        rows.forEach(row -> canonical
            .append(row.tenantId()).append('|')
            .append(row.modelSpecId()).append('|')
            .append(row.revisionScope()).append('|')
            .append(row.proposedBusinessProcessId()).append('|')
            .append(row.proposedSubjectDomainId()).append('|')
            .append(row.issueCode()).append('\n'));
        return DigestUtils.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String itemChecksum(PreviewRow row) {
        String value = String.join(
            "|",
            row.tenantId(),
            row.modelSpecId().toString(),
            String.valueOf(row.revisionScope()),
            String.valueOf(row.currentBusinessProcessId()),
            String.valueOf(row.currentSubjectDomainId()),
            String.valueOf(row.proposedBusinessProcessId()),
            String.valueOf(row.proposedSubjectDomainId())
        );
        return DigestUtils.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private String jsonCandidates(PreviewRow row) {
        List<UUID> values = "FACT".equals(row.modelType()) ? row.businessProcessCandidates() : row.subjectDomainCandidates();
        return values.stream().map(UUID::toString).map(value -> "\"" + value + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static List<UUID> uuidArray(Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) return List.of();
        return Arrays.stream(values).map(value -> value instanceof UUID uuid ? uuid : UUID.fromString(value.toString())).toList();
    }

    private static String value(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record Preview(String previewHash, List<PreviewRow> rows, long totalPending, boolean truncated) {
        public Preview {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record PreviewRow(
        String tenantId,
        UUID modelSpecId,
        int revisionScope,
        Integer revision,
        String modelType,
        UUID domainId,
        String businessActivityRef,
        UUID dataMartId,
        UUID currentBusinessProcessId,
        UUID currentSubjectDomainId,
        UUID proposedBusinessProcessId,
        UUID proposedSubjectDomainId,
        List<UUID> businessProcessCandidates,
        List<UUID> subjectDomainCandidates,
        String issueCode
    ) {
        public PreviewRow {
            businessProcessCandidates = List.copyOf(businessProcessCandidates);
            subjectDomainCandidates = List.copyOf(subjectDomainCandidates);
        }
    }

    public record ApplyResult(
        UUID batchId,
        String previewHash,
        int previewed,
        int applied,
        int unresolved,
        Instant appliedAt
    ) {}

    public record RollbackResult(UUID batchId, int restored, Instant rolledBackAt) {}

    private record Evidence(
        String tenantId,
        UUID modelSpecId,
        int revisionScope,
        UUID previousBusinessProcessId,
        UUID previousSubjectDomainId,
        UUID proposedBusinessProcessId,
        UUID proposedSubjectDomainId
    ) {}

    private record CurrentContext(UUID businessProcessId, UUID subjectDomainId) {}

    private record Batch(UUID id, String status) {}
}
