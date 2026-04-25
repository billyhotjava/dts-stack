package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApiDataSourceSupportTest {

    @Test
    void validateRequest_acceptsApiSourceWithBaseUrlAndSecrets() {
        DataSourceRequest request = new DataSourceRequest(
            "外部系统 API",
            "API",
            null,
            null,
            null,
            Map.of("baseUrl", "https://example.test/openapi", "authProvider", "bearerToken"),
            Map.of("token", "secret")
        );

        ApiDataSourceSupport.validateRequest(request);
    }

    @Test
    void validateRequest_rejectsPlaintextSecretsInProps() {
        DataSourceRequest request = new DataSourceRequest(
            "外部系统 API",
            "api",
            null,
            null,
            null,
            Map.of("baseUrl", "https://example.test/openapi", "token", "plain"),
            Map.of()
        );

        assertThatThrownBy(() -> ApiDataSourceSupport.validateRequest(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("敏感字段 token 必须保存到 secrets");
    }

    @Test
    void validateRequest_rejectsApiKeyValueInAuthProps() {
        DataSourceRequest request = new DataSourceRequest(
            "外部系统 API",
            "api",
            null,
            null,
            null,
            Map.of("baseUrl", "https://example.test/openapi", "auth", Map.of("provider", "apiKey", "value", "plain")),
            Map.of()
        );

        assertThatThrownBy(() -> ApiDataSourceSupport.validateRequest(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("敏感字段 auth.value 必须保存到 secrets");
    }

    @Test
    void validateRequest_allowsSecretReferencesInProps() {
        DataSourceRequest request = new DataSourceRequest(
            "外部系统 API",
            "api",
            null,
            null,
            null,
            Map.of(
                "baseUrl",
                "https://example.test/openapi",
                "auth",
                Map.of("provider", "bearerToken", "secretRefs", Map.of("token", "secret:api-token:v1"))
            ),
            Map.of()
        );

        ApiDataSourceSupport.validateRequest(request);
    }

    @Test
    void normalizeProps_addsApiConnectorDefaults() {
        Map<String, Object> props = ApiDataSourceSupport.normalizeProps(Map.of("baseUrl", "https://example.test/openapi"));

        assertThat(props)
            .containsEntry("connectorType", "api")
            .containsEntry("readerType", "httpreader")
            .containsEntry("sourceCategory", "api")
            .containsEntry("authProvider", "none");
    }
}
