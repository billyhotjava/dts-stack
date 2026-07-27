package com.yuzhi.dts.platform.service.etl;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("dbtRuntimeProfile")
public class DbtRuntimeProfileHealthIndicator
    implements HealthIndicator {

    private final DbtRuntimeProfileLeaseService leases;

    public DbtRuntimeProfileHealthIndicator(
        DbtRuntimeProfileLeaseService leases
    ) {
        this.leases = leases;
    }

    @Override
    public Health health() {
        DbtRuntimeProfileLeaseService.Readiness readiness =
            leases.readiness();
        if (readiness.ready()) {
            return Health.up().withDetail("code", readiness.code()).build();
        }
        return Health
            .down()
            .withDetail("code", readiness.code())
            .build();
    }
}
