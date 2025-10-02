package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovComplianceBatch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovComplianceBatchRepository extends JpaRepository<GovComplianceBatch, UUID> {
    List<GovComplianceBatch> findByStatusInOrderByCreatedDateDesc(List<String> statuses);
    Optional<GovComplianceBatch> findFirstByTemplateCodeOrderByCreatedDateDesc(String templateCode);
    long countByCreatedDateAfter(Instant since);
}

