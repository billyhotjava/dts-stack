package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataStandardRepository extends JpaRepository<DataStandard, UUID>, JpaSpecificationExecutor<DataStandard> {
    Optional<DataStandard> findByCodeIgnoreCase(String code);

    @Query("select ds from DataStandard ds where lower(ds.code) in :codes")
    List<DataStandard> findByCodeLowerIn(@Param("codes") Collection<String> codes);
}
