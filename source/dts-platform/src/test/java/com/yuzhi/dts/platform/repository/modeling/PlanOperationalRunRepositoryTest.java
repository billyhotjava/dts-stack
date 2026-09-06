package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PlanOperationalRunRepositoryTest {

    @Test
    void loadScopeOnlySelectsRunnableSqlAndYamlArtifacts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any()
            )
        )
            .thenReturn(List.of());
        UUID groupId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

        assertThatThrownBy(() ->
            new PlanOperationalRunRepository(jdbc).loadScope(groupId)
        )
            .isInstanceOf(PlanExecutionException.class)
            .hasMessageContaining("Operational run scope does not exist");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
            eq(groupId)
        );
        assertThat(sql.getValue())
            .contains("a.status = 'COMPILED'")
            .contains("a.status = 'IMPORTED'")
            .contains("a.node_kind in ('STG', 'EPHEMERAL')")
            .contains("a.artifact_type in ('SQL', 'TEST', 'STG_SQL')")
            .contains("a.artifact_type = 'SCHEMA'")
            .contains("lower(a.path) like '%.yml'")
            .contains("lower(a.path) like '%.yaml'")
            .contains("join modeling_plan_execution_binding b")
            .contains("b.version = d.binding_version")
            .contains("b.desired_deployment_checksum");
    }

    @Test
    void rejectsLegacyReleaseDagBeforeCreatingAnOperationalDispatch() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID bindingId = UUID.fromString(
            "20000000-0000-0000-0000-000000000002"
        );
        UUID planId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(bindingId);
        when(row.getString("tenant_id")).thenReturn("tenant-a");
        when(row.getObject("plan_id", UUID.class)).thenReturn(planId);
        when(row.getString("environment")).thenReturn("PROD");
        when(row.getString("execution_target_key"))
            .thenReturn("postgres-primary");
        when(row.getInt("version")).thenReturn(4);
        when(row.getString("dag_id"))
            .thenReturn("dts_release_build_postgres_primary");
        when(row.getString("deployment_status")).thenReturn("ACTIVE");
        when(row.getString("desired_scope_checksum"))
            .thenReturn("a".repeat(64));
        when(row.getString("desired_deployment_checksum"))
            .thenReturn("b".repeat(64));
        when(row.getString("deployed_checksum"))
            .thenReturn("b".repeat(64));
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any(Object[].class)
            )
        ).thenAnswer(invocation -> {
            if (!invocation.<String>getArgument(0).contains("from modeling_plan_execution_binding")) {
                return List.of();
            }
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });

        assertThatThrownBy(() ->
            new PlanOperationalRunRepository(jdbc).open(
                "tenant-a",
                planId,
                bindingId,
                "dts_plan_20000000000000000000000000000002_request",
                "MANUAL",
                null,
                "prod",
                null,
                Instant.parse("2026-09-07T01:00:00Z")
            )
        )
            .isInstanceOf(PlanExecutionException.class)
            .satisfies(error ->
                assertThat(((PlanExecutionException) error).code())
                    .isEqualTo(
                        "MODEL_PLAN_BINDING_OPERATIONAL_DAG_REQUIRED"
                    )
            );
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void repairNormalizesLegacyManualBindingBeforeRedeployment() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID bindingId = UUID.fromString(
            "20000000-0000-0000-0000-000000000002"
        );
        UUID planId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );
        ResultSet row = mock(ResultSet.class);
        when(row.getString("environment")).thenReturn("PROD");
        when(row.getString("execution_target_key"))
            .thenReturn("postgres-primary");
        when(row.getString("schedule_mode")).thenReturn("MANUAL_ONLY");
        when(row.getString("desired_scope_checksum"))
            .thenReturn("a".repeat(64));
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any(Object[].class)
            )
        ).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var result = new PlanExecutionBindingRepository(jdbc)
            .requestRedeployment(
                "tenant-a",
                planId,
                bindingId,
                4,
                "operator-a",
                Instant.parse("2026-09-07T01:00:00Z")
            );

        assertThat(result).contains(
            new PlanExecutionBindingRepository.RepairResult(5)
        );
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(
            Object[].class
        );
        verify(jdbc).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue())
            .contains("set version = version + 1")
            .contains("schedule_mode = 'MANUAL_ONLY'")
            .contains("operational_active_claim_key is not null");
        assertThat(arguments.getValue()[0])
            .isEqualTo("dts_plan_20000000000000000000000000000002");
        assertThat((String) arguments.getValue()[1])
            .matches("[0-9a-f]{64}")
            .isNotEqualTo("b".repeat(64));
    }

    @Test
    void repairDoesNotChangeAşgabatBindingWithAnActiveOperationalClaim() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID bindingId = UUID.fromString(
            "20000000-0000-0000-0000-000000000002"
        );
        UUID planId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any(Object[].class)
            )
        ).thenReturn(List.of());

        assertThat(
            new PlanExecutionBindingRepository(jdbc).requestRedeployment(
                "tenant-a",
                planId,
                bindingId,
                4,
                "operator-a",
                Instant.parse("2026-09-07T01:00:00Z")
            )
        ).isEmpty();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
            any(Object[].class)
        );
        assertThat(sql.getValue())
            .contains("from modeling_pipeline_run active")
            .contains("active.operational_active_claim_key is not null");
    }

    @Test
    void loadEvidenceScopePinsFieldsFromTheExactModelRevision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any()
            )
        )
            .thenReturn(List.of());
        UUID groupId = UUID.fromString(
            "10000000-0000-0000-0000-000000000002"
        );

        assertThatThrownBy(() ->
            new PlanOperationalRunRepository(jdbc).loadEvidenceScope(groupId)
        )
            .isInstanceOf(PlanExecutionException.class)
            .hasMessageContaining(
                "Operational run evidence scope does not exist"
            );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
            eq(groupId)
        );
        assertThat(sql.getValue())
            .contains("join modeling_model_spec_revision sr")
            .contains("sr.revision = pr.model_revision")
            .contains("sr.content_checksum = pr.model_checksum")
            .contains("jsonb_array_elements")
            .contains("as expected_columns");
    }
}
