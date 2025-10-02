package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DataStandardRepository extends JpaRepository<DataStandard, UUID>, JpaSpecificationExecutor<DataStandard> {
    Optional<DataStandard> findByCodeIgnoreCase(String code);
}
