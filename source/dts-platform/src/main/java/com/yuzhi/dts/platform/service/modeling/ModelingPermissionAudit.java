package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelingPermissionAudit {
    private final AuditService audit;
    public ModelingPermissionAudit(AuditService audit) { this.audit = audit; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(String actor, String action, String target, String reason) {
        audit.recordAs(Objects.toString(actor, "unknown"), action, action.startsWith("CATALOG_") ? "catalog" : "modeling", action.startsWith("CATALOG_") ? "dataset" : "model_spec", target, "FAILED",
            Map.of("reason", reason), Map.of("audience", "platform"));
    }
    public void success(String actor, String action, String target, Map<String, Object> details) {
        audit.recordAs(actor, action, action.startsWith("CATALOG_") ? "catalog" : "modeling", action.startsWith("CATALOG_") ? "dataset" : "model_spec", target, "SUCCESS", details, Map.of("audience", "platform"));
    }
}
