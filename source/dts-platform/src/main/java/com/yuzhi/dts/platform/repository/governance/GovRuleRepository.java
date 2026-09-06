package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovRuleRepository extends JpaRepository<GovRule, UUID> {
    Optional<GovRule> findByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from GovRule rule where rule.id = :id")
    Optional<GovRule> findByIdForUpdate(@Param("id") UUID id);
    List<GovRule> findByDatasetId(UUID datasetId);
    List<GovRule> findByAutoTriggerTrueAndEnabledTrue();
    int countByEnabledTrue();
}
