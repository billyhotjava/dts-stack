package com.yuzhi.dts.admin.repository.audit;

import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {
    @Query("select distinct lower(e.moduleKey) from AuditEntry e where e.moduleKey is not null and e.moduleKey <> '' order by lower(e.moduleKey)")
    List<String> findDistinctModuleKeys();

    @EntityGraph(attributePaths = { "targets", "details" })
    @Query("select e from AuditEntry e where e.id = :id")
    Optional<AuditEntry> findDetailedById(@Param("id") Long id);
}
