package com.yuzhi.dts.common.management.health;

import com.yuzhi.dts.common.repository.AuditEventRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AuditDbHealthIndicator implements HealthIndicator {

    private final AuditEventRepository repository;

    public AuditDbHealthIndicator(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        try {
            repository.count();
            return Health.up().withDetail("auditDb", "reachable").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("auditDb", "unreachable").build();
        }
    }
}

