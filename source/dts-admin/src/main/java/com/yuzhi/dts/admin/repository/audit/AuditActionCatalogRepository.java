package com.yuzhi.dts.admin.repository.audit;

import com.yuzhi.dts.admin.domain.audit.AuditActionCatalogEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditActionCatalogRepository extends JpaRepository<AuditActionCatalogEntry, Long> {
    List<AuditActionCatalogEntry> findAllByEnabledTrueOrderByModuleKeyAscActionCodeAsc();

    Optional<AuditActionCatalogEntry> findFirstBySourceSystemIgnoreCaseAndActionCodeIgnoreCase(
        String sourceSystem,
        String actionCode
    );

    Optional<AuditActionCatalogEntry> findFirstBySourceSystemIgnoreCaseAndActionCodeIgnoreCaseAndEnabledTrue(
        String sourceSystem,
        String actionCode
    );
}
