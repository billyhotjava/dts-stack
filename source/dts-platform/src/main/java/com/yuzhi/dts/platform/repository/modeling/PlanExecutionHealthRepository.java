package com.yuzhi.dts.platform.repository.modeling;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model for plan execution health.
 *
 * <p>It keeps desired deployment state, platform run state and physical relation evidence
 * separate. Airflow actual state is joined by the application service, never inferred here.
 */
@Repository
public class PlanExecutionHealthRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlanExecutionHealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<BindingHealthRecord> findByPlan(
        String tenantId,
        UUID planId
    ) {
        return jdbcTemplate.query(
            """
            select b.id, b.version, b.environment, b.schedule_mode,
                   b.cron_expression, b.timezone,
                   b.effective_schedule, b.effective_timezone,
                   b.deployment_status, b.desired_scope_checksum,
                   b.desired_deployment_checksum, b.deployed_checksum,
                   b.dag_id, b.airflow_paused,
                   b.last_airflow_observed_at, b.next_run_at,
                   b.last_error_code, b.last_error_message,
                   count(e.id) as entry_count,
                   latest.group_id, latest.airflow_run_id,
                   latest.trigger_type, latest.run_status,
                   latest.run_error_code, latest.created_at,
                   latest.started_at, latest.finished_at,
                   observation.verified as relation_verified,
                   observation.relation_exists,
                   observation.error_code as relation_error_code,
                   observation.schema_name,
                   observation.identifier,
                   observation.observed_at
              from modeling_plan_execution_binding b
              left join modeling_plan_execution_binding_entry e
                on e.tenant_id = b.tenant_id
               and e.binding_id = b.id
              left join lateral (
                    select d.id as group_id, d.airflow_run_id,
                           d.trigger_type, d.status as run_status,
                           d.last_error_code as run_error_code,
                           d.created_at,
                           run_times.started_at,
                           run_times.finished_at
                      from modeling_operational_run_dispatch d
                      left join lateral (
                            select min(pr.started_date) as started_at,
                                   max(pr.finished_date) as finished_at
                              from modeling_pipeline_run pr
                             where pr.tenant_id = d.tenant_id
                               and pr.pipeline_run_group_id = d.id
                               and pr.run_purpose = 'OPERATIONAL_RUN'
                      ) run_times on true
                     where d.tenant_id = b.tenant_id
                       and d.binding_id = b.id
                     order by d.created_at desc, d.id desc
                     limit 1
              ) latest on true
              left join lateral (
                    select bool_and(o.verified) as verified,
                           bool_and(o.relation_exists) as relation_exists,
                           max(o.error_code) filter (
                               where not o.verified
                           ) as error_code,
                           string_agg(
                               o.schema_name || '.' || o.identifier,
                               ', ' order by o.model_spec_id
                           ) as relation_name,
                           max(o.observed_at) as observed_at
                      from modeling_physical_relation_observation o
                     where o.tenant_id = b.tenant_id
                       and o.execution_binding_id = b.id
                       and o.run_purpose = 'OPERATIONAL_RUN'
                       and o.pipeline_run_group_id = latest.group_id
              ) observation on true
             where b.tenant_id = ? and b.plan_id = ?
             group by
                   b.id, b.version, b.environment, b.schedule_mode,
                   b.cron_expression, b.timezone,
                   b.effective_schedule, b.effective_timezone,
                   b.deployment_status, b.desired_scope_checksum,
                   b.desired_deployment_checksum, b.deployed_checksum,
                   b.dag_id, b.airflow_paused,
                   b.last_airflow_observed_at, b.next_run_at,
                   b.last_error_code, b.last_error_message,
                   latest.group_id, latest.airflow_run_id,
                   latest.trigger_type, latest.run_status,
                   latest.run_error_code, latest.created_at,
                   latest.started_at, latest.finished_at,
                   observation.verified, observation.relation_exists,
                   observation.error_code,
                   observation.relation_name,
                   observation.observed_at
             order by b.environment, b.id
            """,
            this::map,
            required(tenantId, "tenantId"),
            planId
        );
    }

    private BindingHealthRecord map(ResultSet row, int rowNumber)
        throws SQLException {
        return new BindingHealthRecord(
            row.getObject("id", UUID.class),
            row.getInt("version"),
            row.getString("environment"),
            row.getString("schedule_mode"),
            row.getString("cron_expression"),
            row.getString("timezone"),
            row.getString("effective_schedule"),
            row.getString("effective_timezone"),
            row.getString("deployment_status"),
            row.getString("desired_scope_checksum"),
            row.getString("desired_deployment_checksum"),
            row.getString("deployed_checksum"),
            row.getString("dag_id"),
            (Boolean) row.getObject("airflow_paused"),
            instant(row.getTimestamp("last_airflow_observed_at")),
            instant(row.getTimestamp("next_run_at")),
            row.getString("last_error_code"),
            row.getString("last_error_message"),
            row.getInt("entry_count"),
            row.getObject("group_id", UUID.class),
            row.getString("airflow_run_id"),
            row.getString("trigger_type"),
            row.getString("run_status"),
            row.getString("run_error_code"),
            instant(row.getTimestamp("created_at")),
            instant(row.getTimestamp("started_at")),
            instant(row.getTimestamp("finished_at")),
            (Boolean) row.getObject("relation_verified"),
            (Boolean) row.getObject("relation_exists"),
            row.getString("relation_error_code"),
            row.getString("relation_name"),
            instant(row.getTimestamp("observed_at"))
        );
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String required(String value, String name) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }

    public record BindingHealthRecord(
        UUID id,
        int version,
        String environment,
        String scheduleMode,
        String cronExpression,
        String timezone,
        String effectiveSchedule,
        String effectiveTimezone,
        String deploymentStatus,
        String desiredScopeChecksum,
        String desiredDeploymentChecksum,
        String deployedChecksum,
        String airflowDagId,
        Boolean persistedAirflowPaused,
        Instant lastAirflowObservedAt,
        Instant persistedNextRunAt,
        String deploymentErrorCode,
        String deploymentErrorMessage,
        int entryCount,
        UUID latestRunGroupId,
        String latestAirflowRunId,
        String latestTriggerType,
        String latestRunStatus,
        String latestRunErrorCode,
        Instant latestRunCreatedAt,
        Instant latestRunStartedAt,
        Instant latestRunFinishedAt,
        Boolean latestRelationVerified,
        Boolean latestRelationExists,
        String latestRelationErrorCode,
        String latestPhysicalRelation,
        Instant latestRelationObservedAt
    ) {}
}
