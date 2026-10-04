package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.AdminApprovalItem;
import com.yuzhi.dts.admin.domain.AdminApprovalRequest;
import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.repository.AdminApprovalRequestRepository;
import com.yuzhi.dts.admin.repository.AdminCustomRoleRepository;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.service.PortalMenuService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.audit.ChangeSnapshotFormatter;
import com.yuzhi.dts.admin.service.notify.DtsCommonNotifyClient;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.common.audit.AuditStage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AdminApiResourceChangeAuditTest {

    @Mock
    private AuditV2Service auditV2Service;
    @Mock
    private OrganizationService organizationService;
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
    private AdminKeycloakUserRepository userRepository;
    @Mock
    private AdminRoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private AdminRoleMemberRepository roleMemberRepository;
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
        // ChangeSnapshotFormatter is no longer pulled by every code path through AdminAuditService —
        // mark these stubs lenient so unused-stub strict-mode does not fail the test, while
        // keeping them available for paths that do format change snapshots.
        org.mockito.Mockito.lenient().when(changeSnapshotFormatter.format(org.mockito.Mockito.any(), org.mockito.Mockito.anyString())).thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient().when(changeSnapshotFormatter.format(org.mockito.Mockito.anyMap(), org.mockito.Mockito.anyMap(), org.mockito.Mockito.anyString())).thenReturn(java.util.List.of());
        resource = new AdminApiResource(
            auditV2Service,
            organizationService,
            null,
            changeRequestRepository,
            approvalRepository,
            changeRequestService,
            portalMenuService,
            datasetRepository,
            customRoleRepository,
            userRepository,
            roleAssignmentRepository,
            roleMemberRepository,
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

    @Test
    void changeRequestsHonorsStatusAndTypeWhenAugmentingApprovalViews() {
        AdminApprovalRequest approval = new AdminApprovalRequest();
        approval.setId(7101L);
        approval.setType("USER_CREATE");
        approval.setStatus("APPLIED");
        approval.setRequester("sysadmin");

        AdminApprovalItem item = new AdminApprovalItem();
        item.setTargetKind("USER");
        item.setTargetId("ptrdemo");
        item.setSeqNumber(1);
        item.setPayloadJson("{\"action\":\"create\",\"changeRequestId\":7052}");
        approval.addItem(item);

        when(changeRequestRepository.findByStatusAndResourceType("PENDING", "ROLE")).thenReturn(java.util.List.of());
        when(approvalRepository.findAll()).thenReturn(java.util.List.of(approval));

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = resource.changeRequests("PENDING", "ROLE", null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void batchUpdateMenuVisibilityCreatesOneApprovalForManyMenus() {
        ReflectionTestUtils.setField(resource, "requireMenuVisibilityApproval", true);
        PortalMenu first = portalMenu(101L, "workbench.overview", "workbench/overview", "我的概览");
        PortalMenu second = portalMenu(102L, "portal.search", "catalog/search", "数据搜索");
        when(portalMenuRepository.findById(101L)).thenReturn(java.util.Optional.of(first));
        when(portalMenuRepository.findById(102L)).thenReturn(java.util.Optional.of(second));
        ChangeRequest pending = new ChangeRequest();
        pending.setId(9001L);
        pending.setResourceType("PORTAL_MENU");
        pending.setAction("BATCH_UPDATE");
        pending.setStatus("PENDING");
        pending.setRequestedBy("sysadmin");
        pending.setPayloadJson(
            "{\"updates\":[{\"id\":101,\"allowedRoles\":[\"ROLE_EMPLOYEE\"]},{\"id\":102,\"allowedRoles\":[\"ROLE_EMPLOYEE\"]}]}"
        );
        when(changeRequestService.draft(eq("PORTAL_MENU"), eq("BATCH_UPDATE"), eq(null), org.mockito.Mockito.any(), org.mockito.Mockito.any(), eq("批量配置角色")))
            .thenReturn(pending);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.batchUpdateMenuVisibility(
            Map.of(
                "menuIds",
                List.of(101, 102),
                "roles",
                List.of("ROLE_EMPLOYEE"),
                "mode",
                "REPLACE",
                "reason",
                "批量配置角色"
            ),
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(changeRequestService, times(1))
            .draft(eq("PORTAL_MENU"), eq("BATCH_UPDATE"), eq(null), afterCaptor.capture(), org.mockito.Mockito.any(), eq("批量配置角色"));
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after).containsEntry("mode", "REPLACE");
        assertThat(after.get("updates")).asList().hasSize(2);
    }

    private PortalMenu portalMenu(Long id, String name, String path, String title) {
        PortalMenu menu = new PortalMenu();
        menu.setId(id);
        menu.setName(name);
        menu.setPath(path);
        menu.setMetadata("{\"title\":\"" + title + "\"}");
        return menu;
    }
}
