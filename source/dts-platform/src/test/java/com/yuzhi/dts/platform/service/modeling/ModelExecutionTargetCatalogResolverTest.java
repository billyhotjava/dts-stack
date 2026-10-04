package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelExecutionTargetCatalogResolverTest {

    private static final String TARGET_KEY = "postgres:warehouse/prod";
    private static final UUID SOURCE_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void resolvesCredentialFreeCatalogSourceForTheCurrentExecutionTarget() {
        ModelMaterializationProperties properties = properties();
        DbtConfigService configs = mock(DbtConfigService.class);
        CandidateView candidate = mock(CandidateView.class);
        when(candidate.id()).thenReturn(
            UUID.fromString("20000000-0000-0000-0000-000000000001")
        );
        when(candidate.executionTargetKey()).thenReturn(TARGET_KEY);
        when(candidate.adapter()).thenReturn("postgres");
        when(configs.loadRuntimeConfig()).thenReturn(config(SOURCE_ID));

        var target = new ModelExecutionTargetCatalogResolver(
            properties,
            configs
        ).resolve(candidate);

        assertThat(target.executionTargetKey()).isEqualTo(TARGET_KEY);
        assertThat(target.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(target.adapter()).isEqualTo("postgres");
    }

    @Test
    void rejectsCandidatePinnedToAnotherExecutionTarget() {
        ModelMaterializationProperties properties = properties();
        DbtConfigService configs = mock(DbtConfigService.class);
        CandidateView candidate = mock(CandidateView.class);
        when(candidate.id()).thenReturn(
            UUID.fromString("20000000-0000-0000-0000-000000000001")
        );
        when(candidate.executionTargetKey()).thenReturn("postgres:warehouse/test");
        when(candidate.adapter()).thenReturn("postgres");

        assertThatThrownBy(() ->
            new ModelExecutionTargetCatalogResolver(properties, configs)
                .resolve(candidate)
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(
                    ((ModelReleaseCandidateException) error).code()
                ).isEqualTo("MODEL_RELEASE_EXECUTION_TARGET_NOT_CURRENT")
            );
    }

    private static ModelMaterializationProperties properties() {
        ModelMaterializationProperties properties =
            new ModelMaterializationProperties();
        properties.setExecutionTargetKey(TARGET_KEY);
        properties.setAdapter("postgres");
        return properties;
    }

    private static DbtConfigService.DbtWorkspaceConfig config(UUID sourceId) {
        return new DbtConfigService.DbtWorkspaceConfig(
            true,
            "/tmp/project",
            "/tmp/profiles",
            "dts",
            "prod",
            sourceId,
            "warehouse",
            "finance",
            Map.of()
        );
    }
}
