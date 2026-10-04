package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DtsModelPackageGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path directory;

    @Test
    void writesOneVersionedJsonFileAndValidateOnlyDoesNotWrite() throws Exception {
        Path manifest = directory.resolve("manifest.json");
        Path overrides = directory.resolve("semantic-overrides.json");
        Path output = directory.resolve("dts-model-package.json");
        Path validateOnlyOutput = directory.resolve("must-not-exist.json");
        Files.writeString(
            manifest,
            """
            {
              "metadata":{"dbt_schema_version":"https://schemas.getdbt.com/dbt/manifest/v12.json","dbt_version":"1.8.0","project_name":"pjm","adapter_type":"postgres"},
              "sources":{},
              "nodes":{"model.pjm.budget":{"unique_id":"model.pjm.budget","name":"budget","resource_type":"model","original_file_path":"models/budget.sql","raw_code":"select id from source_budget","config":{"materialized":"table"},"depends_on":{"nodes":[]},"columns":{"id":{"name":"id"}}}}
            }
            """
        );
        Files.writeString(
            overrides,
            """
            {
              "model.pjm.budget":{
                "modelType":"FACT","layer":"DWD",
                "grain":{"statement":"每行一条预算","keys":["id"]},
                "domainCode":"PROJECT_MANAGEMENT",
                "sourceRefs":[{"kind":"TABLE","ref":"source_budget","layer":"ODS"}],
                "consumptionScenarios":["预算分析"],
                "fieldRoles":{"id":"BUSINESS_KEY"},
                "overrideSource":"semantic-overrides.json",
                "technicalOnly":false
              }
            }
            """
        );

        DtsModelPackageGenerator generator = new DtsModelPackageGenerator(objectMapper);
        int generated = generator.run(
            new String[] {
                "--manifest", manifest.toString(),
                "--overrides", overrides.toString(),
                "--package-id", "pjm-budget-v1",
                "--output", output.toString()
            },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream())
        );
        int validated = generator.run(
            new String[] {
                "--manifest", manifest.toString(),
                "--overrides", overrides.toString(),
                "--package-id", "pjm-budget-v1",
                "--output", validateOnlyOutput.toString(),
                "--validate-only"
            },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream())
        );

        assertThat(generated).isZero();
        assertThat(validated).isZero();
        assertThat(output).exists().isRegularFile();
        assertThat(validateOnlyOutput).doesNotExist();
        JsonNode packageJson = objectMapper.readTree(output.toFile());
        assertThat(packageJson.path("schemaVersion").asText()).isEqualTo("dts.model-package/v1");
        assertThat(packageJson.path("packageChecksum").asText()).hasSize(64);
    }
}
