package com.yuzhi.dts.platform.repository.security;

import com.yuzhi.dts.platform.domain.security.SecurityBackupPlan;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityBackupPlanRepository extends JpaRepository<SecurityBackupPlan, UUID> {}

