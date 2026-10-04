package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.validator.ModelPackageValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PjmModelPackageGoldenFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pjmBudgetFixturePreservesTechnicalGraphAndExpectedConservativeClassifications() throws Exception {
        Path fixture = fixturePath();
        byte[] json = Files.readAllBytes(fixture);
        var modelPackage = new ModelPackageValidator(objectMapper).parseAndValidate(json);
        Map<String, ConversionMode> modes = modelPackage.models()
            .stream()
            .collect(Collectors.toMap(model -> model.dbtUniqueId(), model -> model.conversion().mode()));

        assertThat(modelPackage.schemaVersion()).isEqualTo("dts.model-package/v1");
        assertThat(ModelPackageChecksum.compute(modelPackage)).isEqualTo(modelPackage.packageChecksum());
        assertThat(modelPackage.technicalNodes())
            .anySatisfy(node -> {
                assertThat(node.dbtUniqueId()).isEqualTo("model.pm_analytics_v3.stg_pm__budget_v2");
                assertThat(node.conversion().mode()).isEqualTo(ConversionMode.TECHNICAL_ONLY);
            });
        assertThat(modes).containsOnly(
            org.assertj.core.data.MapEntry.entry("model.pm_analytics_v3.biz_dwd_budget_v2", ConversionMode.DBT_BACKED),
            org.assertj.core.data.MapEntry.entry("model.pm_analytics_v3.biz_dws_budget_v2", ConversionMode.DBT_BACKED),
            org.assertj.core.data.MapEntry.entry("model.pm_analytics_v3.biz_ads_budget_kpi_v2", ConversionMode.DBT_BACKED),
            org.assertj.core.data.MapEntry.entry("model.pm_analytics_v3.biz_ads_budget_derived_v2", ConversionMode.DBT_BACKED),
            org.assertj.core.data.MapEntry.entry("model.pm_analytics_v3.dim_node_type_v2", ConversionMode.DBT_BACKED)
        );
        assertThat(modelPackage.models())
            .filteredOn(model -> model.dbtUniqueId().equals("model.pm_analytics_v3.biz_dwd_budget_v2"))
            .singleElement()
            .satisfies(model -> assertThat(model.dependencies()).contains("model.pm_analytics_v3.stg_pm__budget_v2"));
        assertThat(modelPackage.models())
            .filteredOn(model -> model.dbtUniqueId().equals("model.pm_analytics_v3.dim_node_type_v2"))
            .singleElement()
            .satisfies(model ->
                assertThat(model.semantics().dimensionDefinitionCode()).isEqualTo("dim_0123456789abcdef0123456789abcdef")
            );
        assertThat(modelPackage.issues()).extracting(issue -> issue.code()).containsExactly("CATALOG_MISSING");
    }

    private static Path fixturePath() {
        Path fromRepositoryRoot = Path.of(
            "worklog/v2.2.3/sprint-70-202607-dbt-model-package-import/fixtures/pjm-budget/dts-model-package.json"
        );
        if (Files.isRegularFile(fromRepositoryRoot)) {
            return fromRepositoryRoot;
        }
        return Path.of("..", "..").resolve(fromRepositoryRoot).normalize();
    }
}
