package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.junit.jupiter.api.Test;

class ModelingCompatibilityPolicyTest {

    @Test
    void importedLegacyDbtModelsRemainReadOnlyAndUseTheSemanticCompatibilityRoute() {
        ModelingCompatibilityPolicy.LegacyAsset asset = ModelingCompatibilityPolicy.registerLegacyDbtModel(
            "pjm-project-node-dwd",
            "model.pm_analytics_v3.biz_dwd_project_node_v2",
            "models/dwd/biz_dwd_project_node_v2.sql"
        );

        assertThat(asset.status()).isEqualTo(ModelingCompatibilityPolicy.LegacyStatus.LEGACY_READONLY);
        assertThat(asset.apiRoute()).isEqualTo("/api/semantic/models");
        assertThat(ModelingCompatibilityPolicy.canEditLegacyAsset(asset)).isFalse();
    }

    @Test
    void legacySemanticControllerKeepsTheModelAndBusinessObjectReadRoutes() {
        RequestMapping root = com.yuzhi.dts.platform.web.rest.SemanticModelingResource.class
            .getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).containsExactly("/api/semantic");

        assertThat(hasGetRoute("/models")).isTrue();
        assertThat(hasGetRoute("/business-objects")).isTrue();
    }

    private static boolean hasGetRoute(String path) {
        for (Method method : com.yuzhi.dts.platform.web.rest.SemanticModelingResource.class.getDeclaredMethods()) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping != null && java.util.Arrays.asList(mapping.value()).contains(path)) return true;
        }
        return false;
    }
}
