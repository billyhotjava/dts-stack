package com.yuzhi.dts.admin.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.OrganizationNode;
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
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 在 DTS 中新建/编辑用户后同步快照时，部门列要跟着 Keycloak 的 dept_code 走，
 * 否则列表会一直显示 MDM 写入的旧部门。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceDepartmentMirrorTest {

    @Mock
    private AdminKeycloakUserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
            userRepository,
            org.mockito.Mockito.mock(AdminApprovalRequestRepository.class),
            org.mockito.Mockito.mock(KeycloakAdminClient.class),
            org.mockito.Mockito.mock(AuditV2Service.class),
            org.mockito.Mockito.mock(ChangeRequestService.class),
            org.mockito.Mockito.mock(ChangeRequestRepository.class),
            org.mockito.Mockito.mock(AdminRoleAssignmentRepository.class),
            org.mockito.Mockito.mock(AdminRoleMemberRepository.class),
            organizationRepository,
            org.mockito.Mockito.mock(PersonProfileRepository.class),
            org.mockito.Mockito.mock(ChangeSnapshotFormatter.class),
            new ObjectMapper(),
            org.mockito.Mockito.mock(KeycloakAuthService.class),
            "",
            "",
            "dts-system",
            false
        );
        lenient().when(userRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("手工维护写入的节点 ID 被规整为组织编码与名称")
    void nodeIdAttributeIsNormalizedToDeptCode() {
        AdminKeycloakUser existing = snapshot("D001", "旧部门");
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
        when(organizationRepository.findFirstByDeptCodeIgnoreCase("42")).thenReturn(Optional.empty());
        when(organizationRepository.findById(42L)).thenReturn(Optional.of(org(42L, "D002", "新部门")));

        AdminKeycloakUser saved = service.syncSnapshot(user(Map.of("dept_code", List.of("42"))));

        assertThat(saved.getDeptCode()).isEqualTo("D002");
        assertThat(saved.getDeptName()).isEqualTo("新部门");
    }

    @Test
    @DisplayName("组织没有编码时保留节点 ID，名称取组织名")
    void nodeWithoutDeptCodeKeepsNodeId() {
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.empty());
        when(organizationRepository.findFirstByDeptCodeIgnoreCase("7")).thenReturn(Optional.empty());
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(org(7L, null, "手工部门")));

        AdminKeycloakUser saved = service.syncSnapshot(user(Map.of("dept_code", List.of("7"))));

        assertThat(saved.getDeptCode()).isEqualTo("7");
        assertThat(saved.getDeptName()).isEqualTo("手工部门");
    }

    @Test
    @DisplayName("Keycloak 没有 dept_code 属性时不清空已有部门")
    void missingAttributeKeepsExistingDepartment() {
        AdminKeycloakUser existing = snapshot("D001", "旧部门");
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));

        AdminKeycloakUser saved = service.syncSnapshot(user(Map.of()));

        assertThat(saved.getDeptCode()).isEqualTo("D001");
        assertThat(saved.getDeptName()).isEqualTo("旧部门");
    }

    private KeycloakUserDTO user(Map<String, List<String>> deptAttrs) {
        Map<String, List<String>> attrs = new HashMap<>(deptAttrs);
        attrs.put("person_security_level", List.of(SecurityLevelCatalog.DEFAULT_PERSONNEL_SECURITY_LEVEL.code()));
        KeycloakUserDTO dto = new KeycloakUserDTO();
        dto.setId("kc-1");
        dto.setUsername("alice");
        dto.setEnabled(true);
        dto.setAttributes(attrs);
        return dto;
    }

    private AdminKeycloakUser snapshot(String deptCode, String deptName) {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setKeycloakId("kc-1");
        user.setUsername("alice");
        user.setDeptCode(deptCode);
        user.setDeptName(deptName);
        return user;
    }

    private OrganizationNode org(Long id, String deptCode, String name) {
        OrganizationNode node = new OrganizationNode();
        node.setId(id);
        node.setDeptCode(deptCode);
        node.setName(name);
        return node;
    }
}
