package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsActivity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsActivityRepository extends JpaRepository<AnalyticsActivity, Long> {

    List<AnalyticsActivity> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    interface PopularItemProjection {
        String getModel();

        Long getModelId();

        long getViews();

        Instant getLastViewedAt();
    }

    @Query(
            """
            select a.model as model, a.modelId as modelId, count(a) as views, max(a.createdAt) as lastViewedAt
            from AnalyticsActivity a
            where a.action = 'view'
            group by a.model, a.modelId
            order by count(a) desc, max(a.createdAt) desc
            """)
    List<PopularItemProjection> findPopularViewedItems(Pageable pageable);
}
