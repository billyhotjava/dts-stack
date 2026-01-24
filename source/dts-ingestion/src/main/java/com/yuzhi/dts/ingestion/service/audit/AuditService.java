package com.yuzhi.dts.ingestion.service.audit;

import com.yuzhi.dts.common.audit.AuditStage;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    public void auditAction(String action, AuditStage stage, String subject, Map<String, Object> meta) {
        if (action == null) {
            return;
        }
        LOG.info("audit action={} stage={} subject={} meta={}", action, stage, subject, meta);
    }
}
