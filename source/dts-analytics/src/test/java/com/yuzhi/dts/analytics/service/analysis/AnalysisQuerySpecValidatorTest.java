package com.yuzhi.dts.analytics.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisQuerySpecValidatorTest {

    private final AnalysisQuerySpecParser parser = new AnalysisQuerySpecParser(new ObjectMapper());
    private final AnalysisQuerySpecValidator validator = new AnalysisQuerySpecValidator();

    @Test
    void validate_shouldNormalizeAContractPinnedSpecWithoutRawSql() {
        GovernedAnalysisDatasetContract contract = contract();
        AnalysisQuerySpec spec = new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(contract.datasetId(), 2, "r7", contract.contractChecksum()),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code", "项目编码")),
            List.of(new AnalysisQuerySpec.MetricSelection("record_count", null)),
            List.of(),
            List.of(new AnalysisQuerySpec.FilterSelection("project_code", "IN", List.of("P001"), true)),
            null,
            List.of(new AnalysisQuerySpec.OrderSelection("project_code", "ASC")),
            null,
            new AnalysisQuerySpec.Visualization("table", Map.of("pageSize", 50))
        );

        AnalysisQuerySpec normalized = validator.validateAndNormalize(spec, contract);

        assertThat(normalized.limit()).isEqualTo(5000);
        assertThat(normalized.dataset().checksum()).isEqualTo(contract.contractChecksum());
    }

    @Test
    void validate_shouldEnforceAllFrozenCardinalityBudgets() {
        GovernedAnalysisDatasetContract contract = contract();
        AnalysisQuerySpec tooManyFilters = specWith(
            java.util.stream.IntStream
                .range(0, 51)
                .mapToObj(index -> new AnalysisQuerySpec.FilterSelection("project_code", "EQ", List.of("P" + index), false))
                .toList(),
            10001
        );

        assertThatThrownBy(() -> validator.validateAndNormalize(tooManyFilters, contract))
            .isInstanceOf(AnalysisSpecValidationException.class)
            .hasMessageContaining("ANALYSIS_FILTER_LIMIT_EXCEEDED");
    }

    @Test
    void validate_shouldRejectUnknownFieldsAndExpressionFunctions() throws Exception {
        String raw = new ObjectMapper()
            .writeValueAsString(specWith(List.of(), 100))
            .replaceFirst("\\{", "{\"rawSql\":\"select * from secret\",");
        assertThatThrownBy(() -> parser.parse(raw))
            .isInstanceOf(AnalysisSpecValidationException.class)
            .hasMessageContaining("ANALYSIS_SPEC_UNKNOWN_FIELD");

        AnalysisQuerySpec unsafe = new AnalysisQuerySpec(
            "dts.analysis/v1",
            specWith(List.of(), 100).dataset(),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code", null)),
            List.of(),
            List.of(new AnalysisQuerySpec.DerivedMetric("danger", "pg_sleep(record_count)", null)),
            List.of(),
            null,
            List.of(),
            100,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );
        assertThatThrownBy(() -> validator.validateAndNormalize(unsafe, contract()))
            .isInstanceOf(AnalysisSpecValidationException.class)
            .hasMessageContaining("ANALYSIS_EXPRESSION_FUNCTION_FORBIDDEN");
    }

    private AnalysisQuerySpec specWith(List<AnalysisQuerySpec.FilterSelection> filters, Integer limit) {
        GovernedAnalysisDatasetContract contract = contract();
        return new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(contract.datasetId(), 2, "r7", contract.contractChecksum()),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code", null)),
            List.of(new AnalysisQuerySpec.MetricSelection("record_count", null)),
            List.of(),
            filters,
            null,
            List.of(),
            limit,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );
    }

    private GovernedAnalysisDatasetContract contract() {
        UUID datasetId = UUID.fromString("76db5740-7e53-4c33-81b3-75f7d2047f8c");
        return new GovernedAnalysisDatasetContract(
            datasetId,
            2,
            "PUBLISHED",
            UUID.randomUUID(),
            "select project_code from ads_project_health",
            List.of(
                new GovernedAnalysisDatasetContract.Dimension(
                    "project_code",
                    "项目编码",
                    "VARCHAR",
                    List.of(),
                    "DATA_INTERNAL",
                    List.of("EQ", "NE", "IN")
                )
            ),
            List.of(new GovernedAnalysisDatasetContract.Metric("record_count", "记录数", "COUNT", null, "COUNT(*)")),
            List.of(),
            List.of(),
            "DATA_INTERNAL",
            "r7",
            "a".repeat(64)
        );
    }
}
