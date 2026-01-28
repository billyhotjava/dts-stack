package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.StdCodeMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StdCodeMappingRepository extends JpaRepository<StdCodeMapping, Long> {
    List<StdCodeMapping> findByCodeTypeIdOrderBySourceSysAsc(String codeTypeId);

    long countByCodeTypeId(String codeTypeId);

    boolean existsByCodeTypeIdAndSourceSysAndSrcCode(String codeTypeId, String sourceSys, String srcCode);
}
