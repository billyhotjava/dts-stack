package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenTemplateRepository extends JpaRepository<AnalyticsScreenTemplate, Long> {

    List<AnalyticsScreenTemplate> findAllByArchivedFalseOrderByUpdatedAtDesc();

    Optional<AnalyticsScreenTemplate> findByIdAndArchivedFalse(Long id);
}
