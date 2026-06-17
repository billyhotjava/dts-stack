package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.InfraSecurityProperties;
import com.yuzhi.dts.platform.domain.infra.InfraConnector;
import com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraCatalogSyncRunRepository;
import com.yuzhi.dts.platform.repository.infra.InfraConnectorRepository;
import com.yuzhi.dts.platform.repository.service.InfraConnectionTestLogRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataStorageRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.dto.ApiSecretSummary;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class InfraManagementServiceTest {

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private InfraDataStorageRepository storageRepository;

    @Mock
    private InfraConnectionTestLogRepository testLogRepository;

    @Mock
    private InfraSecretService secretService;

    @Mock
    private InfraSecurityProperties securityProperties;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private PostgresConnectionService postgresConnectionService;

    @Mock
    private IngestionServiceClient ingestionServiceClient;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSyncService columnSyncService;

    @Mock
    private JdbcCatalogSyncService jdbcCatalogSyncService;

    @Mock
    private InfraCatalogSyncRunRepository syncRunRepository;

    @Mock
    private InfraConnectorRepository connectorRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private AdminInfraClient adminInfraClient;

    @Spy
    private ApiSecretMetadataService apiSecretMetadataService = new ApiSecretMetadataService(
        Clock.fixed(Instant.parse("2026-04-26T12:00:00Z"), ZoneOffset.UTC)
    );

    @InjectMocks
    private InfraManagementService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listDataSources_shouldNotBlockWhenAdminDefaultDataLakeLookupIsSlow() {
        InfraDataSource local = new InfraDataSource();
        local.setId(UUID.randomUUID());
        local.setName("Local Lake");
        local.setType("postgresql");
        local.setStatus("ACTIVE");

        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(local));
        when(adminInfraClient.fetchDefaultDataLake()).thenAnswer(invocation -> {
            Thread.sleep(5_000L);
            return Optional.empty();
        });

        List<InfraDataSourceDto> result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
            Duration.ofMillis(1200),
            () -> service.listDataSources(null)
        );

        assertThat(result).extracting(InfraDataSourceDto::name).containsExactly("Local Lake");
    }

    @Test
    void listDataSources_shouldPreferAdminDefaultDataLakeOverLocalFallbackCandidate() {
        InfraDataSource localFallback = new InfraDataSource();
        localFallback.setId(UUID.randomUUID());
        localFallback.setName("平台 PostgreSQL");
        localFallback.setType("POSTGRES");
        localFallback.setJdbcUrl("jdbc:postgresql://localhost:5432/platform");
        localFallback.setStatus("ACTIVE");

        InfraDataSource other = new InfraDataSource();
        other.setId(UUID.randomUUID());
        other.setName("ERP 数据库");
        other.setType("mysql");
        other.setJdbcUrl("jdbc:mysql://erp:3306/erp");
        other.setStatus("ACTIVE");

        UUID adminLakeId = UUID.randomUUID();
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(localFallback, other));
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake(adminLakeId)));

        List<InfraDataSourceDto> result = service.listDataSources(null);

        assertThat(result).extracting(InfraDataSourceDto::name).containsExactly("默认数据湖", "ERP 数据库");
        assertThat(result)
            .extracting(dto -> dto.props() == null ? null : dto.props().get("source"))
            .containsExactly("admin-data-lake", null);
    }

    @Test
    void createDataSource_deptDataOwnerKeepsOwnerDeptEmptyWhenNoDeptIsSelected() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "asset-manager",
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_DEPT_DATA_OWNER"))
                )
            );
        when(dataSourceRepository.save(org.mockito.ArgumentMatchers.any(InfraDataSource.class))).thenAnswer(invocation -> {
            InfraDataSource saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        InfraConnector connector = new InfraConnector();
        connector.setConnectorKey("postgresql");
        connector.setName("PostgreSQL");
        connector.setCategory("database");
        connector.setStatus("ACTIVE");
        when(connectorRepository.findByConnectorKeyIgnoreCase("postgresql")).thenReturn(Optional.of(connector));

        InfraDataSourceDto dto = service.createDataSource(
            new DataSourceRequest(
                "数仓 (biadmin)",
                "POSTGRESQL",
                "jdbc:postgresql://dts-pg:5432/biadmin",
                "biadmin",
                "内置数据湖",
                Map.of(),
                Map.of("password", "secret")
            ),
            "admin",
            null
        );

        assertThat(dto.ownerDept()).isNull();
    }

    @Test
    void updateDataSource_deptDataOwnerCanMaintainGlobalDataSourceWithoutDeptContext() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "asset-manager",
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_DEPT_DATA_OWNER"))
                )
            );
        UUID id = UUID.randomUUID();
        InfraDataSource existing = new InfraDataSource();
        existing.setId(id);
        existing.setName("数仓 (biadmin)");
        existing.setType("POSTGRESQL");
        existing.setConnectorKey("postgresql");
        existing.setJdbcUrl("jdbc:postgresql://dts-pg:5432/biadmin");
        existing.setUsername("biadmin");
        existing.setStatus("ACTIVE");
        existing.setOwnerDept(null);

        InfraConnector connector = new InfraConnector();
        connector.setConnectorKey("postgresql");
        connector.setName("PostgreSQL");
        connector.setCategory("database");
        connector.setStatus("ACTIVE");
        when(dataSourceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(connectorRepository.findByConnectorKeyIgnoreCase("postgresql")).thenReturn(Optional.of(connector));
        when(secretService.readSecrets(existing)).thenReturn(Map.of("password", "secret"));
        when(dataSourceRepository.save(org.mockito.ArgumentMatchers.any(InfraDataSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InfraManagementService.DataSourceUpdateImpact impact = service.updateDataSourceWithImpact(
            id,
            new DataSourceRequest(
                "数仓 (biadmin)",
                "POSTGRESQL",
                "postgresql",
                "jdbc:postgresql://dts-pg:5432/biadmin",
                "biadmin",
                "内置数据湖",
                null,
                Map.of(),
                Map.of("password", "secret")
            ),
            "asset-manager",
            null
        );

        assertThat(impact.dataSource().ownerDept()).isNull();
    }

    private AdminInfraClient.AdminDataLakeConfig adminLake(UUID id) {
        AdminInfraClient.AdminDataLakeConfig lake = new AdminInfraClient.AdminDataLakeConfig();
        setField(lake, "id", id);
        setField(lake, "name", "默认数据湖");
        setField(lake, "type", "DATA_LAKE");
        setField(lake, "jdbcUrl", "jdbc:postgresql://localhost:5432/biadmin");
        setField(lake, "username", "biadmin");
        setField(lake, "password", "secret");
        setField(lake, "status", "ACTIVE");
        setField(lake, "defaulted", Boolean.TRUE);
        setField(lake, "lastVerifiedAt", Instant.now());
        return lake;
    }

    private void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set field " + name, ex);
        }
    }

    @Test
    void getDataSourceDetail_apiType_masksPlaintextSecretsAndExposesSummaries() throws Exception {
        UUID id = UUID.randomUUID();
        InfraDataSource entity = new InfraDataSource();
        entity.setId(id);
        entity.setName("crm-api");
        entity.setType("api");
        entity.setStatus("ACTIVE");

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("providerId", "apiKey");
        meta.put(
            "fields",
            List.of(
                Map.of(
                    "fieldName", "value",
                    "maskedDisplay", "sk***wxyz",
                    "secretVersion", "v2",
                    "rotatedAt", "2026-04-20T08:00:00Z",
                    "status", "ACTIVE"
                )
            )
        );
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("baseUrl", "https://crm.example.com");
        props.put("authProvider", "apiKey");
        props.put(ApiSecretMetadataService.PROPS_METADATA_KEY, meta);
        ObjectMapper mapper = new ObjectMapper();
        entity.setProps(mapper.writeValueAsString(props));

        when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

        InfraDataSourceDetailDto detail = service.getDataSourceDetail(id);

        // Plaintext secrets must never leak through detail for API data sources
        assertThat(detail.secrets()).isEmpty();
        // secretSummaries derived from sidecar metadata
        assertThat(detail.secretSummaries()).hasSize(1);
        ApiSecretSummary summary = detail.secretSummaries().get(0);
        assertThat(summary.providerId()).isEqualTo("apiKey");
        assertThat(summary.fieldName()).isEqualTo("value");
        assertThat(summary.maskedDisplay()).isEqualTo("sk***wxyz");
        assertThat(summary.secretVersion()).isEqualTo("v2");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        // secretService should not be consulted for API data sources in detail flow
        org.mockito.Mockito.verify(secretService, org.mockito.Mockito.never()).readSecrets(entity);
    }

    @Test
    void getDataSourceDetail_jdbcTypeMasksSecretsInUserFacingDetail() {
        UUID id = UUID.randomUUID();
        InfraDataSource entity = new InfraDataSource();
        entity.setId(id);
        entity.setName("erp-db");
        entity.setType("postgresql");
        entity.setStatus("ACTIVE");
        entity.setProps("{}");

        when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));

        InfraDataSourceDetailDto detail = service.getDataSourceDetail(id);

        assertThat(detail.secrets()).isEmpty();
        assertThat(detail.secretSummaries()).isEmpty();
        org.mockito.Mockito.verify(secretService, org.mockito.Mockito.never()).readSecrets(entity);
    }

    @Test
    void getDataSourceRuntimeDetail_jdbcTypeReturnsPlaintextSecretsForInternalExecution() {
        UUID id = UUID.randomUUID();
        InfraDataSource entity = new InfraDataSource();
        entity.setId(id);
        entity.setName("erp-db");
        entity.setType("postgresql");
        entity.setStatus("ACTIVE");
        entity.setProps("{}");

        when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
        when(secretService.readSecrets(entity)).thenReturn(Map.of("password", "p@ssw0rd"));

        InfraDataSourceDetailDto detail = service.getDataSourceRuntimeDetail(id);

        assertThat(detail.secrets()).containsEntry("password", "p@ssw0rd");
        assertThat(detail.secretSummaries()).isEmpty();
    }

    @Test
    void createDataSource_apiTypeStoresSecretsViaSecretServiceAndReturnsMaskedMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        when(connectorRepository.findByConnectorKeyIgnoreCase("http-api")).thenReturn(Optional.of(httpApiConnector()));
        org.mockito.Mockito
            .doAnswer(invocation -> {
                InfraDataSource entity = invocation.getArgument(0);
                entity.setSecureProps("ciphertext-only".getBytes(StandardCharsets.UTF_8));
                entity.setSecureIv("iv".getBytes(StandardCharsets.UTF_8));
                entity.setSecureKeyVersion("v1-test");
                return null;
            })
            .when(secretService)
            .applySecrets(org.mockito.ArgumentMatchers.any(InfraDataSource.class), org.mockito.ArgumentMatchers.anyMap());
        when(dataSourceRepository.save(org.mockito.ArgumentMatchers.any(InfraDataSource.class))).thenAnswer(invocation -> {
            InfraDataSource entity = invocation.getArgument(0);
            entity.setId(id);
            return entity;
        });
        DataSourceRequest request = new DataSourceRequest(
            "crm-api",
            "api",
            null,
            null,
            null,
            Map.of("baseUrl", "https://crm.example.test/openapi", "authProvider", "apiKey"),
            Map.of("value", "plain-api-key")
        );

        InfraDataSourceDto dto = service.createDataSource(request, "operator", "dept-a");

        org.mockito.ArgumentCaptor<InfraDataSource> entityCaptor = org.mockito.ArgumentCaptor.forClass(InfraDataSource.class);
        org.mockito.Mockito.verify(dataSourceRepository).save(entityCaptor.capture());
        InfraDataSource saved = entityCaptor.getValue();
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> secretsCaptor =
            (org.mockito.ArgumentCaptor<Map<String, Object>>) (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(secretService).applySecrets(org.mockito.ArgumentMatchers.same(saved), secretsCaptor.capture());
        assertThat(secretsCaptor.getValue()).containsEntry("value", "plain-api-key");
        assertThat(new String(saved.getSecureProps(), StandardCharsets.UTF_8)).doesNotContain("plain-api-key");
        assertThat(saved.getProps()).doesNotContain("plain-api-key");
        assertThat(dto.hasSecrets()).isTrue();
        assertThat(dto.props()).containsKey(ApiSecretMetadataService.PROPS_METADATA_KEY);
        assertThat(dto.props().toString()).contains("pl***-key").doesNotContain("plain-api-key");
    }

    @Test
    void recordConnectionTest_apiDataSourceRequestRedactsPropsAndSecrets() {
        UUID id = UUID.randomUUID();
        DataSourceRequest request = new DataSourceRequest(
            "crm-api",
            "api",
            null,
            null,
            null,
            Map.of(
                "baseUrl",
                "https://crm.example.test/openapi",
                "token",
                "top-secret-token",
                "auth",
                Map.of("provider", "apiKey", "value", "plain-api-key")
            ),
            Map.of("value", "encrypted-later-secret")
        );

        service.recordConnectionTest(id, request, HiveConnectionTestResult.failure("连接失败", 12), "operator");

        org.mockito.ArgumentCaptor<InfraConnectionTestLog> captor = org.mockito.ArgumentCaptor.forClass(
            InfraConnectionTestLog.class
        );
        org.mockito.Mockito.verify(testLogRepository).save(captor.capture());
        String payload = captor.getValue().getRequestPayload();
        assertThat(payload)
            .contains("\"secretsProvided\":true")
            .contains("\"token\":\"***\"")
            .contains("\"value\":\"***\"")
            .doesNotContain("top-secret-token", "plain-api-key", "encrypted-later-secret");
    }

    private InfraConnector httpApiConnector() {
        InfraConnector connector = new InfraConnector();
        connector.setConnectorKey("http-api");
        connector.setName("HTTP API");
        connector.setCategory("api");
        connector.setDefaultEngine("api-http");
        return connector;
    }
}
