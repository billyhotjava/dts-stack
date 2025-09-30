package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.platform.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditClient client;

    public AuditService(AuditClient client) {
        this.client = client;
    }

    public void audit(String action, String targetKind, String targetRef) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        log.info("AUDIT actor={} action={} targetKind={} targetRef={}", actor, action, targetKind, targetRef);
        client.send(actor, action, targetKind, targetRef);
    }
}

