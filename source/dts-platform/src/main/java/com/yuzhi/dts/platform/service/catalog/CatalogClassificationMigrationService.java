package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogClassificationMigrationService {

    private static final Set<String> FREEZABLE_TABLES = Set.of(
        "catalog_dataset",
        "catalog_table_schema",
        "svc_api",
        "svc_data_product",
        "catalog_data_product",
        "bi_report_link",
        "infra_external_exchange_file"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ClassificationMigrationItemWorker itemWorker;

    public CatalogClassificationMigrationService(
        JdbcTemplate jdbcTemplate,
        ClassificationMigrationItemWorker itemWorker
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.itemWorker = itemWorker;
    }

    @Transactional
    public MigrationRunView dryRun(DryRunCommand command, String actor) {
        String idempotencyKey = required(command == null ? null : command.idempotencyKey(), "idempotencyKey", 128);
        MigrationRunView existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        int batchSize = Math.max(1, Math.min(command.batchSize() == null ? 200 : command.batchSize(), 1000));
        UUID runId = UUID.randomUUID();
        Timestamp now = nowTimestamp();
        jdbcTemplate.update(
            """
            insert into catalog_classification_migration_run (
                id, idempotency_key, mode, status, batch_size, cursor_position,
                requested_by, started_at, record_version, created_by, created_date,
                last_modified_by, last_modified_date
            ) values (?, ?, 'DRY_RUN', 'DRY_RUN_RUNNING', ?, 0, ?, ?, 0, ?, ?, ?, ?)
            """,
            runId,
            idempotencyKey,
            batchSize,
            actor,
            now,
            actor,
            now,
            actor,
            now
        );

        List<Candidate> candidates = inventory();
        Map<String, ExistingFact> existingFacts = existingFacts();
        candidates.sort(Comparator.comparing(Candidate::sourceTable).thenComparing(Candidate::sourceId));
        long sequence = 0;
        long eligible = 0;
        long blocked = 0;
        List<String> itemChecksums = new ArrayList<>();
        for (Candidate candidate : candidates) {
            ExistingFact existingFact = existingFacts.get(candidate.subjectType() + "\u0000" + candidate.subjectKey());
            Decision decision = decide(candidate, existingFact);
            if (decision.code().startsWith("BLOCKED_")) {
                blocked++;
            } else if (!"UNCHANGED".equals(decision.code())) {
                eligible++;
            }
            String itemChecksum = sha256(
                String.join(
                    "|",
                    candidate.sourceTable(),
                    candidate.sourceId(),
                    candidate.subjectType(),
                    candidate.subjectKey(),
                    nullToEmpty(candidate.legacyLevel()),
                    nullToEmpty(candidate.detectedLevel()),
                    nullToEmpty(decision.computedLevel()),
                    decision.code()
                )
            );
            itemChecksums.add(itemChecksum);
            jdbcTemplate.update(
                """
                insert into catalog_classification_migration_item (
                    id, run_id, sequence_no, source_table, source_id, subject_type,
                    subject_key, asset_type, legacy_level, detected_level,
                    existing_effective_level, computed_effective_level, decision,
                    decision_reason, item_checksum, apply_status,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                runId,
                sequence++,
                candidate.sourceTable(),
                candidate.sourceId(),
                candidate.subjectType(),
                candidate.subjectKey(),
                candidate.assetType(),
                candidate.legacyLevel(),
                candidate.detectedLevel(),
                existingFact == null ? null : existingFact.effectiveLevel(),
                decision.computedLevel(),
                decision.code(),
                decision.reason(),
                itemChecksum,
                actor,
                now,
                actor,
                now
            );
        }
        String reportChecksum = sha256(String.join("", itemChecksums));
        jdbcTemplate.update(
            """
            update catalog_classification_migration_run
               set status='DRY_RUN_COMPLETE', total_items=?, eligible_items=?, blocked_items=?,
                   report_checksum=?, completed_at=?, record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=?
            """,
            candidates.size(),
            eligible,
            blocked,
            reportChecksum,
            nowTimestamp(),
            actor,
            nowTimestamp(),
            runId
        );
        return get(runId);
    }

    public MigrationRunView applyBatch(UUID runId, int requestedBatchSize, String actor) {
        MigrationRunView run = get(runId);
        if (!List.of("DRY_RUN_COMPLETE", "APPLY_RUNNING", "PAUSED", "RECONCILIATION_FAILED").contains(run.status())) {
            throw new IllegalStateException("Migration run cannot apply from status " + run.status());
        }
        if ("PAUSED".equals(run.status())) {
            throw new IllegalStateException("Migration run is paused; resume it before applying");
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize > 0 ? requestedBatchSize : run.batchSize(), 1000));
        jdbcTemplate.update(
            """
            update catalog_classification_migration_run
               set status='APPLY_RUNNING', completed_at=null, last_error=null,
                   record_version=record_version+1, last_modified_by=?, last_modified_date=?
             where id=? and status in ('DRY_RUN_COMPLETE','APPLY_RUNNING','RECONCILIATION_FAILED')
            """,
            actor,
            nowTimestamp(),
            runId
        );
        List<ItemPointer> items = jdbcTemplate.query(
            """
            select id, sequence_no
              from catalog_classification_migration_item
             where run_id=? and apply_status in ('PENDING','FAILED')
             order by sequence_no
             limit ?
            """,
            (rs, rowNum) -> new ItemPointer(rs.getObject("id", UUID.class), rs.getLong("sequence_no")),
            runId,
            batchSize
        );
        long cursor = run.cursorPosition();
        for (ItemPointer item : items) {
            try {
                itemWorker.apply(item.id(), actor);
            } catch (RuntimeException failure) {
                itemWorker.fail(item.id(), actor, failure.getMessage());
            }
            cursor = Math.max(cursor, item.sequenceNo() + 1);
        }
        updateProgress(runId, cursor, actor);
        long remaining = jdbcTemplate.queryForObject(
            "select count(*) from catalog_classification_migration_item where run_id=? and apply_status in ('PENDING','FAILED')",
            Long.class,
            runId
        );
        if (remaining == 0) {
            ReconciliationView reconciliation = reconcile(runId);
            jdbcTemplate.update(
                """
                update catalog_classification_migration_run
                   set status=?, mismatch_items=?, completed_at=?,
                       record_version=record_version+1, last_modified_by=?, last_modified_date=?
                 where id=?
                """,
                reconciliation.mismatchItems() == 0 ? "APPLY_COMPLETE" : "RECONCILIATION_FAILED",
                reconciliation.mismatchItems(),
                nowTimestamp(),
                actor,
                nowTimestamp(),
                runId
            );
        }
        return get(runId);
    }

    @Transactional
    public MigrationRunView pause(UUID runId, String actor) {
        jdbcTemplate.update(
            """
            update catalog_classification_migration_run
               set status='PAUSED', paused_at=?, record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=? and status='APPLY_RUNNING'
            """,
            nowTimestamp(),
            actor,
            nowTimestamp(),
            runId
        );
        return get(runId);
    }

    @Transactional
    public MigrationRunView resume(UUID runId, String actor) {
        jdbcTemplate.update(
            """
            update catalog_classification_migration_run
               set status='APPLY_RUNNING', paused_at=null, record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=? and status='PAUSED'
            """,
            actor,
            nowTimestamp(),
            runId
        );
        return get(runId);
    }

    @Transactional(readOnly = true)
    public ReconciliationView reconcile(UUID runId) {
        MigrationRunView run = get(runId);
        List<ReconciliationMismatch> mismatches = jdbcTemplate.query(
            """
            select i.source_table, i.source_id, i.subject_type, i.subject_key,
                   i.legacy_level, i.computed_effective_level,
                   s.effective_level as current_effective_level,
                   case
                     when i.decision like 'BLOCKED_%' then i.decision
                     when s.id is null then 'SNAPSHOT_MISSING'
                     when catalog_classification_level_rank(s.effective_level) <
                          catalog_classification_level_rank(i.computed_effective_level) then 'NEW_READ_LOWER_THAN_EXPECTED'
                     when catalog_classification_level_rank(s.effective_level) <
                          catalog_classification_level_rank(i.legacy_level) then 'NEW_READ_LOWER_THAN_LEGACY'
                     else null
                   end as mismatch_code
              from catalog_classification_migration_item i
              left join catalog_classification_snapshot s
                on s.subject_type=i.subject_type and s.subject_key=i.subject_key
             where i.run_id=?
               and (
                    i.decision like 'BLOCKED_%'
                    or s.id is null
                    or catalog_classification_level_rank(s.effective_level) <
                       catalog_classification_level_rank(i.computed_effective_level)
                    or catalog_classification_level_rank(s.effective_level) <
                       catalog_classification_level_rank(i.legacy_level)
               )
             order by i.sequence_no
             limit 1000
            """,
            (rs, rowNum) -> new ReconciliationMismatch(
                rs.getString("source_table"),
                rs.getString("source_id"),
                rs.getString("subject_type"),
                rs.getString("subject_key"),
                rs.getString("legacy_level"),
                rs.getString("computed_effective_level"),
                rs.getString("current_effective_level"),
                rs.getString("mismatch_code")
            ),
            runId
        );
        return new ReconciliationView(
            runId,
            run.totalItems(),
            mismatches.size(),
            mismatches,
            mismatches.isEmpty()
        );
    }

    @Transactional
    public List<WriteFreezeView> freezeLegacyWrites(
        UUID runId,
        List<String> requestedTables,
        String reason,
        String actor
    ) {
        MigrationRunView run = get(runId);
        if (!"APPLY_COMPLETE".equals(run.status())) {
            throw new IllegalStateException("Legacy writes can freeze only after successful reconciliation");
        }
        List<String> tables = requestedTables == null || requestedTables.isEmpty()
            ? FREEZABLE_TABLES.stream().sorted().toList()
            : requestedTables.stream().map(this::freezableTable).distinct().toList();
        for (String table : tables) {
            Long blockers = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from catalog_classification_migration_item
                 where run_id=? and source_table=?
                   and (decision like 'BLOCKED_%' or apply_status='FAILED')
                """,
                Long.class,
                runId,
                table
            );
            if (blockers != null && blockers > 0) {
                throw new IllegalStateException("Legacy write freeze blocked for " + table + ": unresolved migration items");
            }
            jdbcTemplate.update(
                """
                update catalog_classification_legacy_write_freeze
                   set enabled=true, reconciliation_run_id=?, frozen_by=?, frozen_at=?,
                       reason=?, last_modified_by=?, last_modified_date=?
                 where source_table=?
                """,
                runId,
                actor,
                nowTimestamp(),
                trim(reason),
                actor,
                nowTimestamp(),
                table
            );
        }
        return writeFreezes();
    }

    @Transactional(readOnly = true)
    public MigrationRunView get(UUID runId) {
        return jdbcTemplate
            .query(
                """
                select id, idempotency_key, mode, status, batch_size, cursor_position,
                       total_items, eligible_items, blocked_items, applied_items,
                       mismatch_items, report_checksum, requested_by, started_at,
                       completed_at, paused_at, last_error, record_version
                  from catalog_classification_migration_run
                 where id=?
                """,
                (rs, rowNum) -> runView(rs),
                runId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Migration run does not exist: " + runId));
    }

    @Transactional(readOnly = true)
    public List<MigrationItemView> items(UUID runId, String decision, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        String normalizedDecision = trim(decision);
        return jdbcTemplate.query(
            """
            select id, sequence_no, source_table, source_id, subject_type, subject_key,
                   legacy_level, detected_level, existing_effective_level,
                   computed_effective_level, decision, decision_reason, apply_status,
                   applied_snapshot_id, applied_snapshot_version, applied_at, error_message
              from catalog_classification_migration_item
             where run_id=? and (?::varchar is null or decision=?)
             order by sequence_no
             limit ?
            """,
            (rs, rowNum) -> new MigrationItemView(
                rs.getObject("id", UUID.class),
                rs.getLong("sequence_no"),
                rs.getString("source_table"),
                rs.getString("source_id"),
                rs.getString("subject_type"),
                rs.getString("subject_key"),
                rs.getString("legacy_level"),
                rs.getString("detected_level"),
                rs.getString("existing_effective_level"),
                rs.getString("computed_effective_level"),
                rs.getString("decision"),
                rs.getString("decision_reason"),
                rs.getString("apply_status"),
                rs.getObject("applied_snapshot_id", UUID.class),
                nullableLong(rs, "applied_snapshot_version"),
                instant(rs, "applied_at"),
                rs.getString("error_message")
            ),
            runId,
            normalizedDecision,
            normalizedDecision,
            safeLimit
        );
    }

    @Transactional(readOnly = true)
    public List<WriteFreezeView> writeFreezes() {
        return jdbcTemplate.query(
            """
            select source_table, enabled, reconciliation_run_id, frozen_by, frozen_at, reason
              from catalog_classification_legacy_write_freeze
             order by source_table
            """,
            (rs, rowNum) -> new WriteFreezeView(
                rs.getString("source_table"),
                rs.getBoolean("enabled"),
                rs.getObject("reconciliation_run_id", UUID.class),
                rs.getString("frozen_by"),
                instant(rs, "frozen_at"),
                rs.getString("reason")
            )
        );
    }

    private List<Candidate> inventory() {
        List<Candidate> candidates = new ArrayList<>();
        candidates.addAll(datasetCandidates());
        candidates.addAll(tableCandidates());
        candidates.addAll(columnCandidates());
        addSimpleCandidates(candidates, "svc_api", "ASSET", "API", "api:");
        addSimpleCandidates(candidates, "svc_data_product", "ASSET", "DATA_PRODUCT", "data-product:");
        addSimpleCandidates(candidates, "catalog_data_product", "ASSET", "DATA_PRODUCT", "catalog-product:");
        addSimpleCandidates(candidates, "bi_report_link", "ASSET", "REPORT", "report-link:");
        addSimpleCandidates(candidates, "infra_external_exchange_file", "FILE", "FILE", "exchange-file:");
        return candidates;
    }

    private List<Candidate> datasetCandidates() {
        return jdbcTemplate.query(
            """
            select id, source_id, hive_database, hive_table, name, classification
              from catalog_dataset
             order by id
            """,
            (rs, rowNum) -> {
                UUID sourceId = rs.getObject("source_id", UUID.class);
                String key = CatalogAssetKey.dataset(
                    sourceId,
                    rs.getString("hive_database"),
                    rs.getString("hive_database"),
                    rs.getString("hive_table"),
                    rs.getString("name")
                );
                return new Candidate(
                    "catalog_dataset",
                    rs.getObject("id", UUID.class).toString(),
                    "ASSET",
                    key,
                    "DATASET",
                    rs.getString("classification"),
                    null
                );
            }
        );
    }

    private List<Candidate> tableCandidates() {
        return jdbcTemplate.query(
            """
            select t.id, t.name as table_schema_name, t.classification,
                   d.source_id, d.hive_database, d.hive_table, d.name as dataset_name
              from catalog_table_schema t
              join catalog_dataset d on d.id=t.dataset_id
             order by t.id
            """,
            (rs, rowNum) -> {
                String datasetKey = CatalogAssetKey.dataset(
                    rs.getObject("source_id", UUID.class),
                    rs.getString("hive_database"),
                    rs.getString("hive_database"),
                    rs.getString("hive_table"),
                    rs.getString("dataset_name")
                );
                return new Candidate(
                    "catalog_table_schema",
                    rs.getObject("id", UUID.class).toString(),
                    "ASSET",
                    datasetKey + "/table-schema:" + segment(rs.getString("table_schema_name")),
                    "TABLE",
                    rs.getString("classification"),
                    null
                );
            }
        );
    }

    private List<Candidate> columnCandidates() {
        return jdbcTemplate.query(
            """
            select c.id, c.name as column_name, c.sensitive_tags,
                   t.classification as table_classification,
                   d.classification as dataset_classification,
                   d.source_id, d.hive_database, d.hive_table, d.name as dataset_name
              from catalog_column_schema c
              join catalog_table_schema t on t.id=c.table_id
              join catalog_dataset d on d.id=t.dataset_id
             order by c.id
            """,
            (rs, rowNum) -> {
                String datasetKey = CatalogAssetKey.dataset(
                    rs.getObject("source_id", UUID.class),
                    rs.getString("hive_database"),
                    rs.getString("hive_database"),
                    rs.getString("hive_table"),
                    rs.getString("dataset_name")
                );
                String detected = StringUtils.hasText(rs.getString("sensitive_tags")) ? "SECRET" : null;
                String inheritedFloor = SecurityLevelCatalog.maxDataCode(
                    rs.getString("table_classification"),
                    rs.getString("dataset_classification")
                );
                return new Candidate(
                    "catalog_column_schema",
                    rs.getObject("id", UUID.class).toString(),
                    "COLUMN",
                    datasetKey + "/column:" + segment(rs.getString("column_name")),
                    "COLUMN",
                    inheritedFloor,
                    detected
                );
            }
        );
    }

    private void addSimpleCandidates(
        List<Candidate> candidates,
        String table,
        String subjectType,
        String assetType,
        String keyPrefix
    ) {
        candidates.addAll(
            jdbcTemplate.query(
                "select id::text as source_id, classification from " + table + " order by id",
                (rs, rowNum) -> new Candidate(
                    table,
                    rs.getString("source_id"),
                    subjectType,
                    keyPrefix + rs.getString("source_id"),
                    assetType,
                    rs.getString("classification"),
                    null
                )
            )
        );
    }

    private Map<String, ExistingFact> existingFacts() {
        Map<String, ExistingFact> facts = new LinkedHashMap<>();
        jdbcTemplate.query(
            "select id, subject_type, subject_key, effective_level, record_version from catalog_classification_snapshot",
            rs -> {
                ExistingFact fact = new ExistingFact(
                    rs.getObject("id", UUID.class),
                    rs.getString("effective_level"),
                    rs.getLong("record_version")
                );
                facts.put(rs.getString("subject_type") + "\u0000" + rs.getString("subject_key"), fact);
            }
        );
        return facts;
    }

    private Decision decide(Candidate candidate, ExistingFact existing) {
        ClassificationMigrationPolicy.Decision decision = ClassificationMigrationPolicy.evaluate(
            candidate.legacyLevel(),
            candidate.detectedLevel(),
            existing == null ? null : existing.effectiveLevel()
        );
        return new Decision(decision.code(), decision.computedLevel(), decision.reason());
    }

    private void updateProgress(UUID runId, long cursor, String actor) {
        jdbcTemplate.update(
            """
            update catalog_classification_migration_run r
               set cursor_position=?,
                   applied_items=(
                       select count(*) from catalog_classification_migration_item i
                        where i.run_id=r.id and i.apply_status='APPLIED'
                   ),
                   last_error=(
                       select max(i.error_message) from catalog_classification_migration_item i
                        where i.run_id=r.id and i.apply_status='FAILED'
                   ),
                   record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=?
            """,
            cursor,
            actor,
            nowTimestamp(),
            runId
        );
    }

    private MigrationRunView findByIdempotencyKey(String key) {
        return jdbcTemplate
            .query(
                """
                select id, idempotency_key, mode, status, batch_size, cursor_position,
                       total_items, eligible_items, blocked_items, applied_items,
                       mismatch_items, report_checksum, requested_by, started_at,
                       completed_at, paused_at, last_error, record_version
                  from catalog_classification_migration_run
                 where idempotency_key=?
                """,
                (rs, rowNum) -> runView(rs),
                key
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    private MigrationRunView runView(ResultSet rs) throws SQLException {
        return new MigrationRunView(
            rs.getObject("id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getString("mode"),
            rs.getString("status"),
            rs.getInt("batch_size"),
            rs.getLong("cursor_position"),
            rs.getLong("total_items"),
            rs.getLong("eligible_items"),
            rs.getLong("blocked_items"),
            rs.getLong("applied_items"),
            rs.getLong("mismatch_items"),
            rs.getString("report_checksum"),
            rs.getString("requested_by"),
            instant(rs, "started_at"),
            instant(rs, "completed_at"),
            instant(rs, "paused_at"),
            rs.getString("last_error"),
            rs.getLong("record_version")
        );
    }

    private String freezableTable(String table) {
        String normalized = required(table, "sourceTable", 128).toLowerCase(Locale.ROOT);
        if (!FREEZABLE_TABLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported legacy classification table: " + normalized);
        }
        return normalized;
    }

    private static String segment(String value) {
        return required(value, "asset key segment", 512)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
    }

    private static String required(String value, String field, int max) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                out.append(String.format("%02x", item));
            }
            return out.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp nowTimestamp() {
        return Timestamp.from(Instant.now());
    }

    private record Candidate(
        String sourceTable,
        String sourceId,
        String subjectType,
        String subjectKey,
        String assetType,
        String legacyLevel,
        String detectedLevel
    ) {}

    private record ExistingFact(UUID id, String effectiveLevel, long version) {}

    private record Decision(String code, String computedLevel, String reason) {}

    private record ItemPointer(UUID id, long sequenceNo) {}

    public record DryRunCommand(String idempotencyKey, Integer batchSize) {}

    public record FreezeCommand(List<String> sourceTables, String reason) {}

    public record MigrationRunView(
        UUID id,
        String idempotencyKey,
        String mode,
        String status,
        int batchSize,
        long cursorPosition,
        long totalItems,
        long eligibleItems,
        long blockedItems,
        long appliedItems,
        long mismatchItems,
        String reportChecksum,
        String requestedBy,
        Instant startedAt,
        Instant completedAt,
        Instant pausedAt,
        String lastError,
        long recordVersion
    ) {}

    public record MigrationItemView(
        UUID id,
        long sequenceNo,
        String sourceTable,
        String sourceId,
        String subjectType,
        String subjectKey,
        String legacyLevel,
        String detectedLevel,
        String existingEffectiveLevel,
        String computedEffectiveLevel,
        String decision,
        String decisionReason,
        String applyStatus,
        UUID appliedSnapshotId,
        Long appliedSnapshotVersion,
        Instant appliedAt,
        String errorMessage
    ) {}

    public record ReconciliationMismatch(
        String sourceTable,
        String sourceId,
        String subjectType,
        String subjectKey,
        String legacyLevel,
        String expectedLevel,
        String currentLevel,
        String code
    ) {}

    public record ReconciliationView(
        UUID runId,
        long totalItems,
        long mismatchItems,
        List<ReconciliationMismatch> mismatches,
        boolean readyToFreeze
    ) {}

    public record WriteFreezeView(
        String sourceTable,
        boolean enabled,
        UUID reconciliationRunId,
        String frozenBy,
        Instant frozenAt,
        String reason
    ) {}
}
