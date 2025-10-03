package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamClassificationSyncLogRepository extends JpaRepository<IamClassificationSyncLog, UUID> {
    Optional<IamClassificationSyncLog> findTop1ByOrderByFinishedAtDesc();
}
