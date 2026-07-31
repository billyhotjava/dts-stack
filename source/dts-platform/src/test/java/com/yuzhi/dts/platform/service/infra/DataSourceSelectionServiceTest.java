package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraConnector;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.infra.InfraConnectorRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class DataSourceSelectionServiceTest {

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private InfraConnectorRepository connectorRepository;

    @Mock
    private DefaultDestinationSyncService defaultDestinationSyncService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listSelectionsMarksPlatformSourceAsDefaultWithoutReturningAdminVirtualId() {
        UUID platformSourceId = UUID.randomUUID();
        UUID adminLakeId = UUID.randomUUID();
        InfraDataSource platformLake = source(platformSourceId, "数据仓库 (biadmin)", "POSTGRESQL", "jdbc:postgresql://dts-pg:5432/biadmin");
        InfraDataSource erpLake = source(UUID.randomUUID(), "项目湖仓", "POSTGRESQL", "jdbc:postgresql://dts-pg:5432/project_dw");
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(erpLake, platformLake));
        when(defaultDestinationSyncService.checkDefaultDestinationStatus())
            .thenReturn(new DefaultDestinationSyncService.DefaultDestinationStatus(
                true,
                true,
                true,
                "数仓 (biadmin)",
                "postgresqlwriter",
                null,
                platformSourceId.toString()
            ));

        DataSourceSelectionService.DataSourceSelectionResponse response = new DataSourceSelectionService(
            dataSourceRepository,
            connectorRepository,
            defaultDestinationSyncService
        ).listSelections("MODELING_LAKEHOUSE", null);

        assertThat(response.defaultDataSourceId()).isEqualTo(platformSourceId);
        assertThat(response.items()).extracting(DataSourceSelectionService.DataSourceSelectionItem::id)
            .contains(platformSourceId, erpLake.getId())
            .doesNotContain(adminLakeId);
        assertThat(response.items().get(0).id()).isEqualTo(platformSourceId);
        assertThat(response.items().get(0).defaultSource()).isTrue();
        assertThat(response.items().get(0).recommended()).isTrue();
    }

    @Test
    void listSelectionsKeepsPlatformSourcesWhenDefaultLakeMappingIsMissing() {
        InfraDataSource projectLake = source(UUID.randomUUID(), "项目湖仓", "POSTGRESQL", "jdbc:postgresql://dts-pg:5432/project_dw");
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(projectLake));
        when(defaultDestinationSyncService.checkDefaultDestinationStatus())
            .thenReturn(new DefaultDestinationSyncService.DefaultDestinationStatus(
                true,
                true,
                true,
                "数仓 (biadmin)",
                "postgresqlwriter",
                "未识别默认数据湖数据源",
                null
            ));

        DataSourceSelectionService.DataSourceSelectionResponse response = new DataSourceSelectionService(
            dataSourceRepository,
            connectorRepository,
            defaultDestinationSyncService
        ).listSelections("MODELING_LAKEHOUSE", null);

        assertThat(response.defaultDataSourceId()).isEqualTo(projectLake.getId());
        assertThat(response.message()).contains("未识别默认数据湖数据源");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(projectLake.getId());
        assertThat(response.items().get(0).recommended()).isTrue();
    }

    @Test
    void listSelectionsSyncsDefaultDestinationBeforeReadingSources() {
        UUID platformSourceId = UUID.randomUUID();
        InfraDataSource syncedLake = source(platformSourceId, "数仓 (biadmin)", "POSTGRESQL", "jdbc:postgresql://dts-pg:5432/biadmin");
        when(defaultDestinationSyncService.checkDefaultDestinationStatus())
            .thenReturn(new DefaultDestinationSyncService.DefaultDestinationStatus(
                true,
                true,
                true,
                "数仓 (biadmin)",
                "postgresqlwriter",
                null,
                platformSourceId.toString()
            ));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(syncedLake));

        DataSourceSelectionService.DataSourceSelectionResponse response = new DataSourceSelectionService(
            dataSourceRepository,
            connectorRepository,
            defaultDestinationSyncService
        ).listSelections("DBT_TARGET", null);

        InOrder order = inOrder(defaultDestinationSyncService, dataSourceRepository);
        order.verify(defaultDestinationSyncService).checkDefaultDestinationStatus();
        order.verify(dataSourceRepository).findByStatusIgnoreCase("ACTIVE");
        assertThat(response.defaultDataSourceId()).isEqualTo(platformSourceId);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).recommended()).isTrue();
    }

    @Test
    void ingestionSourceIncludesManagedJdbcAndApiButExcludesUnsupportedTypesAndSecrets() throws Exception {
        InfraDataSource jdbc = source(
            UUID.randomUUID(),
            "CRM database",
            "MYSQL",
            "jdbc:mysql://db.internal:3306/crm"
        );
        InfraDataSource api = source(UUID.randomUUID(), "CRM API", "API", null);
        InfraDataSource kafka = source(UUID.randomUUID(), "event bus", "KAFKA", null);
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(jdbc, api, kafka));
        when(connectorRepository.findByStatusIgnoreCaseOrderByDisplayOrderAscConnectorKeyAsc("ACTIVE"))
            .thenReturn(List.of(connector("mysql", "MySQL", "DATABASE"), connector("api", "HTTP API", "API")));

        DataSourceSelectionService.DataSourceSelectionResponse response = new DataSourceSelectionService(
            dataSourceRepository,
            connectorRepository,
            defaultDestinationSyncService
        ).listSelections("INGESTION_SOURCE", null);

        assertThat(response.capability()).isEqualTo("INGESTION_SOURCE");
        assertThat(response.items())
            .extracting(DataSourceSelectionService.DataSourceSelectionItem::id)
            .containsExactlyInAnyOrder(jdbc.getId(), api.getId())
            .doesNotContain(kafka.getId());
        DataSourceSelectionService.DataSourceSelectionItem apiItem = response.items()
            .stream()
            .filter(item -> api.getId().equals(item.id()))
            .findFirst()
            .orElseThrow();
        assertThat(apiItem.connectorName()).isEqualTo("HTTP API");
        assertThat(apiItem.capabilities()).containsExactly("INGESTION_SOURCE");
        verifyNoInteractions(defaultDestinationSyncService);

        String serialized = new ObjectMapper().writeValueAsString(response);
        assertThat(serialized)
            .doesNotContain("jdbcUrl", "username", "props", "secureProps", "db.internal", "biadmin")
            .contains("connectorKey", "connectorName", "capabilities");
    }

    @Test
    void departmentMaintainerCannotForgeActiveDepartmentHeader() {
        authenticateDepartmentOwner("dept-a");
        InfraDataSource own = source(UUID.randomUUID(), "own", "MYSQL", "jdbc:mysql://db-a:3306/app");
        own.setOwnerDept("dept-a");
        InfraDataSource foreign = source(UUID.randomUUID(), "foreign", "MYSQL", "jdbc:mysql://db-b:3306/app");
        foreign.setOwnerDept("dept-b");
        when(dataSourceRepository.findAll()).thenReturn(List.of(own, foreign));

        DataSourceSelectionService.DataSourceSelectionResponse response = new DataSourceSelectionService(
            dataSourceRepository,
            connectorRepository,
            defaultDestinationSyncService
        ).listSelections("INGESTION_SOURCE", "dept-b");

        assertThat(response.items())
            .extracting(DataSourceSelectionService.DataSourceSelectionItem::id)
            .containsExactly(own.getId());
    }

    private InfraDataSource source(UUID id, String name, String type, String jdbcUrl) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setName(name);
        source.setType(type);
        source.setConnectorKey(type == null ? null : type.toLowerCase(java.util.Locale.ROOT));
        source.setJdbcUrl(jdbcUrl);
        source.setUsername("biadmin");
        source.setStatus("ACTIVE");
        return source;
    }

    private InfraConnector connector(String key, String name, String category) {
        InfraConnector connector = new InfraConnector();
        connector.setId(UUID.randomUUID());
        connector.setConnectorKey(key);
        connector.setName(name);
        connector.setCategory(category);
        connector.setStatus("ACTIVE");
        return connector;
    }

    private void authenticateDepartmentOwner(String department) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "dept-owner")
            .claim("dept_code", department)
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(AuthoritiesConstants.DEPT_DATA_OWNER))
            )
        );
    }
}
