package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.config.AuditProperties.TenancyMode;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import org.springframework.stereotype.Component;

/** Resolves audit ownership exclusively from trusted server configuration. */
@Component
public class AuditTenantResolver {

    private final String tenantId;

    public AuditTenantResolver(AuditProperties properties) {
        if (properties == null) throw new IllegalArgumentException("audit properties are required");
        if (properties.getTenancyMode() == null) {
            throw new IllegalStateException("auditing.tenancy-mode is required and must be SINGLE_TENANT");
        }
        if (properties.getTenancyMode() != TenancyMode.SINGLE_TENANT) {
            throw new IllegalStateException(
                "Only auditing.tenancy-mode=SINGLE_TENANT is supported; MULTI_TENANT requires a request-scoped tenant resolver"
            );
        }
        String configured = properties.getTenantId();
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("auditing.tenant-id is required");
        }
        String normalized = configured.trim();
        if (normalized.length() > 128) throw new IllegalArgumentException("auditing.tenant-id is too long");
        if (PlatformAuditOutboxRepository.LEGACY_UNSCOPED_TENANT.equals(normalized)) {
            throw new IllegalArgumentException("auditing.tenant-id cannot use the legacy unscoped marker");
        }
        this.tenantId = normalized;
    }

    public String currentTenantId() {
        return tenantId;
    }
}
