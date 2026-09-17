package com.yuzhi.dts.admin.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminApprovalRequestRepository;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.repository.PersonProfileRepository;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.audit.ChangeSnapshotFormatter;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 回归：MDM 侧调岗后部门显示不更新。
 *
 * <p>根因是 resolveDepartments 只读 person_profile，而该表自 4d53821ba
 * 「移除 PersonProfile 写入」起再无写入方。部门归属现已回归 Keycloak 快照。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceResolveDepartmentsTest {

    @Mock
    private AdminKeycloakUserRepository userRepository;

    @Mock
    private AdminApprovalRequestRepository approvalRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private AuditV2Service auditV2Service;

    @Mock
    private ChangeRequestService changeRequestService;

    @Mock
    private ChangeRequestRepository changeRequestRepository;

    @Mock
    private AdminRoleAssignmentRepository roleAssignRepo;

    @Mock
    private AdminRoleMemberRepository roleMemberRepo;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PersonProfileRepository personProfileRepository;

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private ChangeSnapshotFormatter changeSnapshotFormatter;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
            userRepository,
            approvalRepository,
            keycloakAdminClient,
            auditV2Service,
            changeRequestService,
            changeRequestRepository,
            roleAssignRepo,
            roleMemberRepo,
            organizationRepository,
            personProfileRepository,
            changeSnapshotFormatter,
            new ObjectMapper(),
            keycloakAuthService,
            "",
            "",
            "dts-system",
            false
        );
    }

    @Test
    @DisplayName("部门取自 Keycloak 快照，不再查询已停写的 person_profile")
    void resolveDepartmentsReadsKeycloakSnapshotOnly() {
        when(userRepository.findByUsernameInIgnoreCase(anyCollection())).thenReturn(List.of(snapshot("alice", "D002", "新部门")));

        Map<String, AdminUserService.DepartmentInfo> result = service.resolveDepartments(List.of("alice"));

        assertThat(result).containsOnlyKeys("alice");
        assertThat(result.get("alice").deptCode()).isEqualTo("D002");
        assertThat(result.get("alice").deptName()).isEqualTo("新部门");
        verify(personProfileRepository, never()).findByAnyIdentifierLowerIn(anyCollection());
    }

    @Test
    @DisplayName("大小写与空白不影响匹配，返回键保持调用方原样")
    void resolveDepartmentsKeepsCallerSpellingAndTrimsInput() {
        when(userRepository.findByUsernameInIgnoreCase(anyCollection())).thenReturn(List.of(snapshot("Alice", "D002", "新部门")));

        Map<String, AdminUserService.DepartmentInfo> result = service.resolveDepartments(List.of("  ALICE  "));

        assertThat(result).containsOnlyKeys("ALICE");
        assertThat(result.get("ALICE").deptCode()).isEqualTo("D002");
    }

    @Test
    @DisplayName("快照无部门时返回空，而不是回退到过期来源")
    void resolveDepartmentsOmitsUserWithoutDept() {
        when(userRepository.findByUsernameInIgnoreCase(anyCollection())).thenReturn(List.of(snapshot("bob", null, null)));

        Map<String, AdminUserService.DepartmentInfo> result = service.resolveDepartments(List.of("bob"));

        assertThat(result).isEmpty();
        verify(personProfileRepository, never()).findByAnyIdentifierLowerIn(anyCollection());
    }

    @Test
    @DisplayName("空输入直接返回，不触碰仓储")
    void resolveDepartmentsReturnsEmptyForBlankInput() {
        assertThat(service.resolveDepartments(List.of())).isEmpty();
        assertThat(service.resolveDepartments(List.of("   "))).isEmpty();

        verify(userRepository, never()).findByUsernameInIgnoreCase(anyCollection());
    }

    @Test
    @DisplayName("快照缺部门名称时按组织编码补齐")
    void resolveDepartmentsFillsMissingNameFromOrganization() {
        when(userRepository.findByUsernameInIgnoreCase(anyCollection())).thenReturn(List.of(snapshot("alice", "D002", null)));
        when(organizationRepository.findByDeptCodeLowerIn(anyCollection())).thenReturn(List.of(org(9L, "D002", "新部门")));

        Map<String, AdminUserService.DepartmentInfo> result = service.resolveDepartments(List.of("alice"));

        assertThat(result.get("alice").deptCode()).isEqualTo("D002");
        assertThat(result.get("alice").deptName()).isEqualTo("新部门");
    }

    @Test
    @DisplayName("部门改名后以组织表当前名称为准")
    void resolveDepartmentsPrefersCurrentOrganizationName() {
        when(userRepository.findByUsernameInIgnoreCase(anyCollection())).thenReturn(List.of(snapshot("alice", "D002", "旧名称")));
        when(organizationRepository.findByDeptCodeLowerIn(anyCollection())).thenReturn(List.of(org(9L, "D002", "改名后")));

        assertThat(service.resolveDepartments(List.of("alice")).get("alice").deptName()).isEqualTo("改名后");
    }

    @Test
    @DisplayName("部门列存的是节点 ID 时按 ID 取组织名称")
    void resolveDepartmentsResolvesNodeIdCode() {
        when(userRepository.findByUsernameInIgnoreCase(anyCollection())).thenReturn(List.of(snapshot("alice", "42", null)));
        when(organizationRepository.findAllById(anyIterable())).thenReturn(List.of(org(42L, null, "手工部门")));

        assertThat(service.resolveDepartments(List.of("alice")).get("alice").deptName()).isEqualTo("手工部门");
    }

    private com.yuzhi.dts.admin.domain.OrganizationNode org(Long id, String deptCode, String name) {
        com.yuzhi.dts.admin.domain.OrganizationNode node = new com.yuzhi.dts.admin.domain.OrganizationNode();
        node.setId(id);
        node.setDeptCode(deptCode);
        node.setName(name);
        return node;
    }

    private AdminKeycloakUser snapshot(String username, String deptCode, String deptName) {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setKeycloakId("kc-" + username);
        user.setUsername(username);
        user.setDeptCode(deptCode);
        user.setDeptName(deptName);
        return user;
    }
}
