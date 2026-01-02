package com.yuzhi.dts.platform.repository.security;

import com.yuzhi.dts.platform.domain.security.SecurityBaselineRemediation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityBaselineRemediationRepository extends JpaRepository<SecurityBaselineRemediation, String> {}

