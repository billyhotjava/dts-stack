package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraExternalArtifactRepository extends JpaRepository<InfraExternalArtifact, UUID> {

    @Query(
        """
        select a from InfraExternalArtifact a
        where (:entryKey is null or lower(a.entryKey) = lower(:entryKey))
          and (:artifactType is null or lower(a.artifactType) = lower(:artifactType))
          and (:enabledOnly = false or a.enabled = true)
          and (
            :keyword is null or
            lower(a.name) like lower(concat('%', :keyword, '%')) or
            lower(a.externalId) like lower(concat('%', :keyword, '%')) or
            lower(a.externalUrl) like lower(concat('%', :keyword, '%'))
          )
        order by a.lastModifiedDate desc
        """
    )
    List<InfraExternalArtifact> search(
        @Param("entryKey") String entryKey,
        @Param("artifactType") String artifactType,
        @Param("keyword") String keyword,
        @Param("enabledOnly") boolean enabledOnly
    );
}

