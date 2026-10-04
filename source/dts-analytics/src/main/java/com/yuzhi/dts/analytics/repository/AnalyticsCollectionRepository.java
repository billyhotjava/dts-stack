package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsCollection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsCollectionRepository extends JpaRepository<AnalyticsCollection, Long> {
    Optional<AnalyticsCollection> findByPersonalOwnerId(Long userId);

    List<AnalyticsCollection> findAllByArchivedFalseOrderByIdAsc();
}
