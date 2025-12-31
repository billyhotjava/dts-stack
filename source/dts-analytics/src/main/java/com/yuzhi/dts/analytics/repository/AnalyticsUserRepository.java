package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsUserRepository extends JpaRepository<AnalyticsUser, Long> {
    Optional<AnalyticsUser> findByEmailIgnoreCase(String email);

    Optional<AnalyticsUser> findFirstBySuperuserTrueOrderByIdAsc();

    long countBySuperuserTrue();
}
