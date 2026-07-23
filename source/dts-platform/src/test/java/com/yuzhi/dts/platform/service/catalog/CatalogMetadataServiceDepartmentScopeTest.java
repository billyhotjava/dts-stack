package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogMetadataServiceDepartmentScopeTest {

    private static final UUID DATASET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSchemaRepository columnRepository;

    @Mock
    private AccessChecker accessChecker;

    private CatalogMetadataService service;

    @BeforeEach
    void setUp() {
        service = new CatalogMetadataService(datasetRepository, tableRepository, columnRepository, accessChecker);
    }

    @Test
    void listUsesExactDepartmentGateSoSuffixCollisionsRemainHidden() {
        CatalogDataset dataset = dataset("dept-ba");
        when(datasetRepository.findAll()).thenReturn(List.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "dept-a")).thenReturn(false);

        OpenMetadataService.OpenMetadataTablePage page = service.listLocalTables(null, 50, "dept-a", null);

        assertThat(page.items()).isEmpty();
        verify(accessChecker).departmentAllowedExact(dataset, "dept-a");
        verify(accessChecker, never()).departmentAllowed(dataset, "dept-a");
    }

    @Test
    void detailUsesExactDepartmentGateSoNumericSuffixCollisionsReturnNotFound() {
        CatalogDataset dataset = dataset("10010");
        when(tableRepository.findById(DATASET_ID)).thenReturn(Optional.empty());
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "10")).thenReturn(false);

        assertThatThrownBy(() -> service.fetchLocalTableDetail("catalog:" + DATASET_ID, "10"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(accessChecker).departmentAllowedExact(dataset, "10");
        verify(accessChecker, never()).departmentAllowed(dataset, "10");
    }

    private static CatalogDataset dataset(String ownerDept) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        dataset.setName("orders");
        dataset.setEnabled(true);
        dataset.setOwnerDept(ownerDept);
        return dataset;
    }
}
