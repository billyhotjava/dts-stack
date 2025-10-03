package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.InfraSecurityProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.domain.service.InfraDataStorage;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void shouldSkipSecretsWhenEncryptionKeyMissing() {
        InfraSecurityProperties disabledProps = new InfraSecurityProperties();
        InfraSecretService disabledService = new InfraSecretService(disabledProps, objectMapper);
        disabledService.init();

        InfraDataStorage storage = new InfraDataStorage();
        storage.setName("lake");
        disabledService.applySecrets(storage, Map.of("accessKey", "abc"));

        assertThat(storage.getSecureProps()).isNull();
        assertThat(storage.getSecureIv()).isNull();
    }
}
