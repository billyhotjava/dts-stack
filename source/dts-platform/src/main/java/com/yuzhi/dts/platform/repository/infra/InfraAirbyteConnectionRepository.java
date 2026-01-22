package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraAirbyteConnection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfraAirbyteConnectionRepository extends JpaRepository<InfraAirbyteConnection, UUID> {
    Optional<InfraAirbyteConnection> findByConnectionId(String connectionId);

    long countByInfraSourceId(UUID infraSourceId);
}
