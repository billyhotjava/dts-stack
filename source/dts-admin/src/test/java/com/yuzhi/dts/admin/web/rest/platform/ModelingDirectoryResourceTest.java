package com.yuzhi.dts.admin.web.rest.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.yuzhi.dts.admin.domain.*;
import com.yuzhi.dts.admin.domain.enumeration.PersonLifecycleStatus;
import com.yuzhi.dts.admin.repository.*;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ModelingDirectoryResourceTest {
    private final AdminInboundServiceAuthenticator authenticator=mock(AdminInboundServiceAuthenticator.class);
    private final KeycloakAuthService auth=mock(KeycloakAuthService.class);
    private final KeycloakAdminClient users=mock(KeycloakAdminClient.class);
    private final OrganizationRepository organizations=mock(OrganizationRepository.class);
    private final PersonProfileRepository profiles=mock(PersonProfileRepository.class);
    private final ModelingDirectoryResource resource=new ModelingDirectoryResource(authenticator,auth,users,organizations,profiles);
    private final MockHttpServletRequest request=new MockHttpServletRequest();
    private KeycloakUserDTO user;
    @BeforeEach void prepare(){
        ReflectionTestUtils.setField(resource,"clientId","client");ReflectionTestUtils.setField(resource,"clientSecret","secret");
        when(authenticator.authenticate(request,"dts-platform")).thenReturn(new AdminInboundServiceAuthenticator.Decision(true,"dts-platform","accepted"));
        when(auth.obtainClientCredentialsToken("client","secret")).thenReturn(new KeycloakAuthService.TokenResponse("token",null,null,null,null,null,null,null));
        user=new KeycloakUserDTO();user.setId("stable-id");user.setUsername("alice");user.setEnabled(true);user.setAttributes(Map.of("dept_code",List.of("old-dept")));
        when(users.currentUser("stable-id","token")).thenReturn(Optional.of(new KeycloakAdminClient.CurrentUser(user,List.of("ROLE_DEPT_DATA_OWNER"))));
        when(organizations.findAll()).thenReturn(List.of(department(1L,"old-dept"),department(2L,"current-dept")));
        when(profiles.findByAnyIdentifierLowerIn(anyCollection())).thenReturn(List.of());
    }
    @Test void preservesStableIdAndCanonicalDepartment(){var identity=resource.resolve("stable-id",request).getData();assertThat(identity.id()).isEqualTo("stable-id");assertThat(identity.deptCode()).isEqualTo("old-dept");verify(users,never()).findByUsername(anyString(),anyString());}
    @Test void personnelDirectoryDepartmentOverridesStaleKeycloakAttribute(){
        PersonProfile profile=profile();when(profiles.findByAnyIdentifierLowerIn(anyCollection())).thenReturn(List.of(profile));
        assertThat(resource.resolve("stable-id",request).getData().deptCode()).isEqualTo("current-dept");
        profile.setLifecycleStatus(PersonLifecycleStatus.LEFT);
        assertThat(resource.resolve("stable-id",request).getData().enabled()).isFalse();
    }
    @Test void profileAndKeycloakFailuresRemainUnavailableRatherThanUsingStaleFacts(){
        when(profiles.findByAnyIdentifierLowerIn(anyCollection())).thenThrow(new IllegalStateException("directory down"));
        assertStatus(503,()->resource.resolve("stable-id",request));
        when(users.currentUser("stable-id","token")).thenThrow(new IllegalStateException("keycloak down"));
        assertStatus(503,()->resource.resolve("stable-id",request));
    }
    @Test void absentIdentityIsNotResolvedByUsername(){when(users.currentUser("stable-id","token")).thenReturn(Optional.empty());assertStatus(404,()->resource.resolve("stable-id",request));verify(users,never()).findByUsername(anyString(),anyString());}
    @Test void onlyAuthenticatedPlatformServiceCanReadIdentity(){when(authenticator.authenticate(request,"dts-platform")).thenReturn(new AdminInboundServiceAuthenticator.Decision(false,null,"denied"));assertStatus(403,()->resource.resolve("stable-id",request));verifyNoInteractions(users,auth,profiles);}
    @Test void nodeIdAliasMustResolveToExactNonRootDepartment(){
        var root=department(1L,"old-dept");root.setRoot(true);var node=department(2L,null);when(organizations.findAll()).thenReturn(List.of(root,node));
        assertThat(resource.resolve("stable-id",request).getData().deptCode()).isNull();
        user.setAttributes(Map.of("dept_code",List.of("2")));
        assertThat(resource.resolve("stable-id",request).getData().deptCode()).isEqualTo("2");
        assertThat(resource.departments(request).getData()).extracting(ModelingDirectoryResource.Department::code).containsExactly("2");
    }
    @Test void candidatesUseOneBatchedProfileLookupAndCurrentDepartment(){
        when(users.currentRoleMembers(anyString(),eq("token"))).thenReturn(List.of());
        when(users.currentRoleMembers("ROLE_DEPT_DATA_OWNER","token")).thenReturn(List.of(user));
        when(profiles.findByAnyIdentifierLowerIn(anyCollection())).thenReturn(List.of(profile()));
        assertThat(resource.candidates("alice","current-dept",request).getData()).extracting(ModelingDirectoryResource.Identity::id).containsExactly("stable-id");
        verify(profiles,times(1)).findByAnyIdentifierLowerIn(anyCollection());verify(users,never()).currentUser(anyString(),anyString());
    }
    private PersonProfile profile(){var p=new PersonProfile();p.setId(10L);p.setAccount("alice");p.setDeptCode("current-dept");p.setLifecycleStatus(PersonLifecycleStatus.ACTIVE);return p;}
    private OrganizationNode department(Long id,String code){var d=new OrganizationNode();d.setId(id);d.setDeptCode(code);d.setName("部门");return d;}
    private void assertStatus(int status,Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(status));}
}
