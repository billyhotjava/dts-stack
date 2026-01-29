package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the IngestionTask entity.
 */
@Repository
public interface IngestionTaskRepository extends JpaRepository<IngestionTask, Long> {

    /**
     * 根据状态查询任务列表
     */
    Page<IngestionTask> findByStatus(String status, Pageable pageable);

    /**
     * 根据状态和创建者查询任务列表
     */
    Page<IngestionTask> findByStatusAndCreatedBy(String status, String createdBy, Pageable pageable);

    /**
     * 根据Airflow DAG ID查找任务
     */
    Optional<IngestionTask> findByAirflowDagId(String airflowDagId);

    /**
     * 查询所有启用Airflow的活跃任务
     */
    Page<IngestionTask> findByAirflowEnabledTrueAndStatus(String status, Pageable pageable);

    /**
     * 根据源数据源ID查询任务
     */
    java.util.List<IngestionTask> findBySourceDataSourceId(java.util.UUID sourceDataSourceId);
}
