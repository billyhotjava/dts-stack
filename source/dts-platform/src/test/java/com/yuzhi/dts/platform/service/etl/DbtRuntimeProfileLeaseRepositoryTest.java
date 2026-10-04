package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseCompensationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationResult;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DbtRuntimeProfileLeaseRepositoryTest {

    @Test
    void consumptionUsesOneDatabaseClockForTheExpiryCas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            )
        )
            .thenReturn(
                List.of(
                    new LeaseMutationResult(
                        LeaseMutationOutcome.SUCCESS,
                        lease(LeaseStatus.CONSUMED)
                    )
                )
            );
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);
        UUID leaseId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

        LeaseMutationResult consumed = repository.consumeState(
            leaseId
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(
            String.class
        );
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate).query(
            sql.capture(),
            any(RowMapper.class),
            arguments.capture()
        );
        assertThat(consumed.outcome())
            .isEqualTo(LeaseMutationOutcome.SUCCESS);
        assertThat(sql.getValue())
            .contains(
                "with lease_candidate as materialized"
            )
            .contains("for update")
            .contains(
                "lease_clock as materialized"
            )
            .contains("clock_timestamp() as now_at")
            .contains("else 'CONSUMED'")
            .contains("then lease_clock.now_at")
            .contains("as outcome");
        assertThat(arguments.getValue()).containsExactly(leaseId);
    }

    @Test
    void expirationClaimLocksTheVersionBeforeReadingDatabaseClock() {
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

        boolean claimed = repository.expire(
            leaseId,
            expectedExpiresAt
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
            .contains("lease_candidate as materialized")
            .contains("for update")
            .contains("lease_clock as materialized")
            .contains("clock_timestamp() as now_at")
            .contains("released_at = lease_clock.now_at")
            .contains("status in ('ISSUED', 'CONSUMED')")
            .contains("lease.expires_at = lease_clock.expires_at")
            .contains("lease.expires_at <= lease_clock.now_at");
        assertThat(arguments.getValue())
            .containsExactly(
                leaseId,
                Timestamp.from(expectedExpiresAt)
            );
    }

    @Test
    void expiredCandidateDiscoveryUsesOnlyTheDatabaseClock() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            )
        )
            .thenReturn(List.of());
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);

        assertThat(repository.findExpired(100)).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(
            String.class
        );
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate).query(
            sql.capture(),
            any(RowMapper.class),
            arguments.capture()
        );
        assertThat(sql.getValue())
            .contains("expires_at <= clock_timestamp()")
            .contains("limit ?");
        assertThat(arguments.getValue()).containsExactly(100);
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
            jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            )
        )
            .thenReturn(
                List.of(
                    new LeaseMutationResult(
                        LeaseMutationOutcome.SUCCESS,
                        lease(LeaseStatus.CONSUMED)
                    )
                )
            );
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);
        UUID leaseId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );
        Duration ttl = Duration.ofMinutes(5);

        LeaseMutationResult renewed = repository.renewState(
            leaseId,
            ttl
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(
            String.class
        );
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbcTemplate).query(
            sql.capture(),
            any(RowMapper.class),
            arguments.capture()
        );
        assertThat(renewed.outcome())
            .isEqualTo(LeaseMutationOutcome.SUCCESS);
        assertThat(sql.getValue())
            .contains(
                "with lease_candidate as materialized"
            )
            .contains("for update")
            .contains(
                "lease_clock as materialized"
            )
            .contains("clock_timestamp() as now_at")
            .contains(
                "then greatest("
            )
            .contains("lease_clock.now_at + (? * interval '1 millisecond')")
            .contains("lease_clock.status = 'CONSUMED'")
            .contains("as outcome");
        assertThat(arguments.getValue())
            .containsExactly(
                leaseId,
                ttl.toMillis()
            );
    }

    @Test
    void activeInspectionLocksBeforeReadingTheDatabaseClock() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            )
        )
            .thenReturn(
                List.of(
                    new LeaseMutationResult(
                        LeaseMutationOutcome.SUCCESS,
                        lease(LeaseStatus.ISSUED)
                    )
                )
            );
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);
        UUID leaseId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

        LeaseMutationResult inspected = repository.viewActiveState(
            leaseId
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(
            String.class
        );
        verify(jdbcTemplate).query(
            sql.capture(),
            any(RowMapper.class),
            any(Object[].class)
        );
        assertThat(inspected.outcome())
            .isEqualTo(LeaseMutationOutcome.SUCCESS);
        assertThat(sql.getValue())
            .contains("lease_candidate as materialized")
            .contains("for update")
            .contains("lease_clock as materialized")
            .contains("clock_timestamp() as now_at")
            .contains("then 'EXPIRED'")
            .contains("then 'SUCCESS'");
    }

    @Test
    void missingProfileCompensationRetriesTheLockedCurrentVersion() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.update(anyString(), any(Object[].class))
        )
            .thenReturn(0, 1);
        when(
            jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
            )
        )
            .thenReturn(List.of(lease(LeaseStatus.CONSUMED)));
        DbtRuntimeProfileLeaseRepository repository =
            new DbtRuntimeProfileLeaseRepository(jdbcTemplate);
        LeaseRecord expected = lease(LeaseStatus.CONSUMED);

        LeaseCompensationOutcome outcome =
            repository.compensateMissingProfile(
                expected.id(),
                expected.status(),
                expected.expiresAt()
            );

        assertThat(outcome)
            .isEqualTo(LeaseCompensationOutcome.COMPENSATED);
        verify(jdbcTemplate, times(2))
            .update(anyString(), any(Object[].class));
    }

    private static LeaseRecord lease(LeaseStatus status) {
        return new LeaseRecord(
            UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
            ),
            "tenant-a",
            UUID.fromString(
                "20000000-0000-0000-0000-000000000001"
            ),
            "manual__lease-unit",
            "DEV",
            "postgres-primary",
            "dev",
            "credential-v3",
            status,
            Instant.parse("2026-07-30T10:00:00Z"),
            Instant.parse("2026-07-30T10:05:00Z"),
            status == LeaseStatus.CONSUMED
                ? Instant.parse("2026-07-30T10:01:00Z")
                : null,
            null
        );
    }
}
