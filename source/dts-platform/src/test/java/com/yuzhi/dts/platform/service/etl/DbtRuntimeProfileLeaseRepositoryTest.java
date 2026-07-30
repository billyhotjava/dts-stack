package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DbtRuntimeProfileLeaseRepositoryTest {

    @Test
    void expirationClaimRequiresTheObservedExpiryVersionAndCutoff() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.update(anyString(), any(Object[].class))
        )
            .thenReturn(1);
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);
        UUID leaseId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );
        Instant expectedExpiresAt = Instant.parse(
            "2026-07-30T10:05:00Z"
        );
        Instant cutoff = Instant.parse("2026-07-30T10:06:00Z");
        Instant expiredAt = Instant.parse("2026-07-30T10:06:01Z");

        boolean claimed = repository.expire(
            leaseId,
            expectedExpiresAt,
            cutoff,
            expiredAt
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(
            String.class
        );
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate).update(
            sql.capture(),
            arguments.capture()
        );
        assertThat(claimed).isTrue();
        assertThat(sql.getValue())
            .contains("set status = 'EXPIRED', released_at = ?")
            .contains("status in ('ISSUED', 'CONSUMED')")
            .contains("expires_at = ?")
            .contains("expires_at <= ?");
        assertThat(arguments.getValue())
            .containsExactly(
                Timestamp.from(expiredAt),
                leaseId,
                Timestamp.from(expectedExpiresAt),
                Timestamp.from(cutoff)
            );
    }

    @Test
    void releaseClaimReportsWhetherAnActiveLeaseTransitioned() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.update(anyString(), any(Object[].class))
        )
            .thenReturn(0);
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);
        UUID leaseId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );
        Instant releasedAt = Instant.parse("2026-07-30T10:06:00Z");

        boolean claimed = repository.release(leaseId, releasedAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(
            String.class
        );
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate).update(
            sql.capture(),
            arguments.capture()
        );
        assertThat(claimed).isFalse();
        assertThat(sql.getValue())
            .contains("set status = 'RELEASED', released_at = ?")
            .contains("status in ('ISSUED', 'CONSUMED')");
        assertThat(arguments.getValue())
            .containsExactly(Timestamp.from(releasedAt), leaseId);
    }

    @Test
    void renewalUsesDatabaseGreatestSoConcurrentRequestsCannotShortenTheLease() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.update(anyString(), any(Object[].class))
        )
            .thenReturn(1);
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);
        UUID leaseId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );
        Instant renewedAt = Instant.parse("2026-07-30T10:04:00Z");
        Instant requestedExpiresAt = Instant.parse(
            "2026-07-30T10:09:00Z"
        );

        boolean renewed = repository.renew(
            leaseId,
            renewedAt,
            requestedExpiresAt
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(
            String.class
        );
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate).update(
            sql.capture(),
            arguments.capture()
        );
        assertThat(renewed).isTrue();
        assertThat(sql.getValue())
            .contains(
                "set expires_at = greatest(expires_at, ?)"
            )
            .contains("status = 'CONSUMED'")
            .contains("expires_at > ?");
        assertThat(arguments.getValue())
            .containsExactly(
                Timestamp.from(requestedExpiresAt),
                leaseId,
                Timestamp.from(renewedAt)
            );
    }
}
