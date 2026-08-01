package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyModelingRuntimeRetirementContractTest {

    private static final List<String> RETIRED_RUNTIME_FILES = List.of(
        "domain/modeling/ModelingPlan.java",
        "domain/modeling/ModelingSqlModel.java",
        "repository/modeling/ModelingPlanRepository.java",
        "repository/modeling/ModelingSqlModelRepository.java",
        "service/analytics/SemanticContractPublishService.java",
        "service/modeling/ModelFileService.java",
        "service/modeling/ModelGenerationService.java",
        "service/modeling/ModelingSqlModelService.java",
        "web/rest/ModelingSqlModelResource.java"
    );

    @Test
    void retiredRuntimeChainIsPhysicallyAbsent() {
        RETIRED_RUNTIME_FILES.forEach(relativePath ->
            assertThat(mainJava(relativePath))
                .as("retired runtime file %s", relativePath)
                .doesNotExist()
        );
    }

    @Test
    void publicCatalogAndOperationalBackfillExposeOnlyCanonicalModelIdentity() throws Exception {
        assertThat(readMain("service/catalog/CatalogAssetType.java"))
            .doesNotContain("MODELING_SQL_MODEL")
            .doesNotContain("MODELING_PLAN");
        assertThat(readMain("web/rest/capability/PlatformCapabilityResource.java"))
            .doesNotContain("MODELING_SQL_MODEL");
        assertThat(readResource("scripts/backfill-code-asset-lifecycle.sql"))
            .doesNotContain("modeling_sql_model")
            .doesNotContain("MODELING_SQL_MODEL");
    }

    private Path mainJava(String relativePath) {
        Path module = Path.of("src/main/java/com/yuzhi/dts/platform", relativePath);
        Path repository = Path.of("source/dts-platform/src/main/java/com/yuzhi/dts/platform", relativePath);
        return Files.exists(Path.of("src/main/java")) ? module : repository;
    }

    private String readMain(String relativePath) throws Exception {
        return Files.readString(mainJava(relativePath), StandardCharsets.UTF_8);
    }

    private String readResource(String relativePath) throws Exception {
        Path module = Path.of("src/main/resources", relativePath);
        Path repository = Path.of("source/dts-platform/src/main/resources", relativePath);
        return Files.readString(Files.exists(module) ? module : repository, StandardCharsets.UTF_8);
    }
}
