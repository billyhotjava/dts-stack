package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsReportTemplate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsReportTemplateRepository extends JpaRepository<AnalyticsReportTemplate, Long> {

    List<AnalyticsReportTemplate> findAllByArchivedFalseOrderByUpdatedAtDesc(Pageable pageable);
}
