package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.InfraSecurityProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraCatalogSyncRunRepository;
import com.yuzhi.dts.platform.repository.service.InfraConnectionTestLogRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataStorageRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.dto.ApiSecretSummary;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
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
    void getDataSourceDetail_jdbcType_stillReturnsPlaintextSecretsForCompatibility() {
        UUID id = UUID.randomUUID();
        InfraDataSource entity = new InfraDataSource();
        entity.setId(id);
        entity.setName("erp-db");
        entity.setType("postgresql");
        entity.setStatus("ACTIVE");
        entity.setProps("{}");

        when(dataSourceRepository.findById(id)).thenReturn(Optional.of(entity));
        when(secretService.readSecrets(entity)).thenReturn(Map.of("password", "p@ssw0rd"));

        InfraDataSourceDetailDto detail = service.getDataSourceDetail(id);

        // JDBC datasources keep existing behaviour — caller still sees plaintext (used by /test endpoint)
        assertThat(detail.secrets()).containsEntry("password", "p@ssw0rd");
        assertThat(detail.secretSummaries()).isEmpty();
    }
}
