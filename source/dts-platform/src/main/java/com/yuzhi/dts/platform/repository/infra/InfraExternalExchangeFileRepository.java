package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraExternalExchangeFileRepository extends JpaRepository<InfraExternalExchangeFile, UUID> {

    @Query(
        """
        select f from InfraExternalExchangeFile f
        where (:entryKey is null or lower(f.entryKey) = lower(:entryKey))
          and (:status is null or lower(f.status) = lower(:status))
          and (:enabledOnly = false or f.enabled = true)
          and (
            :keyword is null or
            lower(f.fileName) like lower(concat('%', :keyword, '%')) or
            lower(f.filePath) like lower(concat('%', :keyword, '%')) or
            lower(f.sourceSystem) like lower(concat('%', :keyword, '%')) or
            lower(f.batchCode) like lower(concat('%', :keyword, '%')) or
            lower(f.externalRef) like lower(concat('%', :keyword, '%'))
          )
        order by f.receivedAt desc nulls last, f.lastModifiedDate desc
        """
    )
    List<InfraExternalExchangeFile> search(
        @Param("entryKey") String entryKey,
        @Param("status") String status,
        @Param("keyword") String keyword,
        @Param("enabledOnly") boolean enabledOnly
    );
}

