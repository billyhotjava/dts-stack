package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryExecutionRepository extends JpaRepository<QueryExecution, UUID> {
    List<QueryExecution> findByResultSetId(UUID resultSetId);

    @Query(
        """
        select
          count(q),
          sum(case when q.status = com.yuzhi.dts.platform.domain.explore.ExecEnums.ExecStatus.SUCCESS then 1 else 0 end),
          sum(case when q.status = com.yuzhi.dts.platform.domain.explore.ExecEnums.ExecStatus.FAILED then 1 else 0 end),
          max(q.startedAt)
        from QueryExecution q
        where q.datasetId = :datasetId
          and (:since is null or q.startedAt >= :since)
        """
    )
    Object[] aggregateUsage(@Param("datasetId") UUID datasetId, @Param("since") Instant since);

    @Query(
        value = """
        select * from query_execution q
        where q.created_by = :user
          and (:status is null or q.status = :status)
          and (:connection is null or q.connection = :connection)
          and (:q is null or q.sql_text ilike concat('%', :q, '%'))
        order by q.started_at desc
        limit :lim
        """,
        nativeQuery = true
    )
    List<QueryExecution> findHistoryByUser(
        @Param("user") String user,
        @Param("status") String status,
        @Param("connection") String connection,
        @Param("q") String q,
        @Param("lim") int lim
    );

    @Modifying
    @Query("update QueryExecution q set q.resultSetId = null where q.resultSetId = ?1")
    int clearResultSetReferences(UUID resultSetId);
}
