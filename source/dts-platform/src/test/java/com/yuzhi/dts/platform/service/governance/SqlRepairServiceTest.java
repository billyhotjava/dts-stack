package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.yuzhi.dts.platform.repository.governance.GovDataEditLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SqlRepairServiceTest {

    @Test
    void previewAndExecuteNeverUseThePlatformDatasource() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GovDataEditLogRepository editLogRepository = mock(GovDataEditLogRepository.class);
        SqlRepairService service = new SqlRepairService(jdbcTemplate, editLogRepository);
        String sql = "UPDATE ods_budget SET token = 'top-secret-token'";

        assertThatThrownBy(() -> service.preview(sql, 10))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("质量 SQL 修复预览暂未开放");
        assertThatThrownBy(() -> service.execute(sql, UUID.randomUUID()))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("质量 SQL 修复执行暂未开放");

        verifyNoInteractions(jdbcTemplate, editLogRepository);
    }
}
