package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GovQualityWorkflowRunRepository extends JpaRepository<GovQualityWorkflowRun, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            INSERT INTO gov_quality_workflow_run (
                id, task_id, dataset_id, rule_id, retry_of_id, attempt_no, max_retry_attempts,
                retry_backoff_seconds, trigger_type, trigger_ref,
                idempotency_key, status, expected_run_count, completed_run_count, passed_count,
                failed_count, dispatch_failure_count, scheduled_at, created_by, created_date,
                last_modified_by, last_modified_date
            ) VALUES (
                :id, :taskId, :datasetId, :ruleId, :retryOfId, :attemptNo, :maxRetryAttempts,
                :retryBackoffSeconds, :triggerType, :triggerRef,
                :idempotencyKey, 'QUEUED', 0, 0, 0, 0, 0, :scheduledAt, :actor, :createdAt,
                :actor, :createdAt
            ) ON CONFLICT DO NOTHING
            """,
        nativeQuery = true
    )
    int insertQueued(
        @Param("id") UUID id,
        @Param("taskId") UUID taskId,
        @Param("datasetId") UUID datasetId,
        @Param("ruleId") UUID ruleId,
        @Param("retryOfId") UUID retryOfId,
        @Param("attemptNo") int attemptNo,
        @Param("maxRetryAttempts") int maxRetryAttempts,
        @Param("retryBackoffSeconds") int retryBackoffSeconds,
        @Param("triggerType") String triggerType,
        @Param("triggerRef") String triggerRef,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("scheduledAt") Instant scheduledAt,
        @Param("actor") String actor,
        @Param("createdAt") Instant createdAt
    );

    Optional<GovQualityWorkflowRun> findByIdempotencyKey(String idempotencyKey);

    List<GovQualityWorkflowRun> findByDatasetIdOrderByCreatedDateDesc(UUID datasetId, Pageable pageable);

    List<GovQualityWorkflowRun> findByDatasetIdAndStatusIgnoreCaseOrderByCreatedDateDesc(
        UUID datasetId,
        String status,
        Pageable pageable
    );

    List<GovQualityWorkflowRun> findByTaskIdOrderByCreatedDateDesc(UUID taskId, Pageable pageable);

    List<GovQualityWorkflowRun> findByTaskIdAndStatusIgnoreCaseOrderByCreatedDateDesc(
        UUID taskId,
        String status,
        Pageable pageable
    );

    List<GovQualityWorkflowRun> findByDatasetIdInOrderByCreatedDateDesc(
        Collection<UUID> datasetIds,
        Pageable pageable
    );

    List<GovQualityWorkflowRun> findByDatasetIdInAndStatusIgnoreCaseOrderByCreatedDateDesc(
        Collection<UUID> datasetIds,
        String status,
        Pageable pageable
    );

    @Query("select distinct workflow.datasetId from GovQualityWorkflowRun workflow")
    List<UUID> findDistinctDatasetIds();

    Optional<GovQualityWorkflowRun> findFirstByTaskIdAndStatusInOrderByCreatedDateDesc(
        UUID taskId,
        Collection<String> statuses
    );

    List<GovQualityWorkflowRun> findByStatusInOrderByCreatedDateAsc(Collection<String> statuses, Pageable pageable);
}
