package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
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
            .contains("lower(a.path) like '%.yaml'");
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
