package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.PlanExecutionHealthRepository.BindingHealthRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PlanExecutionHealthRepositoryTest {

    @Test
    void relationObservationOnlySelectsColumnsProjectedByItsLateralQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(
            jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<BindingHealthRecord>>any(),
                any(),
                any()
            )
        )
            .thenReturn(List.of());
        UUID planId = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

        new PlanExecutionHealthRepository(jdbc).findByPlan("default", planId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<BindingHealthRecord>>any(),
            eq("default"),
            eq(planId)
        );
        assertThat(sql.getValue())
            .contains("observation.relation_name")
            .doesNotContain(
                "observation.schema_name",
                "observation.identifier"
            );
    }
}
