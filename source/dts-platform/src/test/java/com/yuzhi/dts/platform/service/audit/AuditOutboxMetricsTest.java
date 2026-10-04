package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuditOutboxMetricsTest {

    @Test
    void exposesPendingAndDeadDeliveryDepth() {
        PlatformAuditOutboxRepository repository = mock(PlatformAuditOutboxRepository.class);
        when(repository.countPending()).thenReturn(7L);
        when(repository.countDead()).thenReturn(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new AuditOutboxMetrics(registry, repository);

        assertThat(registry.get("dts.platform.audit.outbox.pending").gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("dts.platform.audit.outbox.dead").gauge().value()).isEqualTo(2.0);
    }
}
