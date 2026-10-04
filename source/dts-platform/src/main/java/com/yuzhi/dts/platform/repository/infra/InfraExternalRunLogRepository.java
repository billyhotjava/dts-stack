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
          and (cast(:entryKey as text) is null or lower(r.entryKey) = lower(cast(:entryKey as text)))
          and (cast(:ownerDept as text) is null or lower(r.ownerDept) = lower(cast(:ownerDept as text)))
          and (:artifactId is null or r.artifactId = :artifactId)
          and (cast(:artifactName as text) is null or lower(r.artifactName) like lower(concat('%', cast(:artifactName as text), '%')))
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
        where (cast(:entryKey as text) is null or lower(r.entryKey) = lower(cast(:entryKey as text)))
          and (:artifactId is null or r.artifactId = :artifactId)
          and (cast(:status as text) is null or lower(r.status) = lower(cast(:status as text)))
          and (:enabledOnly = false or r.enabled = true)
          and (
            cast(:keyword as text) is null or
            lower(r.artifactName) like lower(concat('%', cast(:keyword as text), '%')) or
            lower(r.externalRunId) like lower(concat('%', cast(:keyword as text), '%')) or
            lower(r.message) like lower(concat('%', cast(:keyword as text), '%'))
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
