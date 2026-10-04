package com.yuzhi.dts.platform.repository.modeling;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
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
    public LeaseMutationResult viewActiveState(UUID leaseId) {
        return mutation(
            """
            with lease_candidate as materialized (
                select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                       execution_target_key, target_name, credential_version_ref,
                       status, issued_at, expires_at, consumed_at, released_at
                  from modeling_dbt_runtime_profile_lease
                 where id = ?
                   for update
            ),
            lease_clock as materialized (
                select candidate.*, clock_timestamp() as now_at
                  from lease_candidate candidate
            )
            select case
                       when lease_clock.status = 'RELEASED'
                           then 'NOT_FOUND'
                       when lease_clock.status = 'EXPIRED'
                            or (
                                lease_clock.status in ('ISSUED', 'CONSUMED')
                                and lease_clock.expires_at <= lease_clock.now_at
                            )
                           then 'EXPIRED'
                       when lease_clock.status in ('ISSUED', 'CONSUMED')
                            and lease_clock.expires_at > lease_clock.now_at
                           then 'SUCCESS'
                       else 'NOT_FOUND'
                   end as outcome,
                   lease_clock.id, lease_clock.tenant_id,
                   lease_clock.pipeline_run_id, lease_clock.dag_run_id,
                   lease_clock.environment, lease_clock.execution_target_key,
                   lease_clock.target_name, lease_clock.credential_version_ref,
                   lease_clock.status, lease_clock.issued_at,
                   lease_clock.expires_at, lease_clock.consumed_at,
                   lease_clock.released_at
              from lease_clock
            """,
            leaseId
        );
    }

    @Transactional
    public LeaseMutationResult consumeState(UUID leaseId) {
        return mutation(
            """
            with lease_candidate as materialized (
                select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                       execution_target_key, target_name, credential_version_ref,
                       status, issued_at, expires_at, consumed_at, released_at
                  from modeling_dbt_runtime_profile_lease
                 where id = ?
                   for update
            ),
            lease_clock as materialized (
                select candidate.*, clock_timestamp() as now_at
                  from lease_candidate candidate
            ),
            transitioned as (
                update modeling_dbt_runtime_profile_lease as lease
                   set status = case
                           when lease_clock.expires_at <= lease_clock.now_at
                               then 'EXPIRED'
                           else 'CONSUMED'
                       end,
                       consumed_at = case
                           when lease_clock.expires_at > lease_clock.now_at
                               then lease_clock.now_at
                           else lease.consumed_at
                       end,
                       released_at = case
                           when lease_clock.expires_at <= lease_clock.now_at
                               then lease_clock.now_at
                           else lease.released_at
                       end
                  from lease_clock
                 where lease.id = lease_clock.id
                   and lease_clock.status in ('ISSUED', 'CONSUMED')
                   and (
                       lease_clock.status = 'ISSUED'
                       or lease_clock.expires_at <= lease_clock.now_at
                   )
                returning lease.id, lease.tenant_id, lease.pipeline_run_id,
                          lease.dag_run_id, lease.environment,
                          lease.execution_target_key, lease.target_name,
                          lease.credential_version_ref, lease.status,
                          lease.issued_at, lease.expires_at, lease.consumed_at,
                          lease.released_at
            )
            select case
                       when transitioned.status = 'EXPIRED' then 'EXPIRED'
                       else 'SUCCESS'
                   end as outcome,
                   transitioned.*
              from transitioned
            union all
            select case
                       when lease_clock.status = 'CONSUMED'
                            and lease_clock.expires_at > lease_clock.now_at
                           then 'SUCCESS'
                       when lease_clock.status = 'EXPIRED'
                            or (
                                lease_clock.status in ('ISSUED', 'CONSUMED')
                                and lease_clock.expires_at <= lease_clock.now_at
                            )
                           then 'EXPIRED'
                       else 'NOT_FOUND'
                   end as outcome,
                   lease_clock.id, lease_clock.tenant_id,
                   lease_clock.pipeline_run_id, lease_clock.dag_run_id,
                   lease_clock.environment, lease_clock.execution_target_key,
                   lease_clock.target_name, lease_clock.credential_version_ref,
                   lease_clock.status, lease_clock.issued_at,
                   lease_clock.expires_at, lease_clock.consumed_at,
                   lease_clock.released_at
              from lease_clock
             where not exists (select 1 from transitioned)
            """,
            leaseId
        );
    }

    @Transactional
    public boolean consume(UUID leaseId) {
        return consumeState(leaseId).outcome() == LeaseMutationOutcome.SUCCESS;
    }

    /**
     * Compatibility seam for older integration fixtures. The caller timestamp is
     * intentionally ignored so lease admission always uses the database clock.
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public boolean consume(UUID leaseId, Instant ignoredConsumedAt) {
        return consume(leaseId);
    }

    @Transactional
    public LeaseMutationResult renewState(UUID leaseId, Duration ttl) {
        return mutation(
            """
            with lease_candidate as materialized (
                select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                       execution_target_key, target_name, credential_version_ref,
                       status, issued_at, expires_at, consumed_at, released_at
                  from modeling_dbt_runtime_profile_lease
                 where id = ?
                   for update
            ),
            lease_clock as materialized (
                select candidate.*, clock_timestamp() as now_at
                  from lease_candidate candidate
            ),
            transitioned as (
                update modeling_dbt_runtime_profile_lease as lease
                   set status = case
                           when lease_clock.expires_at <= lease_clock.now_at
                               then 'EXPIRED'
                           else lease.status
                       end,
                       expires_at = case
                           when lease_clock.status = 'CONSUMED'
                                and lease_clock.expires_at > lease_clock.now_at
                               then greatest(
                                   lease.expires_at,
                                   lease_clock.now_at + (? * interval '1 millisecond')
                               )
                           else lease.expires_at
                       end,
                       released_at = case
                           when lease_clock.expires_at <= lease_clock.now_at
                               then lease_clock.now_at
                           else lease.released_at
                       end
                  from lease_clock
                 where lease.id = lease_clock.id
                   and lease_clock.status in ('ISSUED', 'CONSUMED')
                   and (
                       lease_clock.status = 'CONSUMED'
                       or lease_clock.expires_at <= lease_clock.now_at
                   )
                returning lease.id, lease.tenant_id, lease.pipeline_run_id,
                          lease.dag_run_id, lease.environment,
                          lease.execution_target_key, lease.target_name,
                          lease.credential_version_ref, lease.status,
                          lease.issued_at, lease.expires_at, lease.consumed_at,
                          lease.released_at
            )
            select case
                       when transitioned.status = 'EXPIRED' then 'EXPIRED'
                       else 'SUCCESS'
                   end as outcome,
                   transitioned.*
              from transitioned
            union all
            select case
                       when lease_clock.status = 'ISSUED'
                            and lease_clock.expires_at > lease_clock.now_at
                           then 'NOT_CONSUMED'
                       when lease_clock.status = 'EXPIRED'
                            or (
                                lease_clock.status in ('ISSUED', 'CONSUMED')
                                and lease_clock.expires_at <= lease_clock.now_at
                            )
                           then 'EXPIRED'
                       else 'NOT_FOUND'
                   end as outcome,
                   lease_clock.id, lease_clock.tenant_id,
                   lease_clock.pipeline_run_id, lease_clock.dag_run_id,
                   lease_clock.environment, lease_clock.execution_target_key,
                   lease_clock.target_name, lease_clock.credential_version_ref,
                   lease_clock.status, lease_clock.issued_at,
                   lease_clock.expires_at, lease_clock.consumed_at,
                   lease_clock.released_at
              from lease_clock
             where not exists (select 1 from transitioned)
            """,
            leaseId,
            ttl.toMillis()
        );
    }

    @Transactional
    public boolean renew(UUID leaseId, Duration ttl) {
        return (
            renewState(leaseId, ttl).outcome() ==
            LeaseMutationOutcome.SUCCESS
        );
    }

    @Transactional
    public LeaseCompensationOutcome compensateMissingProfile(
        UUID leaseId,
        LeaseStatus expectedStatus,
        Instant expectedExpiresAt
    ) {
        if (
            expectedStatus != LeaseStatus.ISSUED &&
            expectedStatus != LeaseStatus.CONSUMED
        ) {
            return LeaseCompensationOutcome.CONFLICT;
        }
        if (
            expireMissingProfileVersion(
                leaseId,
                expectedStatus,
                expectedExpiresAt
            )
        ) {
            return LeaseCompensationOutcome.COMPENSATED;
        }
        LeaseRecord current = findForUpdate(leaseId).orElse(null);
        if (current == null) {
            return LeaseCompensationOutcome.NOT_FOUND;
        }
        if (
            current.status() == LeaseStatus.EXPIRED ||
            current.status() == LeaseStatus.RELEASED
        ) {
            return LeaseCompensationOutcome.ALREADY_TERMINAL;
        }
        return expireMissingProfileVersion(
                leaseId,
                current.status(),
                current.expiresAt()
            )
            ? LeaseCompensationOutcome.COMPENSATED
            : LeaseCompensationOutcome.CONFLICT;
    }

    @Transactional
    public boolean release(UUID leaseId, Instant releasedAt) {
        return (
            jdbcTemplate.update(
                """
                update modeling_dbt_runtime_profile_lease
                   set status = 'RELEASED', released_at = ?
                 where id = ? and status in ('ISSUED', 'CONSUMED')
                """,
                Timestamp.from(releasedAt),
                leaseId
            ) ==
            1
        );
    }

    @Transactional
    public boolean expire(
        UUID leaseId,
        Instant expectedExpiresAt
    ) {
        return (
            jdbcTemplate.update(
                """
                with lease_candidate as materialized (
                    select id, expires_at
                      from modeling_dbt_runtime_profile_lease
                     where id = ?
                       and status in ('ISSUED', 'CONSUMED')
                       and expires_at = ?
                       for update
                ),
                lease_clock as materialized (
                    select id, expires_at, clock_timestamp() as now_at
                      from lease_candidate
                )
                update modeling_dbt_runtime_profile_lease as lease
                   set status = 'EXPIRED',
                       released_at = lease_clock.now_at
                  from lease_clock
                 where lease.id = lease_clock.id
                   and lease.expires_at = lease_clock.expires_at
                   and lease.expires_at <= lease_clock.now_at
                """,
                leaseId,
                Timestamp.from(expectedExpiresAt)
            ) ==
            1
        );
    }

    @Transactional(readOnly = true)
    public List<LeaseRecord> findExpired(int limit) {
        return jdbcTemplate.query(
            """
            select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                   execution_target_key, target_name, credential_version_ref,
                   status, issued_at, expires_at, consumed_at, released_at
              from modeling_dbt_runtime_profile_lease
             where status in ('ISSUED', 'CONSUMED')
               and expires_at <= clock_timestamp()
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
            limit
        );
    }

    @Transactional(readOnly = true)
    public List<LeaseRecord> findAllByIds(List<UUID> leaseIds) {
        List<UUID> ids = List.copyOf(leaseIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > 100) {
            throw new IllegalArgumentException(
                "Runtime profile lease batch exceeds 100 ids"
            );
        }
        String placeholders = String.join(
            ", ",
            Collections.nCopies(ids.size(), "?")
        );
        return jdbcTemplate.query(
            """
            select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                   execution_target_key, target_name, credential_version_ref,
                   status, issued_at, expires_at, consumed_at, released_at
              from modeling_dbt_runtime_profile_lease
             where id in (%s)
            """.formatted(placeholders),
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
            ids.toArray()
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

    private Optional<LeaseRecord> findForUpdate(UUID leaseId) {
        return jdbcTemplate
            .query(
                """
                select id, tenant_id, pipeline_run_id, dag_run_id, environment,
                       execution_target_key, target_name, credential_version_ref,
                       status, issued_at, expires_at, consumed_at, released_at
                  from modeling_dbt_runtime_profile_lease
                 where id = ?
                   for update
                """,
                (row, rowNumber) -> lease(row),
                leaseId
            )
            .stream()
            .findFirst();
    }

    private boolean expireMissingProfileVersion(
        UUID leaseId,
        LeaseStatus expectedStatus,
        Instant expectedExpiresAt
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_dbt_runtime_profile_lease
                   set status = 'EXPIRED',
                       released_at = clock_timestamp()
                 where id = ?
                   and status = ?
                   and status in ('ISSUED', 'CONSUMED')
                   and expires_at = ?
                """,
                leaseId,
                expectedStatus.name(),
                Timestamp.from(expectedExpiresAt)
            ) ==
            1
        );
    }

    private LeaseMutationResult mutation(String sql, Object... arguments) {
        return jdbcTemplate
            .query(
                sql,
                (row, rowNumber) ->
                    new LeaseMutationResult(
                        LeaseMutationOutcome.valueOf(
                            row.getString("outcome")
                        ),
                        lease(row)
                    ),
                arguments
            )
            .stream()
            .findFirst()
            .orElseGet(LeaseMutationResult::notFound);
    }

    private static LeaseRecord lease(ResultSet row) throws SQLException {
        return new LeaseRecord(
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
        );
    }

    public enum LeaseMutationOutcome {
        SUCCESS,
        EXPIRED,
        NOT_FOUND,
        NOT_CONSUMED,
    }

    public enum LeaseCompensationOutcome {
        COMPENSATED,
        ALREADY_TERMINAL,
        NOT_FOUND,
        CONFLICT,
    }

    public record LeaseMutationResult(
        LeaseMutationOutcome outcome,
        LeaseRecord lease
    ) {
        private static LeaseMutationResult notFound() {
            return new LeaseMutationResult(
                LeaseMutationOutcome.NOT_FOUND,
                null
            );
        }
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
