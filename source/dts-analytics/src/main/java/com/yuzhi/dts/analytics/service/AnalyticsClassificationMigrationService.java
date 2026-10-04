package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AnalyticsClassificationMigrationService {

    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsMetricRepository metricRepository;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsConsumerClassificationService classificationService;

    public AnalyticsClassificationMigrationService(
        JdbcTemplate jdbcTemplate,
        AnalyticsCardRepository cardRepository,
        AnalyticsMetricRepository metricRepository,
        AnalyticsScreenRepository screenRepository,
        AnalyticsDashboardRepository dashboardRepository,
        AnalyticsConsumerClassificationService classificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cardRepository = cardRepository;
        this.metricRepository = metricRepository;
        this.screenRepository = screenRepository;
        this.dashboardRepository = dashboardRepository;
        this.classificationService = classificationService;
    }

    @Transactional
    public RunView dryRun(String idempotencyKey, int requestedBatchSize, String actor) {
        String key = required(idempotencyKey, "idempotencyKey");
        RunView existing = findByKey(key);
        if (existing != null) {
            return existing;
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize <= 0 ? 200 : requestedBatchSize, 1000));
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            insert into analytics_classification_migration_run (
                id, idempotency_key, status, batch_size, requested_by, started_at
            ) values (?, ?, 'DRY_RUN_RUNNING', ?, ?, ?)
            """,
            runId,
            key,
            batchSize,
            actor,
            now
        );
        List<ItemSeed> seeds = new ArrayList<>();
        cardRepository.findAll().forEach(card ->
            seeds.add(new ItemSeed("CARD", card.getId(), null, "DERIVE"))
        );
        metricRepository.findAll().forEach(metric ->
            seeds.add(
                new ItemSeed(
                    "METRIC",
                    metric.getId(),
                    null,
                    metric.getBaseTableId() == null ? "BLOCKED_MISSING_SOURCE" : "DERIVE"
                )
            )
        );
        screenRepository.findAll().forEach(screen ->
            seeds.add(
                new ItemSeed(
                    "SCREEN",
                    screen.getId(),
                    screen.getClassification(),
                    StringUtils.hasText(screen.getClassification()) ? "DERIVE" : "BLOCKED_MISSING_FLOOR"
                )
            )
        );
        dashboardRepository.findAll().forEach(dashboard ->
            seeds.add(new ItemSeed("DASHBOARD", dashboard.getId(), null, "DERIVE"))
        );
        long sequence = 0;
        for (ItemSeed seed : seeds) {
            jdbcTemplate.update(
                """
                insert into analytics_classification_migration_item (
                    id, run_id, sequence_no, entity_type, entity_id,
                    legacy_level, decision, status
                ) values (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """,
                UUID.randomUUID(),
                runId,
                sequence++,
                seed.entityType(),
                seed.entityId(),
                seed.legacyLevel(),
                seed.decision()
            );
        }
        jdbcTemplate.update(
            """
            update analytics_classification_migration_run
               set status='DRY_RUN_COMPLETE', total_items=?, completed_at=?
             where id=?
            """,
            seeds.size(),
            Instant.now(),
            runId
        );
        return get(runId);
    }

    public RunView applyBatch(UUID runId, int requestedBatchSize, String actor) {
        RunView run = get(runId);
        if ("PAUSED".equals(run.status())) {
            throw new IllegalStateException("Analytics classification migration is paused");
        }
        if (!List.of("DRY_RUN_COMPLETE", "APPLY_RUNNING", "FAILED").contains(run.status())) {
            throw new IllegalStateException("Analytics classification migration cannot apply from " + run.status());
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize <= 0 ? run.batchSize() : requestedBatchSize, 1000));
        jdbcTemplate.update(
            "update analytics_classification_migration_run set status='APPLY_RUNNING', completed_at=null where id=?",
            runId
        );
        List<ItemView> items = listPending(runId, batchSize);
        long cursor = run.cursorPosition();
        for (ItemView item : items) {
            try {
                applyItem(item);
            } catch (RuntimeException failure) {
                jdbcTemplate.update(
                    """
                    update analytics_classification_migration_item
                       set status='FAILED', error_message=?
                     where id=?
                    """,
                    truncate(failure.getMessage()),
                    item.id()
                );
            }
            cursor = Math.max(cursor, item.sequenceNo() + 1);
        }
        jdbcTemplate.update(
            """
            update analytics_classification_migration_run r
               set cursor_position=?,
                   applied_items=(select count(*) from analytics_classification_migration_item i where i.run_id=r.id and i.status='APPLIED'),
                   failed_items=(select count(*) from analytics_classification_migration_item i where i.run_id=r.id and i.status='FAILED'),
                   last_error=(select max(i.error_message) from analytics_classification_migration_item i where i.run_id=r.id and i.status='FAILED')
             where id=?
            """,
            cursor,
            runId
        );
        Long pending = jdbcTemplate.queryForObject(
            "select count(*) from analytics_classification_migration_item where run_id=? and status='PENDING'",
            Long.class,
            runId
        );
        if (pending != null && pending == 0) {
            Long failed = jdbcTemplate.queryForObject(
                "select count(*) from analytics_classification_migration_item where run_id=? and status='FAILED'",
                Long.class,
                runId
            );
            jdbcTemplate.update(
                "update analytics_classification_migration_run set status=?, completed_at=? where id=?",
                failed != null && failed > 0 ? "FAILED" : "APPLY_COMPLETE",
                Instant.now(),
                runId
            );
        }
        return get(runId);
    }

    @Transactional
    public RunView pause(UUID runId) {
        jdbcTemplate.update(
            "update analytics_classification_migration_run set status='PAUSED', paused_at=? where id=? and status='APPLY_RUNNING'",
            Instant.now(),
            runId
        );
        return get(runId);
    }

    @Transactional
    public RunView resume(UUID runId) {
        jdbcTemplate.update(
            "update analytics_classification_migration_run set status='APPLY_RUNNING', paused_at=null where id=? and status='PAUSED'",
            runId
        );
        return get(runId);
    }

    @Transactional(readOnly = true)
    public RunView get(UUID runId) {
        return jdbcTemplate
            .query(
                """
                select id, idempotency_key, status, batch_size, cursor_position,
                       total_items, applied_items, failed_items, requested_by,
                       started_at, completed_at, paused_at, last_error
                  from analytics_classification_migration_run where id=?
                """,
                (rs, rowNum) -> runView(rs),
                runId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Analytics migration run does not exist"));
    }

    @Transactional(readOnly = true)
    public List<ItemView> items(UUID runId, int limit) {
        return jdbcTemplate.query(
            """
            select id, sequence_no, entity_type, entity_id, legacy_level, decision,
                   status, snapshot_id, snapshot_version, effective_level,
                   error_message, applied_at
              from analytics_classification_migration_item
             where run_id=? order by sequence_no limit ?
            """,
            (rs, rowNum) -> itemView(rs),
            runId,
            Math.max(1, Math.min(limit, 1000))
        );
    }

    private void applyItem(ItemView item) {
        if (item.decision().startsWith("BLOCKED_")) {
            jdbcTemplate.update(
                "update analytics_classification_migration_item set status='SKIPPED' where id=?",
                item.id()
            );
            return;
        }
        AnalyticsClassificationClient.ClassificationResult result = switch (item.entityType()) {
            case "CARD" -> classificationService.deriveCard(
                cardRepository.findById(item.entityId()).orElseThrow(() -> new IllegalArgumentException("Card not found"))
            );
            case "METRIC" -> classificationService.deriveMetric(
                metricRepository.findById(item.entityId()).orElseThrow(() -> new IllegalArgumentException("Metric not found"))
            );
            case "SCREEN" -> {
                var screen = screenRepository
                    .findById(item.entityId())
                    .orElseThrow(() -> new IllegalArgumentException("Screen not found"));
                if (!StringUtils.hasText(screen.getManualClassificationFloor())) {
                    screen.setManualClassificationFloor(item.legacyLevel());
                }
                yield classificationService.deriveScreen(screen);
            }
            case "DASHBOARD" -> {
                dashboardRepository
                    .findById(item.entityId())
                    .orElseThrow(() -> new IllegalArgumentException("Dashboard not found"));
                yield classificationService.deriveDashboard(item.entityId());
            }
            default -> throw new IllegalArgumentException("Unsupported analytics migration entity " + item.entityType());
        };
        jdbcTemplate.update(
            """
            update analytics_classification_migration_item
               set status='APPLIED', snapshot_id=?, snapshot_version=?,
                   effective_level=?, error_message=null, applied_at=?
             where id=?
            """,
            result.snapshotId(),
            result.snapshotVersion(),
            result.effectiveLevel(),
            Instant.now(),
            item.id()
        );
    }

    private List<ItemView> listPending(UUID runId, int limit) {
        return jdbcTemplate.query(
            """
            select id, sequence_no, entity_type, entity_id, legacy_level, decision,
                   status, snapshot_id, snapshot_version, effective_level,
                   error_message, applied_at
              from analytics_classification_migration_item
             where run_id=? and status in ('PENDING','FAILED')
             order by sequence_no limit ?
            """,
            (rs, rowNum) -> itemView(rs),
            runId,
            limit
        );
    }

    private RunView findByKey(String key) {
        return jdbcTemplate
            .query(
                """
                select id, idempotency_key, status, batch_size, cursor_position,
                       total_items, applied_items, failed_items, requested_by,
                       started_at, completed_at, paused_at, last_error
                  from analytics_classification_migration_run where idempotency_key=?
                """,
                (rs, rowNum) -> runView(rs),
                key
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    private RunView runView(ResultSet rs) throws SQLException {
        return new RunView(
            rs.getObject("id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getString("status"),
            rs.getInt("batch_size"),
            rs.getLong("cursor_position"),
            rs.getLong("total_items"),
            rs.getLong("applied_items"),
            rs.getLong("failed_items"),
            rs.getString("requested_by"),
            instant(rs, "started_at"),
            instant(rs, "completed_at"),
            instant(rs, "paused_at"),
            rs.getString("last_error")
        );
    }

    private ItemView itemView(ResultSet rs) throws SQLException {
        return new ItemView(
            rs.getObject("id", UUID.class),
            rs.getLong("sequence_no"),
            rs.getString("entity_type"),
            rs.getLong("entity_id"),
            rs.getString("legacy_level"),
            rs.getString("decision"),
            rs.getString("status"),
            rs.getString("snapshot_id"),
            nullableLong(rs, "snapshot_version"),
            rs.getString("effective_level"),
            rs.getString("error_message"),
            instant(rs, "applied_at")
        );
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String truncate(String value) {
        String effective = StringUtils.hasText(value) ? value : "unknown analytics classification migration failure";
        return effective.substring(0, Math.min(effective.length(), 2048));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record ItemSeed(String entityType, Long entityId, String legacyLevel, String decision) {}

    public record RunView(
        UUID id,
        String idempotencyKey,
        String status,
        int batchSize,
        long cursorPosition,
        long totalItems,
        long appliedItems,
        long failedItems,
        String requestedBy,
        Instant startedAt,
        Instant completedAt,
        Instant pausedAt,
        String lastError
    ) {}

    public record ItemView(
        UUID id,
        long sequenceNo,
        String entityType,
        long entityId,
        String legacyLevel,
        String decision,
        String status,
        String snapshotId,
        Long snapshotVersion,
        String effectiveLevel,
        String errorMessage,
        Instant appliedAt
    ) {}
}
