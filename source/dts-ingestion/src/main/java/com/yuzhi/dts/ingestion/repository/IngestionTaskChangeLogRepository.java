package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionTaskChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestionTaskChangeLogRepository extends JpaRepository<IngestionTaskChangeLog, Long> {

    @Query(
        """
        select c from IngestionTaskChangeLog c
        where (:taskId is null or c.taskId = :taskId)
          and (:objType is null or c.objType = :objType)
          and (:changeType is null or c.changeType = :changeType)
          and (:status is null or c.status = :status)
          and (
            :keyword is null
            or lower(c.taskName) like lower(concat('%', :keyword, '%'))
            or lower(c.summary) like lower(concat('%', :keyword, '%'))
            or lower(c.detail) like lower(concat('%', :keyword, '%'))
          )
        """
    )
    Page<IngestionTaskChangeLog> search(
        @Param("taskId") Long taskId,
        @Param("objType") String objType,
        @Param("changeType") String changeType,
        @Param("status") String status,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}
