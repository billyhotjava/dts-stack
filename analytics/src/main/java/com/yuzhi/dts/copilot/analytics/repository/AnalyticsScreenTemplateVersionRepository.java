package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenTemplateVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenTemplateVersionRepository extends JpaRepository<AnalyticsScreenTemplateVersion, Long> {

    List<AnalyticsScreenTemplateVersion> findAllByTemplateIdOrderByVersionNoDesc(Long templateId);

    Optional<AnalyticsScreenTemplateVersion> findByTemplateIdAndVersionNo(Long templateId, Integer versionNo);

    Optional<AnalyticsScreenTemplateVersion> findFirstByTemplateIdOrderByVersionNoDesc(Long templateId);
}
