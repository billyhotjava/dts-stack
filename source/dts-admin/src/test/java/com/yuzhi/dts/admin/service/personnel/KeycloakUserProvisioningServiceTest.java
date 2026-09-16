package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService.TokenResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakUserProvisioningServiceTest {

    @Test
    void provisionShouldNotCreateUserWhenStrictUsernameLookupFails() {
        KeycloakAdminClient keycloakAdminClient = org.mockito.Mockito.mock(KeycloakAdminClient.class);
        KeycloakAuthService keycloakAuthService = org.mockito.Mockito.mock(KeycloakAuthService.class);
        OrganizationRepository organizationRepository = org.mockito.Mockito.mock(OrganizationRepository.class);
        KeycloakUserProvisioningService service = new KeycloakUserProvisioningService(
            keycloakAdminClient,
            keycloakAuthService,
            organizationRepository,
            new MdmGatewayProperties(),
            "admin-cli",
            "secret"
        );
        PersonnelPayload payload = payload("alice");
        when(keycloakAuthService.obtainClientCredentialsToken("admin-cli", "secret"))
            .thenReturn(new TokenResponse("token", null, null, null, null, null, null, null));
        when(keycloakAdminClient.findByUsernameStrict("alice", "token")).thenThrow(new IllegalStateException("Keycloak 查询失败"));

        assertThatThrownBy(() -> service.provision(payload)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Keycloak 查询失败");

        verify(keycloakAdminClient, never()).createUser(any(KeycloakUserDTO.class), eq("token"));
    }

    @Test
    void provisionShouldRemoveStaleDeptGroupWhenDeptCodeChanges() {
        KeycloakAdminClient keycloakAdminClient = org.mockito.Mockito.mock(KeycloakAdminClient.class);
        KeycloakAuthService keycloakAuthService = org.mockito.Mockito.mock(KeycloakAuthService.class);
        OrganizationRepository organizationRepository = org.mockito.Mockito.mock(OrganizationRepository.class);
        KeycloakUserProvisioningService service = new KeycloakUserProvisioningService(
            keycloakAdminClient,
            keycloakAuthService,
            organizationRepository,
            new MdmGatewayProperties(),
            "admin-cli",
            "secret"
        );

        when(keycloakAuthService.obtainClientCredentialsToken("admin-cli", "secret"))
            .thenReturn(new TokenResponse("token", null, null, null, null, null, null, null));
        KeycloakUserDTO existing = new KeycloakUserDTO();
        existing.setId("kc-1");
        existing.setUsername("alice");
        existing.setFullName("Alice");
        when(keycloakAdminClient.findByUsernameStrict("alice", "token")).thenReturn(java.util.Optional.of(existing));

        OrganizationNode newDept = new OrganizationNode();
        newDept.setDeptCode("D002");
        newDept.setName("新部门");
        newDept.setKeycloakGroupId("grp-new");
        when(organizationRepository.findFirstByDeptCodeIgnoreCase("D002")).thenReturn(java.util.Optional.of(newDept));

        KeycloakGroupDTO staleGroup = new KeycloakGroupDTO();
        staleGroup.setId("grp-old");
        staleGroup.setPath("/旧部门");
        when(keycloakAdminClient.listUserGroups("kc-1", "token")).thenReturn(java.util.List.of(staleGroup));

        OrganizationNode oldDept = new OrganizationNode();
        oldDept.setDeptCode("D001");
        oldDept.setName("旧部门");
        oldDept.setKeycloakGroupId("grp-old");
        when(organizationRepository.findByKeycloakGroupId("grp-old")).thenReturn(java.util.Optional.of(oldDept));

        KeycloakUserProvisioningService.ProvisionResult result = service.provision(payloadWithDept("alice", "D002"));

        assertThat(result.keycloakUserId()).isEqualTo("kc-1");
        verify(keycloakAdminClient).removeUserFromGroup("kc-1", "grp-old", "token");
        verify(keycloakAdminClient).addUserToGroup("kc-1", "grp-new", "token");
    }

    @Test
    void provisionShouldKeepNonDepartmentGroupsUntouched() {
        KeycloakAdminClient keycloakAdminClient = org.mockito.Mockito.mock(KeycloakAdminClient.class);
        KeycloakAuthService keycloakAuthService = org.mockito.Mockito.mock(KeycloakAuthService.class);
        OrganizationRepository organizationRepository = org.mockito.Mockito.mock(OrganizationRepository.class);
        KeycloakUserProvisioningService service = new KeycloakUserProvisioningService(
            keycloakAdminClient,
            keycloakAuthService,
            organizationRepository,
            new MdmGatewayProperties(),
            "admin-cli",
            "secret"
        );

        when(keycloakAuthService.obtainClientCredentialsToken("admin-cli", "secret"))
            .thenReturn(new TokenResponse("token", null, null, null, null, null, null, null));
        KeycloakUserDTO existing = new KeycloakUserDTO();
        existing.setId("kc-1");
        existing.setUsername("alice");
        existing.setFullName("Alice");
        when(keycloakAdminClient.findByUsernameStrict("alice", "token")).thenReturn(java.util.Optional.of(existing));

        OrganizationNode newDept = new OrganizationNode();
        newDept.setDeptCode("D002");
        newDept.setName("新部门");
        newDept.setKeycloakGroupId("grp-new");
        when(organizationRepository.findFirstByDeptCodeIgnoreCase("D002")).thenReturn(java.util.Optional.of(newDept));

        KeycloakGroupDTO adhocGroup = new KeycloakGroupDTO();
        adhocGroup.setId("grp-adhoc");
        adhocGroup.setPath("/专项组");
        when(keycloakAdminClient.listUserGroups("kc-1", "token")).thenReturn(java.util.List.of(adhocGroup));
        when(organizationRepository.findByKeycloakGroupId("grp-adhoc")).thenReturn(java.util.Optional.empty());

        service.provision(payloadWithDept("alice", "D002"));

        verify(keycloakAdminClient, never()).removeUserFromGroup(eq("kc-1"), eq("grp-adhoc"), eq("token"));
        verify(keycloakAdminClient).addUserToGroup("kc-1", "grp-new", "token");
    }

    private PersonnelPayload payload(String account) {
        return new PersonnelPayload(
            "P001",
            "EXT001",
            account,
            "Alice",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "ACTIVE",
            null,
            null,
            Map.of("person_security_level", "3")
        );
    }

    private PersonnelPayload payloadWithDept(String account, String deptCode) {
        return new PersonnelPayload(
            "P001",
            "EXT001",
            account,
            "Alice",
            null,
            deptCode,
            null,
            null,
            null,
            null,
            null,
            null,
            "ACTIVE",
            null,
            null,
            Map.of("person_security_level", "3")
        );
    }
}
