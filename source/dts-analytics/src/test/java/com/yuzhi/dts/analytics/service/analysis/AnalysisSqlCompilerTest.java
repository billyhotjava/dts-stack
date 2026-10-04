package com.yuzhi.dts.analytics.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisSqlCompilerTest {

    private final AnalysisSqlCompiler compiler = new AnalysisSqlCompiler(new AnalysisQuerySpecValidator());

    @Test
    void compilesPinnedSpecIntoParameterizedReadOnlyPlan() {
        GovernedAnalysisDatasetContract contract = contract();
        AnalysisQuerySpec spec = new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(contract.datasetId(), 3, "r7", "checksum-7"),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code", null)),
            List.of(new AnalysisQuerySpec.MetricSelection("record_count", null)),
            List.of(),
            List.of(new AnalysisQuerySpec.FilterSelection("project_code", "EQ", List.of("P-001"), true)),
            null,
            List.of(new AnalysisQuerySpec.OrderSelection("record_count", "DESC")),
            100,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );

        AnalysisSqlCompiler.CompiledAnalysisQuery compiled = compiler.compile(spec, contract, 11L, List.of());

        assertThat(compiled.sql())
            .startsWith("SELECT")
            .contains("FROM (select project_code from ads_project_health) governed_dataset")
            .contains("COUNT(*) AS \"record_count\"")
            .contains("\"project_code\" = ?")
            .contains("LIMIT 101");
        assertThat(compiled.bindings()).containsExactly("P-001");
        assertThat(compiled.constraints().maxResults()).isEqualTo(101);
        assertThat(compiled.requestedLimit()).isEqualTo(100);
    }

    @Test
    void rejectsUnsafeContractIdentifiersInsteadOfInterpolatingThem() {
        GovernedAnalysisDatasetContract contract = contract();
        GovernedAnalysisDatasetContract unsafe = new GovernedAnalysisDatasetContract(
            contract.datasetId(),
            contract.version(),
            contract.status(),
            contract.sourceDatasourceId(),
            contract.baseSql(),
            List.of(new GovernedAnalysisDatasetContract.Dimension("project_code;drop", "bad", "text", List.of(), null, List.of("EQ"))),
            contract.metrics(),
            contract.joins(),
            contract.policyRefs(),
            contract.classification(),
            contract.contractVersion(),
            contract.contractChecksum()
        );
        AnalysisQuerySpec spec = new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(contract.datasetId(), 3, "r7", "checksum-7"),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code;drop", null)),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            100,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );

        assertThatThrownBy(() -> compiler.compile(spec, unsafe, 11L, List.of()))
            .isInstanceOf(AnalysisSpecValidationException.class)
            .hasMessageContaining("identifier");
    }

    private GovernedAnalysisDatasetContract contract() {
        return new GovernedAnalysisDatasetContract(
            UUID.randomUUID(),
            3,
            "PUBLISHED",
            UUID.randomUUID(),
            "select project_code from ads_project_health",
            List.of(new GovernedAnalysisDatasetContract.Dimension("project_code", "项目编码", "text", List.of(), null, List.of("EQ", "IN"))),
            List.of(new GovernedAnalysisDatasetContract.Metric("record_count", "记录数", "COUNT", null, "*")),
            List.of(),
            List.of("classification:snapshot-7"),
            "DATA_INTERNAL",
            "r7",
            "checksum-7"
        );
    }
}
