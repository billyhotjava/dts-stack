package com.yuzhi.dts.platform.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.policy.AssetAction;
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
class AccessCheckerCanPerformTest {

    @Mock
    private ClassificationUtils classificationUtils;

    @Mock
    private CatalogDatasetGrantRepository grantRepository;

    @Mock
    private OrganizationVisibilityService organizationVisibilityService;

    @Mock
    private AssetActionPolicyEvaluator actionPolicyEvaluator;

    private AccessChecker checker;
    private CatalogDataset dataset;

    @BeforeEach
    void setUp() {
        checker = new AccessChecker(
            classificationUtils,
            grantRepository,
            organizationVisibilityService,
            actionPolicyEvaluator
        );
        dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        dataset.setName("finance_orders");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesToDenyByDefaultEvaluator() {
        when(actionPolicyEvaluator.canPerform(dataset, AssetAction.EXPORT)).thenReturn(false);

        assertThat(checker.canPerform(dataset, AssetAction.EXPORT)).isFalse();
    }

    @Test
    void superAdminKeepsTheExistingBypassContract() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(new TestingAuthenticationToken("opadmin", "n/a", AuthoritiesConstants.OP_ADMIN));

        assertThat(checker.canPerform(dataset, AssetAction.DESTROY)).isTrue();
        verifyNoInteractions(actionPolicyEvaluator);
    }

    @Test
    void instituteDataOwnerCanMaintainAssetsWithoutPerAssetPolicyGrant() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken("institute-owner", "n/a", AuthoritiesConstants.INST_DATA_OWNER)
            );

        assertThat(checker.canPerform(dataset, AssetAction.CREATE)).isTrue();
        assertThat(checker.canPerform(dataset, AssetAction.UPDATE)).isTrue();
        assertThat(checker.canPerform(dataset, AssetAction.ARCHIVE)).isTrue();
        verifyNoInteractions(actionPolicyEvaluator);
    }

    @Test
    void departmentDataOwnerStillUsesTheScopedPolicyMatrix() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken("department-owner", "n/a", AuthoritiesConstants.DEPT_DATA_OWNER)
            );
        when(actionPolicyEvaluator.canPerform(dataset, AssetAction.UPDATE)).thenReturn(false);

        assertThat(checker.canPerform(dataset, AssetAction.UPDATE)).isFalse();
    }
}
