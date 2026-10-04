package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovDataEditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovDataEditLogRepository extends JpaRepository<GovDataEditLog, UUID> {
    Page<GovDataEditLog> findByTableNameAndRowId(String tableName, String rowId, Pageable pageable);
    Page<GovDataEditLog> findByTableName(String tableName, Pageable pageable);
}
