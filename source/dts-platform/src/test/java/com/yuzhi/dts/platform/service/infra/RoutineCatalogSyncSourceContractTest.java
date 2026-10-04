package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutineCatalogSyncSourceContractTest {

    @Test
    void routineSynchronizationCannotReachPhysicalColumnOrDatasetDeletion() throws IOException {
        for (String service : List.of("PostgresCatalogSyncService", "JdbcCatalogSyncService", "InceptorCatalogSyncService")) {
            Path sourcePath = Path.of(
                "src/main/java/com/yuzhi/dts/platform/service/infra/" + service + ".java"
            );
            String source = Files.readString(sourcePath);
            int explicitPurgeBoundary = source.indexOf("private void purgeDataset");

            assertThat(explicitPurgeBoundary).as(service + " explicit purge boundary").isPositive();
            assertThat(source.substring(0, explicitPurgeBoundary))
                .as(service + " routine path")
                .doesNotContain("columnRepository.deleteByTable")
                .doesNotContain("purgeDataset(dataset)");
        }
    }
}
