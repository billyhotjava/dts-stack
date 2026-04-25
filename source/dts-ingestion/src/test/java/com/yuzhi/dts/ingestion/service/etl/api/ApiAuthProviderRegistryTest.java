package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiAuthProviderRegistryTest {

    private final ApiAuthProviderRegistry registry = new ApiAuthProviderRegistry();

    @Test
    void listDescriptors_includesExtensibleBuiltinProviders() {
        assertThat(registry.listDescriptors())
            .extracting(ApiAuthProviderDescriptor::id)
            .contains("none", "apiKey", "bearerToken", "basic", "oauth2ClientCredentials", "customSignature", "mtls");
    }

    @Test
    void apiKeyProvider_marksSecretValueAsSensitive() {
        ApiAuthProviderDescriptor descriptor = registry.findDescriptor("apiKey").orElseThrow();

        assertThat(descriptor.fields())
            .anySatisfy(field -> {
                assertThat(field.name()).isEqualTo("value");
                assertThat(field.sensitive()).isTrue();
                assertThat(field.required()).isTrue();
            });
    }
}

