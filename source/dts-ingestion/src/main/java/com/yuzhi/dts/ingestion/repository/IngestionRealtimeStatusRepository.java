package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionRealtimeStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRealtimeStatusRepository extends JpaRepository<IngestionRealtimeStatus, Long> {
    Optional<IngestionRealtimeStatus> findByTaskId(Long taskId);
}
