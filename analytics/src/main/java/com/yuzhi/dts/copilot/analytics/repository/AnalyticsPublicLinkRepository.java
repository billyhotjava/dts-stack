package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPublicLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsPublicLinkRepository extends JpaRepository<AnalyticsPublicLink, Long> {
    Optional<AnalyticsPublicLink> findByModelAndModelId(String model, Long modelId);

    Optional<AnalyticsPublicLink> findByPublicUuid(String publicUuid);

    List<AnalyticsPublicLink> findAllByModel(String model);
}

