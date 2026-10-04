package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AuditOperationMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditOperationMappingRepository extends JpaRepository<AuditOperationMapping, Long> {
    List<AuditOperationMapping> findAllByEnabledTrueOrderByOrderValueAscIdAsc();
}

