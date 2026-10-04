package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class QualityDatasetReadGuardTest {

    private static final UUID DATASET_ID = UUID.fromString("10000000-0000-0000-0000-000000000091");
    private static final UUID SOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000091");

    @Test
    void requiresDefaultLakeReadAndDepartmentAuthorization() {
        DefaultLakeDatasetGuard defaultLake = mock(DefaultLakeDatasetGuard.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        DataStandardSecurity security = mock(DataStandardSecurity.class);
        QualityEffectiveDepartmentResolver departmentResolver = mock(QualityEffectiveDepartmentResolver.class);
        CatalogDataset dataset = dataset(DATASET_ID);
        when(defaultLake.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(defaultLake.requireDefaultLakeSourceId()).thenReturn(SOURCE_ID);
        when(defaultLake.isDefaultLakeDataset(dataset, SOURCE_ID)).thenReturn(true);
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(departmentResolver.resolve("D01")).thenReturn("D01");
        when(security.hasDepartmentScope()).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "D01")).thenReturn(true);

        QualityDatasetReadGuard guard = new QualityDatasetReadGuard(defaultLake, accessChecker, security, departmentResolver);

        assertThat(guard.requireReadable(DATASET_ID, "D01")).isSameAs(dataset);
        verify(accessChecker).departmentAllowed(dataset, "D01");
    }

    @Test
    void rejectsDatasetWhenCatalogReadIsDenied() {
        DefaultLakeDatasetGuard defaultLake = mock(DefaultLakeDatasetGuard.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        DataStandardSecurity security = mock(DataStandardSecurity.class);
        QualityEffectiveDepartmentResolver departmentResolver = mock(QualityEffectiveDepartmentResolver.class);
        CatalogDataset dataset = dataset(DATASET_ID);
        when(defaultLake.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(defaultLake.requireDefaultLakeSourceId()).thenReturn(SOURCE_ID);
        when(defaultLake.isDefaultLakeDataset(dataset, SOURCE_ID)).thenReturn(true);
        when(accessChecker.canRead(dataset)).thenReturn(false);

        QualityDatasetReadGuard guard = new QualityDatasetReadGuard(defaultLake, accessChecker, security, departmentResolver);

        assertThatThrownBy(() -> guard.requireReadable(DATASET_ID, "D01"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void bulkFilteringNeverReturnsUnauthorizedDatasets() {
        DefaultLakeDatasetGuard defaultLake = mock(DefaultLakeDatasetGuard.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        DataStandardSecurity security = mock(DataStandardSecurity.class);
        QualityEffectiveDepartmentResolver departmentResolver = mock(QualityEffectiveDepartmentResolver.class);
        CatalogDataset readable = dataset(DATASET_ID);
        CatalogDataset denied = dataset(UUID.fromString("10000000-0000-0000-0000-000000000092"));
        when(defaultLake.requireDefaultLakeSourceId()).thenReturn(SOURCE_ID);
        when(defaultLake.isDefaultLakeDataset(readable, SOURCE_ID)).thenReturn(true);
        when(defaultLake.isDefaultLakeDataset(denied, SOURCE_ID)).thenReturn(true);
        when(accessChecker.canRead(readable)).thenReturn(true);
        when(accessChecker.canRead(denied)).thenReturn(false);
        when(security.hasInstituteScope()).thenReturn(true);

        QualityDatasetReadGuard guard = new QualityDatasetReadGuard(defaultLake, accessChecker, security, departmentResolver);

        assertThat(guard.readableDatasetIds(List.of(readable, denied), null)).containsExactly(DATASET_ID);
    }

    private static CatalogDataset dataset(UUID id) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setSourceId(SOURCE_ID);
        return dataset;
    }
}
