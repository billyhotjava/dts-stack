package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbtTargetConnectionFactoryTest {

    private static final UUID TARGET_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");

    private DbtConfigService configService;
    private InfraDataSourceRepository repository;
    private InfraSecretService secrets;
    private DbtTargetConnectionFactory factory;

    @BeforeEach
    void setUp() {
        configService = mock(DbtConfigService.class);
        repository = mock(InfraDataSourceRepository.class);
        secrets = mock(InfraSecretService.class);
        factory = new DbtTargetConnectionFactory(
            configService,
            repository,
            secrets
        );
        when(configService.loadRuntimeConfig()).thenReturn(
            new DbtConfigService.DbtWorkspaceConfig(
                true,
                "/project",
                "/legacy-profiles",
                "dts",
                "dev",
                TARGET_ID,
                "warehouse",
                "finance",
                Map.of()
            )
        );
    }

    @Test
    void resolvesOnlyEncryptedServerConfiguredRuntimeTarget() {
        InfraDataSource source = source("v3");
        when(repository.findById(TARGET_ID)).thenReturn(Optional.of(source));
        when(secrets.readSecrets(source)).thenReturn(
            Map.of("password", "runtime-only")
        );

        var target = factory.resolveRuntimeTarget();

        assertThat(target.dataSourceId()).isEqualTo(TARGET_ID);
        assertThat(target.database()).isEqualTo("warehouse");
        assertThat(target.schema()).isEqualTo("finance");
        assertThat(target.password()).isEqualTo("runtime-only");
        assertThat(target.credentialVersionRef())
            .startsWith("sha256:")
            .hasSize(71);
        verify(configService).loadRuntimeConfig();
        verify(configService, never()).loadConfig();
    }

    @Test
    void rejectsPlaintextSecretBeforeReadingIt() {
        InfraDataSource source = source("PLAINTEXT");
        when(repository.findById(TARGET_ID)).thenReturn(Optional.of(source));

        assertThatThrownBy(factory::resolveRuntimeTarget)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("不是受保护");

        verify(secrets, never()).readSecrets(source);
        verify(configService, never()).loadConfig();
    }

    @Test
    void reportsMissingConfiguredTargetWithStableCode() {
        when(repository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(factory::resolveRuntimeTarget)
            .isInstanceOf(IllegalStateException.class)
            .isInstanceOf(DbtRuntimeTargetException.class)
            .extracting(error -> ((DbtRuntimeTargetException) error).code())
            .isEqualTo("DBT_TARGET_DATASOURCE_NOT_FOUND");
        verify(configService, never()).loadConfig();
    }

    @Test
    void reportsUnprotectedSecretWithStableCode() {
        InfraDataSource source = source("PLAINTEXT");
        when(repository.findById(TARGET_ID)).thenReturn(Optional.of(source));

        assertThatThrownBy(factory::resolveRuntimeTarget)
            .extracting(error -> ((DbtRuntimeTargetException) error).code())
            .isEqualTo("DBT_TARGET_SECRET_UNPROTECTED");
    }

    private static InfraDataSource source(String keyVersion) {
        InfraDataSource source = new InfraDataSource();
        source.setId(TARGET_ID);
        source.setName("warehouse");
        source.setType("postgres");
        source.setJdbcUrl(
            "jdbc:postgresql://dts-pg:5432/warehouse"
        );
        source.setUsername("warehouse_user");
        source.setSecureKeyVersion(keyVersion);
        source.setSecureProps(new byte[] { 1, 2, 3 });
        source.setLastModifiedDate(
            Instant.parse("2026-07-27T13:00:00Z")
        );
        return source;
    }
}
