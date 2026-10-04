package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.sql.dto.SqlValidateRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateResponse;
import java.security.Principal;
import org.junit.jupiter.api.Test;

class SqlValidationServiceTest {

    private final SqlValidationService service = new SqlValidationService();
    private final Principal principal = () -> "tester";

    @Test
    void shouldAppendLimitForReadOnlySelect() {
        SqlValidateRequest request = new SqlValidateRequest("select * from orders", null, null, null, null);
        SqlValidateResponse response = service.validate(request, principal);

        assertThat(response.executable()).isTrue();
        assertThat(response.rewrittenSql().toLowerCase()).contains("limit 1000");
        assertThat(response.warnings()).isNotEmpty();
    }

    @Test
    void shouldBlockWriteStatements() {
        SqlValidateRequest request = new SqlValidateRequest("delete from orders", null, null, null, null);
        SqlValidateResponse response = service.validate(request, principal);

        assertThat(response.executable()).isFalse();
        assertThat(response.violations())
            .anySatisfy(v -> assertThat(v.code()).isEqualTo("WRITE_BLOCKED"));
    }
}
