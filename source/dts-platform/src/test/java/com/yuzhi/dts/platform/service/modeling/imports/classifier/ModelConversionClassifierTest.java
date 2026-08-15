package com.yuzhi.dts.platform.service.modeling.imports.classifier;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Grain;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelConversionClassifierTest {

    private final ModelConversionClassifier classifier = new ModelConversionClassifier();

    @Test
    void complexSqlIsDbtBackedWithStableReasons() {
        ModelConversionClassifier.ClassificationInput input = new ModelConversionClassifier.ClassificationInput(
            "model",
            "table",
            "select project_no, sum(amount), case when amount > 0 then 1 else 0 end from source group by project_no",
            ModelPackageFixtures.completeSemantics(),
            false
        );

        var result = classifier.classify(input);

        assertThat(result.mode()).isEqualTo(ConversionMode.DBT_BACKED);
        assertThat(result.reasonCodes()).containsExactly("SQL_AGGREGATION", "SQL_CASE", "SQL_CONSTANT");
    }

    @Test
    void missingExplicitBusinessSemanticsBlocksEvenWhenNameLooksCanonical() {
        var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "table",
            "select id from source",
            null,
            false
        ));

        assertThat(result.mode()).isEqualTo(ConversionMode.BLOCKED);
        assertThat(result.reasonCodes()).contains(
            "MISSING_MODEL_TYPE",
            "MISSING_LAYER",
            "MISSING_GRAIN",
            "MISSING_SOURCE"
        );
    }

    @Test
    void factAndSummaryDoNotRequireApplicationConsumptionScenario() {
        for (String modelType : List.of("FACT", "SUMMARY")) {
            var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
                "model",
                "table",
                "select id, sum(amount) as amount from source group by id",
                semantics(modelType, List.of(new SourceRef("TABLE", "source.pjm.ledger", "ODS")), List.of(), null, null),
                false
            ));

            assertThat(result.mode()).as(modelType).isEqualTo(ConversionMode.DBT_BACKED);
            assertThat(result.reasonCodes()).as(modelType).doesNotContain("MISSING_CONSUMPTION_SCENARIO");
        }
    }

    @Test
    void applicationStillRequiresConsumptionScenario() {
        var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "table",
            "select id from source",
            semantics("APPLICATION", List.of(new SourceRef("TABLE", "source.pjm.ledger", "ODS")), List.of(), null, null),
            false
        ));

        assertThat(result.mode()).isEqualTo(ConversionMode.BLOCKED);
        assertThat(result.reasonCodes()).containsExactly("MISSING_CONSUMPTION_SCENARIO");
    }

    @Test
    void governedStaticDimensionMayUseDbtSqlAsItsSourceProvenance() {
        var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "table",
            "select * from (values ('high', '高')) as value(code, label)",
            semantics(
                "DIMENSION",
                List.of(),
                List.of("公共维度标准化与事实关联"),
                "STATIC_DBT_SQL_TYPE_1",
                "dim_1234567890abcdef1234567890abcdef"
            ),
            false
        ));

        assertThat(result.mode()).isEqualTo(ConversionMode.DBT_BACKED);
        assertThat(result.reasonCodes()).doesNotContain("MISSING_SOURCE");
    }

    @Test
    void sourceLessDimensionWithoutStableGenerationMetadataRemainsBlocked() {
        var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "table",
            "select code from values_source",
            semantics("DIMENSION", List.of(), List.of("公共维度消费"), null, null),
            false
        ));

        assertThat(result.mode()).isEqualTo(ConversionMode.BLOCKED);
        assertThat(result.reasonCodes()).containsExactly("MISSING_SOURCE");
    }

    @Test
    void explicitTechnicalAndEphemeralNodesNeverCreateModelSpecs() {
        var explicit = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "view",
            "select * from source",
            new com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata(
                "STG",
                "STG",
                null,
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.Map.of(),
                null,
                "schema.yml#meta.dts",
                true
            ),
            true
        ));
        var ephemeral = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "ephemeral",
            "select * from source",
            null,
            false
        ));

        assertThat(explicit.mode()).isEqualTo(ConversionMode.TECHNICAL_ONLY);
        assertThat(explicit.reasonCodes()).containsExactly("EXPLICIT_TECHNICAL_SEMANTICS");
        assertThat(ephemeral.mode()).isEqualTo(ConversionMode.TECHNICAL_ONLY);
        assertThat(ephemeral.reasonCodes()).containsExactly("EPHEMERAL_MATERIALIZATION");
    }

    @Test
    void safeSqlWithCompleteSemanticsIsDesignerGeneratedRegardlessOfNodeName() {
        var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "table",
            "select distinct source_id as budget_id, cast(amount as numeric) as amount from source_budget",
            ModelPackageFixtures.completeSemantics(),
            false
        ));

        assertThat(result.mode()).isEqualTo(ConversionMode.DESIGNER_GENERATED);
        assertThat(result.reasonCodes()).containsExactly("SAFE_SQL_SUBSET");
    }

    @Test
    void numericBooleanNullAndComparisonConstantsAreAlwaysDbtBacked() {
        for (
            String sql : java.util.List.of(
                "select 1 as constant_value from source_budget",
                "select true as enabled from source_budget",
                "select null as missing_value from source_budget",
                "select amount from source_budget where amount>=100"
            )
        ) {
            var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
                "model",
                "table",
                sql,
                ModelPackageFixtures.completeSemantics(),
                false
            ));

            assertThat(result.mode()).as(sql).isEqualTo(ConversionMode.DBT_BACKED);
            assertThat(result.reasonCodes()).as(sql).contains("SQL_CONSTANT");
        }
    }

    @Test
    void arithmeticWithoutWhitespaceIsAlwaysDbtBackedWithStableReason() {
        var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
            "model",
            "table",
            "select amount*0.1 as discounted, amount/total as ratio, amount%bucket as remainder from source_budget",
            ModelPackageFixtures.completeSemantics(),
            false
        ));

        assertThat(result.mode()).isEqualTo(ConversionMode.DBT_BACKED);
        assertThat(result.reasonCodes()).contains("SQL_CONSTANT", "SQL_ARITHMETIC");
    }

    @Test
    void wildcardAndTypePrecisionAreNotMistakenForBusinessConstantsOrArithmetic() {
        for (
            String sql : java.util.List.of(
                "select * from source_budget",
                "select cast(amount as numeric(18,2)) as amount from source_budget"
            )
        ) {
            var result = classifier.classify(new ModelConversionClassifier.ClassificationInput(
                "model",
                "table",
                sql,
                ModelPackageFixtures.completeSemantics(),
                false
            ));

            assertThat(result.mode()).as(sql).isEqualTo(ConversionMode.DESIGNER_GENERATED);
            assertThat(result.reasonCodes()).as(sql).containsExactly("SAFE_SQL_SUBSET");
        }
    }

    private static SemanticMetadata semantics(
        String modelType,
        List<SourceRef> sourceRefs,
        List<String> consumptionScenarios,
        String dimensionStrategy,
        String dimensionDefinitionCode
    ) {
        return new SemanticMetadata(
            modelType,
            "APPLICATION".equals(modelType) ? "ADS" : "DIMENSION".equals(modelType) || "FACT".equals(modelType) ? "DWD" : "DWS",
            new Grain("每个业务键一行", List.of("id")),
            "FACT".equals(modelType) ? "TRANSACTION" : null,
            null,
            "PROJECT_MANAGEMENT",
            sourceRefs,
            consumptionScenarios,
            Map.of("id", "KEY"),
            dimensionStrategy,
            dimensionDefinitionCode,
            "test#meta.dts",
            false
        );
    }
}
