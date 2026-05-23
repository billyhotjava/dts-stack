package com.yuzhi.dts.admin.repository.audit;

import com.yuzhi.dts.admin.domain.audit.AuditModuleCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditModuleCatalogRepository extends JpaRepository<AuditModuleCatalog, Long> {
    List<AuditModuleCatalog> findAllByEnabledTrueOrderByOrderValueAscModuleKeyAsc();

    Optional<AuditModuleCatalog> findFirstBySourceSystemIgnoreCaseAndModuleKeyIgnoreCase(String sourceSystem, String moduleKey);
}
