package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationStore.BatchRecord;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationStore.RelationEvidence;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationStore.RelationKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCatalogTagMigrationStore implements CatalogTagMigrationStore {

    private static final String GLOBAL_MIGRATION_LOCK = "catalog-tag-migration-global-v1";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcCatalogTagMigrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public void acquireMigrationLock() {
        jdbc.execute(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            (PreparedStatementCallback<Void>) statement -> {
                statement.setString(1, GLOBAL_MIGRATION_LOCK);
                try (var ignored = statement.executeQuery()) {
                    return null;
                }
            }
        );
    }

    @Override
    public void lockMigrationInputs() {
        jdbc.execute(
            """
            lock table catalog_dataset, catalog_tag, catalog_asset_tag
            in share row exclusive mode
            """
        );
    }

    @Override
    public Optional<BatchRecord> lockBatch(String batchId) {
        return jdbc
            .query(
                """
                select batch_id, checksum, status, plan_json::text,
                       execution_json::text, rollback_json::text,
                       created_by, created_date
                  from catalog_tag_migration_batch
                 where batch_id = ?
                 for update
                """,
                (resultSet, rowNumber) ->
                    new BatchRecord(
                        resultSet.getString("batch_id"),
                        resultSet.getString("checksum"),
                        resultSet.getString("status"),
                        resultSet.getString("plan_json"),
                        resultSet.getString("execution_json"),
                        resultSet.getString("rollback_json"),
                        resultSet.getString("created_by"),
                        resultSet.getTimestamp("created_date").toInstant()
                    ),
                batchId
            )
            .stream()
            .findFirst();
    }

    @Override
    public int insertBatch(BatchRecord batch) {
        return jdbc.update(
            """
            insert into catalog_tag_migration_batch (
                batch_id, checksum, status, plan_json, execution_json, rollback_json,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, cast(? as jsonb), null, null, ?, ?, ?, ?)
            on conflict (batch_id) do nothing
            """,
            batch.batchId(),
            batch.checksum(),
            batch.status(),
            batch.planJson(),
            batch.actor(),
            Timestamp.from(batch.timestamp()),
            batch.actor(),
            Timestamp.from(batch.timestamp())
        );
    }

    @Override
    public int markExecuted(String batchId, String executionJson, String actor, Instant modifiedAt) {
        return jdbc.update(
            """
            update catalog_tag_migration_batch
               set status = 'EXECUTED',
                   execution_json = cast(? as jsonb),
                   last_modified_by = ?,
                   last_modified_date = ?
             where batch_id = ? and status = 'EXECUTING'
            """,
            executionJson,
            actor,
            Timestamp.from(modifiedAt),
            batchId
        );
    }

    @Override
    public int markRolledBack(String batchId, String rollbackJson, String actor, Instant modifiedAt) {
        return jdbc.update(
            """
            update catalog_tag_migration_batch
               set status = 'ROLLED_BACK',
                   rollback_json = cast(? as jsonb),
                   last_modified_by = ?,
                   last_modified_date = ?
             where batch_id = ? and status = 'EXECUTED'
            """,
            rollbackJson,
            actor,
            Timestamp.from(modifiedAt),
            batchId
        );
    }

    @Override
    public Set<RelationKey> findExistingRelations(Collection<RelationKey> relations) {
        if (relations == null || relations.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<RelationKey> requested = new LinkedHashSet<>(relations);
        Set<String> assetKeys = requested.stream().map(RelationKey::assetKey).collect(java.util.stream.Collectors.toSet());
        Set<UUID> tagIds = requested.stream().map(RelationKey::tagId).collect(java.util.stream.Collectors.toSet());
        List<RelationKey> candidates = namedJdbc.query(
            """
            select tag_id, asset_type, asset_key
              from catalog_asset_tag
             where asset_type = :assetType
               and asset_key in (:assetKeys)
               and tag_id in (:tagIds)
            """,
            Map.of(
                "assetType",
                CatalogAssetType.DATASET.name(),
                "assetKeys",
                assetKeys,
                "tagIds",
                tagIds
            ),
            (resultSet, rowNumber) ->
                new RelationKey(
                    resultSet.getObject("tag_id", UUID.class),
                    resultSet.getString("asset_type"),
                    resultSet.getString("asset_key")
                )
        );
        LinkedHashSet<RelationKey> result = new LinkedHashSet<>();
        candidates.stream().filter(requested::contains).forEach(result::add);
        return Set.copyOf(result);
    }

    @Override
    public Optional<RelationEvidence> insertRelation(
        RelationKey relation,
        String migrationBatch,
        String actor,
        Instant taggedAt
    ) {
        UUID id = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(taggedAt);
        return jdbc
            .query(
                """
                insert into catalog_asset_tag (
                    id, tag_id, asset_type, asset_key, tagged_by, tagged_at, migration_batch,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (tag_id, asset_type, asset_key) do nothing
                returning id, tag_id, asset_type, asset_key
                """,
                (resultSet, rowNumber) ->
                    new RelationEvidence(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tag_id", UUID.class),
                        resultSet.getString("asset_type"),
                        resultSet.getString("asset_key")
                    ),
                id,
                relation.tagId(),
                relation.assetType(),
                relation.assetKey(),
                actor,
                timestamp,
                migrationBatch,
                actor,
                timestamp,
                actor,
                timestamp
            )
            .stream()
            .findFirst();
    }

    @Override
    public List<RelationEvidence> findRelationsByBatch(String batchId) {
        return jdbc.query(
            """
            select id, tag_id, asset_type, asset_key
              from catalog_asset_tag
             where migration_batch = ?
             order by id
            """,
            (resultSet, rowNumber) ->
                new RelationEvidence(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("tag_id", UUID.class),
                    resultSet.getString("asset_type"),
                    resultSet.getString("asset_key")
                ),
            batchId
        );
    }

    @Override
    public int deleteRelationsByBatch(String batchId) {
        return jdbc.update("delete from catalog_asset_tag where migration_batch = ?", batchId);
    }
}
