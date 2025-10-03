package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.SvcDataProductVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SvcDataProductVersionRepository extends JpaRepository<SvcDataProductVersion, UUID> {
    List<SvcDataProductVersion> findByProductIdOrderByReleasedAtDesc(UUID productId);
    Optional<SvcDataProductVersion> findByProductIdAndVersion(UUID productId, String version);
}
