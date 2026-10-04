package com.yuzhi.dts.platform.service.modeling.imports.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelPackageChecksumTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canonicalChecksumIgnoresObjectKeyOrderNullFieldsAndExistingPackageChecksum() throws Exception {
        JsonNode first = objectMapper.readTree(
            """
            {"schemaVersion":"dts.model-package/v1","packageId":"pjm","packageChecksum":"old","dbt":{"projectName":"pm","projectVersion":null}}
            """
        );
        JsonNode second = objectMapper.readTree(
            """
            {"dbt":{"projectVersion":null,"projectName":"pm"},"packageChecksum":"different","packageId":"pjm","schemaVersion":"dts.model-package/v1"}
            """
        );

        assertThat(ModelPackageChecksum.compute(first)).isEqualTo(ModelPackageChecksum.compute(second));
    }

    @Test
    void semanticFieldDependencyConfigurationAndSqlChangesEachChangeChecksum() {
        ModelPackage baseline = ModelPackageFixtures.validPackage();
        PackageModel model = baseline.models().getFirst();

        assertThat(ModelPackageChecksum.compute(replaceModel(baseline, new PackageModel(
            model.dbtUniqueId(),
            model.name(),
            model.description(),
            model.resourcePath(),
            model.sql(),
            "view",
            java.util.Map.of("materialized", "view"),
            model.tags(),
            model.columns(),
            model.tests(),
            model.dependencies(),
            model.semantics(),
            model.conversion()
        )))).isNotEqualTo(baseline.packageChecksum());

        assertThat(ModelPackageChecksum.compute(replaceModel(baseline, new PackageModel(
            model.dbtUniqueId(),
            model.name(),
            model.description(),
            model.resourcePath(),
            model.sql(),
            model.materialization(),
            model.config(),
            model.tags(),
            model.columns(),
            model.tests(),
            List.of("source.pjm.other"),
            model.semantics(),
            model.conversion()
        )))).isNotEqualTo(baseline.packageChecksum());

        assertThat(ModelPackageChecksum.compute(replaceModel(baseline, ModelPackageFixtures.model(
            model.dbtUniqueId(),
            model.dependencies().getFirst(),
            "select budget_id, amount from source_budget",
            model.semantics()
        )))).isNotEqualTo(baseline.packageChecksum());

        assertThat(ModelPackageChecksum.compute(replaceModel(baseline, new PackageModel(
            model.dbtUniqueId(),
            model.name(),
            model.description(),
            model.resourcePath(),
            model.sql(),
            model.materialization(),
            model.config(),
            model.tags(),
            model.columns(),
            model.tests(),
            model.dependencies(),
            new com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata(
                "SUMMARY",
                model.semantics().layer(),
                model.semantics().grain(),
                model.semantics().factShape(),
                model.semantics().timeSemantics(),
                model.semantics().domainCode(),
                model.semantics().sourceRefs(),
                model.semantics().consumptionScenarios(),
                model.semantics().fieldRoles(),
                model.semantics().dimensionStrategy(),
                model.semantics().overrideSource(),
                model.semantics().technicalOnly()
            ),
            model.conversion()
        )))).isNotEqualTo(baseline.packageChecksum());
    }

    private static ModelPackage replaceModel(ModelPackage source, PackageModel model) {
        return new ModelPackage(
            source.schemaVersion(),
            source.packageId(),
            null,
            source.dbt(),
            source.defaults(),
            source.sources(),
            source.technicalNodes(),
            List.of(model),
            source.issues()
        );
    }
}
