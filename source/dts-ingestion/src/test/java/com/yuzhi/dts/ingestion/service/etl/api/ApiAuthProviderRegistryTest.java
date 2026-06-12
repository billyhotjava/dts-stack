package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiAuthProviderRegistryTest {

    private final ApiAuthProviderRegistry registry = new ApiAuthProviderRegistry();

    @Test
    void listDescriptors_includesExtensibleBuiltinProviders() {
        assertThat(registry.listDescriptors())
            .extracting(ApiAuthProviderDescriptor::id)
            .contains("none", "apiKey", "bearerToken", "basic", "oauth2ClientCredentials", "jwtLogin", "customSignature", "mtls");
    }

    @Test
    void listDescriptors_marksOnlyRuntimeSupportedProvidersEnabled() {
        assertThat(registry.findDescriptor("jwtLogin").orElseThrow().enabled()).isTrue();
        assertThat(registry.findDescriptor("oauth2ClientCredentials").orElseThrow().enabled()).isTrue();
        assertThat(registry.findDescriptor("customSignature").orElseThrow().enabled()).isFalse();
        assertThat(registry.findDescriptor("mtls").orElseThrow().enabled()).isFalse();
    }

    @Test
    void listDescriptors_enabledProvidersMatchRuntimeSupportedProviders() {
        assertThat(registry.listDescriptors())
            .filteredOn(ApiAuthProviderDescriptor::enabled)
            .extracting(ApiAuthProviderDescriptor::id)
            .containsExactlyElementsOf(ApiHttpEngine.supportedAuthProviders());
    }

    @Test
    void jwtLoginProvider_exposesLoginTemplateAndSecretRefMapFields() {
        ApiAuthProviderDescriptor descriptor = registry.findDescriptor("jwtLogin").orElseThrow();

        assertThat(descriptor.fields())
            .extracting(ApiAuthProviderField::name)
            .contains("loginUrl", "loginBodyTemplate", "tokenPath", "expiresInPath", "secretRefs");
        assertThat(descriptor.fields())
            .anySatisfy(field -> {
                assertThat(field.name()).isEqualTo("secretRefs");
                assertThat(field.sensitive()).isTrue();
                assertThat(field.type()).isEqualTo("secretRefMap");
            });
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
