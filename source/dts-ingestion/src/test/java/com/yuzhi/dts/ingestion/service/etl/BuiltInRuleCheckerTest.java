package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BuiltInRuleCheckerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BuiltInRuleChecker checker;

    @BeforeEach
    void setup() {
        checker = new BuiltInRuleChecker(jdbcTemplate);
    }

    @Test
    void checkShouldAcceptIsoTimestampSeparator() {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        var errors = checker.check(
            "tmp_ingestion_1234567890abcdef1234567890abcdef",
            List.of(new ColumnInfo("update_time", "TIMESTAMP", 100))
        );

        assertThat(errors).isEmpty();
        verify(jdbcTemplate).queryForList(argThat(sql -> sql != null && sql.contains("!~") && sql.contains("[ T]")));
    }
}
