package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDatasetAccessTaskRepository extends JpaRepository<CatalogDatasetAccessTask, UUID> {
    List<CatalogDatasetAccessTask> findByRequestIdOrderByStepOrderAsc(UUID requestId);

    @Query(
        """
        select t from CatalogDatasetAccessTask t
        where t.requestId = :requestId
          and t.status = 'PENDING'
        order by t.stepOrder asc
        """
    )
    List<CatalogDatasetAccessTask> findPendingTasks(@Param("requestId") UUID requestId);

    @Query(
        """
        select t from CatalogDatasetAccessTask t
        where t.status = 'PENDING'
          and t.approverRole = :role
          and (:deptCode is null or t.deptCode is null or lower(t.deptCode) = lower(:deptCode))
        order by t.createdDate desc
        """
    )
    List<CatalogDatasetAccessTask> findPendingTasksForRole(
        @Param("role") String role,
        @Param("deptCode") String deptCode
    );

    @Query(
        """
        select t from CatalogDatasetAccessTask t
        where t.requestId = :requestId
          and t.status = 'PENDING'
        order by t.stepOrder asc
        """
    )
    Optional<CatalogDatasetAccessTask> findFirstPendingTask(@Param("requestId") UUID requestId);

    @Query(
        """
        select t from CatalogDatasetAccessTask t
        where lower(t.decidedBy) = lower(:decidedBy)
          and t.status in ('APPROVED', 'REJECTED')
        order by t.decidedAt desc
        """
    )
    List<CatalogDatasetAccessTask> findDecidedTasksForUser(@Param("decidedBy") String decidedBy);
}
