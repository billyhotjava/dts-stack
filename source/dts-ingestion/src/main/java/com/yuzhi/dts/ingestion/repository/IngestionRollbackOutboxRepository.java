package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionRollbackOutbox;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionRollbackOutboxRepository extends JpaRepository<IngestionRollbackOutbox, UUID> {

    List<IngestionRollbackOutbox> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        Collection<String> statuses,
        Instant dueAt
    );

    @Modifying
    @Query(
        "update IngestionRollbackOutbox event set event.status = 'SENDING', event.updatedAt = :now " +
        "where event.id = :id and event.status in ('PENDING', 'RETRY') and event.nextAttemptAt <= :now"
    )
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query(
        "update IngestionRollbackOutbox event set event.status = 'RETRY', event.updatedAt = :now, " +
        "event.nextAttemptAt = :now where event.status = 'SENDING' and event.updatedAt < :staleBefore"
    )
    int releaseStaleClaims(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
