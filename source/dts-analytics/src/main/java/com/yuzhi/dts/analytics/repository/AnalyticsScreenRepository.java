package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsScreenRepository extends JpaRepository<AnalyticsScreen, Long> {
    List<AnalyticsScreen> findAllByArchivedFalseOrderByIdDesc();

    List<AnalyticsScreen> findAllByIdInAndArchivedFalse(List<Long> ids);

    @Query("SELECT s.id FROM AnalyticsScreen s WHERE s.creatorId = :creatorId AND s.archived = false")
    List<Long> findIdsByCreatorIdAndArchivedFalse(@Param("creatorId") Long creatorId);

    /** Sprint-17: includes archived rows so dts-platform reconcile can flip enabled=false. */
    List<AnalyticsScreen> findAllByOrderByIdDesc();

    /**
     * Sprint-24 F4：大屏密级合规盘点。返回所有未归档且未设密级的大屏，供数据治理
     * 角色（见 MetabaseAuth.SCREEN_AUDITOR_ROLES）通过 /admin/unclassified 端点收敛
     * 存量未设密大屏。空字符串视为已设：历史 column 类型是 varchar 可能存空串，
     * 用 IS NULL OR LENGTH(TRIM())=0 兜底。
     */
    @Query(
        "SELECT s FROM AnalyticsScreen s " +
        "WHERE s.archived = false AND (s.classification IS NULL OR LENGTH(TRIM(s.classification)) = 0) " +
        "ORDER BY s.createdAt DESC"
    )
    List<AnalyticsScreen> findUnclassified();
}
