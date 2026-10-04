package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenEditLock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenEditLockRepository extends JpaRepository<AnalyticsScreenEditLock, Long> {

    Optional<AnalyticsScreenEditLock> findByScreenId(Long screenId);

    void deleteByScreenId(Long screenId);
}
