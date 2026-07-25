package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SqlArtifact;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TechnicalNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelSpecImportApplyPayloadCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelSpecImportApplyPayloadCodec codec = new ModelSpecImportApplyPayloadCodec(objectMapper);

    @Test
    void createsSanitizedPayloadWithDeterministicChecksums() throws Exception {
        ModelPackage modelPackage = packageWithUntrustedConfig();

        var first = codec.sanitize(modelPackage);
        var second = codec.sanitize(modelPackage);
        JsonNode payload = objectMapper.readTree(first.json());

        assertThat(first.checksum()).isEqualTo(second.checksum());
        assertThat(codec.isValid(first.json(), first.checksum())).isTrue();
        assertThat(payload.path("schemaVersion").asText()).isEqualTo("dts.model-import.apply-payload/v1");
        assertThat(payload.at("/models/0/sql/effectiveSql").asText()).contains("select budget_id");
        assertThat(payload.at("/models/0/executionSettings/materialized").asText()).isEqualTo("table");
        assertThat(payload.at("/models/0/executionSettings/password").isMissingNode()).isTrue();
        assertThat(payload.at("/models/0/configChecksum").asText()).hasSize(64);
        assertThat(payload.at("/models/0/schemaChecksum").asText()).hasSize(64);
        assertThat(payload.at("/models/0/dependencyChecksum").asText()).hasSize(64);
        assertThat(payload.at("/technicalNodes/0/sql/effectiveSql").asText()).isEqualTo("select * from staging_budget");
        assertThat(first.json()).doesNotContain("rawSql", "compiledSql", "password", "secret-value", "post-hook");
    }

    @Test
    void rejectsPayloadChecksumTampering() throws Exception {
        var payload = codec.sanitize(packageWithUntrustedConfig());
        JsonNode modified = objectMapper.readTree(payload.json());
        modified.at("/models/0/sql").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) modified.at("/models/0/sql")).put("effectiveSql", "select tampered");

        assertThat(codec.isValid(objectMapper.writeValueAsString(modified), payload.checksum())).isFalse();
    }

    private ModelPackage packageWithUntrustedConfig() {
        ModelPackage base = ModelPackageFixtures.validPackage();
        PackageModel original = base.models().getFirst();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("materialized", "table");
        config.put("password", "secret-value");
        config.put("post-hook", "delete from audit_log");
        PackageModel model = new PackageModel(
            original.dbtUniqueId(),
            original.name(),
            original.description(),
            original.resourcePath(),
            original.sql(),
            original.materialization(),
            config,
            original.tags(),
            original.columns(),
            original.tests(),
            original.dependencies(),
            original.semantics(),
            original.conversion()
        );
        SqlArtifact sql = new SqlArtifact(
            "select * from raw_staging_budget",
            ModelPackageChecksum.sha256Text("select * from raw_staging_budget"),
            "select * from compiled_staging_budget",
            ModelPackageChecksum.sha256Text("select * from compiled_staging_budget"),
            "select * from staging_budget",
            ModelPackageChecksum.sha256Text("select * from staging_budget"),
            "PROJECT_FILE"
        );
        TechnicalNode technicalNode = new TechnicalNode(
            "model.pjm.staging_budget",
            "staging_budget",
            "model",
            "models/stg/staging_budget.sql",
            sql,
            config,
            List.of("source.pjm.budget"),
            List.of("staging"),
            original.conversion()
        );
        return ModelPackageChecksum.withChecksum(
            new ModelPackage(
                base.schemaVersion(),
                base.packageId(),
                null,
                base.dbt(),
                base.defaults(),
                base.sources(),
                List.of(technicalNode),
                List.of(model),
                base.issues()
            )
        );
    }
}
