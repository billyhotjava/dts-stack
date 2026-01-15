package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsCardRepository extends JpaRepository<AnalyticsCard, Long> {
    List<AnalyticsCard> findAllByArchivedFalseOrderByIdAsc();

    List<AnalyticsCard> findAllByArchivedTrueOrderByIdAsc();

    List<AnalyticsCard> findAllByArchivedFalseAndCollectionIdOrderByIdAsc(Long collectionId);

    List<AnalyticsCard> findAllByArchivedFalseAndCollectionIdIsNullOrderByIdAsc();
}
