package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModelingLifecycleSourceContractTest {

    @Test
    void publicationDoesNotFabricateAPassedTestForANewRevision() throws Exception {
        String source = source("ModelLifecycleService.java");

        assertThat(source)
            .doesNotContain("current.revision() + 1")
            .doesNotContain("Promoted from approved revision")
            .contains("testEvidence.verify");
    }

    private static String source(String name) throws Exception {
        Path module = Path.of("src/main/java/com/yuzhi/dts/platform/service/modeling", name);
        Path repository = Path.of("source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling", name);
        return Files.readString(Files.exists(module) ? module : repository, StandardCharsets.UTF_8);
    }
}
