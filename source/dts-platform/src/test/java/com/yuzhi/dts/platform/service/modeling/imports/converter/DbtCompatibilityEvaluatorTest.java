package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.MaterializationCompatibility;
import org.junit.jupiter.api.Test;

import java.util.List;

class DbtCompatibilityEvaluatorTest {

    @Test
    void keepsInspectionImportProjectionAndRuntimeCertificationAsIndependentAxes() {
        var compatibility = new DbtCompatibilityEvaluator().evaluate(ModelPackageFixtures.validPackage());

        assertThat(compatibility.inspection()).isEqualTo(InspectionCompatibility.SUPPORTED);
        assertThat(compatibility.importProjection()).isEqualTo(ImportProjectionCompatibility.IMPORTABLE);
        assertThat(compatibility.materialization()).isNotEqualTo(MaterializationCompatibility.CERTIFIED);
        assertThat(compatibility.issues()).extracting("code").contains("DBT_RUNTIME_NOT_CERTIFIED");
    }

    @Test
    void treatsOnlyStableIncompleteSemanticsAsCompletable() {
        ModelPackage fixture = ModelPackageFixtures.validPackage();
        PackageModel original = fixture.models().getFirst();
        PackageModel semanticOnly = new PackageModel(
            original.dbtUniqueId(),
            original.name(),
            original.description(),
            original.resourcePath(),
            original.sql(),
            original.materialization(),
            original.config(),
            original.tags(),
            original.columns(),
            original.tests(),
            original.dependencies(),
            original.semantics(),
            new ConversionResult(ConversionMode.BLOCKED, List.of("SOURCE_SEMANTICS_INCOMPLETE"))
        );
        ModelPackage semanticPackage = new ModelPackage(
            fixture.schemaVersion(),
            fixture.packageId(),
            fixture.packageChecksum(),
            fixture.dbt(),
            fixture.defaults(),
            fixture.sources(),
            fixture.technicalNodes(),
            List.of(semanticOnly),
            fixture.issues()
        );

        assertThat(new DbtCompatibilityEvaluator().evaluate(semanticPackage).importProjection())
            .isEqualTo(ImportProjectionCompatibility.IMPORTABLE);

        PackageModel unsafe = new PackageModel(
            semanticOnly.dbtUniqueId(),
            semanticOnly.name(),
            semanticOnly.description(),
            semanticOnly.resourcePath(),
            semanticOnly.sql(),
            semanticOnly.materialization(),
            semanticOnly.config(),
            semanticOnly.tags(),
            semanticOnly.columns(),
            semanticOnly.tests(),
            semanticOnly.dependencies(),
            semanticOnly.semantics(),
            new ConversionResult(
                ConversionMode.BLOCKED,
                List.of("SOURCE_SEMANTICS_INCOMPLETE", "SOURCE_DEPENDENCY_DYNAMIC")
            )
        );
        ModelPackage unsafePackage = new ModelPackage(
            semanticPackage.schemaVersion(),
            semanticPackage.packageId(),
            semanticPackage.packageChecksum(),
            semanticPackage.dbt(),
            semanticPackage.defaults(),
            semanticPackage.sources(),
            semanticPackage.technicalNodes(),
            List.of(unsafe),
            semanticPackage.issues()
        );
        assertThat(new DbtCompatibilityEvaluator().evaluate(unsafePackage).importProjection())
            .isEqualTo(ImportProjectionCompatibility.STRUCTURE_VIEW_ONLY);
    }
}
