package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ModelSpecPlanWriteAccessAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void delegatesToTheTenantPlanOwnerFactAndFailsClosedForMissingContext() {
        UUID planId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("tenant"), eq(planId), eq("alice")))
            .thenReturn(true);
        ModelSpecPlanWriteAccessAdapter adapter = new ModelSpecPlanWriteAccessAdapter(jdbcTemplate);

        assertThat(adapter.canMaintain("tenant", planId, "alice")).isTrue();
        assertThat(adapter.canMaintain("tenant", planId, " ")).isFalse();
    }
}
