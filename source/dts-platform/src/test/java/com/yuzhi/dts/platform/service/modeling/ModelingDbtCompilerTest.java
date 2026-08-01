package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelingDbtCompilerTest {

    @Test
    void compilesPjmDwdModelIntoTraceableDbtArtifacts() {
        ModelingCompilerContract.CompilerModel model = PjmModelingFixture.projectNode().compilerModel();

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

        ModelingDbtCompiler.CompiledArtifacts artifacts = ModelingDbtCompiler.compile(fixture.compilerModels().get(1));

        assertThat(artifacts.outputDirectory()).isEqualTo("models/dws/project_progress_monthly/v1");
        assertThat(artifacts.files().get("project_progress_monthly.sql")).contains("{{ ref('project_node_detail') }}");
        assertThat(artifacts.files().get("project_progress_monthly.sql")).doesNotContain("from project_node_detail");
    }

    @Test
    void blocksModelsWithoutGrainOrTraceableSource() {
        ModelingCompilerContract.CompilerModel base = PjmModelingFixture.projectNode().compilerModel();
        ModelingCompilerContract.CompilerModel invalid = new ModelingCompilerContract.CompilerModel(
            "invalid",
            ModelingCompilerContract.Layer.DWD,
            base.modelType(),
            base.implementationMode(),
            "invalid_model",
            new ModelingCompilerContract.Grain("", List.of()),
            base.standardBindings(),
            List.of(),
            base.dimensions(),
            base.metrics(),
            base.revision()
        );

        assertThatThrownBy(() -> ModelingDbtCompiler.compile(invalid))
            .isInstanceOf(ModelingDbtCompiler.CompileException.class)
            .hasMessageContaining("GRAIN_REQUIRED");
    }

    @Test
    void compilesOrdinaryImplementationThroughADeterministicEphemeralStg() {
        ModelingCompilerContract.CompilerModel model = PjmModelingFixture.projectNode().compilerModel();
        ModelSpecCompilerProjection.ImplementationProjection implementation = new ModelSpecCompilerProjection.ImplementationProjection(
            model,
            "tenant-a",
            "a".repeat(64),
            3,
            "b".repeat(64),
            "model.pjm.project_node_detail",
            InputMode.PHYSICAL_ASSET,
            List.of(),
            List.of(new FieldMapping("raw_project_no", "project_no")),
            Map.of(
                "targetPhysicalName", "project_node_detail",
                "loadStrategy", "FULL",
                "partitionFields", List.of(),
                "casts", Map.of("project_no", "string"),
                "deduplicateBy", List.of("project_no")
            ),
            "table",
            List.of("project_no"),
            "plan-a",
            typedFields(model)
        );

        ModelingDbtCompiler.CompiledArtifacts first = ModelingDbtCompiler.compile(implementation);
        ModelingDbtCompiler.CompiledArtifacts replay = ModelingDbtCompiler.compile(implementation);

        assertThat(first.outputDirectory()).isEqualTo("models/dwd/project_node_detail/v1/i3");
        assertThat(first.files()).containsOnlyKeys(
            "stg_project_node_detail.sql",
            "project_node_detail.sql",
            "project_node_detail.yml"
        );
        assertThat(first.files()).isEqualTo(replay.files());
        assertThat(first.files().get("stg_project_node_detail.sql"))
            .startsWith("{{ config(materialized='ephemeral') }}")
            .contains("cast(raw_project_no as text) as project_no")
            .doesNotContain("left join")
            .doesNotContain("where is_deleted")
            .contains("row_number() over (partition by project_no order by project_no)");
        assertThat(first.files().get("project_node_detail.sql"))
            .contains("cast(project_no as text) as project_no")
            .contains("{{ ref('stg_project_node_detail') }}");
        assertThat(first.files().get("project_node_detail.yml"))
            .contains("tests:")
            .contains("unique:")
            .contains("column_name: project_no")
            .contains("data_type: text")
            .contains("dts_logical_data_type: \"string\"");
    }

    @Test
    void rejectsUntrustedOrUnsupportedFieldTypeSyntax() {
        ModelingCompilerContract.CompilerModel model =
            PjmModelingFixture.projectNode().compilerModel();
        ModelSpecCompilerProjection.ImplementationProjection implementation =
            new ModelSpecCompilerProjection.ImplementationProjection(
                model,
                "tenant-a",
                "a".repeat(64),
                3,
                "b".repeat(64),
                "model.pjm.project_node_detail",
                InputMode.PHYSICAL_ASSET,
                List.of(),
                List.of(
                    new FieldMapping(
                        "raw_project_no",
                        "project_no"
                    )
                ),
                Map.of(
                    "targetPhysicalName",
                    "project_node_detail",
                    "loadStrategy",
                    "FULL",
                    "partitionFields",
                    List.of()
                ),
                "table",
                List.of("project_no"),
                "plan-a",
                List.of(
                    new ModelSpecCompilerProjection.CompilerField(
                        "project_no",
                        "text); drop table catalog_dataset; --",
                        false
                    )
                )
            );

        assertThatThrownBy(() ->
            ModelingDbtCompiler.compile(implementation)
        )
            .isInstanceOf(
                ModelingDbtCompiler.CompileException.class
            )
            .hasMessageContaining(
                "MODEL_IMPLEMENTATION_FIELD_TYPE_UNSUPPORTED"
            );
    }

    @Test
    void rejectsIncompleteFieldTypeCoverage() {
        ModelingCompilerContract.CompilerModel model =
            PjmModelingFixture.projectNode().compilerModel();
        ModelSpecCompilerProjection.ImplementationProjection implementation =
            new ModelSpecCompilerProjection.ImplementationProjection(
                model,
                "tenant-a",
                "a".repeat(64),
                3,
                "b".repeat(64),
                "model.pjm.project_node_detail",
                InputMode.PHYSICAL_ASSET,
                List.of(),
                List.of(
                    new FieldMapping(
                        "raw_project_no",
                        "project_no"
                    )
                ),
                Map.of(
                    "targetPhysicalName",
                    "project_node_detail",
                    "loadStrategy",
                    "FULL",
                    "partitionFields",
                    List.of()
                ),
                "table",
                List.of("project_no"),
                "plan-a",
                List.of(
                    new ModelSpecCompilerProjection.CompilerField(
                        "project_no",
                        "string",
                        false
                    )
                )
            );

        assertThatThrownBy(() ->
            ModelingDbtCompiler.compile(implementation)
        )
            .isInstanceOf(
                ModelingDbtCompiler.CompileException.class
            )
            .hasMessageContaining(
                "MODEL_IMPLEMENTATION_FIELD_TYPE_COVERAGE_INVALID"
            );
    }

    @Test
    void compilesTheRealUiSettingsThroughTheCanonicalExecutionPlan() {
        ModelingCompilerContract.CompilerModel model = new ModelingCompilerContract.CompilerModel(
            "finance-detail",
            ModelingCompilerContract.Layer.DWD,
            ModelingCompilerContract.ModelType.FACT,
            ModelingCompilerContract.ImplementationMode.DESIGNER_GENERATED,
            "finance_detail",
            new ModelingCompilerContract.Grain("one row per finance event", List.of("project_no")),
            List.of(),
            List.of(new ModelingCompilerContract.SourceRef("TABLE", "ods.finance_event", ModelingCompilerContract.Layer.ODS)),
            List.of("project_no"),
            List.of(),
            1
        );
        ModelSpecCompilerProjection.ImplementationProjection implementation = new ModelSpecCompilerProjection.ImplementationProjection(
            model,
            "tenant-a",
            "a".repeat(64),
            3,
            "b".repeat(64),
            "model.pjm.project_node_detail",
            InputMode.PHYSICAL_ASSET,
            List.of(),
            List.of(new FieldMapping("raw_project_no", "project_no")),
            Map.of(
                "targetPhysicalName", "dwd_project_node",
                "loadStrategy", "INCREMENTAL",
                "partitionFields", List.of(),
                "retentionDays", 365,
                "casts", Map.of("project_no", "string"),
                "deduplicateBy", List.of("project_no")
            ),
            "incremental",
            typedFields(model)
        );

        String sql = ModelingDbtCompiler.compile(implementation).files().get("project_node_detail.sql");

        assertThat(sql)
            .contains("materialized='incremental'")
            .contains("alias='dwd_project_node'")
            .contains("unique_key=['project_no']");
    }

    @Test
    void compilesFullViewWithoutInventingAnIncrementalKey() {
        ModelingCompilerContract.CompilerModel model = PjmModelingFixture.projectNode().compilerModel();
        ModelSpecCompilerProjection.ImplementationProjection implementation =
            new ModelSpecCompilerProjection.ImplementationProjection(
                model,
                "tenant-a",
                "a".repeat(64),
                3,
                "b".repeat(64),
                "model.pjm.project_node_detail",
                InputMode.PHYSICAL_ASSET,
                List.of(),
                List.of(new FieldMapping("raw_project_no", "project_no")),
                Map.of(
                    "targetPhysicalName", "vw_project_node_detail",
                    "loadStrategy", "FULL",
                    "partitionFields", List.of()
                ),
                "view",
                typedFields(model)
            );

        String sql = ModelingDbtCompiler.compile(implementation).files().get("project_node_detail.sql");

        assertThat(sql)
            .contains("materialized='view'")
            .contains("alias='vw_project_node_detail'")
            .doesNotContain("unique_key=");
    }

    @Test
    void projectsPinnedUpstreamModelsAsDbtRefsInTheOrdinaryStgNode() {
        ModelingCompilerContract.CompilerModel base = PjmModelingFixture.projectNode().compilerModel();
        ModelingCompilerContract.CompilerModel derived = new ModelingCompilerContract.CompilerModel(
            base.id(),
            base.layer(),
            base.modelType(),
            base.implementationMode(),
            base.name(),
            base.grain(),
            base.standardBindings(),
            List.of(new ModelingCompilerContract.SourceRef("DBT_MODEL", "upstream_finance_node", ModelingCompilerContract.Layer.DWD)),
            base.dimensions(),
            base.metrics(),
            base.revision()
        );
        ModelSpecCompilerProjection.ImplementationProjection implementation =
            new ModelSpecCompilerProjection.ImplementationProjection(
                derived,
                "tenant-a",
                "a".repeat(64),
                3,
                "b".repeat(64),
                "model.pjm.project_node_detail",
                InputMode.UPSTREAM_MODEL,
                List.of(),
                List.of(new FieldMapping("raw_project_no", "project_no")),
                Map.of(
                    "targetPhysicalName", "project_node_detail",
                    "loadStrategy", "FULL",
                    "partitionFields", List.of()
                ),
                "table",
                typedFields(derived)
            );

        String stg = ModelingDbtCompiler.compile(implementation).files().get("stg_project_node_detail.sql");

        assertThat(stg).contains("from {{ ref('upstream_finance_node') }}");
    }

    @Test
    void generatedImplementationDoesNotRequireAPhysicalSource() {
        ModelingCompilerContract.CompilerModel base = PjmModelingFixture.projectNode().compilerModel();
        ModelingCompilerContract.CompilerModel generated = new ModelingCompilerContract.CompilerModel(
            base.id(),
            base.layer(),
            ModelingCompilerContract.ModelType.DIMENSION,
            ModelingCompilerContract.ImplementationMode.DESIGNER_GENERATED,
            "calendar_day",
            base.grain(),
            base.standardBindings(),
            List.of(),
            base.dimensions(),
            base.metrics(),
            base.revision()
        );

        ModelingDbtCompiler.CompiledArtifacts artifacts = ModelingDbtCompiler.compile(new ModelSpecCompilerProjection.ImplementationProjection(
            generated,
            "tenant-a",
            "a".repeat(64),
            1,
            "b".repeat(64),
            "model.pjm.calendar_day",
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(
                "targetPhysicalName", "calendar_day",
                "loadStrategy", "FULL",
                "partitionFields", List.of()
            ),
            "table",
            typedFields(generated)
        ));

        assertThat(artifacts.files().get("stg_calendar_day.sql")).contains("generated_input");
    }

    @Test
    void usesThePinnedTechnicalNodeNameInsteadOfTheBusinessDisplayName() {
        ModelingCompilerContract.CompilerModel base = PjmModelingFixture.projectNode().compilerModel();
        ModelingCompilerContract.CompilerModel localized = new ModelingCompilerContract.CompilerModel(
            base.id(),
            base.layer(),
            base.modelType(),
            base.implementationMode(),
            "项目节点明细",
            base.grain(),
            base.standardBindings(),
            base.sourceRefs(),
            base.dimensions(),
            base.metrics(),
            base.revision()
        );

        ModelingDbtCompiler.CompiledArtifacts artifacts = ModelingDbtCompiler.compile(new ModelSpecCompilerProjection.ImplementationProjection(
            localized,
            "tenant-a",
            "a".repeat(64),
            1,
            "b".repeat(64),
            "model.plan_123.model_456",
            InputMode.PHYSICAL_ASSET,
            List.of(),
            List.of(new FieldMapping("raw_project_no", "project_no")),
            Map.of(
                "targetPhysicalName", "model_456",
                "loadStrategy", "FULL",
                "partitionFields", List.of()
            ),
            "table",
            typedFields(localized)
        ));

        assertThat(artifacts.outputDirectory()).contains("/model_456/");
        assertThat(artifacts.files()).containsKeys("model_456.sql", "model_456.yml");
        assertThat(artifacts.files().get("model_456.yml")).contains("- name: model_456").doesNotContain("- name: 项目节点明细");
    }

    @Test
    void compilesControlledMultiInputJoinsWithStableSystemAliases() {
        ModelingCompilerContract.CompilerModel model = new ModelingCompilerContract.CompilerModel(
            "model-id",
            ModelingCompilerContract.Layer.DWD,
            ModelingCompilerContract.ModelType.FACT,
            ModelingCompilerContract.ImplementationMode.DESIGNER_GENERATED,
            "客户事实",
            new ModelingCompilerContract.Grain("one row per customer", List.of("customer_id")),
            List.of(),
            List.of(
                new ModelingCompilerContract.SourceRef("TABLE", "ods.customer", ModelingCompilerContract.Layer.ODS),
                new ModelingCompilerContract.SourceRef("TABLE", "ods.customer_status", ModelingCompilerContract.Layer.ODS)
            ),
            List.of("customer_id"),
            List.of(),
            1
        );
        ModelSpecCompilerProjection.ImplementationProjection projection = new ModelSpecCompilerProjection.ImplementationProjection(
            model,
            "tenant-a",
            "a".repeat(64),
            1,
            "b".repeat(64),
            "model.plan_123.model_456",
            InputMode.PHYSICAL_ASSET,
            List.of(),
            List.of(new FieldMapping("src_0.customer_id", "customer_id")),
            Map.of(
                "targetPhysicalName", "model_456",
                "loadStrategy", "FULL",
                "partitionFields", List.of(),
                "joins", List.of(Map.of(
                    "inputIndex", 1,
                    "type", "LEFT",
                    "leftField", "src_0.customer_id",
                    "rightField", "src_1.customer_id"
                ))
            ),
            "table",
            typedFields(model)
        );

        String sql = ModelingDbtCompiler.compile(projection).files().get("stg_model_456.sql");

        assertThat(sql)
            .contains("from source_0 src_0")
            .contains("LEFT JOIN source_1 src_1 on src_0.customer_id = src_1.customer_id");
    }

    @Test
    void rejectsFreeSqlSettingsEvenWhenAProjectionBypassesTheHttpDecoder() {
        ModelingCompilerContract.CompilerModel model = PjmModelingFixture.projectNode().compilerModel();
        ModelSpecCompilerProjection.ImplementationProjection projection = new ModelSpecCompilerProjection.ImplementationProjection(
            model,
            "tenant-a",
            "a".repeat(64),
            3,
            "b".repeat(64),
            "model.pjm.project_node_detail",
            InputMode.PHYSICAL_ASSET,
            List.of(),
            List.of(new FieldMapping("raw_project_no", "project_no")),
            Map.of("filter", "is_deleted = false"),
            "table",
            typedFields(model)
        );

        assertThatThrownBy(() -> ModelingDbtCompiler.compile(projection))
            .isInstanceOf(ModelingDbtCompiler.CompileException.class)
            .hasMessageContaining("IMPLEMENTATION_SETTING_NOT_ALLOWED");
    }

    private static List<ModelSpecCompilerProjection.CompilerField> typedFields(
        ModelingCompilerContract.CompilerModel model
    ) {
        java.util.LinkedHashSet<String> names =
            new java.util.LinkedHashSet<>();
        if (model.grain() != null) {
            names.addAll(model.grain().keys());
        }
        if (model.standardBindings() != null) {
            model
                .standardBindings()
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(
                    ModelingCompilerContract.StandardBinding::fieldName
                )
                .forEach(names::add);
        }
        if (model.dimensions() != null) {
            names.addAll(model.dimensions());
        }
        if (model.metrics() != null) {
            names.addAll(model.metrics());
        }
        return names
            .stream()
            .map(name ->
                new ModelSpecCompilerProjection.CompilerField(
                    name,
                    "string",
                    false
                )
            )
            .toList();
    }
}
