package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovRuleVersionRepository extends JpaRepository<GovRuleVersion, UUID> {
    List<GovRuleVersion> findByRuleIdOrderByVersionDesc(UUID ruleId);
    Optional<GovRuleVersion> findFirstByRuleIdOrderByVersionDesc(UUID ruleId);
}

