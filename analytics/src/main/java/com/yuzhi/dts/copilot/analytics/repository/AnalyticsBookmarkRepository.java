package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsBookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsBookmarkRepository extends JpaRepository<AnalyticsBookmark, Long> {

    Optional<AnalyticsBookmark> findByUserIdAndModelAndModelId(Long userId, String model, Long modelId);

    long deleteByUserIdAndModelAndModelId(Long userId, String model, Long modelId);

    List<AnalyticsBookmark> findAllByUserIdAndModel(Long userId, String model);

    List<AnalyticsBookmark> findAllByUserIdOrderByOrderingAscCreatedAtDesc(Long userId);

    @Query("select coalesce(max(b.ordering), 0) from AnalyticsBookmark b where b.userId = :userId")
    int findMaxOrderingByUserId(@Param("userId") Long userId);
}
