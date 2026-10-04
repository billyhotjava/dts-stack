package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.repository.AdminApprovalRequestRepository;
import com.yuzhi.dts.admin.repository.AdminCustomRoleRepository;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * F11-T03：权限目录包含首批建模/目录/治理动作码，格式为 模块:资源:动作。
 */
@ExtendWith(MockitoExtension.class)
class AdminApiResourcePermissionCatalogF11Test {

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
    @DisplayName("F11-UT-017：目录含建模/目录/治理动作码且格式合法、无重复")
    void catalogContainsModelingCatalogGovernanceActions() {
        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response =
            resource.permissionCatalog(new MockHttpServletRequest());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> sections = response.getBody().getData();
        Set<String> codes = new HashSet<>();
        for (Map<String, Object> section : sections) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> permissions = (List<Map<String, Object>>) section.get("permissions");
            for (Map<String, Object> permission : permissions) {
                String code = String.valueOf(permission.get("code"));
                assertThat(com.yuzhi.dts.common.security.PermissionCodes.isValid(code))
                    .as("权限码格式非法: %s", code)
                    .isTrue();
                assertThat(codes.add(code)).as("权限码重复: %s", code).isTrue();
            }
        }
        assertThat(codes).contains(
            com.yuzhi.dts.common.security.PermissionCodes.MODELING_MODEL_READ,
            com.yuzhi.dts.common.security.PermissionCodes.MODELING_MODEL_UPDATE,
            com.yuzhi.dts.common.security.PermissionCodes.MODELING_MODEL_SHARE,
            com.yuzhi.dts.common.security.PermissionCodes.CATALOG_DATASET_READ,
            com.yuzhi.dts.common.security.PermissionCodes.CATALOG_DATASET_EXPORT,
            com.yuzhi.dts.common.security.PermissionCodes.GOVERNANCE_RULE_MANAGE
        );
    }
}
