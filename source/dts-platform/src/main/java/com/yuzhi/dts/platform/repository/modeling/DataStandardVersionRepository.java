package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataStandardVersionRepository extends JpaRepository<DataStandardVersion, UUID> {
    List<DataStandardVersion> findByStandardOrderByCreatedDateDesc(DataStandard standard);
    Optional<DataStandardVersion> findByStandardAndVersion(DataStandard standard, String version);
}
