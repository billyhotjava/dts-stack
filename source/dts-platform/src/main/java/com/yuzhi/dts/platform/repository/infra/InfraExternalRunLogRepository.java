package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraExternalRunLogRepository extends JpaRepository<InfraExternalRunLog, UUID> {
    List<InfraExternalRunLog> findByFinishedAtGreaterThanEqualAndFinishedAtLessThan(Instant fromInclusive, Instant toExclusive);

    long countByFinishedAtGreaterThanEqual(Instant since);

    long countByFinishedAtGreaterThanEqualAndStatusIn(Instant since, Collection<String> statuses);

    long countByStatusInAndFinishedAtIsNull(Collection<String> statuses);

    List<InfraExternalRunLog> findTop200ByOrderByStartedAtDesc();

    java.util.Optional<InfraExternalRunLog> findFirstByEntryKeyIgnoreCaseAndExternalRunId(
        String entryKey,
        String externalRunId
    );

    long deleteByEntryKeyIgnoreCaseAndArtifactNameIgnoreCase(String entryKey, String artifactName);

    long deleteByEntryKeyIgnoreCaseAndArtifactId(String entryKey, UUID artifactId);

    @Query(
        """
        select r from InfraExternalRunLog r
        where r.startedAt is not null
          and r.startedAt >= :fromInclusive
          and r.startedAt < :toExclusive
          and (:entryKey is null or lower(r.entryKey) = lower(:entryKey))
          and (:ownerDept is null or lower(r.ownerDept) = lower(:ownerDept))
          and (:artifactId is null or r.artifactId = :artifactId)
          and (:artifactName is null or lower(r.artifactName) like lower(concat('%', :artifactName, '%')))
        order by r.startedAt asc
        """
    )
    List<InfraExternalRunLog> findForMetrics(
        @Param("fromInclusive") Instant fromInclusive,
        @Param("toExclusive") Instant toExclusive,
        @Param("entryKey") String entryKey,
        @Param("ownerDept") String ownerDept,
        @Param("artifactId") UUID artifactId,
        @Param("artifactName") String artifactName
    );

    @Query(
        """
        select r from InfraExternalRunLog r
        where (:entryKey is null or lower(r.entryKey) = lower(:entryKey))
          and (:artifactId is null or r.artifactId = :artifactId)
          and (:status is null or lower(r.status) = lower(:status))
          and (:enabledOnly = false or r.enabled = true)
          and (
            :keyword is null or
            lower(r.artifactName) like lower(concat('%', :keyword, '%')) or
            lower(r.externalRunId) like lower(concat('%', :keyword, '%')) or
            lower(r.message) like lower(concat('%', :keyword, '%'))
          )
        order by r.startedAt desc nulls last, r.lastModifiedDate desc
        """
    )
    List<InfraExternalRunLog> search(
        @Param("entryKey") String entryKey,
        @Param("artifactId") UUID artifactId,
        @Param("status") String status,
        @Param("keyword") String keyword,
        @Param("enabledOnly") boolean enabledOnly
    );
}
