package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsField;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsFieldRepository extends JpaRepository<AnalyticsField, Long> {
    List<AnalyticsField> findAllByActiveTrueOrderByTableIdAscPositionAscIdAsc();

    List<AnalyticsField> findAllByTableIdOrderByPositionAscIdAsc(Long tableId);

    List<AnalyticsField> findAllByDatabaseIdOrderByTableIdAscPositionAscIdAsc(Long databaseId);

    Optional<AnalyticsField> findByTableIdAndName(Long tableId, String name);

    long deleteByDatabaseId(Long databaseId);
}
