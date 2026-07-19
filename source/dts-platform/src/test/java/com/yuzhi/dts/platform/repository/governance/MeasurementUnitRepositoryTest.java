package com.yuzhi.dts.platform.repository.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class MeasurementUnitRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void scansOnlyCanonicalTenantScopedModelBindingsAndReadsPinnedVersions() {
        MeasurementUnitRepository repository = new MeasurementUnitRepository(
            jdbcTemplate,
            new ObjectMapper().findAndRegisterModules()
        );
        UUID unitId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.listModelSpecReferences("server-tenant", unitId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
            .contains("s.contract_version = 2")
            .contains("s.tenant_id = ?")
            .contains("jsonb_array_elements")
            .contains("measurementUnitId")
            .contains("measurementUnitVersion")
            .doesNotContain("business_object");
    }
}
