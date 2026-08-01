package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionRollbackAffectedObject;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionRollbackAffectedObjectRepository extends JpaRepository<IngestionRollbackAffectedObject, Long> {
    List<IngestionRollbackAffectedObject> findByOperationReceiptIdOrderByIdAsc(UUID operationReceiptId);
}
