package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraExternalRunLogRepository extends JpaRepository<InfraExternalRunLog, UUID> {
    List<InfraExternalRunLog> findByFinishedAtGreaterThanEqualAndFinishedAtLessThan(Instant fromInclusive, Instant toExclusive);

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
