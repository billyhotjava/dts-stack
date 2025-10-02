package com.yuzhi.dts.platform.repository.audit;

import com.yuzhi.dts.platform.domain.audit.AuditEvent;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    Optional<AuditEvent> findTopByOrderByIdDesc();

    int deleteAllByOccurredAtBefore(Instant threshold);
}
