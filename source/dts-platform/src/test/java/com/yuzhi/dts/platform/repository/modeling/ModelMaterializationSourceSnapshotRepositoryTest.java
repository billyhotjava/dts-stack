package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository.InputSnapshot;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ModelMaterializationSourceSnapshotRepositoryTest {

    private static final UUID GROUP_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000001"
    );

    @Test
    void operationalInputsUseOnlyThePublishedPipelinePins() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InputSnapshot first = snapshot("20000000-0000-0000-0000-000000000001");
        InputSnapshot second = snapshot("20000000-0000-0000-0000-000000000002");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(GROUP_ID))).thenReturn(2);
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<InputSnapshot>>any(),
                eq(GROUP_ID)
            )
        )
            .thenReturn(List.of(first, second));

        List<InputSnapshot> actual = new ModelMaterializationSourceSnapshotRepository(jdbc)
            .findOperationalInputs(GROUP_ID);

        assertThat(actual).containsExactly(first, second);
        ArgumentCaptor<String> select = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            select.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<InputSnapshot>>any(),
            eq(GROUP_ID)
        );
        assertThat(select.getValue())
            .contains("modeling_operational_run_dispatch")
            .contains("modeling_pipeline_run pipeline_run")
            .contains("binding_entry.published_release_id")
            .contains("release_event.event_type = 'RELEASE'")
            .contains("release_event.details_json ->> 'candidateId'")
            .contains("release_event.details_json ->> 'candidateVersion'")
            .contains("release_event.details_json ->> 'implementationRevision' =")
            .contains("pipeline_run.implementation_revision::text")
            .contains("release_event.details_json ->> 'targetIdentifier' = binding_entry.target_identifier")
            .contains("release_event.details_json ->> 'executionTargetKey' = binding.execution_target_key")
            .contains("release_event.details_json ->> 'environment' = binding.environment")
            .contains("implementation_revision.implementation_id = candidate_entry.implementation_id")
            .contains("pipeline_run.target = dispatch.target_name")
            .doesNotContain("status = 'ACTIVE'");
    }

    @Test
    void operationalInputsRejectWhenAHistoricalPipelineRowCannotBeProven() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(GROUP_ID))).thenReturn(2);
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<InputSnapshot>>any(),
                eq(GROUP_ID)
            )
        )
            .thenReturn(List.of(snapshot("20000000-0000-0000-0000-000000000001")));

        assertThatThrownBy(() ->
            new ModelMaterializationSourceSnapshotRepository(jdbc).findOperationalInputs(GROUP_ID)
        )
            .isInstanceOf(PlanExecutionException.class)
            .extracting(error -> ((PlanExecutionException) error).code())
            .isEqualTo("MODEL_OPERATIONAL_SOURCE_SNAPSHOT_MISMATCH");
    }

    @Test
    void operationalInputsRejectMissingPipelineScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(GROUP_ID))).thenReturn(0);

        assertThatThrownBy(() ->
            new ModelMaterializationSourceSnapshotRepository(jdbc).findOperationalInputs(GROUP_ID)
        )
            .isInstanceOf(PlanExecutionException.class)
            .extracting(error -> ((PlanExecutionException) error).code())
            .isEqualTo("MODEL_OPERATIONAL_SOURCE_SNAPSHOT_MISSING");
    }

    private static InputSnapshot snapshot(String modelSpecId) {
        return new InputSnapshot(
            "tenant-a",
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            UUID.fromString(modelSpecId),
            4,
            "a".repeat(64),
            "PHYSICAL_ASSET",
            "[]",
            "[]"
        );
    }
}
