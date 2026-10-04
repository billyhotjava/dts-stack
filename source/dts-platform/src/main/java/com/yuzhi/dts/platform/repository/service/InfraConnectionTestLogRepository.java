package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraConnectionTestLogRepository extends JpaRepository<InfraConnectionTestLog, UUID> {
    List<InfraConnectionTestLog> findTop20ByOrderByCreatedDateDesc();
    List<InfraConnectionTestLog> findTop20ByDataSourceIdOrderByCreatedDateDesc(UUID dataSourceId);
}
