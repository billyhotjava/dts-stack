package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelingDbtCompilerTest {

    @Test
    void compilesPjmDwdModelIntoTraceableDbtArtifacts() {
        ModelingVNextContract.ModelSpec model = PjmModelingFixture.projectNode().modelSpec();

        ModelingDbtCompiler.CompiledArtifacts artifacts = ModelingDbtCompiler.compile(model);

        assertThat(artifacts.files()).containsKeys("project_node_detail.sql", "project_node_detail.yml", "project_node_detail.tests.yml", "project_node_detail.md");
        assertThat(artifacts.files().get("project_node_detail.sql")).contains("{{ source('ods', 'ods_project_subject_domain_v2') }}");
        assertThat(artifacts.files().get("project_node_detail.yml")).contains("std.project.code").contains("not_null");
        assertThat(artifacts.files().get("project_node_detail.tests.yml")).contains("unique").contains("project_no");
        assertThat(artifacts.files().get("project_node_detail.md")).contains("业务粒度");
    }

    @Test
    void usesRefForDbtModelSourcesAndKeepsRevisionInOutputPath() {
        PjmModelingFixture.GoldenPathFixture fixture = PjmModelingFixture.goldenPath();

        ModelingDbtCompiler.CompiledArtifacts artifacts = ModelingDbtCompiler.compile(fixture.modelSpecs().get(1));

        assertThat(artifacts.outputDirectory()).isEqualTo("models/dws/project_progress_monthly/v1");
        assertThat(artifacts.files().get("project_progress_monthly.sql")).contains("{{ ref('project_node_detail') }}");
        assertThat(artifacts.files().get("project_progress_monthly.sql")).doesNotContain("from project_node_detail");
    }

    @Test
    void blocksModelsWithoutGrainOrTraceableSource() {
        ModelingVNextContract.ModelSpec base = PjmModelingFixture.projectNode().modelSpec();
        ModelingVNextContract.ModelSpec invalid = new ModelingVNextContract.ModelSpec(
            "invalid",
            base.objectId(),
            base.processId(),
            ModelingVNextContract.Layer.DWD,
            base.modelType(),
            base.implementationMode(),
            "invalid_model",
            new ModelingVNextContract.Grain("", List.of()),
            base.standardBindings(),
            List.of(),
            base.revision()
        );

        assertThatThrownBy(() -> ModelingDbtCompiler.compile(invalid))
            .isInstanceOf(ModelingDbtCompiler.CompileException.class)
            .hasMessageContaining("GRAIN_REQUIRED");
    }
}
