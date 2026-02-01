package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionTaskChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestionTaskChangeLogRepository extends JpaRepository<IngestionTaskChangeLog, Long> {

    @Query(
        value = """
        select * from ingestion_task_change_log c
        where (:taskId is null or c.task_id = :taskId)
          and (:objType is null or c.obj_type = :objType)
          and (:changeType is null or c.change_type = :changeType)
          and (:status is null or c.status = :status)
          and (
            :keyword is null
            or :keyword = ''
            or (
                lower(cast(c.task_name as text)) like lower(concat('%', :keyword, '%'))
                or lower(cast(c.summary as text)) like lower(concat('%', :keyword, '%'))
                or lower(cast(c.detail as text)) like lower(concat('%', :keyword, '%'))
            )
          )
        order by c.created_date desc
        """,
        countQuery = """
        select count(1) from ingestion_task_change_log c
        where (:taskId is null or c.task_id = :taskId)
          and (:objType is null or c.obj_type = :objType)
          and (:changeType is null or c.change_type = :changeType)
          and (:status is null or c.status = :status)
          and (
            :keyword is null
            or :keyword = ''
            or (
                lower(cast(c.task_name as text)) like lower(concat('%', :keyword, '%'))
                or lower(cast(c.summary as text)) like lower(concat('%', :keyword, '%'))
                or lower(cast(c.detail as text)) like lower(concat('%', :keyword, '%'))
            )
          )
        """,
        nativeQuery = true
    )
    Page<IngestionTaskChangeLog> search(
        @Param("taskId") Long taskId,
        @Param("objType") String objType,
        @Param("changeType") String changeType,
        @Param("status") String status,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    void deleteByTaskId(Long taskId);
}
