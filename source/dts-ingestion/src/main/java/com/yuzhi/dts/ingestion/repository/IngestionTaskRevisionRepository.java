package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionTaskRevisionRepository extends JpaRepository<IngestionTaskRevision, Long> {
    Optional<IngestionTaskRevision> findFirstByTaskIdAndStateOrderByRevisionNumberDesc(Long taskId, String state);

    Optional<IngestionTaskRevision> findFirstByTaskIdOrderByRevisionNumberDesc(Long taskId);

    List<IngestionTaskRevision> findAllByTaskIdOrderByRevisionNumberDesc(Long taskId);

    List<IngestionTaskRevision> findAllByTaskIdInOrderByTaskIdAscRevisionNumberDesc(Collection<Long> taskIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IngestionTaskRevision r where r.task.id = :taskId order by r.revisionNumber desc")
    List<IngestionTaskRevision> findAllByTaskIdForUpdate(@Param("taskId") Long taskId);

    @Query("select coalesce(max(r.revisionNumber), 0) from IngestionTaskRevision r where r.task.id = :taskId")
    Integer findMaxRevisionNumber(@Param("taskId") Long taskId);
}
