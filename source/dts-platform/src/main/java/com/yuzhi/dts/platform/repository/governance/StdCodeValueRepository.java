package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StdCodeValueRepository extends JpaRepository<StdCodeValue, Long> {
    List<StdCodeValue> findByCodeTypeIdOrderBySortNumAscCodeValueAsc(String codeTypeId);

    long countByCodeTypeId(String codeTypeId);

    boolean existsByCodeTypeIdAndCodeValue(String codeTypeId, String codeValue);

    void deleteByCodeTypeId(String codeTypeId);
}
