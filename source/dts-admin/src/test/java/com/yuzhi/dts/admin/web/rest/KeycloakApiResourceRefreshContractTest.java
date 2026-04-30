package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.security.session.AdminSessionRegistry;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.inmemory.InMemoryStores;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class KeycloakApiResourceRefreshContractTest {

    @Test
    void platformProfileUsesAuthenticatedPrincipalAndDoesNotLoginToKeycloak() {
        KeycloakAuthService keycloakAuthService = mock(KeycloakAuthService.class);
        AdminUserService adminUserService = mock(AdminUserService.class);
        AdminKeycloakUser snapshot = new AdminKeycloakUser();
        snapshot.setKeycloakId("kc-alice");
        snapshot.setUsername("alice");
        snapshot.setFullName("Alice");
        snapshot.setEnabled(true);
        snapshot.setPersonSecurityLevel("L2");
        snapshot.setRealmRoles(List.of("dept_data_owner"));
        when(adminUserService.findSnapshotByUsername("alice")).thenReturn(Optional.of(snapshot));
        KeycloakApiResource resource = new KeycloakApiResource(
            new InMemoryStores(),
            mock(AuditV2Service.class),
            keycloakAuthService,
            mock(KeycloakAdminClient.class),
            adminUserService,
            mock(AdminRoleAssignmentRepository.class),
            mock(AdminRoleMemberRepository.class),
            mock(AdminSessionRegistry.class),
            mock(AdminKeycloakUserRepository.class)
        );
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
            );

        try {
            ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.platformProfile(
                Map.of("username", "alice", "user", Map.of("username", "alice", "roles", List.of("ROLE_USER")))
            );

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) response.getBody().getData().get("user");
            assertThat(user).containsEntry("id", "kc-alice").containsEntry("fullName", "Alice");
            assertThat(user.get("roles")).asList().contains("ROLE_USER", "ROLE_DEPT_DATA_OWNER");
            verify(keycloakAuthService, never()).login(anyString(), anyString());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void refreshRejectsLocalRevokedSessionBeforeCallingKeycloak() {
        KeycloakAuthService keycloakAuthService = mock(KeycloakAuthService.class);
        AdminSessionRegistry sessionRegistry = mock(AdminSessionRegistry.class);
        when(sessionRegistry.assertRefreshTokenUsable("revoked-refresh")).thenThrow(new IllegalStateException("session_revoked"));
        KeycloakApiResource resource = new KeycloakApiResource(
            new InMemoryStores(),
            mock(AuditV2Service.class),
            keycloakAuthService,
            mock(KeycloakAdminClient.class),
            mock(AdminUserService.class),
            mock(AdminRoleAssignmentRepository.class),
            mock(AdminRoleMemberRepository.class),
            sessionRegistry,
            mock(AdminKeycloakUserRepository.class)
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.refresh(
            Map.of("refreshToken", "revoked-refresh"),
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("登录状态已失效");
        verify(keycloakAuthService, never()).refreshTokens(anyString());
    }
}
