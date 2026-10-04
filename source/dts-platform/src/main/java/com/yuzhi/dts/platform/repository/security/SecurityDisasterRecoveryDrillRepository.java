package com.yuzhi.dts.platform.repository.security;

import com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityDisasterRecoveryDrillRepository extends JpaRepository<SecurityDisasterRecoveryDrill, UUID> {
    List<SecurityDisasterRecoveryDrill> findTop50ByOrderByDrillDateDesc();
}

