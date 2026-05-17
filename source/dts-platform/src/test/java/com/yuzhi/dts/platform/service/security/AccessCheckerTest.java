package com.yuzhi.dts.platform.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessCheckerTest {

    @Mock
    private ClassificationUtils classificationUtils;
    @Mock
    private CatalogDatasetGrantRepository grantRepository;
    @Mock
    private OrganizationVisibilityService organizationVisibilityService;

    private AccessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new AccessChecker(classificationUtils, grantRepository, organizationVisibilityService);
    }

    @Test
    void canRead_shouldDenyDatasetWithMissingClassification() {
        CatalogDataset dataset = dataset(null);

        boolean result = checker.canRead(dataset);

        assertThat(result).isFalse();
        verifyNoInteractions(classificationUtils);
    }

    @Test
    void canRead_shouldDelegateKnownClassificationToClassificationGate() {
        CatalogDataset dataset = dataset("INTERNAL");
        when(classificationUtils.canAccess("INTERNAL")).thenReturn(true);

        boolean result = checker.canRead(dataset);

        assertThat(result).isTrue();
    }

    private CatalogDataset dataset(String classification) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName("Orders");
        dataset.setEnabled(true);
        dataset.setClassification(classification);
        return dataset;
    }
}
