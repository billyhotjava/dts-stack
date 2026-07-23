package com.yuzhi.dts.admin.web.rest.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.repository.AdminCustomRoleRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.inmemory.InMemoryStores;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService.TokenResponse;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlatformDirectoryResourceTest {

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private InMemoryStores stores;

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private AdminCustomRoleRepository customRoleRepository;

    @Mock
    private AdminRoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private PlatformDirectoryResource resource;

    @BeforeEach
    void setUp() {
        resource = new PlatformDirectoryResource(
            keycloakAuthService,
            keycloakAdminClient,
            stores,
            adminUserService,
            customRoleRepository,
            roleAssignmentRepository,
            organizationRepository
        );
        ReflectionTestUtils.setField(resource, "managementClientId", "admin-cli");
        ReflectionTestUtils.setField(resource, "managementClientSecret", "secret");
    }

    @Test
    void resolveUserByKeycloakIdAppliesTheAuthoritativeAdminDepartmentOverride() {
        KeycloakUserDTO raw = new KeycloakUserDTO();
        raw.setId("kc-100");
        raw.setUsername("alice");
        raw.setFullName("Raw Alice");
        raw.setAttributes(Map.of("dept_code", List.of("raw-dept")));
        when(keycloakAuthService.obtainClientCredentialsToken("admin-cli", "secret"))
            .thenReturn(new TokenResponse("token", null, null, null, null, null, null, null));
        when(keycloakAdminClient.findById("kc-100", "token")).thenReturn(Optional.of(raw));
        when(adminUserService.resolveDisplayNames(List.of("alice"))).thenReturn(Map.of("alice", "Alice"));
        when(adminUserService.resolveDepartments(List.of("alice")))
            .thenReturn(Map.of("alice", new AdminUserService.DepartmentInfo("authoritative-dept", "权威部门")));

        ResponseEntity<ApiResponse<PlatformDirectoryResource.UserSummary>> response = resource.resolveUser("kc-100");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
            .isEqualTo(new PlatformDirectoryResource.UserSummary("kc-100", "alice", "Alice", "authoritative-dept", "权威部门"));
        verify(keycloakAdminClient).findById("kc-100", "token");
    }
}
