package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStep;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ModelingLifecycleSourceContractTest {

    @Test
    void legacyBridgeCannotWriteNullArtifactOwnersOrReuseRowVersionAsModelRevision() throws Exception {
        String source = source("ModelingVNextApplicationService.java");

        assertThat(source)
            .doesNotContain("values (?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .doesNotContain("on conflict (project_key, dbt_unique_id)")
            .contains("on conflict (model_spec_id, revision, implementation_revision, artifact_key)")
            .contains("model_spec_id, revision, implementation_revision,")
            .contains("project_key, dbt_unique_id, node_kind, artifact_type")
            .contains("set status = excluded.status")
            .contains("coalesce(i.implementation_revision, 1) as implementation_revision")
            .contains("model_revision")
            .contains("model_checksum")
            .contains("repair_path")
            .doesNotContain("rs.getInt(\"version\"), rs.getString(\"status\")");
    }

    @Test
    void publicationDoesNotFabricateAPassedTestForANewRevision() throws Exception {
        String source = source("ModelLifecycleService.java");

        assertThat(source)
            .doesNotContain("current.revision() + 1")
            .doesNotContain("Promoted from approved revision")
            .contains("testEvidence.verify")
            .contains("startRegistration")
            .contains("completeRegistration");
    }

    @Test
    void releaseRegistrationLedgerWritesOwnTheirTransactionBoundary() throws Exception {
        assertThat(transactional("startRegistration", UUID.class, RegistrationStep.class, Instant.class).propagation())
            .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(
            transactional(
                "completeRegistration",
                UUID.class,
                RegistrationStep.class,
                String.class,
                String.class,
                String.class,
                Instant.class
            ).propagation()
        ).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional("updateEventStatus", UUID.class, String.class, Map.class).propagation())
            .isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static Transactional transactional(String name, Class<?>... parameterTypes) throws Exception {
        return AnnotatedElementUtils.findMergedAnnotation(
            ModelLifecycleRepository.class.getMethod(name, parameterTypes),
            Transactional.class
        );
    }

    private static String source(String name) throws Exception {
        Path module = Path.of("src/main/java/com/yuzhi/dts/platform/service/modeling", name);
        Path repository = Path.of("source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling", name);
        return Files.readString(Files.exists(module) ? module : repository, StandardCharsets.UTF_8);
    }
}
