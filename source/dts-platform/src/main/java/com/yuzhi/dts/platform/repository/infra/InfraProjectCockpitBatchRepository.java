package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitBatch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfraProjectCockpitBatchRepository extends JpaRepository<InfraProjectCockpitBatch, UUID> {
    Optional<InfraProjectCockpitBatch> findByExternalExchangeFileId(UUID externalExchangeFileId);
}
