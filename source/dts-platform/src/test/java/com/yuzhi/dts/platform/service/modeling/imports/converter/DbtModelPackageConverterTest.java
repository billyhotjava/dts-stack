package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Defaults;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtModelPackageConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DbtModelPackageConverter converter = new DbtModelPackageConverter(objectMapper, new ModelConversionClassifier());

    @TempDir
    Path projectRoot;

    @Test
    void preservesExplicitTechnicalNodeAndItsDownstreamDependency() throws Exception {
        Files.createDirectories(projectRoot.resolve("models/stg"));
        Files.createDirectories(projectRoot.resolve("models/dwd"));
        Files.writeString(projectRoot.resolve("models/stg/stg_budget.sql"), "select id, amount from raw_budget");
        Files.writeString(
            projectRoot.resolve("models/dwd/budget.sql"),
            "select id, sum(amount) as amount from {{ ref('stg_budget') }} group by id"
        );

        var result = converter.convert(new DbtModelPackageConverter.ConversionRequest(
            "pjm-budget-v1",
            manifest(),
            null,
            projectRoot,
            Map.of(
                "model.pjm.stg_budget",
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
                    "test override",
                    true
                ),
                "model.pjm.budget",
                ModelPackageFixtures.completeSemantics()
            ),
            Set.of("model.pjm.budget"),
            new Defaults(null, null)
        ));

        assertThat(result.technicalNodes())
            .extracting(node -> node.dbtUniqueId() + ":" + node.conversion().mode())
            .contains("model.pjm.stg_budget:TECHNICAL_ONLY", "test.pjm.not_null_budget:TECHNICAL_ONLY");
        assertThat(result.models()).singleElement().satisfies(model -> {
            assertThat(model.dependencies()).containsExactly("model.pjm.stg_budget");
            assertThat(model.conversion().mode()).isEqualTo(ConversionMode.DBT_BACKED);
            assertThat(model.sql().effectiveSource()).isEqualTo("PROJECT_FILE");
        });
        assertThat(result.packageChecksum()).hasSize(64);
    }

    @Test
    void missingCatalogIsVisibleAndMissingSemanticsIsBlockedWithoutNameInference() {
        var result = converter.convert(new DbtModelPackageConverter.ConversionRequest(
            "pjm-budget-v1",
            manifest(),
            null,
            null,
            Map.of(),
            Set.of("model.pjm.budget"),
            new Defaults(null, null)
        ));

        assertThat(result.issues()).extracting(issue -> issue.code()).contains("CATALOG_MISSING");
        assertThat(result.models())
            .filteredOn(model -> model.dbtUniqueId().equals("model.pjm.budget"))
            .singleElement()
            .satisfies(model -> {
                assertThat(model.conversion().mode()).isEqualTo(ConversionMode.BLOCKED);
                assertThat(model.conversion().reasonCodes()).contains("MISSING_MODEL_TYPE", "MISSING_GRAIN");
            });
    }

    @Test
    void blocksCanonicalDimensionWithoutStableDefinitionCode() {
        var fact = ModelPackageFixtures.completeSemantics();
        var dimension = new com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata(
            "DIMENSION",
            "DWD",
            fact.grain(),
            null,
            null,
            fact.domainCode(),
            java.util.List.of(),
            java.util.List.of(),
            fact.fieldRoles(),
            "STATIC_DBT_SEED_SQL",
            null,
            "test override",
            false
        );

        var result = converter.convert(new DbtModelPackageConverter.ConversionRequest(
            "pjm-budget-v1",
            manifest(),
            null,
            projectRoot,
            Map.of(
                "model.pjm.budget",
                dimension,
                "model.pjm.stg_budget",
                dimension
            ),
            Set.of("model.pjm.budget"),
            new Defaults(null, null)
        ));

        assertThat(result.models())
            .filteredOn(model -> model.dbtUniqueId().equals("model.pjm.budget"))
            .singleElement()
            .satisfies(model -> {
                assertThat(model.conversion().mode()).isEqualTo(ConversionMode.BLOCKED);
                assertThat(model.conversion().reasonCodes()).contains("MISSING_DIMENSION_DEFINITION_CODE");
            });
        assertThat(result.technicalNodes())
            .filteredOn(model -> model.dbtUniqueId().equals("model.pjm.stg_budget"))
            .singleElement()
            .satisfies(model -> {
                assertThat(model.conversion().mode()).isEqualTo(ConversionMode.TECHNICAL_ONLY);
                assertThat(model.conversion().reasonCodes()).doesNotContain("MISSING_DIMENSION_DEFINITION_CODE");
            });
        assertThat(result.issues())
            .filteredOn(issue -> "MODEL_PACKAGE_DIMENSION_DEFINITION_CODE_REQUIRED".equals(issue.code()))
            .extracting(issue -> issue.modelUniqueId())
            .containsExactly("model.pjm.budget");
    }

    @Test
    void catalogManifestColumnMismatchProducesIssuesWithoutSilentOverwrite() throws Exception {
        ObjectNode catalog = (ObjectNode) objectMapper.readTree(
            """
            {
              "nodes": {
                "model.pjm.budget": {
                  "columns": {
                    "id": {"name": "id", "type": "bigint"},
                    "catalog_only": {"name": "catalog_only", "type": "text"}
                  }
                }
              }
            }
            """
        );

        var result = converter.convert(new DbtModelPackageConverter.ConversionRequest(
            "pjm-budget-v1",
            manifest(),
            catalog,
            null,
            Map.of("model.pjm.budget", ModelPackageFixtures.completeSemantics()),
            Set.of("model.pjm.budget"),
            new Defaults(null, null)
        ));

        assertThat(result.issues())
            .extracting(issue -> issue.code())
            .contains("CATALOG_COLUMN_NOT_IN_MANIFEST", "CATALOG_COLUMN_MISSING");
        assertThat(result.models().getFirst().columns())
            .anySatisfy(column -> {
                assertThat(column.name()).isEqualTo("id");
                assertThat(column.dataType()).isEqualTo("bigint");
            });
    }

    @Test
    void preservesCatalogOrdinalColumnOrderAndAppendsManifestOnlyColumns() throws Exception {
        ObjectNode orderedManifest = manifestWithOrderedBudgetColumns(
            "manifest_only",
            "second_column",
            "first_column",
            "third_column"
        );
        ObjectNode catalog = (ObjectNode) objectMapper.readTree(
            """
            {
              "nodes": {
                "model.pjm.budget": {
                  "columns": {
                    "first_column": {"name": "first_column", "type": "text", "index": 3},
                    "third_column": {"name": "third_column", "type": "text", "index": 1},
                    "second_column": {"name": "second_column", "type": "text", "index": 2}
                  }
                }
              }
            }
            """
        );

        var result = converter.convert(new DbtModelPackageConverter.ConversionRequest(
            "pjm-budget-v1",
            orderedManifest,
            catalog,
            null,
            Map.of("model.pjm.budget", ModelPackageFixtures.completeSemantics()),
            Set.of("model.pjm.budget"),
            new Defaults(null, null)
        ));

        assertThat(result.models().getFirst().columns())
            .extracting(column -> column.name())
            .containsExactly("third_column", "second_column", "first_column", "manifest_only");
    }

    @Test
    void preservesManifestDeclarationOrderWhenCatalogIsMissing() {
        ObjectNode orderedManifest = manifestWithOrderedBudgetColumns(
            "third_column",
            "first_column",
            "second_column"
        );

        var result = converter.convert(new DbtModelPackageConverter.ConversionRequest(
            "pjm-budget-v1",
            orderedManifest,
            null,
            null,
            Map.of("model.pjm.budget", ModelPackageFixtures.completeSemantics()),
            Set.of("model.pjm.budget"),
            new Defaults(null, null)
        ));

        assertThat(result.models().getFirst().columns())
            .extracting(column -> column.name())
            .containsExactly("third_column", "first_column", "second_column");
    }

    @Test
    void preservesExplicitDimensionAttributeCodeFromManifestColumnMetadata() {
        ObjectNode manifest = (ObjectNode) manifest();
        ObjectNode column = (ObjectNode) manifest
            .path("nodes")
            .path("model.pjm.budget")
            .path("columns")
            .path("id");
        column.putObject("meta").putObject("dts").put("dimensionAttributeCode", "BUDGET_ID");

        var result = converter.convert(new DbtModelPackageConverter.ConversionRequest(
            "pjm-budget-v1",
            manifest,
            null,
            null,
            Map.of("model.pjm.budget", ModelPackageFixtures.completeSemantics()),
            Set.of("model.pjm.budget"),
            new Defaults(null, null)
        ));

        assertThat(result.models().getFirst().columns())
            .filteredOn(item -> "id".equals(item.name()))
            .singleElement()
            .extracting("dimensionAttributeCode")
            .isEqualTo("BUDGET_ID");
    }

    private ObjectNode manifestWithOrderedBudgetColumns(String... names) {
        ObjectNode result = (ObjectNode) manifest();
        ObjectNode columns = objectMapper.createObjectNode();
        for (String name : names) {
            columns.putObject(name).put("name", name).put("data_type", "text");
        }
        ((ObjectNode) result.path("nodes").path("model.pjm.budget")).set("columns", columns);
        return result;
    }

    private JsonNode manifest() {
        return objectMapper.valueToTree(Map.of(
            "metadata",
            Map.of(
                "dbt_schema_version",
                "https://schemas.getdbt.com/dbt/manifest/v12.json",
                "dbt_version",
                "1.8.0",
                "project_name",
                "pjm",
                "adapter_type",
                "postgres"
            ),
            "sources",
            Map.of(
                "source.pjm.raw_budget",
                Map.of(
                    "unique_id",
                    "source.pjm.raw_budget",
                    "name",
                    "raw_budget",
                    "original_file_path",
                    "models/sources.yml",
                    "columns",
                    Map.of("id", Map.of("name", "id"))
                )
            ),
            "nodes",
            Map.of(
                "model.pjm.stg_budget",
                Map.of(
                    "unique_id",
                    "model.pjm.stg_budget",
                    "name",
                    "stg_budget",
                    "resource_type",
                    "model",
                    "original_file_path",
                    "models/stg/stg_budget.sql",
                    "raw_code",
                    "select id, amount from raw_budget",
                    "config",
                    Map.of("materialized", "view"),
                    "depends_on",
                    Map.of("nodes", java.util.List.of("source.pjm.raw_budget"), "macros", java.util.List.of()),
                    "columns",
                    Map.of("id", Map.of("name", "id"), "amount", Map.of("name", "amount"))
                ),
                "model.pjm.budget",
                Map.of(
                    "unique_id",
                    "model.pjm.budget",
                    "name",
                    "budget",
                    "resource_type",
                    "model",
                    "original_file_path",
                    "models/dwd/budget.sql",
                    "raw_code",
                    "select id, sum(amount) from stg_budget group by id",
                    "compiled_code",
                    "select id, sum(amount) from analytics.stg_budget group by id",
                    "config",
                    Map.of("materialized", "table"),
                    "depends_on",
                    Map.of("nodes", java.util.List.of("model.pjm.stg_budget"), "macros", java.util.List.of()),
                    "columns",
                    Map.of("id", Map.of("name", "id"), "manifest_only", Map.of("name", "manifest_only"))
                ),
                "test.pjm.not_null_budget",
                Map.of(
                    "unique_id",
                    "test.pjm.not_null_budget",
                    "name",
                    "not_null_budget",
                    "resource_type",
                    "test",
                    "column_name",
                    "id",
                    "depends_on",
                    Map.of("nodes", java.util.List.of("model.pjm.budget"), "macros", java.util.List.of())
                )
            )
        ));
    }
}
