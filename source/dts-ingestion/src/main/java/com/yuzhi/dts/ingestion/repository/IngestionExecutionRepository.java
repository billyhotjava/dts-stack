package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
     * 根据执行ID查找
     */
    Optional<IngestionExecution> findByExecutionId(String executionId);

    /**
     * 根据状态查询执行历史
     */
    Page<IngestionExecution> findByStatus(String status, Pageable pageable);

    /**
     * 查询任务的最新一次执行记录
     */
    Optional<IngestionExecution> findFirstByTaskIdOrderByCreatedAtDesc(Long taskId);
}
