package com.yuzhi.dts.admin.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
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
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ChangeSnapshotFormatter;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService.TokenResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
}
