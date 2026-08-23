package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GovRuleBindingRepository extends JpaRepository<GovRuleBinding, UUID> {
    List<GovRuleBinding> findByRuleVersionId(UUID ruleVersionId);
    List<GovRuleBinding> findByDatasetId(UUID datasetId);
    List<GovRuleBinding> findByDatasetIdAndRuleVersionStatus(UUID datasetId, String status);

    @EntityGraph(attributePaths = { "ruleVersion", "ruleVersion.rule" })
    @Query("SELECT b FROM GovRuleBinding b WHERE b.datasetId = :datasetId AND UPPER(b.ruleVersion.status) = UPPER(:status)")
    List<GovRuleBinding> findWorkflowBindings(
        @Param("datasetId") UUID datasetId,
        @Param("status") String status
    );

    @EntityGraph(attributePaths = { "ruleVersion", "ruleVersion.rule" })
    @Query(
        "SELECT b FROM GovRuleBinding b WHERE b.ruleVersion.rule.id = :ruleId " +
        "AND UPPER(b.ruleVersion.status) = UPPER(:status)"
    )
    List<GovRuleBinding> findPublishedWorkflowBindingsByRuleId(
        @Param("ruleId") UUID ruleId,
        @Param("status") String status
    );

    @Query("SELECT COUNT(DISTINCT b.datasetId) FROM GovRuleBinding b")
    long countDistinctDatasetIds();
}
