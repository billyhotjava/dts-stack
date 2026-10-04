package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionRollbackOperationRepository extends JpaRepository<IngestionRollbackOperation, UUID> {

    Optional<IngestionRollbackOperation> findByIdempotencyKey(String idempotencyKey);

    List<IngestionRollbackOperation> findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
        String status,
        Instant cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IngestionRollbackOperation> findFirstBySourceDataSourceIdAndStatusInOrderByCreatedAtDesc(
        UUID sourceDataSourceId,
        Collection<String> statuses
    );

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(:idempotencyKey, 0))", nativeQuery = true)
    void lockIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from IngestionRollbackOperation operation where operation.receiptId = :receiptId")
    Optional<IngestionRollbackOperation> findForUpdate(@Param("receiptId") UUID receiptId);
}
