package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PjmGoldenPathContractTest {

    @Test
    void goldenPathHasAnExplicitDwdDwsAdsDependencyChainAndReadOnlyLegacyRefs() {
        PjmModelingFixture.GoldenPathFixture fixture = PjmModelingFixture.goldenPath();
        Map<String, ModelingVNextContract.ModelSpec> models = fixture.modelSpecs().stream().collect(Collectors.toMap(ModelingVNextContract.ModelSpec::id, Function.identity()));

        assertThat(models.keySet()).containsExactlyInAnyOrder("pjm-project-node-dwd", "pjm-project-node-dws", "pjm-project-node-ads");
        assertThat(models.get("pjm-project-node-dws").dependsOn()).containsExactly("pjm-project-node-dwd");
        assertThat(models.get("pjm-project-node-ads").dependsOn()).containsExactly("pjm-project-node-dws");
        assertThat(fixture.legacyRefs()).anySatisfy(ref -> {
            assertThat(ref.status()).isEqualTo("LEGACY_READONLY");
            assertThat(ref.dbtUniqueId()).isEqualTo("model.pm_analytics_v3.biz_dwd_project_node_v2");
        });
    }
}
