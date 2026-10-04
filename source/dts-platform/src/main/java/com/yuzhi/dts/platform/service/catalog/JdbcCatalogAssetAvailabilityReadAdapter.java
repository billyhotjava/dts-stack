package com.yuzhi.dts.platform.service.catalog;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JdbcCatalogAssetAvailabilityReadAdapter implements CatalogAssetAvailabilityReadPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCatalogAssetAvailabilityReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Availability read(CatalogAssetType assetType, String assetKey) {
        if (assetType == null || !StringUtils.hasText(assetKey)) {
            throw new IllegalArgumentException("asset identity is required");
        }
        List<Availability> rows = jdbcTemplate.query(
            """
            select status, availability_epoch, source_sequence, event_id
              from catalog_asset_availability
             where asset_type = ? and asset_key = ?
            """,
            (result, rowNumber) ->
                new Availability(
                    result.getString("status"),
                    result.getLong("availability_epoch"),
                    result.getLong("source_sequence"),
                    result.getString("event_id")
                ),
            assetType.name(),
            assetKey.trim()
        );
        return rows.isEmpty() ? Availability.legacyAvailable() : rows.getFirst();
    }
}
