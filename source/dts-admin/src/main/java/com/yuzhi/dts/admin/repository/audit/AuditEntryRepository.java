package com.yuzhi.dts.admin.repository.audit;

import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {
    Optional<AuditEntry> findByIngestProducerAndIngestEventId(String ingestProducer, String ingestEventId);

    @Query("select distinct lower(e.moduleKey) from AuditEntry e where e.moduleKey is not null and e.moduleKey <> '' order by lower(e.moduleKey)")
    List<String> findDistinctModuleKeys();

    @EntityGraph(attributePaths = { "targets", "details" })
    @Query("select e from AuditEntry e where e.id = :id")
    Optional<AuditEntry> findDetailedById(@Param("id") Long id);

    /**
     * Delete audit_entry_target rows whose parent entry is older than the cutoff.
     * Uses a native sub-select to stay within batch-size limits.
     */
    @Modifying
    @Query(
        value = "DELETE FROM audit_entry_target WHERE entry_id IN " +
                "(SELECT id FROM audit_entry WHERE occurred_at < :cutoff LIMIT :limit)",
        nativeQuery = true
    )
    int deleteTargetsByCutoff(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /**
     * Delete audit_entry_detail rows whose parent entry is older than the cutoff.
     */
    @Modifying
    @Query(
        value = "DELETE FROM audit_entry_detail WHERE entry_id IN " +
                "(SELECT id FROM audit_entry WHERE occurred_at < :cutoff LIMIT :limit)",
        nativeQuery = true
    )
    int deleteDetailsByCutoff(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /**
     * Delete audit_entry rows older than the cutoff, limited by batch size.
     * Must be called AFTER child rows (targets, details) have been removed.
     */
    @Modifying
    @Query(
        value = "DELETE FROM audit_entry WHERE id IN " +
                "(SELECT id FROM audit_entry WHERE occurred_at < :cutoff LIMIT :limit)",
        nativeQuery = true
    )
    int deleteEntriesByCutoff(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
