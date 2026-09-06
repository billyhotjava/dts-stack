package com.yuzhi.dts.platform.repository.modeling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC owner for plan execution deployment state; Airflow remains the scheduling truth. */
@Repository
public class PlanExecutionBindingRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlanExecutionBindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<BindingRecord> findDeployable(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query(
            """
            select b.id, b.tenant_id, b.plan_id, b.environment,
                   b.execution_target_key, b.version, b.schedule_mode,
                   b.cron_expression, b.timezone,
                   b.desired_scope_checksum,
                   b.desired_deployment_checksum, b.dag_id,
                   b.deployment_status, b.deployed_checksum,
                   count(e.id) as entry_count
              from modeling_plan_execution_binding b
              left join modeling_plan_execution_binding_entry e
                on e.binding_id = b.id and e.tenant_id = b.tenant_id
             where b.deployment_status in (
                       'DEPLOYING', 'UNKNOWN', 'FAILED'
                   )
             group by
                   b.id, b.tenant_id, b.plan_id, b.environment,
                   b.execution_target_key, b.version, b.schedule_mode,
                   b.cron_expression, b.timezone,
                   b.desired_scope_checksum,
                   b.desired_deployment_checksum, b.dag_id,
                   b.deployment_status, b.deployed_checksum,
                   b.last_modified_date
             order by b.last_modified_date, b.id
             limit ?
            """,
            this::map,
            safeLimit
        );
    }

    @Transactional
    public boolean markActive(
        BindingRecord expected,
        String deployedChecksum,
        String effectiveSchedule,
        String effectiveTimezone,
        boolean airflowPaused,
        Instant observedAt
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_plan_execution_binding
                   set deployment_status = 'ACTIVE',
                       deployed_checksum = ?,
                       effective_schedule = ?,
                       effective_timezone = ?,
                       airflow_paused = ?,
                       last_airflow_observed_at = ?,
                       last_deployment_at = ?,
                       last_error_code = null,
                       last_error_message = null,
                       last_modified_date = ?
                 where id = ? and tenant_id = ? and version = ?
                   and desired_deployment_checksum = ?
                   and deployment_status in (
                       'DEPLOYING', 'UNKNOWN', 'FAILED'
                   )
                """,
                deployedChecksum,
                effectiveSchedule,
                effectiveTimezone,
                airflowPaused,
                Timestamp.from(observedAt),
                Timestamp.from(observedAt),
                Timestamp.from(observedAt),
                expected.id(),
                expected.tenantId(),
                expected.version(),
                expected.desiredDeploymentChecksum()
            ) ==
            1
        );
    }

    @Transactional
    public boolean markUnknown(
        BindingRecord expected,
        String errorCode,
        Instant observedAt
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_plan_execution_binding
                   set deployment_status = 'UNKNOWN',
                       airflow_paused = null,
                       last_airflow_observed_at = ?,
                       last_error_code = ?,
                       last_error_message = null,
                       last_modified_date = ?
                 where id = ? and tenant_id = ? and version = ?
                   and desired_deployment_checksum = ?
                   and deployment_status in (
                       'DEPLOYING', 'UNKNOWN', 'FAILED'
                   )
                """,
                Timestamp.from(observedAt),
                required(errorCode, "errorCode"),
                Timestamp.from(observedAt),
                expected.id(),
                expected.tenantId(),
                expected.version(),
                expected.desiredDeploymentChecksum()
            ) ==
            1
        );
    }

    @Transactional
    public Optional<RepairResult> requestRedeployment(
        String tenantId,
        UUID planId,
        UUID bindingId,
        int expectedVersion,
        String actorId,
        Instant requestedAt
    ) {
        String tenant = required(tenantId, "tenantId");
        List<RepairState> states = jdbcTemplate.query(
            """
            select environment, execution_target_key, schedule_mode,
                   desired_scope_checksum
              from modeling_plan_execution_binding
             where tenant_id = ? and plan_id = ? and id = ?
               and version = ?
               and deployment_status in (
                   'ACTIVE', 'STALE', 'FAILED', 'UNKNOWN'
               )
               and not exists (
                   select 1
                     from modeling_pipeline_run active
                    where active.tenant_id = modeling_plan_execution_binding.tenant_id
                      and active.execution_binding_id = modeling_plan_execution_binding.id
                      and active.run_purpose = 'OPERATIONAL_RUN'
                      and active.operational_active_claim_key is not null
               )
             for update
            """,
            (row, rowNumber) -> new RepairState(
                row.getString("environment"),
                row.getString("execution_target_key"),
                row.getString("schedule_mode"),
                row.getString("desired_scope_checksum")
            ),
            tenant,
            planId,
            bindingId,
            expectedVersion
        );
        if (states.isEmpty()) return Optional.empty();
        RepairState state = states.getFirst();
        if (!"MANUAL_ONLY".equals(state.scheduleMode())) {
            return redeployUnchanged(
                tenant,
                planId,
                bindingId,
                expectedVersion,
                actorId,
                requestedAt
            )
                ? Optional.of(new RepairResult(expectedVersion))
                : Optional.empty();
        }
        if (
            state.environment() == null ||
            state.executionTargetKey() == null ||
            state.desiredScopeChecksum() == null ||
            !state.desiredScopeChecksum().matches("^[0-9a-f]{64}$")
        ) {
            return Optional.empty();
        }
        String dagId = operationalDagId(bindingId);
        String deploymentChecksum = deploymentChecksum(
            tenant,
            planId,
            state.environment(),
            state.executionTargetKey(),
            dagId,
            state.desiredScopeChecksum()
        );
        int repaired = jdbcTemplate.update(
            """
            update modeling_plan_execution_binding
               set version = version + 1,
                   dag_id = ?,
                   desired_deployment_checksum = ?,
                   deployment_status = 'DEPLOYING',
                   last_error_code = null,
                   last_error_message = null,
                   last_modified_by = ?,
                   last_modified_date = ?
             where tenant_id = ? and plan_id = ? and id = ?
               and version = ? and schedule_mode = 'MANUAL_ONLY'
               and deployment_status in (
                   'ACTIVE', 'STALE', 'FAILED', 'UNKNOWN'
               )
               and not exists (
                   select 1
                     from modeling_pipeline_run active
                    where active.tenant_id = modeling_plan_execution_binding.tenant_id
                      and active.execution_binding_id = modeling_plan_execution_binding.id
                      and active.run_purpose = 'OPERATIONAL_RUN'
                      and active.operational_active_claim_key is not null
               )
            """,
            dagId,
            deploymentChecksum,
            required(actorId, "actorId"),
            Timestamp.from(requestedAt),
            tenant,
            planId,
            bindingId,
            expectedVersion
        );
        return repaired == 1
            ? Optional.of(new RepairResult(expectedVersion + 1))
            : Optional.empty();
    }

    private boolean redeployUnchanged(
        String tenantId,
        UUID planId,
        UUID bindingId,
        int expectedVersion,
        String actorId,
        Instant requestedAt
    ) {
        return (
            jdbcTemplate.update(
                """
                update modeling_plan_execution_binding
                   set deployment_status = 'DEPLOYING',
                       last_error_code = null,
                       last_error_message = null,
                       last_modified_by = ?,
                       last_modified_date = ?
                 where tenant_id = ? and plan_id = ? and id = ?
                   and version = ?
                   and deployment_status in (
                       'ACTIVE', 'STALE', 'FAILED', 'UNKNOWN'
                   )
                   and not exists (
                       select 1
                         from modeling_pipeline_run active
                        where active.tenant_id = modeling_plan_execution_binding.tenant_id
                          and active.execution_binding_id = modeling_plan_execution_binding.id
                          and active.run_purpose = 'OPERATIONAL_RUN'
                          and active.operational_active_claim_key is not null
                   )
                """,
                required(actorId, "actorId"),
                Timestamp.from(requestedAt),
                required(tenantId, "tenantId"),
                planId,
                bindingId,
                expectedVersion
            ) == 1
        );
    }

    private static String operationalDagId(UUID bindingId) {
        return "dts_plan_" + bindingId.toString().replace("-", "");
    }

    private static String deploymentChecksum(
        String tenantId,
        UUID planId,
        String environment,
        String executionTargetKey,
        String dagId,
        String scopeChecksum
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(
                tenantId,
                planId.toString(),
                environment,
                executionTargetKey,
                "MANUAL_ONLY",
                dagId,
                scopeChecksum
            )) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private BindingRecord map(ResultSet row, int rowNumber)
        throws SQLException {
        return new BindingRecord(
            row.getObject("id", UUID.class),
            row.getString("tenant_id"),
            row.getObject("plan_id", UUID.class),
            row.getString("environment"),
            row.getString("execution_target_key"),
            row.getInt("version"),
            row.getString("schedule_mode"),
            row.getString("cron_expression"),
            row.getString("timezone"),
            row.getString("desired_scope_checksum"),
            row.getString("desired_deployment_checksum"),
            row.getString("dag_id"),
            row.getString("deployment_status"),
            row.getString("deployed_checksum"),
            row.getInt("entry_count")
        );
    }

    private static String required(String value, String name) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }

    public record BindingRecord(
        UUID id,
        String tenantId,
        UUID planId,
        String environment,
        String executionTargetKey,
        int version,
        String scheduleMode,
        String cronExpression,
        String timezone,
        String desiredScopeChecksum,
        String desiredDeploymentChecksum,
        String dagId,
        String deploymentStatus,
        String deployedChecksum,
        int entryCount
    ) {}

    public record RepairResult(int bindingVersion) {}

    private record RepairState(
        String environment,
        String executionTargetKey,
        String scheduleMode,
        String desiredScopeChecksum
    ) {}
}
