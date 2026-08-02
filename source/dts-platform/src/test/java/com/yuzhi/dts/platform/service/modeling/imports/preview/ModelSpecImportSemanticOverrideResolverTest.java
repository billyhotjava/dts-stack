package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.SemanticGrainOverride;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.SemanticOverride;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.SemanticStandardBindingOverride;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecImportSemanticOverrideResolverTest {

    private final ModelSpecImportSemanticOverrideResolver resolver = new ModelSpecImportSemanticOverrideResolver();

    @Test
    void appliesOnlyBusinessSemanticsAndKeepsTargetStandardBindingsOutOfTheModelPackage() {
        var source = ModelPackageFixtures.validPackage().models().getFirst();
        String field = source.columns().getFirst().name();
        UUID standardElementId = UUID.fromString("10000000-0000-0000-0000-000000000083");
        SemanticOverride override = new SemanticOverride(
            source.dbtUniqueId(),
            "FACT",
            "DWD",
            "预算事实",
            "统一预算执行口径",
            new SemanticGrainOverride("每个预算编号一行", List.of(field)),
            Map.of(field, "KEY"),
            List.of(field),
            List.of(new SemanticStandardBindingOverride(field, standardElementId, 3, null, null, null, null, "L2")),
            List.of("预算执行分析")
        );

        var resolved = resolver.resolve(source, override);

        assertThat(resolved.model().name()).isEqualTo("预算事实");
        assertThat(resolved.model().description()).isEqualTo("统一预算执行口径");
        assertThat(resolved.model().sql()).isSameAs(source.sql());
        assertThat(resolved.model().dependencies()).isEqualTo(source.dependencies());
        assertThat(resolved.model().columns()).extracting("name").contains(field);
        assertThat(resolved.standardBindings()).singleElement().satisfies(binding -> {
            assertThat(binding.fieldName()).isEqualTo(field);
            assertThat(binding.standardElementId()).isEqualTo(standardElementId);
        });
    }

    @Test
    void rejectsAnySemanticFieldReferenceThatIsNotAnInspectedTechnicalColumn() {
        var source = ModelPackageFixtures.validPackage().models().getFirst();
        SemanticOverride override = new SemanticOverride(
            source.dbtUniqueId(),
            null,
            null,
            null,
            null,
            new SemanticGrainOverride("bad", List.of("invented_column")),
            Map.of("invented_column", FieldRole.KEY.name()),
            List.of(),
            List.of(),
            List.of()
        );

        assertThatThrownBy(() -> resolver.resolve(source, override))
            .isInstanceOfSatisfying(ModelSpecImportPreviewContract.ModelSpecImportPreviewException.class, error ->
                assertThat(error.code()).isEqualTo("MODEL_IMPORT_SEMANTIC_OVERRIDE_INVALID")
            );
    }
}
