package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StandardPackageImportRunRepository extends JpaRepository<StandardPackageImportRun, UUID> {
    Page<StandardPackageImportRun> findAllByOrderByCreatedDateDesc(Pageable pageable);

    boolean existsByPackageNameAndSourceAndStatus(String packageName, String source, String status);
}
