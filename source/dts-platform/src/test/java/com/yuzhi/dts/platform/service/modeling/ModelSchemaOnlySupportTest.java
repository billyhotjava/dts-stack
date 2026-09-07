package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelSchemaOnlySupportTest {
    @Test
    void supportsExplicitApiRawPayloadColumnsWithoutOpeningArbitrarySqlTypes() {
        var projection = org.mockito.Mockito.spy(projection("SOURCE", false));
        org.mockito.Mockito.when(projection.typedFields()).thenReturn(List.of(
            new ModelSpecCompilerProjection.CompilerField("record_id", "bigint", false),
            new ModelSpecCompilerProjection.CompilerField("amount", "jsonb", true)));
        assertThat(ModelingDbtCompiler.compile(projection).files().get("schema_model.sql"))
            .contains("'data_type':'jsonb'");
        assertThat(ModelFieldPhysicalTypeContract.postgresTypesMatch("jsonb", "jsonb")).isTrue();
        assertThatThrownBy(() -> ModelFieldPhysicalTypeContract.requireSupported("jsonb); drop table records; --"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compilesAllFourLayersWithoutAnUpstreamQueryAndPinsDeclaredStructure() {
        for (String type : List.of("SOURCE", "FACT", "SUMMARY", "APPLICATION")) {
            var projection = projection(type, false);
            var artifacts = ModelingDbtCompiler.compile(projection);
            String model = artifacts.files().get("schema_model.sql");
            assertThat(model).contains("materialized='dts_schema_only'", "dts_primary_keys=['record_id']",
                "'data_type':'bigint'", "'nullable':False", "'modelChecksum':'" + "a".repeat(64) + "'");
            assertThat(artifacts.files().get("stg_schema_model.sql")).contains("where false").doesNotContain("source(", "generate_series");
        }
    }

    @Test
    void refusesNullableKeysAndDataProcessingSettings() {
        assertThatThrownBy(() -> ModelingDbtCompiler.compile(projection("SOURCE", true)))
            .hasMessageContaining("MODEL_SCHEMA_ONLY_KEY_NULLABLE");
        assertThat(ModelSchemaOnlySupport.validSettings("view", settings())).isFalse();
        assertThat(ModelSchemaOnlySupport.validSettings("table", Map.of("targetPhysicalName", "ods_orders", "loadStrategy", "INCREMENTAL"))).isFalse();
        assertThat(ModelSchemaOnlySupport.validSettings("table", Map.of("targetPhysicalName", "ods_orders", "loadStrategy", "FULL", "filters", List.of()))).isFalse();
    }

    @Test
    void requiresAnExplicitEmptyStructureGeneratorConfiguration() {
        var valid = new SaveImplementationCommand(InputMode.GENERATED, List.of(new GeneratedInput("SCHEMA_ONLY", Map.of())),
            List.of(), settings(), ModelSpecContract.ImplementationMode.DESIGNER_GENERATED, "table", "schema-test");
        assertThat(ModelSchemaOnlySupport.valid(valid)).isTrue();
        var unexpected = new SaveImplementationCommand(InputMode.GENERATED, List.of(new GeneratedInput("SCHEMA_ONLY", Map.of("sql", "select 1"))),
            List.of(), settings(), ModelSpecContract.ImplementationMode.DESIGNER_GENERATED, "table", "schema-test");
        assertThat(ModelSchemaOnlySupport.valid(unexpected)).isFalse();
        assertThat(ModelSchemaOnlySupport.isSchemaOnly(InputMode.GENERATED, List.of(new GeneratedInput("DATE_DIMENSION", Map.of())))).isFalse();
    }

    private static Map<String, Object> settings() {
        return Map.of("targetPhysicalName", "ods_orders", "loadStrategy", "FULL", "partitionFields", List.of());
    }

    private static ModelSpecCompilerProjection.ImplementationProjection projection(String type, boolean nullableKey) {
        var base = PjmModelingFixture.projectNode().compilerModel();
        var model = new ModelingCompilerContract.CompilerModel(base.id(),
            ModelingCompilerContract.Layer.valueOf(switch (type) { case "SOURCE" -> "ODS"; case "FACT" -> "DWD"; case "SUMMARY" -> "DWS"; default -> "ADS"; }),
            ModelingCompilerContract.ModelType.valueOf(type), ModelingCompilerContract.ImplementationMode.DESIGNER_GENERATED,
            "schema_model", new ModelingCompilerContract.Grain("one source record", List.of("record_id")), List.of(), List.of(),
            List.of("record_id", "amount"), List.of(), 1);
        return new ModelSpecCompilerProjection.ImplementationProjection(model, "tenant-a", "a".repeat(64), 1, "b".repeat(64),
            "model.dts.schema_model", InputMode.GENERATED, List.of(new GeneratedInput("SCHEMA_ONLY", Map.of())), List.of(), settings(),
            "table", List.of("record_id"), "plan-a", List.of(
                new ModelSpecCompilerProjection.CompilerField("record_id", "bigint", nullableKey),
                new ModelSpecCompilerProjection.CompilerField("amount", "numeric(12,2)", true)
            ));
    }
}
