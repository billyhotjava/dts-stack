package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenDataset;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenDatasetRepository extends JpaRepository<AnalyticsScreenDataset, Long> {
    Optional<AnalyticsScreenDataset> findByUuid(String uuid);
    List<AnalyticsScreenDataset> findAllByOrderByCreatedAtDesc();
    void deleteByUuid(String uuid);
}
