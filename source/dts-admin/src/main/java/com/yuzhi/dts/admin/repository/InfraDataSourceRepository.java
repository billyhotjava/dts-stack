package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.InfraDataSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraDataSourceRepository extends JpaRepository<InfraDataSource, UUID> {
    Optional<InfraDataSource> findFirstByTypeIgnoreCaseOrderByUpdatedAtDesc(String type);
}
