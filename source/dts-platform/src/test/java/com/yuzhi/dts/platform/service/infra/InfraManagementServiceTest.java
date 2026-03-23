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
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
}
