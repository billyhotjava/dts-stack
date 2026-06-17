package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceSelectionServiceTest {

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private DefaultDestinationSyncService defaultDestinationSyncService;

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
            defaultDestinationSyncService
        ).listSelections("DBT_TARGET", null);

        InOrder order = inOrder(defaultDestinationSyncService, dataSourceRepository);
        order.verify(defaultDestinationSyncService).checkDefaultDestinationStatus();
        order.verify(dataSourceRepository).findByStatusIgnoreCase("ACTIVE");
        assertThat(response.defaultDataSourceId()).isEqualTo(platformSourceId);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).recommended()).isTrue();
    }

    private InfraDataSource source(UUID id, String name, String type, String jdbcUrl) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setName(name);
        source.setType(type);
        source.setJdbcUrl(jdbcUrl);
        source.setUsername("biadmin");
        source.setStatus("ACTIVE");
        return source;
    }
}
