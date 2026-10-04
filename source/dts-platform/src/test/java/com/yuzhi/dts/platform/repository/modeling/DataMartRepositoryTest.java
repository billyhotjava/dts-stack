package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DataMartRepositoryTest {

    @Test
    void replacesPlanBindingWithoutWritingColumnsMissingFromWarehousePlan() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        DataMartRepository repository = new DataMartRepository(jdbcTemplate);
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID dataMartId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Instant now = Instant.parse("2026-07-26T12:00:00Z");

        int updated = repository.replacePlanBinding("tenant-a", "actor-a", planId, 3, List.of(dataMartId), now);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(3)).update(sql.capture(), arguments.capture());

        assertThat(updated).isEqualTo(1);
        assertThat(sql.getAllValues().get(0))
            .contains("data_marts_version = data_marts_version + 1")
            .contains("last_modified_date = ?")
            .doesNotContain("last_modified_by");
        assertThat(arguments.getAllValues().get(0))
            .containsExactly(Timestamp.from(now), "tenant-a", planId, 3);
        assertThat(arguments.getAllValues().get(2))
            .containsSequence("tenant-a", planId, dataMartId, "actor-a", Timestamp.from(now));
    }
}
