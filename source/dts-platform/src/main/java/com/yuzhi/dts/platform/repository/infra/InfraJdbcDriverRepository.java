package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraJdbcDriverRepository extends JpaRepository<InfraJdbcDriver, UUID> {
    Optional<InfraJdbcDriver> findByFileNameIgnoreCase(String fileName);
}
