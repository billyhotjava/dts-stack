package com.yuzhi.dts.common.repository;

import com.yuzhi.dts.common.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
    AuditEvent findByIdempotencyKey(String idempotencyKey);
}

