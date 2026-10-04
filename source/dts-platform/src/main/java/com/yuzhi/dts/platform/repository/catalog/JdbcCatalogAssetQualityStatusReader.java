package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetQualityStatusReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL batch reader for the latest non-dry-run governance quality fact per dataset. */
@Repository
public class JdbcCatalogAssetQualityStatusReader implements CatalogAssetQualityStatusReader {

    private final JdbcTemplate jdbc;

    public JdbcCatalogAssetQualityStatusReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, QualityStatusSnapshot> readLatest(Collection<UUID> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        datasetIds.stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
            """
            SELECT DISTINCT ON (dataset_id)
                   dataset_id, id AS run_id, status,
                   coalesce(finished_at, started_at, scheduled_at, created_date) AS observed_at
              FROM gov_quality_run
             WHERE dataset_id IN (
            """ +
            placeholders +
            """
             )
               AND upper(coalesce(trigger_type, '')) <> 'DRY_RUN'
             ORDER BY dataset_id,
                      coalesce(finished_at, started_at, scheduled_at, created_date) DESC NULLS LAST,
                      created_date DESC,
                      id DESC
            """;
        List<Object> arguments = new ArrayList<>(ids);
        List<QualityStatusSnapshot> rows = jdbc.query(
            sql,
            (rs, rowNum) -> {
                Timestamp observedAt = rs.getTimestamp("observed_at");
                return new QualityStatusSnapshot(
                    rs.getObject("dataset_id", UUID.class),
                    rs.getObject("run_id", UUID.class),
                    rs.getString("status"),
                    observedAt == null ? null : observedAt.toInstant()
                );
            },
            arguments.toArray()
        );
        Map<UUID, QualityStatusSnapshot> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.datasetId(), row));
        return Map.copyOf(result);
    }
}
