package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.analytics.SemanticContractPublishService;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.DefaultLakeDatasetGuard;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.modeling.ModelGenerationService;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelingSqlModelResourceTest {

    @Mock
    private ModelingSqlModelService sqlModelService;

    @Mock
    private ModelGenerationService generationService;

    @Mock
    private ModelingSqlModelRepository sqlModelRepository;

    @Mock
    private InfraOdsTableMappingRepository odsTableMappingRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private DataStandardSecurity security;

    @Mock
    private SemanticContractPublishService semanticContractPublishService;

    @Mock
    private DefaultLakeDatasetGuard defaultLakeDatasetGuard;

    @Test
    void listDbtSourcesAllowsExplicitNonDefaultPlatformSourceId() {
        UUID defaultSourceId = UUID.randomUUID();
        UUID projectSourceId = UUID.randomUUID();
        InfraOdsTableMapping mapping = mapping(projectSourceId, "ods", "ods_project_budget");
        when(defaultLakeDatasetGuard.currentDefaultLakeSourceId()).thenReturn(Optional.of(defaultSourceId));
        when(odsTableMappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc()).thenReturn(List.of(mapping));
        when(datasetRepository.existsBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(
            eq(projectSourceId),
            eq("ods"),
            eq("ods_project_budget"),
            eq("ODS")
        )).thenReturn(true);
        InfraDataSource source = new InfraDataSource();
        source.setId(projectSourceId);
        source.setName("项目湖仓");
        when(dataSourceRepository.findById(projectSourceId)).thenReturn(Optional.of(source));

        List<Map<String, Object>> rows = resource().listDbtSources(null, projectSourceId, "D1").getData();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("sourceDataSourceId", projectSourceId);
        assertThat(rows.get(0)).containsEntry("sourceDataSourceName", "项目湖仓");
    }

    private ModelingSqlModelResource resource() {
        return new ModelingSqlModelResource(
            sqlModelService,
            generationService,
            sqlModelRepository,
            odsTableMappingRepository,
            datasetRepository,
            dataSourceRepository,
            auditService,
            security,
            semanticContractPublishService,
            defaultLakeDatasetGuard
        );
    }

    private InfraOdsTableMapping mapping(UUID connectionId, String schema, String table) {
        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setConnectionId(connectionId);
        mapping.setOdsSchema(schema);
        mapping.setOdsTable(table);
        mapping.setDescription("项目预算");
        mapping.setSystemCode("PROJECT");
        mapping.setBizCode("PROJECT");
        mapping.setEntityCode("budget");
        mapping.setEnabled(true);
        return mapping;
    }
}
