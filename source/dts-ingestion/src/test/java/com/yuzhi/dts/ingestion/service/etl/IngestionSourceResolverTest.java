package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionSourceResolverTest {

    @Test
    void resolveApiInfo_exposesBaseUrlAuthHeadersAndDecryptedSecrets() {
        PlatformInfraClient platformInfraClient = mock(PlatformInfraClient.class);
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlatformInfraClient.DataSourceDetail detail = new PlatformInfraClient.DataSourceDetail(
            sourceId,
            "CRM API",
            "api",
            null,
            null,
            null,
            null,
            Map.of(
                "baseUrl",
                "https://crm.example.test/openapi",
                "auth",
                Map.of("provider", "jwtLogin", "loginUrl", "/login", "tokenPath", "$.data.token"),
                "defaultHeaders",
                Map.of("X-Tenant", "demo")
            ),
            Map.of("password", "secret-password", "tenantSecret", "tenant-secret"),
            "ACTIVE"
        );
        when(platformInfraClient.fetchDataSourceDetail(sourceId)).thenReturn(detail);

        IngestionSourceResolver resolver = new IngestionSourceResolver(platformInfraClient);

        IngestionSourceResolver.ApiConnectionInfo info = resolver.resolveApiInfo(sourceId);
        IngestionSourceResolver.ResolvedSource source = resolver.resolve(sourceId, List.of("orders"));

        assertThat(info.baseUrl()).isEqualTo("https://crm.example.test/openapi");
        assertThat(info.authProvider()).isEqualTo("jwtLogin");
        assertThat(info.authConfig()).containsEntry("provider", "jwtLogin").containsEntry("loginUrl", "/login");
        assertThat(info.defaultHeaders()).containsEntry("X-Tenant", "demo");
        assertThat(info.secrets()).containsEntry("password", "secret-password").containsEntry("tenantSecret", "tenant-secret");

        assertThat(source.readerType()).isEqualTo("httpreader");
        assertThat(source.readerConfig())
            .containsEntry("readerType", "httpreader")
            .containsEntry("connectorType", "api")
            .containsEntry("baseUrl", "https://crm.example.test/openapi")
            .containsEntry("authProvider", "jwtLogin")
            .containsEntry("resources", List.of("orders"));
        @SuppressWarnings("unchecked")
        Map<String, Object> secrets = (Map<String, Object>) source.readerConfig().get("secrets");
        assertThat(secrets).containsEntry("password", "secret-password");
    }

    @Test
    void resolve_keepsJdbcDataSourcesOnExistingReaderPath() {
        PlatformInfraClient platformInfraClient = mock(PlatformInfraClient.class);
        UUID sourceId = UUID.fromString("22222222-3333-4444-5555-666666666666");
        PlatformInfraClient.DataSourceDetail detail = new PlatformInfraClient.DataSourceDetail(
            sourceId,
            "ODS PG",
            "postgresql",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "biadmin",
            null,
            null,
            Map.of(),
            Map.of("password", "pg-secret"),
            "ACTIVE"
        );
        when(platformInfraClient.fetchDataSourceDetail(sourceId)).thenReturn(detail);

        IngestionSourceResolver resolver = new IngestionSourceResolver(platformInfraClient);

        IngestionSourceResolver.ResolvedSource source = resolver.resolve(sourceId, List.of("public.orders"));

        assertThat(source.readerType()).isEqualTo("postgresqlreader");
        assertThat(source.readerConfig()).containsEntry("username", "biadmin").containsEntry("password", "pg-secret");
        assertThat(source.readerConfig()).doesNotContainKeys("baseUrl", "authProvider", "secrets");
    }
}
