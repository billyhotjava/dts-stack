package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsDataPortalItemRepository extends JpaRepository<AnalyticsDataPortalItem, Long> {
    List<AnalyticsDataPortalItem> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByDirectoryId(Long directoryId);

    boolean existsByDirectoryIdAndContentTypeAndContentId(Long directoryId, String contentType, Long contentId);
}
