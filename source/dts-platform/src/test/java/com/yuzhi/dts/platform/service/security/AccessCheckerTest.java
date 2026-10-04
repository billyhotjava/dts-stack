package com.yuzhi.dts.platform.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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

    @Test
    void exactDepartmentGateRejectsTextAndNumericSuffixCollisions() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER);
        CatalogDataset textual = dataset("DATA_INTERNAL");
        textual.setOwnerDept("dept-ba");
        CatalogDataset numeric = dataset("DATA_INTERNAL");
        numeric.setOwnerDept("10010");

        assertThat(checker.departmentAllowedExact(textual, "dept-a")).isFalse();
        assertThat(checker.departmentAllowedExact(numeric, "10")).isFalse();
    }

    @Test
    void exactDepartmentGateKeepsExplicitGrantAndInstituteBypasses() {
        CatalogDataset granted = dataset("DATA_INTERNAL");
        granted.setId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        granted.setOwnerDept("dept-b");
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER);
        when(grantRepository.existsForDatasetAndUser(granted.getId(), null, "actor")).thenReturn(true);

        assertThat(checker.departmentAllowedExact(granted, "dept-a")).isTrue();

        CatalogDataset instituteDataset = dataset("DATA_INTERNAL");
        instituteDataset.setOwnerDept("dept-b");
        authenticate(AuthoritiesConstants.INST_DATA_OWNER);
        assertThat(checker.departmentAllowedExact(instituteDataset, "dept-a")).isTrue();
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("actor", "n/a", authority));
    }

    private CatalogDataset dataset(String classification) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setName("Orders");
        dataset.setEnabled(true);
        dataset.setClassification(classification);
        return dataset;
    }
}
