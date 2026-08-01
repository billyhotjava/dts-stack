package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ModelingCompilerContractTest {

    @Test
    void exposesOnlyCompilerOwnedTypesAndFields() {
        assertThat(Arrays.stream(ModelingCompilerContract.class.getDeclaredClasses()).map(Class::getSimpleName))
            .containsExactlyInAnyOrder(
                "Layer",
                "ModelType",
                "ImplementationMode",
                "Grain",
                "SourceRef",
                "StandardBinding",
                "CompilerModel"
            )
            .doesNotContain("BusinessObject", "ObjectKind", "LegacyModelRef", "ModelSpec");

        assertThat(Arrays.stream(ModelingCompilerContract.CompilerModel.class.getRecordComponents()).map(component -> component.getName()))
            .containsExactly(
                "id",
                "layer",
                "modelType",
                "implementationMode",
                "name",
                "grain",
                "standardBindings",
                "sourceRefs",
                "dimensions",
                "metrics",
                "revision"
            );
        assertThat(Arrays.stream(ModelingCompilerContract.StandardBinding.class.getRecordComponents()).map(component -> component.getName()))
            .containsExactly("fieldName", "standardElementId");
    }
}
