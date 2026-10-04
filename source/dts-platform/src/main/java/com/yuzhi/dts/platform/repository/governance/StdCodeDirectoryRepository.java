package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StdCodeDirectoryRepository extends JpaRepository<StdCodeDirectory, String>, JpaSpecificationExecutor<StdCodeDirectory> {
    Optional<StdCodeDirectory> findByCodeTypeCodeIgnoreCase(String codeTypeCode);
}
