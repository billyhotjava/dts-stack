package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionConnectorCapability;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionConnectorCapabilityRepository extends JpaRepository<IngestionConnectorCapability, Long> {
    List<IngestionConnectorCapability> findByEnabledTrueOrderByConnectorTypeAsc();

    Optional<IngestionConnectorCapability> findByConnectorTypeIgnoreCase(String connectorType);
}
