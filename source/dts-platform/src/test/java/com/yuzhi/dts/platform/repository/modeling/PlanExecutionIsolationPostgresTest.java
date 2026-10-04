package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real SQL regression: editing or withdrawing publication must not rewrite the deployed model snapshot. */
@Testcontainers
class PlanExecutionIsolationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    @Test void explicitDeploymentLocksPublishedRevisionAndActivationBeforeRunningAnOlderRevision() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        jdbc.execute("""
            create table modeling_model_spec (id uuid primary key, tenant_id text, plan_id uuid, contract_version int,
                status text, revision int, current_checksum text);
            create table modeling_model_lifecycle_event (id uuid primary key, tenant_id text, model_spec_id uuid,
                model_revision int, model_checksum text, event_type text, status text, details_json jsonb, created_date timestamptz);
            create table modeling_model_release_candidate (id uuid primary key, tenant_id text, plan_id uuid, environment text,
                execution_target_key text, status text, last_modified_date timestamptz);
            create table modeling_plan_execution_binding (id uuid primary key, tenant_id text, plan_id uuid,
                environment text, execution_target_key text, version int, schedule_mode text, cron_expression text, timezone text,
                desired_scope_checksum text, desired_deployment_checksum text, deployed_checksum text, dag_id text,
                deployment_status text, activation_required boolean, airflow_paused boolean, created_by text, created_date timestamptz,
                last_modified_by text, last_modified_date timestamptz, last_error_code text, last_error_message text,
                unique(tenant_id, plan_id, environment, execution_target_key));
            create table modeling_plan_execution_binding_entry (id uuid primary key, tenant_id text, binding_id uuid,
                model_spec_id uuid, published_release_id uuid, model_revision int, dbt_unique_id text, target_identifier text,
                artifact_checksum text, dependency_snapshot_checksum text, created_date timestamptz);
            create table modeling_pipeline_run (id uuid primary key, tenant_id text, model_spec_id uuid, idempotency_key text,
                status text, airflow_dag_id text, airflow_run_id text, dbt_selector text, target text, started_date timestamptz,
                version int, created_date timestamptz, last_modified_date timestamptz, plan_id uuid, model_revision int,
                model_checksum text, repair_path text, pipeline_run_group_id uuid, implementation_revision int,
                implementation_checksum text, environment text, run_purpose text, attempt int, artifact_bundle_checksum text,
                dbt_invocation_id uuid, execution_binding_id uuid, binding_version int, trigger_type text, logical_date timestamptz,
                scope_checksum text, operational_active_claim_key text);
            create table modeling_operational_run_dispatch (id uuid primary key, tenant_id text, binding_id uuid,
                binding_version int, trigger_type text, logical_date timestamptz, execution_target_key text, target_name text,
                airflow_dag_id text, airflow_run_id text, scope_checksum text, initiator_id text, status text,
                dispatch_attempts int, next_attempt_at timestamptz, created_at timestamptz, last_modified_at timestamptz, project_bundle_checksum text);
            """);
        UUID plan=UUID.randomUUID(), model=UUID.randomUUID(), release=UUID.randomUUID(), candidateId=UUID.randomUUID();
        String publishedChecksum="a".repeat(64), draftChecksum="b".repeat(64);
        jdbc.update("insert into modeling_model_spec values (?, 't', ?, 2, 'DRAFT', 3, ?)", model, plan, draftChecksum);
        String facts = new ObjectMapper().writeValueAsString(java.util.Map.of(
            "executionTargetKey", "warehouse", "environment", "prod", "dbtUniqueId", "model.budget", "targetIdentifier", "budget",
            "artifactChecksum", "c".repeat(64), "dependencySnapshotChecksum", "d".repeat(64), "physicalAssetId", UUID.randomUUID(),
            "implementationRevision", 1, "implementationChecksum", "e".repeat(64)));
        jdbc.update("insert into modeling_model_lifecycle_event values (?, 't', ?, 2, ?, 'RELEASE', 'PUBLISHED', ?::jsonb, now())", release, model, publishedChecksum, facts);
        jdbc.update("insert into modeling_model_release_candidate values (?, 't', ?, 'prod', 'warehouse', 'PUBLISHED', now())", candidateId, plan);
        var candidate=mock(CandidateView.class);
        when(candidate.id()).thenReturn(candidateId); when(candidate.tenantId()).thenReturn("t"); when(candidate.planId()).thenReturn(plan);
        when(candidate.environment()).thenReturn("prod"); when(candidate.executionTargetKey()).thenReturn("warehouse");
        var publications=new CandidatePublicationRepository(jdbc, new ObjectMapper());
        var deployments=new PlanExecutionDeploymentRepository(jdbc);
        assertThat(deployments.publishedTargets("t", plan)).containsExactly(candidateId);
        assertThat(deployments.publishedTargets("other", plan)).isEmpty();
        var scope=publications.loadPublishedScope(candidate);
        assertThat(scope).hasSize(1); assertThat(scope.getFirst().modelRevision()).isEqualTo(2);
        tx.executeWithoutResult(status -> publications.rebuildManualBinding(candidate, scope, "operator", Instant.now()));
        var binding=deployments.bindings("t", plan, false).getFirst();
        assertThat(binding.status()).isEqualTo("DEPLOYING");
        assertThat(new PlanExecutionBindingRepository(jdbc).requiresActivation(binding.id())).isTrue();
        assertThat(jdbc.queryForObject("select airflow_paused from modeling_plan_execution_binding", Boolean.class)).isTrue();
        // Same-scope explicit redeployment must still advance CAS and return to the paused deployment state.
        tx.executeWithoutResult(status -> publications.rebuildManualBinding(candidate, scope, "operator", Instant.now()));
        assertThat(deployments.bindings("t", plan, false).getFirst().version()).isEqualTo(2);
        jdbc.update("update modeling_plan_execution_binding set deployment_status='ACTIVE', deployed_checksum=desired_deployment_checksum");
        binding=deployments.bindings("t", plan, false).getFirst();
        assertThat(deployments.enable("t", binding, "operator")).isTrue();
        assertThat(deployments.enable("t", binding, "operator")).isFalse();
        assertThat(new PlanExecutionBindingRepository(jdbc).requiresActivation(binding.id())).isFalse();
        // Withdrawal changes publication only; an explicitly deployed snapshot still identifies revision 2.
        jdbc.update("update modeling_model_lifecycle_event set status='ROLLED_BACK'");
        String checksum=jdbc.queryForObject("select desired_deployment_checksum from modeling_plan_execution_binding", String.class);
        UUID bindingId=binding.id();
        tx.executeWithoutResult(status -> new PlanOperationalRunRepository(jdbc).open("t", plan, bindingId, "run-1", "CRON", Instant.now(), "prod", checksum, Instant.now()));
        assertThat(jdbc.queryForObject("select model_revision from modeling_pipeline_run", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select model_checksum from modeling_pipeline_run", String.class)).isEqualTo(publishedChecksum);
    }
}
