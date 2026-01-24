package com.yuzhi.dts.ingestion.repository.infra;

import com.yuzhi.dts.ingestion.domain.infra.InfraAirbyteSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfraAirbyteSourceRepository extends JpaRepository<InfraAirbyteSource, UUID> {
    Optional<InfraAirbyteSource> findBySourceId(String sourceId);
}
