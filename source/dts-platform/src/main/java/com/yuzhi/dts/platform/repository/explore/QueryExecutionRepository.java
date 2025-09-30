package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryExecutionRepository extends JpaRepository<QueryExecution, UUID> {
    List<QueryExecution> findByResultSetId(UUID resultSetId);

    @Modifying
    @Query("update QueryExecution q set q.resultSetId = null where q.resultSetId = ?1")
    int clearResultSetReferences(UUID resultSetId);
}

