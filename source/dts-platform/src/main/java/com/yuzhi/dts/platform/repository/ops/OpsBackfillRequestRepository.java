package com.yuzhi.dts.platform.repository.ops;

import com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpsBackfillRequestRepository extends JpaRepository<OpsBackfillRequest, UUID> {
    List<OpsBackfillRequest> findTop200ByOrderByCreatedDateDesc();
}
