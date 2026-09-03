package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DbtSourceOnlyContractTest {

    private final DbtModelArchiveInspectService service = new DbtModelArchiveInspectService(new ObjectMapper(), new SafeZipExtractor());

    @Test
    void enforcedCompleteSchemaProducesDeclaredImportableProjection() throws Exception {
        ModelPackage result = inspect(
            Map.of(
                "dbt_project.yml",
                "name: source_only\nmodel-paths: [models]\n",
                "models/orders.sql",
                "select 1 as order_id, 'new' as status",
                "models/orders.yml",
                schemaModel("orders", true, List.of(column("order_id", "bigint"), column("status", "varchar")))
            )
        );

        PackageModel model = model(result, "model.source_only.orders");
        assertThat(model.columns()).extracting("name", "dataType").containsExactly(tuple("order_id", "bigint"), tuple("status", "varchar"));
        assertThat(model.config())
            .containsEntry("structureProvenance", "DECLARED")
            .containsEntry("implementationOwnership", "DBT_MANAGED")
            .containsEntry("sourceContractEnforced", true);
        assertThat(model.conversion().mode()).isEqualTo(ConversionMode.BLOCKED);
        assertThat(model.conversion().reasonCodes()).containsExactly("SOURCE_SEMANTICS_INCOMPLETE");
        assertThat(result.issues())
            .filteredOn(issue -> "SOURCE_SEMANTICS_INCOMPLETE".equals(issue.code()))
            .singleElement()
            .satisfies(issue -> assertThat(issue.recoveryAction()).isEqualTo("COMPLETE_MAPPING"));
        assertThat(new DbtCompatibilityEvaluator().evaluate(result).importProjection())
            .isEqualTo(ImportProjectionCompatibility.IMPORTABLE);
    }

    @Test
    void incompleteSchemaContractsStayStructureOnlyAndCannotBeGuessed() throws Exception {
        LinkedHashMap<String, String> files = baseFiles("schema_gaps");
        files.put("models/not_enforced.sql", "select 1 as id");
        files.put("models/missing_type.sql", "select 1 as id");
        files.put("models/duplicate.sql", "select 1 as id");
        files.put("models/no_columns.sql", "select 1 as id");
        files.put("models/dynamic_name.sql", "select 1 as id");
        files.put("models/dynamic_type.sql", "select 1 as id");
        files.put("models/oversized_type.sql", "select 1 as id");
        files.put(
            "models/schema.yml",
            """
            version: 2
            models:
              - name: not_enforced
                columns:
                  - name: id
                    data_type: bigint
              - name: missing_type
                config:
                  contract:
                    enforced: true
                columns:
                  - name: id
              - name: duplicate
                config:
                  contract:
                    enforced: true
                columns:
                  - name: id
                    data_type: bigint
                  - name: id
                    data_type: varchar
              - name: no_columns
                config:
                  contract:
                    enforced: true
                columns: []
              - name: dynamic_name
                config:
                  contract:
                    enforced: true
                columns:
                  - name: "{{ var('column_name') }}"
                    data_type: bigint
              - name: dynamic_type
                config:
                  contract:
                    enforced: true
                columns:
                  - name: id
                    data_type: "{% if target.name %}bigint{% else %}varchar{% endif %}"
              - name: oversized_type
                config:
                  contract:
                    enforced: true
                columns:
                  - name: id
                    data_type: "__OVERSIZED_TYPE__"
            """
                .replace("__OVERSIZED_TYPE__", "x".repeat(257))
        );

        ModelPackage result = inspect(files);

        assertThat(result.models())
            .allSatisfy(model -> {
                assertThat(model.columns()).isEmpty();
                assertThat(model.conversion().mode()).isEqualTo(ConversionMode.BLOCKED);
                assertThat(model.conversion().reasonCodes()).contains("SOURCE_FIELDS_UNVERIFIED");
            });
        assertThat(result.issues()).filteredOn(issue -> "SOURCE_FIELDS_UNVERIFIED".equals(issue.code())).hasSize(7);
        assertThat(new DbtCompatibilityEvaluator().evaluate(result).importProjection())
            .isEqualTo(ImportProjectionCompatibility.STRUCTURE_VIEW_ONLY);
    }

    @Test
    void dynamicAndMacroDependenciesBlockTheirCompleteDownstreamClosure() throws Exception {
        LinkedHashMap<String, String> files = baseFiles("dependency_guards");
        files.put("models/dynamic_base.sql", "select * from {{ ref(var('base_name')) }}");
        files.put("models/macro_base.sql", "select {{ hidden_dependency() }} as id");
        files.put("models/dynamic_child.sql", "select * from {{ ref('dynamic_base') }}");
        files.put("models/macro_child.sql", "select * from {{ ref('macro_base') }}");
        files.put("models/independent.sql", "select 1 as id");
        files.put(
            "models/schema.yml",
            schemaModels("dynamic_base", "macro_base", "dynamic_child", "macro_child", "independent")
        );

        ModelPackage result = inspect(files);

        assertBlocked(result, "model.dependency_guards.dynamic_base", "SOURCE_DEPENDENCY_DYNAMIC");
        assertBlocked(result, "model.dependency_guards.dynamic_child", "SOURCE_DEPENDENCY_DYNAMIC");
        assertBlocked(result, "model.dependency_guards.macro_base", "SOURCE_MACRO_DEPENDENCY_UNVERIFIED");
        assertBlocked(result, "model.dependency_guards.macro_child", "SOURCE_MACRO_DEPENDENCY_UNVERIFIED");
        assertThat(model(result, "model.dependency_guards.independent").conversion().reasonCodes())
            .containsExactly("SOURCE_SEMANTICS_INCOMPLETE");
        assertThat(result.issues())
            .filteredOn(issue ->
                "SOURCE_DEPENDENCY_DYNAMIC".equals(issue.code()) || "SOURCE_MACRO_DEPENDENCY_UNVERIFIED".equals(issue.code())
            )
            .extracting(issue -> issue.modelUniqueId())
            .containsExactlyInAnyOrder(
                "model.dependency_guards.dynamic_base",
                "model.dependency_guards.dynamic_child",
                "model.dependency_guards.macro_base",
                "model.dependency_guards.macro_child"
            );
    }

    @Test
    void acceptsOnlyFullyConsumedLiteralJinjaExpressionsAndRestrictedConfig() throws Exception {
        LinkedHashMap<String, String> files = baseFiles("jinja_whitelist");
        files.put("models/independent.sql", "select 1 as id");
        files.put("models/safe.sql", "{{ config(materialized='table', tags=['dwd']) }} select * from {{ ref('independent') }}");
        files.put("models/target_name.sql", "select {{ target.name }} as id");
        files.put("models/subscript.sql", "select {{ context['model'] }} as id");
        files.put("models/ternary.sql", "select * from {{ ref('independent') if execute else ref('missing') }}");
        files.put("models/unconsumed.sql", "select * from {{ ref('independent') + ref('missing') }}");
        files.put("models/parenthesized.sql", "select * from {{ (ref)('independent') }}");
        files.put("models/dynamic_source.sql", "select * from {{ source('erp', var('table_name')) }}");
        files.put(
            "models/literal_config.sql",
            "{{ config(materialized='incremental', alias='renamed', unique_key=['id'], meta={'revision':1,'owner':'dts'}) }} select 1 as id"
        );
        files.put("models/unknown_config.sql", "{{ config(database='runtime') }} select 1 as id");
        files.put("models/hook_config.sql", "{{ config(pre_hook='delete from audit') }} select 1 as id");
        files.put(
            "models/schema.yml",
            schemaModels(
                "independent",
                "safe",
                "target_name",
                "subscript",
                "ternary",
                "unconsumed",
                "parenthesized",
                "dynamic_source",
                "literal_config",
                "unknown_config",
                "hook_config"
            )
        );

        ModelPackage result = inspect(files);

        assertThat(model(result, "model.jinja_whitelist.safe").conversion().reasonCodes())
            .containsExactly("SOURCE_SEMANTICS_INCOMPLETE");
        assertThat(model(result, "model.jinja_whitelist.literal_config").conversion().reasonCodes())
            .containsExactly("SOURCE_SEMANTICS_INCOMPLETE");
        for (
            String name : List.of(
                "target_name",
                "subscript",
                "ternary",
                "unconsumed",
                "parenthesized",
                "dynamic_source",
                "unknown_config",
                "hook_config"
            )
        ) {
            assertBlocked(result, "model.jinja_whitelist." + name, "SOURCE_DEPENDENCY_DYNAMIC");
        }
    }

    @Test
    void doesNotPropagateMissingFieldContractsFromTechnicalEphemeralNodes() throws Exception {
        LinkedHashMap<String, String> files = baseFiles("compiler_bundle");
        files.put(
            "models/stg_orders.sql",
            "{{ config(materialized='ephemeral') }} select raw_id as id from {{ source('public', 'orders') }}"
        );
        files.put(
            "models/orders.sql",
            "{{ config(materialized='table', alias='dwd_orders', meta={'revision':1,'owner':'dts'}) }} select id from {{ ref('stg_orders') }}"
        );
        files.put(
            "models/orders.yml",
            schemaModel("orders", true, List.of(column("id", "bigint")))
        );

        ModelPackage result = inspect(files);

        assertThat(result.technicalNodes())
            .filteredOn(node -> "model.compiler_bundle.stg_orders".equals(node.dbtUniqueId()))
            .singleElement()
            .satisfies(node -> assertThat(node.conversion().mode()).isEqualTo(ConversionMode.TECHNICAL_ONLY));
        assertThat(model(result, "model.compiler_bundle.orders").conversion().reasonCodes())
            .containsExactly("SOURCE_SEMANTICS_INCOMPLETE");
    }

    @Test
    void adoptsTrustedSchemaAndProjectConfigsAndIncludesThemInFingerprint() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
            "dbt_project.yml",
            """
            name: trusted_config
            model-paths: [models]
            models:
              trusted_config:
                staging:
                  +tags: [stg]
                marts:
                  +materialized: table
            """
        );
        files.put("models/staging/from_project.sql", "select 1 as id");
        files.put("models/marts/from_schema.sql", "select 1 as id");
        files.put("models/marts/from_inline.sql", "{{ config(tags=['stg']) }} select 1 as id");
        files.put(
            "models/schema.yml",
            """
            version: 2
            models:
              - name: from_project
                config:
                  contract:
                    enforced: true
                columns:
                  - name: id
                    data_type: bigint
              - name: from_schema
                config:
                  contract:
                    enforced: true
                  materialized: ephemeral
                columns:
                  - name: id
                    data_type: bigint
              - name: from_inline
                config:
                  contract:
                    enforced: true
                columns:
                  - name: id
                    data_type: bigint
            """
        );

        ModelPackage result = inspect(files);

        assertThat(result.technicalNodes())
            .extracting(node -> node.dbtUniqueId())
            .containsExactlyInAnyOrder(
                "model.trusted_config.from_project",
                "model.trusted_config.from_schema",
                "model.trusted_config.from_inline"
            );
        assertThat(result.technicalNodes())
            .allSatisfy(node -> assertThat(node.conversion().mode()).isEqualTo(ConversionMode.TECHNICAL_ONLY));

        LinkedHashMap<String, String> changed = new LinkedHashMap<>(files);
        changed.put("dbt_project.yml", files.get("dbt_project.yml").replace("+materialized: table", "+materialized: view"));
        ModelPackage changedResult = inspect(changed);
        assertThat(changedResult.packageId()).isNotEqualTo(result.packageId());
        assertThat(changedResult.packageChecksum()).isNotEqualTo(result.packageChecksum());
    }

    @Test
    void projectVarsDispatchHooksAndUnknownModelConfigsFailClosed() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
            "dbt_project.yml",
            """
            name: unsafe_project_config
            model-paths: [models]
            vars:
              relation_name: runtime_table
            dispatch:
              - macro_namespace: dbt_utils
                search_order: [unsafe_project_config, dbt_utils]
            on-run-start: ["{{ custom_hook() }}"]
            models:
              unsafe_project_config:
                +unknown_structural_option: true
            """
        );
        files.put("models/base.sql", "select 1 as id");
        files.put("models/child.sql", "select * from {{ ref('base') }}");
        files.put("models/schema.yml", schemaModels("base", "child"));

        ModelPackage result = inspect(files);

        assertBlocked(result, "model.unsafe_project_config.base", "SOURCE_DEPENDENCY_DYNAMIC");
        assertBlocked(result, "model.unsafe_project_config.child", "SOURCE_DEPENDENCY_DYNAMIC");
    }

    @Test
    void missingPackageAndControlFlowFailClosedWhileEphemeralStaysTechnicalOnly() throws Exception {
        LinkedHashMap<String, String> files = baseFiles("complex_guards");
        files.put("models/package_base.sql", "select * from {{ ref('external_pkg', 'orders') }}");
        files.put("models/control_base.sql", "{% if execute %} select 1 as id {% else %} select 2 as id {% endif %}");
        files.put("models/dispatch_base.sql", "select {{ adapter.dispatch('resolve_relation')() }} as id");
        files.put("models/package_child.sql", "select * from {{ ref('package_base') }}");
        files.put("models/transient.sql", "{{ config(materialized='ephemeral') }} select 1 as id");
        files.put(
            "models/schema.yml",
            schemaModels("package_base", "control_base", "dispatch_base", "package_child", "transient")
        );

        ModelPackage result = inspect(files);

        assertBlocked(result, "model.complex_guards.package_base", "SOURCE_PACKAGE_MISSING");
        assertBlocked(result, "model.complex_guards.package_child", "SOURCE_PACKAGE_MISSING");
        assertBlocked(result, "model.complex_guards.control_base", "SOURCE_DEPENDENCY_DYNAMIC");
        assertBlocked(result, "model.complex_guards.dispatch_base", "SOURCE_MACRO_DEPENDENCY_UNVERIFIED");
        assertThat(result.technicalNodes())
            .filteredOn(node -> "model.complex_guards.transient".equals(node.dbtUniqueId()))
            .singleElement()
            .satisfies(node -> assertThat(node.conversion().mode()).isEqualTo(ConversionMode.TECHNICAL_ONLY));
    }

    private static LinkedHashMap<String, String> baseFiles(String projectName) {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", "name: " + projectName + "\nmodel-paths: [models]\n");
        return files;
    }

    private ModelPackage inspect(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return service.inspect(new MockMultipartFile("archive", "source-only.zip", "application/zip", bytes.toByteArray()));
    }

    private static PackageModel model(ModelPackage modelPackage, String uniqueId) {
        return modelPackage.models().stream().filter(model -> uniqueId.equals(model.dbtUniqueId())).findFirst().orElseThrow();
    }

    private static void assertBlocked(ModelPackage result, String uniqueId, String reason) {
        assertThat(model(result, uniqueId).conversion().mode()).isEqualTo(ConversionMode.BLOCKED);
        assertThat(model(result, uniqueId).conversion().reasonCodes()).contains(reason);
    }

    private static String schemaModels(String... names) {
        StringBuilder yaml = new StringBuilder("version: 2\nmodels:\n");
        for (String name : names) {
            yaml.append(schemaModelItem(name));
        }
        return yaml.toString();
    }

    private static String schemaModel(String name, boolean enforced, List<String> columns) {
        StringBuilder yaml = new StringBuilder("version: 2\nmodels:\n  - name: ").append(name).append('\n');
        if (enforced) {
            yaml.append("    config:\n      contract:\n        enforced: true\n");
        }
        yaml.append("    columns:\n");
        columns.forEach(yaml::append);
        return yaml.toString();
    }

    private static String schemaModelItem(String name) {
        return (
            "  - name: " +
            name +
            "\n    config:\n      contract:\n        enforced: true\n    columns:\n      - name: id\n        data_type: bigint\n"
        );
    }

    private static String column(String name, String dataType) {
        return "      - name: " + name + "\n        data_type: " + dataType + "\n";
    }
}
