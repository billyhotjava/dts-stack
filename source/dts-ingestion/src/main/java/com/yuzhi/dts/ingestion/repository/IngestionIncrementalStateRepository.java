package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionIncrementalState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionIncrementalStateRepository extends JpaRepository<IngestionIncrementalState, Long> {

    @Query(
        "select s from IngestionIncrementalState s " +
        "where s.taskId = :taskId and upper(s.sourceTable) = upper(:sourceTable)"
    )
    Optional<IngestionIncrementalState> findByTaskIdAndSourceTableIgnoreCase(
        @Param("taskId") Long taskId,
        @Param("sourceTable") String sourceTable
    );

    List<IngestionIncrementalState> findByTaskIdOrderByUpdatedAtDescSourceTableAsc(Long taskId);

    void deleteByTaskId(Long taskId);
}
