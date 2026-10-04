package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsUserRepository extends JpaRepository<AnalyticsUser, Long> {
    Optional<AnalyticsUser> findByUsernameIgnoreCase(String username);

    Optional<AnalyticsUser> findFirstBySuperuserTrueOrderByIdAsc();

    long countBySuperuserTrue();
}
