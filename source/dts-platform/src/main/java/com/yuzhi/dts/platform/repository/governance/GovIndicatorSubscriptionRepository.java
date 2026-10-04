package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorSubscriptionRepository extends JpaRepository<GovIndicatorSubscription, UUID> {
    List<GovIndicatorSubscription> findByUserLoginOrderByDisplayOrderAsc(String userLogin);
    void deleteByIdAndUserLogin(UUID id, String userLogin);
    boolean existsByIndicatorIdAndUserLogin(UUID indicatorId, String userLogin);
}
