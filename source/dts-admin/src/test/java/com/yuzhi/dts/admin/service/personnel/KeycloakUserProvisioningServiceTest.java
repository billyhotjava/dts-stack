package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
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
}
