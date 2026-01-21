package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraAirbyteSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfraAirbyteSourceRepository extends JpaRepository<InfraAirbyteSource, UUID> {
    Optional<InfraAirbyteSource> findBySourceId(String sourceId);
}
