package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.notify.DtsCommonNotifyClient;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.common.audit.AuditStage;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AdminApiResourceChangeAuditTest {

    @Mock
    private AdminAuditService auditService;
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

    private AdminApiResource resource;

    @BeforeEach
    void setUp() {
        resource = new AdminApiResource(
            auditService,
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
            adminUserService
        );
    }

    @Test
    void recordChangeExecutionAuditEmitsRequesterSuccess() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(98L);
        cr.setResourceType("ROLE");
        cr.setAction("UPDATE");
        cr.setRequestedBy("sysadmin");
        cr.setResourceId("123");
        cr.setStatus("APPROVED");
        cr.setDecidedBy("authadmin");
        cr.setDecidedAt(Instant.parse("2025-01-01T01:02:03Z"));
        cr.setReason("auto-approval");

        resource.recordChangeExecutionAudit(cr, AuditStage.SUCCESS);

        ArgumentCaptor<Map<String, Object>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService)
            .recordAction(eq("sysadmin"), eq("ADMIN_ROLE_UPDATE"), eq(AuditStage.SUCCESS), eq("123"), detailCaptor.capture());
        Map<String, Object> detail = detailCaptor.getValue();
        assertThat(detail.get("changeRequestId")).isEqualTo(98L);
        assertThat(detail.get("status")).isEqualTo("APPROVED");
        assertThat(detail.get("requestedBy")).isEqualTo("sysadmin");
        assertThat(detail.get("decidedBy")).isEqualTo("authadmin");
        assertThat(detail.get("stage")).isEqualTo("SUCCESS");
    }

    @Test
    void recordChangeExecutionAuditSkipsWhenRequesterMissing() {
        ChangeRequest cr = new ChangeRequest();
        cr.setResourceType("PORTAL_MENU");
        cr.setAction("CREATE");
        cr.setRequestedBy("  ");

        resource.recordChangeExecutionAudit(cr, AuditStage.SUCCESS);

        verify(auditService, never()).recordAction(
            org.mockito.Mockito.anyString(),
            org.mockito.Mockito.anyString(),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.anyString(),
            org.mockito.Mockito.anyMap()
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
}
