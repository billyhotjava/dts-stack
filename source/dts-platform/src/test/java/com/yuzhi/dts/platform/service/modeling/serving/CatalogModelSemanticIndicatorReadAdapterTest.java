package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CatalogModelSemanticIndicatorReadAdapterTest {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void readsOnlyPublishedAtomicIndicatorsBoundToTheExactModelRevision() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        CatalogModelSemanticIndicatorReadAdapter adapter = new CatalogModelSemanticIndicatorReadAdapter(jdbc);
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");

        assertThat(adapter.findPublishedAtomicIndicators(modelId, 4)).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertThat(sql.getValue())
            .contains("upper(coalesce(indicator.status, '')) = 'PUBLISHED'")
            .contains("upper(coalesce(indicator.metric_type, '')) = 'ATOMIC'")
            .contains("source_ref ->> 'sourceType'")
            .contains("source_ref ->> 'sourceId'")
            .contains("source_ref ->> 'sourceVersion'");
        assertThat(arguments.getValue()).containsExactly(modelId.toString(), "r4");
    }
}
