package com.yuzhi.dts.opmanager.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileJobStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsPlanJobAndEvents() {
        OpManagerProperties properties = new OpManagerProperties();
        properties.setDataDir(tempDir);
        FileJobStore store = new FileJobStore(properties, new ObjectMapper());

        UpgradeJob job = store.createPlanJob("dts-2.2.4-arm64", "2.2.4", "dry-run plan created");

        assertThat(store.find(job.id())).isPresent();
        assertThat(store.find(job.id()).orElseThrow().state()).isEqualTo(UpgradeJobState.PLANNED);
        assertThat(store.events(job.id())).hasSize(1);
        assertThat(store.events(job.id()).getFirst().message()).contains("dry-run plan");
        assertThat(store.list()).extracting(UpgradeJob::id).contains(job.id());
    }
}
