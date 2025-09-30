package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.SystemConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByKey(String key);
}

