package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenCompliancePolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenCompliancePolicyRepository extends JpaRepository<AnalyticsScreenCompliancePolicy, Long> {

    Optional<AnalyticsScreenCompliancePolicy> findFirstByCurrentTrueOrderByVersionNoDesc();

    Optional<AnalyticsScreenCompliancePolicy> findFirstByOrderByVersionNoDesc();

    Optional<AnalyticsScreenCompliancePolicy> findByVersionNo(Integer versionNo);

    List<AnalyticsScreenCompliancePolicy> findTop200ByOrderByVersionNoDesc();
}
