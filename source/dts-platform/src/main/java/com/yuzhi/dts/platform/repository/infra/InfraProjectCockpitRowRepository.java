package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitRow;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface InfraProjectCockpitRowRepository extends JpaRepository<InfraProjectCockpitRow, UUID> {
    @Transactional
    void deleteByBatchId(UUID batchId);
}
