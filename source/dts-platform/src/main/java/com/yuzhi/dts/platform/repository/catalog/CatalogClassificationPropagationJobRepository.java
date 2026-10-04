package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogClassificationPropagationJobRepository
    extends JpaRepository<CatalogClassificationPropagationJob, UUID> {
    Optional<CatalogClassificationPropagationJob> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(
        value = """
        insert into catalog_classification_propagation_job (
            id, idempotency_key, target_dataset_id, target_asset_key,
            trigger_type, trigger_ref, status, attempts, affected_subjects,
            next_attempt_at, created_by, created_date, last_modified_by, last_modified_date
        ) values (
            :id, :idempotencyKey, :targetDatasetId, :targetAssetKey,
            :triggerType, :triggerRef, 'PENDING', 0, 0,
            :now, :actor, :now, :actor, :now
        )
        on conflict (idempotency_key) do nothing
        """,
        nativeQuery = true
    )
    int insertPendingIfAbsent(
        @Param("id") UUID id,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("targetDatasetId") UUID targetDatasetId,
        @Param("targetAssetKey") String targetAssetKey,
        @Param("triggerType") String triggerType,
        @Param("triggerRef") String triggerRef,
        @Param("actor") String actor,
        @Param("now") Instant now
    );

    List<CatalogClassificationPropagationJob> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedDateAsc(
        Collection<String> statuses,
        Instant nextAttemptAt
    );
}
