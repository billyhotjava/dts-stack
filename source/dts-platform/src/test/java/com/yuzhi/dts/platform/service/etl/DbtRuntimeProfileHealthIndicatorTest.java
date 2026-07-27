package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class DbtRuntimeProfileHealthIndicatorTest {

    @Test
    void exposesOnlyStableReadinessCode() {
        DbtRuntimeProfileLeaseService leases = mock(
            DbtRuntimeProfileLeaseService.class
        );
        when(leases.readiness()).thenReturn(
            new DbtRuntimeProfileLeaseService.Readiness(
                false,
                "DBT_RUNTIME_PROFILE_ROOT_NOT_TMPFS"
            )
        );

        var health = new DbtRuntimeProfileHealthIndicator(
            leases
        ).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
            .containsOnlyKeys("code")
            .containsEntry(
                "code",
                "DBT_RUNTIME_PROFILE_ROOT_NOT_TMPFS"
            );
    }
}
