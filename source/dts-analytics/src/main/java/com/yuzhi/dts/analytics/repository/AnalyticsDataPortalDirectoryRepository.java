package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalDirectory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsDataPortalDirectoryRepository extends JpaRepository<AnalyticsDataPortalDirectory, Long> {
    List<AnalyticsDataPortalDirectory> findAllByArchivedFalseOrderBySortOrderAscIdAsc();

    boolean existsByArchivedFalseAndParentId(Long parentId);

    long countByArchivedFalse();
}
