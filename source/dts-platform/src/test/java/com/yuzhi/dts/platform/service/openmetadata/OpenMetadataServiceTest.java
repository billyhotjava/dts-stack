package com.yuzhi.dts.platform.service.openmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.OpenMetadataProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpenMetadataServiceTest {

    @Test
    void fetchTableForDatasetFallsBackWhenPatternIsTruncated() {
        OpenMetadataClient client = Mockito.mock(OpenMetadataClient.class);
        OpenMetadataProperties props = new OpenMetadataProperties();
        props.setEnabled(true);
        props.setServiceName("hive");
        props.setTableFqnPattern("{service");
        props.setTableFields("columns");
        OpenMetadataService service = new OpenMetadataService(client, props);

        CatalogDataset dataset = new CatalogDataset();
        dataset.setName("orders");
        dataset.setHiveDatabase("ods");
        dataset.setHiveTable("orders");

        when(client.getTableByFqn("hive.ods.orders", "columns")).thenReturn(Optional.of(Map.of("id", "table-1")));

        OpenMetadataService.OpenMetadataResult result = service.fetchTableForDataset(dataset);

        assertThat(result.found()).isTrue();
        assertThat(result.fqn()).isEqualTo("hive.ods.orders");
        assertThat(result.metadataSource()).isEqualTo(OpenMetadataService.SOURCE_OPENMETADATA);
        verify(client, never()).getTableByFqn("{service", "columns");
    }

    @Test
    void summaryKeepsMetadataSourceAndFallbackReason() {
        OpenMetadataClient client = Mockito.mock(OpenMetadataClient.class);
        OpenMetadataProperties props = new OpenMetadataProperties();
        OpenMetadataService service = new OpenMetadataService(client, props);

        OpenMetadataService.OpenMetadataResult result = OpenMetadataService.OpenMetadataResult.notFound(
            "catalog:table-1",
            "未找到本地元数据",
            OpenMetadataService.SOURCE_CATALOG,
            "OpenMetadata: 未找到匹配的元数据"
        );

        OpenMetadataService.OpenMetadataSummary summary = service.summarize(result);

        assertThat(summary.metadataSource()).isEqualTo(OpenMetadataService.SOURCE_CATALOG);
        assertThat(summary.fallbackReason()).isEqualTo("OpenMetadata: 未找到匹配的元数据");
    }

    @Test
    void fetchTableForDatasetUsesSourceJdbcDatabaseAndDatasetSchema() {
        OpenMetadataClient client = Mockito.mock(OpenMetadataClient.class);
        InfraDataSourceRepository dataSourceRepository = Mockito.mock(InfraDataSourceRepository.class);
        OpenMetadataProperties props = new OpenMetadataProperties();
        props.setEnabled(true);
        props.setServiceName("hive");
        props.setTableFqnPattern("{service}.{database}.{table}");
        props.setTableFields("columns");
        OpenMetadataService service = new OpenMetadataService(client, props, dataSourceRepository);

        UUID sourceId = UUID.randomUUID();
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName("orders");
        dataset.setSourceId(sourceId);
        dataset.setHiveDatabase("ods");
        dataset.setHiveTable("orders");

        InfraDataSource source = new InfraDataSource();
        source.setJdbcUrl("jdbc:postgresql://warehouse:5432/biadmin?sslmode=disable");
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(client.getTableByFqn("hive.biadmin.orders", "columns")).thenReturn(Optional.empty());
        when(client.getTableByFqn("hive.biadmin.ods.orders", "columns")).thenReturn(Optional.of(Map.of("id", "table-1")));

        OpenMetadataService.OpenMetadataResult result = service.fetchTableForDataset(dataset);

        assertThat(result.found()).isTrue();
        assertThat(result.fqn()).isEqualTo("hive.biadmin.ods.orders");
    }

}
