package com.yuzhi.dts.platform.repository.catalog;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Performs conflict-safe catalog tag assignment inserts in bounded PostgreSQL statements.
 */
@Repository
public class CatalogAssetTagBatchWriter {

    public static final int MAX_ROWS_PER_STATEMENT = 500;

    private static final String VALUE_PLACEHOLDER = "(?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public CatalogAssetTagBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<AssignmentKey> insertIgnore(List<AssignmentKey> assignments, String actor, Instant timestamp) {
        if (assignments == null || assignments.isEmpty()) {
            return Set.of();
        }
        List<AssignmentKey> uniqueAssignments = new ArrayList<>(new LinkedHashSet<>(assignments));
        uniqueAssignments.sort(
            Comparator.comparing(AssignmentKey::assetType)
                .thenComparing(AssignmentKey::assetKey)
                .thenComparing(AssignmentKey::tagId)
        );
        Set<AssignmentKey> inserted = new LinkedHashSet<>();
        for (int start = 0; start < uniqueAssignments.size(); start += MAX_ROWS_PER_STATEMENT) {
            int end = Math.min(start + MAX_ROWS_PER_STATEMENT, uniqueAssignments.size());
            inserted.addAll(insertChunk(uniqueAssignments.subList(start, end), actor, timestamp));
        }
        return Collections.unmodifiableSet(inserted);
    }

    private Set<AssignmentKey> insertChunk(List<AssignmentKey> assignments, String actor, Instant timestamp) {
        String sql = """
            insert into catalog_asset_tag (
                id, tag_id, asset_type, asset_key, tagged_by, tagged_at, migration_batch,
                created_by, created_date, last_modified_by, last_modified_date
            ) values %s
            on conflict (tag_id, asset_type, asset_key) do nothing
            returning asset_type, asset_key, tag_id
            """.formatted(String.join(", ", Collections.nCopies(assignments.size(), VALUE_PLACEHOLDER)));
        Timestamp auditTimestamp = Timestamp.from(timestamp);
        return jdbcTemplate.query(
            sql,
            statement -> {
                int parameter = 1;
                for (AssignmentKey assignment : assignments) {
                    statement.setObject(parameter++, UUID.randomUUID());
                    statement.setObject(parameter++, assignment.tagId());
                    statement.setString(parameter++, assignment.assetType());
                    statement.setString(parameter++, assignment.assetKey());
                    statement.setString(parameter++, actor);
                    statement.setTimestamp(parameter++, auditTimestamp);
                    statement.setString(parameter++, actor);
                    statement.setTimestamp(parameter++, auditTimestamp);
                    statement.setString(parameter++, actor);
                    statement.setTimestamp(parameter++, auditTimestamp);
                }
            },
            resultSet -> {
                Set<AssignmentKey> result = new LinkedHashSet<>();
                while (resultSet.next()) {
                    result.add(
                        new AssignmentKey(
                            resultSet.getString("asset_type"),
                            resultSet.getString("asset_key"),
                            resultSet.getObject("tag_id", UUID.class)
                        )
                    );
                }
                return result;
            }
        );
    }

    public record AssignmentKey(String assetType, String assetKey, UUID tagId) {}
}
