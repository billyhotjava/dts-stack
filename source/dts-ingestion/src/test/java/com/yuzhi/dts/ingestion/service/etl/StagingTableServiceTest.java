package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.repository.StagingTableMetadataRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StagingTableServiceTest {

    private static final String TABLE = "tmp_ingestion_1234567890abcdef1234567890abcdef";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StagingTableMetadataRepository metadataRepository;

    private StagingTableService service;

    @BeforeEach
    void setup() {
        service = new StagingTableService(jdbcTemplate, new ObjectMapper(), metadataRepository);
    }

    @Test
    void summarizeErrorsShouldReturnCountsRulesAndSamples() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rule_name", "[内置]空行检测");
        rule.put("fail_count", 2L);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("_row_num", 2);
        sample.put("_status", "ERROR");

        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class)).thenReturn(3L);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE + " WHERE _status = 'ERROR'", Long.class)).thenReturn(2L);
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("jsonb_array_elements"))))
            .thenReturn(List.of(rule));
        when(jdbcTemplate.queryForList("SELECT * FROM " + TABLE + " WHERE _status = 'ERROR' ORDER BY _row_num LIMIT ?", 1))
            .thenReturn(List.of(sample));

        StagingTableService.StagingErrorSummary summary = service.summarizeErrors(TABLE, 1);

        assertThat(summary.totalRows()).isEqualTo(3);
        assertThat(summary.cleanRows()).isEqualTo(1);
        assertThat(summary.errorRows()).isEqualTo(2);
        assertThat(summary.errorsByRule()).containsExactly(new StagingTableService.RuleErrorSummary("[内置]空行检测", 2));
        assertThat(summary.sampleRows()).containsExactly(sample);
    }

    @Test
    void exportErrorRowsCsvShouldEscapeCsvValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_row_num", 4);
        row.put("project_name", "项目,一期");
        row.put("_errors", "[{\"rule\":\"x\"}]");
        when(jdbcTemplate.queryForList("SELECT * FROM " + TABLE + " WHERE _status = 'ERROR' ORDER BY _row_num"))
            .thenReturn(List.of(row));

        String csv = new String(service.exportErrorRowsCsv(TABLE), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("_row_num,project_name,_errors\n");
        assertThat(csv).contains("4,\"项目,一期\",\"[{\"\"rule\"\":\"\"x\"\"}]\"");
    }
}
