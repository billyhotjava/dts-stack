package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorDefinitionRepository extends JpaRepository<GovIndicatorDefinition, UUID>,
    JpaSpecificationExecutor<GovIndicatorDefinition> {
    Optional<GovIndicatorDefinition> findFirstByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GovIndicatorDefinition g WHERE g.id = :id")
    Optional<GovIndicatorDefinition> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GovIndicatorDefinition g ORDER BY g.id")
    List<GovIndicatorDefinition> findAllForLifecycleUpdate();

    List<GovIndicatorDefinition> findByDomainAndStatusNot(String domain, String excludedStatus);

    List<GovIndicatorDefinition> findByTemplateId(UUID templateId);

    List<GovIndicatorDefinition> findByIsDerivedTrue();

    List<GovIndicatorDefinition> findByStatusIn(List<String> statuses);

    List<GovIndicatorDefinition> findByDatasetId(String datasetId);

    List<GovIndicatorDefinition> findByDomainIgnoreCase(String domain);

    @Query("SELECT COUNT(g) FROM GovIndicatorDefinition g WHERE LOWER(g.domain) = LOWER(:domain)")
    long countByDomainIgnoreCase(@Param("domain") String domain);

    @Query("SELECT COUNT(g) FROM GovIndicatorDefinition g WHERE LOWER(g.domain) = LOWER(:domain) AND UPPER(g.status) = :status")
    long countByDomainIgnoreCaseAndStatusUpper(@Param("domain") String domain, @Param("status") String status);
}
