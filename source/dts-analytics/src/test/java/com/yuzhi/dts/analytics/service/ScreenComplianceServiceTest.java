package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScreenComplianceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void applyMasking_keepsDataWhenMaskingDisabled() {
        ScreenComplianceService service = new ScreenComplianceService(objectMapper);

        DatasetQueryService.DatasetResult input = sampleResult();
        DatasetQueryService.DatasetResult masked = service.applyMasking(input);

        assertThat(masked.rows()).isEqualTo(input.rows());
    }

    @Test
    void applyMasking_masksDefaultSensitiveColumns() {
        ScreenComplianceService service = new ScreenComplianceService(objectMapper);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("maskingEnabled", true);
        service.updatePolicy(update, "tester");

        DatasetQueryService.DatasetResult masked = service.applyMasking(sampleResult());

        assertThat(masked.rows()).hasSize(1);
        List<Object> row = masked.rows().get(0);
        assertThat(row.get(0)).isEqualTo("张*");
        assertThat(row.get(1)).isEqualTo("138****5678");
        assertThat(row.get(2)).isEqualTo("u***@corp.local");
        assertThat(row.get(3)).isEqualTo("normal");
    }

    @Test
    void applyMasking_respectsCustomMaskRules() {
        ScreenComplianceService service = new ScreenComplianceService(objectMapper);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("maskingEnabled", true);
        ArrayNode maskRules = objectMapper.createArrayNode();
        maskRules.add("salary");
        update.set("maskRules", maskRules);
        service.updatePolicy(update, "tester");

        List<Map<String, Object>> cols = List.of(
                Map.of("name", "employee_name"),
                Map.of("name", "salary_amount"));
        List<List<Object>> rows = List.of(List.of("Alice", "998877"));
        DatasetQueryService.DatasetResult input =
                new DatasetQueryService.DatasetResult(rows, cols, List.of(), "UTC");

        DatasetQueryService.DatasetResult masked = service.applyMasking(input);

        assertThat(masked.rows().get(0).get(0)).isEqualTo("Alice");
        assertThat(masked.rows().get(0).get(1)).isEqualTo("******");
    }

    private DatasetQueryService.DatasetResult sampleResult() {
        List<Map<String, Object>> cols = List.of(
                Map.of("name", "user_name"),
                Map.of("name", "mobile_phone"),
                Map.of("name", "email_address"),
                Map.of("name", "biz_tag"));

        List<List<Object>> rows = List.of(List.of("张三", "13812345678", "user@corp.local", "normal"));
        return new DatasetQueryService.DatasetResult(rows, cols, List.of(), "UTC");
    }
}
