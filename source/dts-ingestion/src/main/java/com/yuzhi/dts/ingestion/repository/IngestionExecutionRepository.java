package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the IngestionExecution entity.
 */
@Repository
public interface IngestionExecutionRepository extends JpaRepository<IngestionExecution, Long> {

    /**
     * 根据任务ID查询执行历史
     */
    Page<IngestionExecution> findByTaskId(Long taskId, Pageable pageable);

    /**
     * 根据任务ID + 状态查询执行历史
     */
    Page<IngestionExecution> findByTaskIdAndStatusIgnoreCase(Long taskId, String status, Pageable pageable);

    /**
     * 根据任务ID + 失败分类查询执行历史
     */
    Page<IngestionExecution> findByTaskIdAndFailureCategoryIgnoreCase(Long taskId, String failureCategory, Pageable pageable);

    /**
     * 根据任务ID + 状态 + 失败分类查询执行历史
     */
    Page<IngestionExecution> findByTaskIdAndStatusIgnoreCaseAndFailureCategoryIgnoreCase(
        Long taskId,
        String status,
        String failureCategory,
        Pageable pageable
    );

    /**
     * 根据执行ID查找
     */
    Optional<IngestionExecution> findByExecutionId(String executionId);

    /**
     * 根据任务和执行ID查找
     */
    Optional<IngestionExecution> findFirstByTaskIdAndExecutionId(Long taskId, String executionId);

    /**
     * 根据状态查询执行历史
     */
    Page<IngestionExecution> findByStatus(String status, Pageable pageable);

    /**
     * 查询任务的最新一次执行记录
     */
    Optional<IngestionExecution> findFirstByTaskIdOrderByCreatedAtDesc(Long taskId);

    /**
     * 删除任务的全部执行记录
     */
    void deleteByTaskId(Long taskId);

    List<IngestionExecution> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);

    List<IngestionExecution> findByTaskIdAndCreatedAtBetweenOrderByCreatedAtAsc(Long taskId, Instant from, Instant to);

    @Query("select count(e) from IngestionExecution e where e.task.id = :taskId and lower(e.status) in :statuses")
    long countByTaskIdAndStatusesIgnoreCase(@Param("taskId") Long taskId, @Param("statuses") Collection<String> statuses);

    @Query(
        "select count(e) from IngestionExecution e where e.task.sourceDataSourceId = :sourceDataSourceId and lower(e.status) in :statuses"
    )
    long countBySourceDataSourceIdAndStatusesIgnoreCase(
        @Param("sourceDataSourceId") java.util.UUID sourceDataSourceId,
        @Param("statuses") Collection<String> statuses
    );

    @Query("select e from IngestionExecution e join fetch e.task t where lower(e.status) in :statuses")
    List<IngestionExecution> findByStatusesIgnoreCase(@Param("statuses") Collection<String> statuses);
}
