package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MetadataStandardRepository extends JpaRepository<MetadataStandard, UUID>, JpaSpecificationExecutor<MetadataStandard> {
    Optional<MetadataStandard> findByFieldNameEnIgnoreCaseAndDomainIgnoreCase(String fieldNameEn, String domain);
}
