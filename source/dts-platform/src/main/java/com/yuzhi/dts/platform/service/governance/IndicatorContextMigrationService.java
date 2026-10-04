package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Explicit preview/apply/rollback lane for legacy indicator context; no fuzzy or first-candidate matching. */
@Service
public class IndicatorContextMigrationService {

    private static final int MAX_BATCH_SIZE = 500;
    private final JdbcTemplate jdbc;

    public IndicatorContextMigrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Preview preview(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        List<PreviewRow> rows = jdbc.query(
            """
            SELECT indicator.id, indicator.category, indicator.domain, indicator.is_derived,
                   indicator.business_category_id, indicator.data_domain_id, indicator.metric_type,
                   ARRAY(
                       SELECT candidate.id
                         FROM catalog_domain candidate
                        WHERE candidate.parent_id IS NULL
                          AND (lower(btrim(candidate.code)) = lower(btrim(indicator.category))
                               OR lower(btrim(candidate.name)) = lower(btrim(indicator.category)))
                        ORDER BY candidate.id
                   ) AS category_candidates,
                   ARRAY(
                       SELECT domain_candidate.id
                         FROM catalog_domain domain_candidate
                        WHERE domain_candidate.parent_id IN (
                                  SELECT category_candidate.id
                                    FROM catalog_domain category_candidate
                                   WHERE category_candidate.parent_id IS NULL
                                     AND (lower(btrim(category_candidate.code)) = lower(btrim(indicator.category))
                                          OR lower(btrim(category_candidate.name)) = lower(btrim(indicator.category)))
                              )
                          AND (lower(btrim(domain_candidate.code)) = lower(btrim(indicator.domain))
                               OR lower(btrim(domain_candidate.name)) = lower(btrim(indicator.domain)))
                        ORDER BY domain_candidate.id
                   ) AS domain_candidates
              FROM gov_indicator_definition indicator
             WHERE indicator.business_category_id IS NULL
                OR indicator.data_domain_id IS NULL
                OR indicator.metric_type IS NULL
             ORDER BY indicator.id
             LIMIT ?
            """,
            (rs, rowNum) -> previewRow(rs),
            limit
        );
        String hash = hash(rows);
        long totalPending = countPending();
        return new Preview(hash, rows, totalPending, rows.size() == limit && totalPending > rows.size());
    }

    @Transactional
    public ApplyResult apply(String expectedPreviewHash, int requestedLimit) {
        Preview preview = preview(requestedLimit);
        if (expectedPreviewHash == null || !expectedPreviewHash.equals(preview.previewHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INDICATOR_CONTEXT_PREVIEW_STALE");
        }
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        jdbc.update(
            """
            INSERT INTO indicator_context_migration_batch(
                id, preview_hash, status, row_count, created_by, created_at, applied_at
            ) VALUES (?, ?, 'APPLIED', ?, ?, ?, ?)
            """,
            batchId,
            preview.previewHash(),
            preview.rows().size(),
            actor,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        int applied = 0;
        for (PreviewRow row : preview.rows()) {
            if (row.proposedBusinessCategoryId() == null && row.proposedDataDomainId() == null && row.proposedMetricType() == null) {
                continue;
            }
            jdbc.update(
                """
                INSERT INTO indicator_context_migration_evidence(
                    batch_id, indicator_id, previous_business_category_id, previous_data_domain_id,
                    previous_business_process_id, previous_metric_type, previous_metric_group_code,
                    previous_source_refs, proposed_business_category_id, proposed_data_domain_id,
                    proposed_metric_type, created_at
                )
                SELECT ?, id, business_category_id, data_domain_id, business_process_id, metric_type,
                       metric_group_code, source_refs, ?, ?, ?, ?
                  FROM gov_indicator_definition
                 WHERE id = ?
                """,
                batchId,
                row.proposedBusinessCategoryId(),
                row.proposedDataDomainId(),
                row.proposedMetricType(),
                Timestamp.from(now),
                row.indicatorId()
            );
            int changed = jdbc.update(
                """
                UPDATE gov_indicator_definition
                   SET business_category_id = coalesce(business_category_id, ?),
                       data_domain_id = coalesce(data_domain_id, ?),
                       metric_type = coalesce(metric_type, ?),
                       last_modified_by = ?,
                       last_modified_date = ?
                 WHERE id = ?
                """,
                row.proposedBusinessCategoryId(),
                row.proposedDataDomainId(),
                row.proposedMetricType(),
                actor,
                Timestamp.from(now),
                row.indicatorId()
            );
            if (changed > 0) {
                applied++;
                resolveAppliedIssues(batchId, actor, row, now);
            }
        }
        return new ApplyResult(batchId, preview.previewHash(), preview.rows().size(), applied, now);
    }

    @Transactional
    public RollbackResult rollback(UUID batchId) {
        Batch batch = batch(batchId);
        if (!"APPLIED".equals(batch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INDICATOR_CONTEXT_BATCH_NOT_APPLIED");
        }
        Instant now = Instant.now();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        int restored = jdbc.update(
            """
            UPDATE gov_indicator_definition indicator
               SET business_category_id = evidence.previous_business_category_id,
                   data_domain_id = evidence.previous_data_domain_id,
                   business_process_id = evidence.previous_business_process_id,
                   metric_type = evidence.previous_metric_type,
                   metric_group_code = evidence.previous_metric_group_code,
                   source_refs = evidence.previous_source_refs,
                   last_modified_by = ?,
                   last_modified_date = ?
              FROM indicator_context_migration_evidence evidence
             WHERE evidence.batch_id = ? AND indicator.id = evidence.indicator_id
            """,
            actor,
            Timestamp.from(now),
            batchId
        );
        jdbc.update(
            """
            UPDATE indicator_context_migration_issue
               SET status = 'PENDING', resolved_by = NULL, resolved_at = NULL
             WHERE resolved_by LIKE ?
            """,
            "batch:" + batchId + ":%"
        );
        jdbc.update(
            """
            UPDATE indicator_context_migration_batch
               SET status = 'ROLLED_BACK', rolled_back_at = ?
             WHERE id = ? AND status = 'APPLIED'
            """,
            Timestamp.from(now),
            batchId
        );
        return new RollbackResult(batchId, restored, now);
    }

    private PreviewRow previewRow(ResultSet rs) throws SQLException {
        List<UUID> categoryCandidates = uuidArray(rs.getArray("category_candidates"));
        List<UUID> domainCandidates = uuidArray(rs.getArray("domain_candidates"));
        UUID existingCategory = rs.getObject("business_category_id", UUID.class);
        UUID existingDomain = rs.getObject("data_domain_id", UUID.class);
        String existingMetricType = rs.getString("metric_type");
        Boolean isDerived = nullableBoolean(rs, "is_derived");
        UUID proposedCategory = existingCategory == null && categoryCandidates.size() == 1
            ? categoryCandidates.get(0)
            : null;
        UUID proposedDomain = existingDomain == null && domainCandidates.size() == 1
            ? domainCandidates.get(0)
            : null;
        String proposedMetricType = existingMetricType == null && !Boolean.TRUE.equals(isDerived) ? "ATOMIC" : null;
        List<String> issues = new ArrayList<>();
        if (existingCategory == null && categoryCandidates.size() != 1) {
            issues.add(categoryCandidates.isEmpty() ? "BUSINESS_CATEGORY_MATCH_MISSING" : "BUSINESS_CATEGORY_MATCH_AMBIGUOUS");
        }
        if (existingDomain == null && domainCandidates.size() != 1) {
            issues.add(domainCandidates.isEmpty() ? "DATA_DOMAIN_MATCH_MISSING" : "DATA_DOMAIN_MATCH_AMBIGUOUS");
        }
        if (existingMetricType == null && Boolean.TRUE.equals(isDerived)) {
            issues.add("DERIVED_TYPE_AMBIGUOUS");
        }
        return new PreviewRow(
            rs.getObject("id", UUID.class),
            rs.getString("category"),
            rs.getString("domain"),
            existingCategory,
            existingDomain,
            existingMetricType,
            proposedCategory,
            proposedDomain,
            proposedMetricType,
            categoryCandidates,
            domainCandidates,
            issues
        );
    }

    private void resolveAppliedIssues(UUID batchId, String actor, PreviewRow row, Instant now) {
        String resolvedBy = "batch:" + batchId + ":" + actor;
        if (row.proposedBusinessCategoryId() != null) {
            resolveIssue(row.indicatorId(), "businessCategoryId", resolvedBy, now);
        }
        if (row.proposedDataDomainId() != null) {
            resolveIssue(row.indicatorId(), "dataDomainId", resolvedBy, now);
        }
        if (row.proposedMetricType() != null) {
            resolveIssue(row.indicatorId(), "metricType", resolvedBy, now);
        }
    }

    private void resolveIssue(UUID indicatorId, String field, String actor, Instant now) {
        jdbc.update(
            """
            UPDATE indicator_context_migration_issue
               SET status = 'RESOLVED', resolved_by = ?, resolved_at = ?
             WHERE indicator_id = ? AND field_name = ? AND status = 'PENDING'
            """,
            actor,
            Timestamp.from(now),
            indicatorId,
            field
        );
    }

    private Batch batch(UUID batchId) {
        List<Batch> rows = jdbc.query(
            "SELECT id, status FROM indicator_context_migration_batch WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new Batch(rs.getObject("id", UUID.class), rs.getString("status")),
            batchId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "INDICATOR_CONTEXT_BATCH_NOT_FOUND");
        }
        return rows.get(0);
    }

    private long countPending() {
        Long value = jdbc.queryForObject(
            """
            SELECT count(*) FROM gov_indicator_definition
             WHERE business_category_id IS NULL OR data_domain_id IS NULL OR metric_type IS NULL
            """,
            Long.class
        );
        return value == null ? 0 : value;
    }

    private String hash(List<PreviewRow> rows) {
        StringBuilder canonical = new StringBuilder();
        rows.forEach(row -> canonical
            .append(row.indicatorId()).append('|')
            .append(row.proposedBusinessCategoryId()).append('|')
            .append(row.proposedDataDomainId()).append('|')
            .append(row.proposedMetricType()).append('|')
            .append(String.join(",", row.issueCodes())).append('\n'));
        return DigestUtils.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static List<UUID> uuidArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) {
            return List.of();
        }
        return Arrays.stream(values).map(value -> value instanceof UUID uuid ? uuid : UUID.fromString(value.toString())).toList();
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    public record Preview(String previewHash, List<PreviewRow> rows, long totalPending, boolean truncated) {
        public Preview {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record PreviewRow(
        UUID indicatorId,
        String legacyCategory,
        String legacyDomain,
        UUID currentBusinessCategoryId,
        UUID currentDataDomainId,
        String currentMetricType,
        UUID proposedBusinessCategoryId,
        UUID proposedDataDomainId,
        String proposedMetricType,
        List<UUID> businessCategoryCandidates,
        List<UUID> dataDomainCandidates,
        List<String> issueCodes
    ) {
        public PreviewRow {
            businessCategoryCandidates = List.copyOf(businessCategoryCandidates);
            dataDomainCandidates = List.copyOf(dataDomainCandidates);
            issueCodes = List.copyOf(issueCodes);
        }
    }

    public record ApplyResult(UUID batchId, String previewHash, int previewed, int applied, Instant appliedAt) {}

    public record RollbackResult(UUID batchId, int restored, Instant rolledBackAt) {}

    private record Batch(UUID id, String status) {}
}
