package com.yuzhi.dts.platform.repository.modeling;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DbtRuntimeProfileLeaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public DbtRuntimeProfileLeaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void issue(LeaseRecord lease) {
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_dbt_runtime_profile_lease (
                id, tenant_id, pipeline_run_id, dag_run_id, environment,
                execution_target_key, target_name, credential_version_ref,
                status, issued_at, expires_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED', ?, ?)
            """,
            lease.id(),
            lease.tenantId(),
            lease.pipelineRunId(),
            lease.dagRunId(),
            lease.environment(),
            lease.executionTargetKey(),
            lease.targetName(),
            lease.credentialVersionRef(),
            Timestamp.from(lease.issuedAt()),
            Timestamp.from(lease.expiresAt())
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                "Runtime profile lease metadata was not persisted"
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<LeaseRecord> find(UUID leaseId) {
        return jdbcTemplate
            .query(
                """
                select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                       execution_target_key, target_name, credential_version_ref,
                       status, issued_at, expires_at, consumed_at, released_at
                  from modeling_dbt_runtime_profile_lease
                 where id = ?
                """,
                (row, rowNumber) ->
                    new LeaseRecord(
                        row.getObject("id", UUID.class),
                        row.getString("tenant_id"),
                        row.getObject("pipeline_run_id", UUID.class),
                        row.getString("dag_run_id"),
                        row.getString("environment"),
                        row.getString("execution_target_key"),
                        row.getString("target_name"),
                        row.getString("credential_version_ref"),
                        LeaseStatus.valueOf(row.getString("status")),
                        row.getTimestamp("issued_at").toInstant(),
                        row.getTimestamp("expires_at").toInstant(),
                        instant(row.getTimestamp("consumed_at")),
                        instant(row.getTimestamp("released_at"))
                    ),
                leaseId
            )
            .stream()
            .findFirst();
    }

    @Transactional
    public boolean consume(UUID leaseId, Instant consumedAt) {
        return (
            jdbcTemplate.update(
                """
                update modeling_dbt_runtime_profile_lease
                   set status = 'CONSUMED', consumed_at = ?
                 where id = ? and status = 'ISSUED' and expires_at > ?
                """,
                Timestamp.from(consumedAt),
                leaseId,
                Timestamp.from(consumedAt)
            ) ==
            1
        );
    }

    @Transactional
    public void release(UUID leaseId, Instant releasedAt) {
        jdbcTemplate.update(
            """
            update modeling_dbt_runtime_profile_lease
               set status = 'RELEASED', released_at = ?
             where id = ? and status in ('ISSUED', 'CONSUMED')
            """,
            Timestamp.from(releasedAt),
            leaseId
        );
    }

    @Transactional
    public void expire(UUID leaseId, Instant expiredAt) {
        jdbcTemplate.update(
            """
            update modeling_dbt_runtime_profile_lease
               set status = 'EXPIRED', released_at = ?
             where id = ? and status in ('ISSUED', 'CONSUMED')
            """,
            Timestamp.from(expiredAt),
            leaseId
        );
    }

    @Transactional(readOnly = true)
    public List<LeaseRecord> findExpired(Instant now, int limit) {
        return jdbcTemplate.query(
            """
            select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                   execution_target_key, target_name, credential_version_ref,
                   status, issued_at, expires_at, consumed_at, released_at
              from modeling_dbt_runtime_profile_lease
             where status in ('ISSUED', 'CONSUMED') and expires_at <= ?
             order by expires_at, id
             limit ?
            """,
            (row, rowNumber) ->
                new LeaseRecord(
                    row.getObject("id", UUID.class),
                    row.getString("tenant_id"),
                    row.getObject("pipeline_run_id", UUID.class),
                    row.getString("dag_run_id"),
                    row.getString("environment"),
                    row.getString("execution_target_key"),
                    row.getString("target_name"),
                    row.getString("credential_version_ref"),
                    LeaseStatus.valueOf(row.getString("status")),
                    row.getTimestamp("issued_at").toInstant(),
                    row.getTimestamp("expires_at").toInstant(),
                    instant(row.getTimestamp("consumed_at")),
                    instant(row.getTimestamp("released_at"))
                ),
            Timestamp.from(now),
            limit
        );
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID leaseId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_dbt_runtime_profile_lease
                 where id = ?
            )
            """,
            Boolean.class,
            leaseId
        );
        return Boolean.TRUE.equals(exists);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public enum LeaseStatus {
        ISSUED,
        CONSUMED,
        RELEASED,
        EXPIRED,
    }

    public record LeaseRecord(
        UUID id,
        String tenantId,
        UUID pipelineRunId,
        String dagRunId,
        String environment,
        String executionTargetKey,
        String targetName,
        String credentialVersionRef,
        LeaseStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant releasedAt
    ) {}
}
