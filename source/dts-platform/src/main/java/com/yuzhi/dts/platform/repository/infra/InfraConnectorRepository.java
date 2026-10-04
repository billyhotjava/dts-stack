package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraConnector;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraConnectorRepository extends JpaRepository<InfraConnector, UUID> {
    Optional<InfraConnector> findByConnectorKeyIgnoreCase(String connectorKey);

    List<InfraConnector> findByStatusIgnoreCaseOrderByDisplayOrderAscConnectorKeyAsc(String status);

    List<InfraConnector> findByCategoryIgnoreCaseAndStatusIgnoreCaseOrderByDisplayOrderAscConnectorKeyAsc(String category, String status);
}
