package com.yuzhi.dts.platform.service.catalog;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CatalogTagMigrationStore {

    void acquireMigrationLock();

    void lockMigrationInputs();

    Optional<BatchRecord> lockBatch(String batchId);

    int insertBatch(BatchRecord batch);

    int markExecuted(String batchId, String executionJson, String actor, Instant modifiedAt);

    int markRolledBack(String batchId, String rollbackJson, String actor, Instant modifiedAt);

    Set<RelationKey> findExistingRelations(Collection<RelationKey> relations);

    Optional<RelationEvidence> insertRelation(
        RelationKey relation,
        String migrationBatch,
        String actor,
        Instant taggedAt
    );

    List<RelationEvidence> findRelationsByBatch(String batchId);

    int deleteRelationsByBatch(String batchId);

    record BatchRecord(
        String batchId,
        String checksum,
        String status,
        String planJson,
        String executionJson,
        String rollbackJson,
        String actor,
        Instant timestamp
    ) {}

    record RelationKey(UUID tagId, String assetType, String assetKey) {}

    record RelationEvidence(UUID id, UUID tagId, String assetType, String assetKey) {

        public RelationKey key() {
            return new RelationKey(tagId, assetType, assetKey);
        }
    }
}
