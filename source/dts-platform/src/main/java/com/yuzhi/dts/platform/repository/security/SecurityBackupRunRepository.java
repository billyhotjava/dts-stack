package com.yuzhi.dts.platform.repository.security;

import com.yuzhi.dts.platform.domain.security.SecurityBackupRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityBackupRunRepository extends JpaRepository<SecurityBackupRun, UUID> {
    List<SecurityBackupRun> findTop50ByPlanIdOrderByStartedAtDesc(UUID planId);
}

