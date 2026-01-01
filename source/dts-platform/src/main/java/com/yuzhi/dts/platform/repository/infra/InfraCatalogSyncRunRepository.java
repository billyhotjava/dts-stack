package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfraCatalogSyncRunRepository extends JpaRepository<InfraCatalogSyncRun, UUID> {
    List<InfraCatalogSyncRun> findTop100ByIntegrationOrderByStartedAtDesc(String integration);
}

