package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.RollbackAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the RollbackAuditLog entity.
 */
@Repository
public interface RollbackAuditLogRepository extends JpaRepository<RollbackAuditLog, Long> {

	List<RollbackAuditLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);

	List<RollbackAuditLog> findByDataSourceIdOrderByCreatedAtDesc(UUID dataSourceId);
}
