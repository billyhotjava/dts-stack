package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fail-closed authorization projection. Any new or raised canonical dataset seal expires active
 * use/share grants so access is reassessed against the current effective level.
 */
@Component
public class CatalogGrantClassificationProjection implements CatalogClassificationProjection {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CatalogGrantClassificationProjection(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean supports(CatalogClassificationSnapshot snapshot) {
        return (
            snapshot != null &&
            "ASSET".equals(snapshot.getSubjectType()) &&
            "DATASET".equals(snapshot.getAssetType())
        );
    }

    @Override
    public void project(CatalogClassificationSnapshot snapshot) {
        UUID datasetId = jdbcTemplate
            .query(
                """
                select dataset.id
                  from catalog_dataset dataset
                 where (
                        'source:' || coalesce(dataset.source_id::text, 'unknown')
                        || '/schema:' || regexp_replace(
                            regexp_replace(
                                regexp_replace(
                                    lower(trim(coalesce(nullif(trim(dataset.hive_database), ''), 'default'))),
                                    '[^a-z0-9_.:-]+', '_', 'g'
                                ), '_+', '_', 'g'
                            ), '^_+|_+$', '', 'g'
                        )
                        || '/table:' || regexp_replace(
                            regexp_replace(
                                regexp_replace(
                                    lower(trim(coalesce(nullif(trim(dataset.hive_table), ''), dataset.name))),
                                    '[^a-z0-9_.:-]+', '_', 'g'
                                ), '_+', '_', 'g'
                            ), '^_+|_+$', '', 'g'
                        )
                     ) = :subjectKey
                 limit 1
                """,
                Map.of("subjectKey", snapshot.getSubjectKey()),
                (row, rowNumber) -> row.getObject("id", UUID.class)
            )
            .stream()
            .findFirst()
            .orElse(null);
        if (datasetId == null) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        int datasetGrants = jdbcTemplate.update(
            """
            update catalog_dataset_grant
               set valid_to=least(coalesce(valid_to, :now), :now),
                   last_modified_by='system', last_modified_date=:now
             where dataset_id=:datasetId
               and (valid_to is null or valid_to>:now)
            """,
            Map.of("datasetId", datasetId, "now", now)
        );
        int assetGrants = jdbcTemplate.update(
            """
            update asset_grant
               set valid_to=least(coalesce(valid_to, :now), :now),
                   last_modified_by='system', last_modified_date=:now
             where upper(asset_type) in ('DATASET','CATALOG_DATASET')
               and asset_id=:datasetIdText
               and (valid_to is null or valid_to>:now)
            """,
            Map.of("datasetIdText", datasetId.toString(), "now", now)
        );
        if (datasetGrants + assetGrants == 0) {
            return;
        }
        jdbcTemplate.update(
            """
            insert into catalog_lifecycle_event (
                id, dataset_id, stage, event_type, status, request_source,
                request_ref, seal_id, seal_version, effective_level,
                evidence_json, actor, occurred_at, created_by, created_date
            ) values (
                :id, :datasetId, 'SHARE', 'AUTHORIZATION_SUSPENDED',
                'REAPPROVAL_REQUIRED', 'CLASSIFICATION_PROJECTION',
                :subjectKey, :sealId, :sealVersion, :effectiveLevel,
                cast(:evidence as jsonb), 'system', :now, 'system', :now
            )
            """,
            Map.of(
                "id",
                UUID.randomUUID(),
                "datasetId",
                datasetId,
                "subjectKey",
                snapshot.getSubjectKey(),
                "sealId",
                snapshot.getId(),
                "sealVersion",
                snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion(),
                "effectiveLevel",
                snapshot.getEffectiveLevel(),
                "evidence",
                "{\"datasetGrants\":" + datasetGrants + ",\"assetGrants\":" + assetGrants + "}",
                "now",
                now
            )
        );
    }
}
