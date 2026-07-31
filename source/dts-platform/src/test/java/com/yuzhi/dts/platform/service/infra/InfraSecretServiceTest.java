package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.InfraSecurityProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.domain.service.InfraDataStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

class InfraSecretServiceTest {

    private InfraSecretService service;
    private InfraSecurityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new InfraSecurityProperties();
        properties.setEncryptionKey("MDEyMzQ1Njc4OUFCQ0RFRg==");
        properties.setKeyVersion("v1-test");
        service = new InfraSecretService(properties, objectMapper);
        service.init();
    }

    @Test
    void shouldEncryptAndDecryptDataSourceSecrets() {
        InfraDataSource entity = new InfraDataSource();
        entity.setName("primary");
        Map<String, Object> secrets = Map.of("password", "Strong@Pass1", "kerberosKeytab", "BASE64==");

        service.applySecrets(entity, secrets);

        assertThat(entity.getSecureProps()).isNotNull();
        assertThat(entity.getSecureIv()).isNotNull();
        assertThat(entity.getSecureKeyVersion()).isEqualTo("v1-test");

        Map<String, Object> decrypted = service.readSecrets(entity);
        assertThat(decrypted)
            .containsEntry("password", "Strong@Pass1")
            .containsEntry("kerberosKeytab", "BASE64==");
    }

    @Test
    void shouldRejectStorageSecretWriteWhenEncryptionKeyMissing() {
        InfraSecurityProperties disabledProps = new InfraSecurityProperties();
        InfraSecretService disabledService = new InfraSecretService(disabledProps, objectMapper);
        disabledService.init();

        InfraDataStorage storage = new InfraDataStorage();
        storage.setName("lake");

        assertThatThrownBy(() -> disabledService.applySecrets(storage, Map.of("accessKey", "abc")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Secret encryption key is not configured");

        assertThat(storage.getSecureProps()).isNull();
        assertThat(storage.getSecureIv()).isNull();
        assertThat(storage.getSecureKeyVersion()).isNull();
    }

    @Test
    void shouldRejectDataSourceSecretWriteWhenEncryptionKeyMissing() {
        InfraSecurityProperties disabledProps = new InfraSecurityProperties();
        InfraSecretService disabledService = new InfraSecretService(disabledProps, objectMapper);
        disabledService.init();
        InfraDataSource dataSource = new InfraDataSource();
        dataSource.setName("legacy-source");

        assertThatThrownBy(() -> disabledService.applySecrets(dataSource, Map.of("password", "secret")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Secret encryption key is not configured");
        assertThat(dataSource.getSecureProps()).isNull();
        assertThat(dataSource.getSecureKeyVersion()).isNull();
    }

    @Test
    void shouldReadLegacyPlaintextSecretsForInternalMigrationCompatibility() {
        InfraSecurityProperties disabledProps = new InfraSecurityProperties();
        InfraSecretService disabledService = new InfraSecretService(disabledProps, objectMapper);
        disabledService.init();
        InfraDataSource dataSource = new InfraDataSource();
        dataSource.setName("legacy-source");
        dataSource.setSecureProps("{\"password\":\"legacy\"}".getBytes(StandardCharsets.UTF_8));
        dataSource.setSecureKeyVersion("PLAINTEXT");

        assertThat(disabledService.readSecrets(dataSource)).containsEntry("password", "legacy");
    }

    @Test
    void applicationYamlBindsInfraSecretKeyFromDtsInfraEnvironment() throws IOException {
        ConfigurableEnvironment environment = new MockEnvironment()
            .withProperty("DTS_INFRA_ENCRYPTION_KEY", "MDEyMzQ1Njc4OUFCQ0RFRg==")
            .withProperty("DTS_INFRA_KEY_VERSION", "v2-live");
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (org.springframework.core.env.PropertySource<?> propertySource : loader.load(
            "application",
            new FileSystemResource("src/main/resources/config/application.yml")
        )) {
            environment.getPropertySources().addLast(propertySource);
        }

        InfraSecurityProperties bound = Binder
            .get(environment)
            .bind("dts.platform.infra", Bindable.of(InfraSecurityProperties.class))
            .orElseThrow(() -> new AssertionError("dts.platform.infra properties were not bound"));

        assertThat(bound.getEncryptionKey()).isEqualTo("MDEyMzQ1Njc4OUFCQ0RFRg==");
        assertThat(bound.getKeyVersion()).isEqualTo("v2-live");
    }
}
