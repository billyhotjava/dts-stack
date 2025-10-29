package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.repository.AdminApprovalRequestRepository;
import com.yuzhi.dts.admin.repository.AdminCustomRoleRepository;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.service.OrganizationSyncService;
import com.yuzhi.dts.admin.service.PortalMenuService;
import com.yuzhi.dts.admin.service.auditv2.ChangeSnapshotFormatter;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.notify.DtsCommonNotifyClient;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AdminApiResourceChangeAuditTest {

    @Mock
    private AuditV2Service auditV2Service;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private OrganizationSyncService organizationSyncService;
    @Mock
    private ChangeRequestRepository changeRequestRepository;
    @Mock
    private AdminApprovalRequestRepository approvalRepository;
    @Mock
    private ChangeRequestService changeRequestService;
    @Mock
    private PortalMenuService portalMenuService;
    @Mock
    private AdminDatasetRepository datasetRepository;
    @Mock
    private AdminCustomRoleRepository customRoleRepository;
    @Mock
    private AdminRoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private SystemConfigRepository systemConfigRepository;
    @Mock
    private PortalMenuRepository portalMenuRepository;
    @Mock
    private PortalMenuVisibilityRepository portalMenuVisibilityRepository;
    @Mock
    private DtsCommonNotifyClient notifyClient;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AdminUserService adminUserService;
    @Mock
    private ChangeSnapshotFormatter changeSnapshotFormatter;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminApiResource resource;

    @BeforeEach
    void setUp() {
        when(changeSnapshotFormatter.format(org.mockito.Mockito.any(), org.mockito.Mockito.anyString())).thenReturn(java.util.List.of());
        when(changeSnapshotFormatter.format(org.mockito.Mockito.anyMap(), org.mockito.Mockito.anyMap(), org.mockito.Mockito.anyString())).thenReturn(java.util.List.of());
        resource = new AdminApiResource(
            auditV2Service,
            organizationService,
            organizationSyncService,
            changeRequestRepository,
            approvalRepository,
            changeRequestService,
            portalMenuService,
            datasetRepository,
            customRoleRepository,
            roleAssignmentRepository,
            systemConfigRepository,
            portalMenuRepository,
            portalMenuVisibilityRepository,
            notifyClient,
            organizationRepository,
            adminUserService,
            changeSnapshotFormatter,
            transactionManager,
            adminAuditService
        );
    }

    @Test
    void buildChangeActionCodeNormalizesTokens() {
        ChangeRequest cr = new ChangeRequest();
        cr.setResourceType("portal-menu");
        cr.setAction("create");

        String code = AdminApiResource.buildChangeActionCode(cr);

        assertThat(code).isEqualTo("ADMIN_PORTAL_MENU_CREATE");
    }

    @Test
    void resolveStageForChangeOutcomePrefersStatusOverAppliedFlag() {
        ChangeRequest cr = new ChangeRequest();
        cr.setStatus("APPLIED");

        AuditStage stage = resource.resolveStageForChangeOutcome(cr, false);

        assertThat(stage).isEqualTo(AuditStage.SUCCESS);
    }

    @Test
    void resolveStageForChangeOutcomeFallsBackToFailOnErrorMessage() {
        ChangeRequest cr = new ChangeRequest();
        cr.setStatus("APPLIED");
        cr.setLastError("timeout");

        AuditStage stage = resource.resolveStageForChangeOutcome(cr, true);

        assertThat(stage).isEqualTo(AuditStage.FAIL);
    }
}
