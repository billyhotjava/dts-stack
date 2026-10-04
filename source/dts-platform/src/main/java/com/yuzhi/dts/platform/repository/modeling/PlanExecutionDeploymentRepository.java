package com.yuzhi.dts.platform.repository.modeling;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Operations owns deployment selection and activation; publishing never writes this state. */
@Repository
public class PlanExecutionDeploymentRepository {
    private final JdbcTemplate jdbc;
    public PlanExecutionDeploymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<UUID> publishedTargets(String tenant, UUID plan) {
        return jdbc.query("""
            select distinct on (environment, execution_target_key) id
              from modeling_model_release_candidate
             where tenant_id = ? and plan_id = ? and status = 'PUBLISHED'
             order by environment, execution_target_key, last_modified_date desc, id desc
            """, (row, n) -> row.getObject("id", UUID.class), tenant, plan);
    }

    public List<Binding> bindings(String tenant, UUID plan, boolean lock) {
        return jdbc.query("""
            select b.id, b.version, b.environment, b.execution_target_key, b.dag_id, b.deployment_status,
                   exists(select 1 from modeling_pipeline_run r where r.tenant_id=b.tenant_id
                     and r.execution_binding_id=b.id and r.run_purpose='OPERATIONAL_RUN'
                     and r.operational_active_claim_key is not null) as running
              from modeling_plan_execution_binding b where tenant_id=? and plan_id=? order by b.id
            """ + (lock ? " for update" : ""), (row,n) -> new Binding(row.getObject("id", UUID.class),
                row.getInt("version"), row.getString("environment"), row.getString("execution_target_key"),
                row.getString("dag_id"), row.getString("deployment_status"), row.getBoolean("running")), tenant, plan);
    }

    public boolean enable(String tenant, Binding binding, String actor) {
        return jdbc.update("""
            update modeling_plan_execution_binding set activation_required=false, airflow_paused=false,
                   version=version+1, last_modified_by=?, last_modified_date=?
             where tenant_id=? and id=? and version=? and deployment_status='ACTIVE'
               and deployed_checksum=desired_deployment_checksum
            """, actor, Timestamp.from(Instant.now()), tenant, binding.id(), binding.version()) == 1;
    }

    public record Binding(UUID id, int version, String environment, String target, String dagId, String status, boolean running) {}
}
