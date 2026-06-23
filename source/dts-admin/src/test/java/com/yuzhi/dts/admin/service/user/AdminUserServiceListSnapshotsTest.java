package com.yuzhi.dts.admin.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.AdminRoleMember;
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
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService.TokenResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceListSnapshotsTest {

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

    private AdminUserService serviceWithoutMgmtToken;

    @BeforeEach
    void setUp() {
        serviceWithoutMgmtToken = buildService("", "");
    }

    @Test
    void keywordEmptyResultShouldNotTriggerFullRefresh() {
        Page<AdminKeycloakUser> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(userRepository.findByUsernameContainingIgnoreCaseExcludingUsernames(anyString(), anyCollection(), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(empty);

        Page<AdminKeycloakUser> result = serviceWithoutMgmtToken.listSnapshots(0, 20, "u1001", null);

        assertThat(result.getTotalElements()).isZero();
        verify(keycloakAdminClient, never()).listUsers(anyInt(), anyInt(), anyString());
        verify(personProfileRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void firstPageEmptyWithoutFilterShouldTriggerRefresh() {
        AdminUserService serviceWithMgmtToken = buildService("mgmt-client", "mgmt-secret");
        Page<AdminKeycloakUser> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(userRepository.findAllExcludingUsernames(anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(empty);
        when(keycloakAuthService.obtainClientCredentialsToken(anyString(), anyString()))
            .thenReturn(new TokenResponse("token", null, null, null, null, null, null, null));
        when(keycloakAdminClient.listUsers(anyInt(), anyInt(), anyString())).thenReturn(List.of());
        when(personProfileRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty());

        serviceWithMgmtToken.listSnapshots(0, 20, null, null);

        verify(keycloakAdminClient, atLeastOnce()).listUsers(anyInt(), anyInt(), anyString());
        verify(personProfileRepository, atLeastOnce()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void keycloakRefreshShouldReadPersonnelSecurityLevelAlias() {
        AdminUserService serviceWithMgmtToken = buildService("mgmt-client", "mgmt-secret");
        Page<AdminKeycloakUser> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        KeycloakUserDTO remote = keycloakUser("kc-alias", "alias-user", Map.of("personnel_security_level", List.of("1")));
        when(userRepository.findAllExcludingUsernames(anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(empty);
        when(keycloakAuthService.obtainClientCredentialsToken(anyString(), anyString()))
            .thenReturn(new TokenResponse("token", null, null, null, null, null, null, null));
        when(keycloakAdminClient.listUsers(anyInt(), anyInt(), anyString())).thenReturn(List.of(remote));
        when(userRepository.findByKeycloakId("kc-alias")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("alias-user")).thenReturn(java.util.Optional.empty());
        when(personProfileRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty());

        serviceWithMgmtToken.listSnapshots(0, 20, null, null);

        ArgumentCaptor<AdminKeycloakUser> saved = ArgumentCaptor.forClass(AdminKeycloakUser.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPersonSecurityLevel()).isEqualTo("IMPORTANT");
    }

    @Test
    void syncSnapshotShouldReadPersonLevelAlias() {
        KeycloakUserDTO remote = keycloakUser("kc-sync", "sync-user", Map.of("person_level", List.of("CORE")));
        when(userRepository.findByKeycloakId("kc-sync")).thenReturn(java.util.Optional.empty());
        when(userRepository.save(any(AdminKeycloakUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminKeycloakUser saved = serviceWithoutMgmtToken.syncSnapshot(remote);

        assertThat(saved.getPersonSecurityLevel()).isEqualTo("CORE");
    }

    @Test
    void roleAssignmentUsersShouldFilterByUsernameFullNameDepartmentAndMarkExistingMembers() {
        AdminKeycloakUser zhang = user("zhangsan", "张三", "/总院/数据部");
        when(
            userRepository.findRoleAssignmentCandidates(
                eq("zhang"),
                eq("张"),
                eq("/总院/数据部"),
                eq(true),
                anyCollection(),
                anyCollection(),
                any(org.springframework.data.domain.Pageable.class)
            )
        )
            .thenReturn(new PageImpl<>(List.of(zhang), PageRequest.of(0, 20), 1));
        AdminRoleMember member = new AdminRoleMember();
        member.setRole("DATA_STEWARD");
        member.setUsername("zhangsan");
        member.setDisplayName("张三");
        when(roleMemberRepo.findByRoleIgnoreCase("DATA_STEWARD")).thenReturn(List.of(member));

        Page<AdminUserService.RoleAssignmentUser> result = serviceWithoutMgmtToken.listRoleAssignmentUsers(
            "DATA_STEWARD",
            0,
            20,
            "zhang",
            "张",
            "/总院/数据部",
            true
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        AdminUserService.RoleAssignmentUser row = result.getContent().get(0);
        assertThat(row.username()).isEqualTo("zhangsan");
        assertThat(row.fullName()).isEqualTo("张三");
        assertThat(row.groupPaths()).contains("/总院/数据部");
        assertThat(row.inRole()).isTrue();
    }

    @Test
    void roleAssignmentUsersShouldUseUnsortedPageableBecauseNativeQueryOwnsOrdering() {
        AdminKeycloakUser zhang = user("zhangsan", "张三", "/总院/数据部");
        when(userRepository.findRoleAssignmentCandidates(isNull(), isNull(), isNull(), isNull(), anyCollection(), anyCollection(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(zhang), PageRequest.of(0, 20), 1));

        serviceWithoutMgmtToken.listRoleAssignmentUsers("DATA_STEWARD", 0, 20, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findRoleAssignmentCandidates(isNull(), isNull(), isNull(), isNull(), anyCollection(), anyCollection(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    private AdminUserService buildService(String managementClientId, String managementClientSecret) {
        return new AdminUserService(
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
            managementClientId,
            managementClientSecret,
            "dts-system",
            false
        );
    }

    private static AdminKeycloakUser user(String username, String fullName, String groupPath) {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setKeycloakId("kc-" + username);
        user.setEmail(username + "@example.test");
        user.setGroupPaths(List.of(groupPath));
        user.setEnabled(true);
        user.setMdmEnabled(1);
        return user;
    }

    private static KeycloakUserDTO keycloakUser(String id, String username, Map<String, List<String>> attributes) {
        KeycloakUserDTO user = new KeycloakUserDTO();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(username);
        user.setEnabled(true);
        user.setAttributes(attributes);
        return user;
    }
}
