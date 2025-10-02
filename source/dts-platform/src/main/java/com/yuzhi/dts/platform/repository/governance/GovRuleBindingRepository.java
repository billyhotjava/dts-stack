package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovRuleBindingRepository extends JpaRepository<GovRuleBinding, UUID> {
    List<GovRuleBinding> findByRuleVersionId(UUID ruleVersionId);
    List<GovRuleBinding> findByDatasetId(UUID datasetId);
}

