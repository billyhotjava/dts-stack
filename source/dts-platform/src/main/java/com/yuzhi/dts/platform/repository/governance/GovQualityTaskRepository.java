package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityTask;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GovQualityTaskRepository extends JpaRepository<GovQualityTask, UUID> {
    List<GovQualityTask> findByEnabledTrue();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update GovQualityTask task
           set task.lastTriggeredAt = :claimedAt
         where task.id = :taskId
           and task.enabled = true
           and (task.lastTriggeredAt is null or task.lastTriggeredAt <= :dueBefore)
        """)
    int claimDueExecution(
        @Param("taskId") UUID taskId,
        @Param("claimedAt") Instant claimedAt,
        @Param("dueBefore") Instant dueBefore
    );
}
