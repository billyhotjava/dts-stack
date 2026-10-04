package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassificationMigrationItemWorker {

    private final JdbcTemplate jdbcTemplate;
    private final CatalogClassificationService classificationService;

    public ClassificationMigrationItemWorker(
        JdbcTemplate jdbcTemplate,
        CatalogClassificationService classificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.classificationService = classificationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppliedItem apply(UUID itemId, String actor) {
        MigrationItem item = jdbcTemplate
            .query(
                """
                select id, source_table, source_id, subject_type, subject_key, asset_type,
                       legacy_level, detected_level, decision, item_checksum, apply_status
                  from catalog_classification_migration_item
                 where id=?
                 for update
                """,
                (rs, rowNum) -> new MigrationItem(
                    rs.getObject("id", UUID.class),
                    rs.getString("source_table"),
                    rs.getString("source_id"),
                    rs.getString("subject_type"),
                    rs.getString("subject_key"),
                    rs.getString("asset_type"),
                    rs.getString("legacy_level"),
                    rs.getString("detected_level"),
                    rs.getString("decision"),
                    rs.getString("item_checksum"),
                    rs.getString("apply_status")
                ),
                itemId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Migration item does not exist: " + itemId));
        if ("APPLIED".equals(item.applyStatus()) || "SKIPPED".equals(item.applyStatus())) {
            return new AppliedItem(item.id(), item.applyStatus(), null, null);
        }
        if (item.decision().startsWith("BLOCKED_") || "UNCHANGED".equals(item.decision())) {
            jdbcTemplate.update(
                """
                update catalog_classification_migration_item
                   set apply_status='SKIPPED', last_modified_by=?, last_modified_date=?
                 where id=?
                """,
                actor,
                nowTimestamp(),
                item.id()
            );
            return new AppliedItem(item.id(), "SKIPPED", null, null);
        }

        CatalogClassificationSnapshot snapshot = classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                item.subjectType(),
                item.subjectKey(),
                item.assetType(),
                item.legacyLevel(),
                item.detectedLevel(),
                null,
                List.of(),
                "MIGRATION",
                item.sourceTable() + ":" + item.sourceId(),
                item.itemChecksum(),
                evidenceJson(item)
            )
        );
        Timestamp now = nowTimestamp();
        long version = snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion();
        jdbcTemplate.update(
            """
            update catalog_classification_migration_item
               set apply_status='APPLIED', applied_snapshot_id=?, applied_snapshot_version=?,
                   applied_at=?, error_message=null, last_modified_by=?, last_modified_date=?
             where id=?
            """,
            snapshot.getId(),
            version,
            now,
            actor,
            now,
            item.id()
        );
        return new AppliedItem(item.id(), "APPLIED", snapshot.getId(), version);
    }

    private static String evidenceJson(MigrationItem item) {
        return (
            "{\"sourceTable\":\"" +
            escapeJson(item.sourceTable()) +
            "\",\"sourceId\":\"" +
            escapeJson(item.sourceId()) +
            "\",\"migrationDecision\":\"" +
            escapeJson(item.decision()) +
            "\"}"
        );
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID itemId, String actor, String error) {
        jdbcTemplate.update(
            """
            update catalog_classification_migration_item
               set apply_status='FAILED', error_message=?,
                   last_modified_by=?, last_modified_date=?
             where id=? and apply_status not in ('APPLIED','SKIPPED')
            """,
            error == null ? "unknown migration failure" : error.substring(0, Math.min(error.length(), 2048)),
            actor,
            nowTimestamp(),
            itemId
        );
    }

    private static Timestamp nowTimestamp() {
        return Timestamp.from(Instant.now());
    }

    private record MigrationItem(
        UUID id,
        String sourceTable,
        String sourceId,
        String subjectType,
        String subjectKey,
        String assetType,
        String legacyLevel,
        String detectedLevel,
        String decision,
        String itemChecksum,
        String applyStatus
    ) {}

    public record AppliedItem(UUID itemId, String status, UUID snapshotId, Long snapshotVersion) {}
}
