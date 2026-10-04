package com.yuzhi.dts.admin.repository.audit;

import com.yuzhi.dts.admin.domain.audit.AuditClassificationMiss;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditClassificationMissRepository extends JpaRepository<AuditClassificationMiss, Long> {}
