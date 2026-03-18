package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitIssue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface InfraProjectCockpitIssueRepository extends JpaRepository<InfraProjectCockpitIssue, UUID> {
    List<InfraProjectCockpitIssue> findByBatchIdOrderByRowNoAscIdAsc(UUID batchId);

    @Transactional
    void deleteByBatchId(UUID batchId);
}
