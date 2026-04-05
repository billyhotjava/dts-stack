package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovRuleRepository extends JpaRepository<GovRule, UUID> {
    Optional<GovRule> findByCode(String code);
    List<GovRule> findByAutoTriggerTrueAndEnabledTrue();
    int countByEnabledTrue();
}
