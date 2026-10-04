package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraDataSourceRepository extends JpaRepository<InfraDataSource, UUID> {
    long countByTypeIgnoreCase(String type);

    Optional<InfraDataSource> findFirstByTypeIgnoreCase(String type);

    List<InfraDataSource> findByTypeIgnoreCase(String type);

    Optional<InfraDataSource> findFirstByTypeIgnoreCaseAndStatusIgnoreCase(String type, String status);

    List<InfraDataSource> findByStatusIgnoreCase(String status);
}
