package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamDept;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamDeptRepository extends JpaRepository<IamDept, UUID> {
    List<IamDept> findTop100ByOrderByCodeAsc();
    List<IamDept> findTop100ByCodeIgnoreCaseContainingOrNameZhIgnoreCaseContainingOrderByCodeAsc(String code, String nameZh);
}

