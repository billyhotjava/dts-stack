package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetSecurityMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetSecurityMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogSecurityResourceTest {

    private final CatalogDatasetRepository datasetRepo = mock(CatalogDatasetRepository.class);
    private final CatalogDatasetSecurityMappingRepository mappingRepo = mock(CatalogDatasetSecurityMappingRepository.class);
    private final CatalogTableSchemaRepository tableRepo = mock(CatalogTableSchemaRepository.class);
    private final CatalogColumnSchemaRepository columnRepo = mock(CatalogColumnSchemaRepository.class);
    private final CatalogDatasetGrantRepository grantRepo = mock(CatalogDatasetGrantRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccessChecker accessChecker = mock(AccessChecker.class);
    private final CatalogResourceHelper helper = mock(CatalogResourceHelper.class);

    private final CatalogSecurityResource resource = new CatalogSecurityResource(
        datasetRepo,
        mappingRepo,
        tableRepo,
        columnRepo,
        grantRepo,
        audit,
        accessChecker,
        helper
    );

    @Test
    void upsertDatasetSecurityMapping_clearsMappingWithNullablePayload() {
        UUID datasetId = UUID.randomUUID();
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        CatalogTableSchema table = new CatalogTableSchema();
        table.setId(UUID.randomUUID());
        table.setDataset(dataset);
        CatalogColumnSchema tenant = column("tenant_id", table);
        CatalogColumnSchema dept = column("source_system", table);

        CatalogDatasetSecurityMapping existing = new CatalogDatasetSecurityMapping();
        existing.setDatasetId(datasetId);
        existing.setDataLevelField("tenant_id");
        existing.setDeptField("source_system");

        when(datasetRepo.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(tableRepo.findByDataset(dataset)).thenReturn(List.of(table));
        when(columnRepo.findByTable(table)).thenReturn(List.of(tenant, dept));
        when(mappingRepo.findById(datasetId)).thenReturn(Optional.of(existing));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("dataLevelField", null);
        request.put("deptField", null);

        ApiResponse<Map<String, Object>> response = resource.upsertDatasetSecurityMapping(datasetId, request);

        assertThat(response.getData()).containsEntry("datasetId", datasetId.toString());
        assertThat(response.getData()).containsEntry("dataLevelField", null);
        assertThat(response.getData()).containsEntry("deptField", null);
        verify(mappingRepo).delete(existing);
        verify(audit).auditAction(eq("CATALOG_SECURITY_MAPPING_EDIT"), any(), eq(datasetId.toString()), any());
    }

    private CatalogColumnSchema column(String name, CatalogTableSchema table) {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setId(UUID.randomUUID());
        column.setTable(table);
        column.setName(name);
        column.setDataType("string");
        return column;
    }
}
