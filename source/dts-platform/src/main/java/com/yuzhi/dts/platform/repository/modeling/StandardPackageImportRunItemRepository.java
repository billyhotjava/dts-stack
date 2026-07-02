package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StandardPackageImportRunItemRepository extends JpaRepository<StandardPackageImportRunItem, UUID> {
    List<StandardPackageImportRunItem> findByRunIdOrderBySeqDesc(UUID runId);

    long countByRunId(UUID runId);
}
