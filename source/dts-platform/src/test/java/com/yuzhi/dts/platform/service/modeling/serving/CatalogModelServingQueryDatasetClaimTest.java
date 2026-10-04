package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CatalogModelServingQueryDatasetClaimTest {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void reclaimsSyncedDwsAdsProjectionWhenCanonicalQueryDatasetIsMissing() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        Instant now = Instant.parse("2026-08-19T02:00:00Z");

        repository.claimSyncCandidates(50, now, Duration.ofMinutes(2));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertThat(sql.getValue())
            .contains("sync_status = 'SYNCED'")
            .contains("query_dataset_asset")
            .contains("source_model_spec_id")
            .contains("warehouse_layer")
            .contains("'DWS', 'ADS'");
        assertThat(arguments.getValue()).contains(Timestamp.from(now.plus(Duration.ofMinutes(2))));
    }
}
