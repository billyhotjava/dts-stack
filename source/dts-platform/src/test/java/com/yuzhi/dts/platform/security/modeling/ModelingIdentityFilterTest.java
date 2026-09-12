package com.yuzhi.dts.platform.security.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.modeling.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;

class ModelingIdentityFilterTest {
    private final AdminDirectoryGateway directory=mock(AdminDirectoryGateway.class);
    private final ModelSpecAccessService access=mock(ModelSpecAccessService.class);
    private final ModelingIdentityFilter filter=new ModelingIdentityFilter(new ModelingIdentityService(directory),new ObjectMapper(),mock(ModelingPermissionAudit.class),access);
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void oldSessionMustReloginBeforeAnyModelLookup() throws Exception {
        authenticate(Map.of("sub","old-username"));
        var response=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET","/api/modeling/model-specs"),response,(request,reply)->{throw new AssertionError("must not reach resource");});
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MODELING_REAUTHENTICATION_REQUIRED");
        verifyNoInteractions(directory,access);
    }
    @Test void currentRoleDeniesOldEmployeeMenuGrant() throws Exception {
        authenticate(Map.of("sub","alice","directory_user_id","stable"));
        when(directory.currentModelingUser("stable")).thenReturn(new AdminDirectoryGateway.ModelingUser("stable","alice","Alice","dept-a","甲",List.of("ROLE_EMPLOYEE"),true,"GENERAL"));
        var response=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST","/api/modeling/model-specs"),response,(request,reply)->{throw new AssertionError("must not write");});
        assertThat(response.getStatus()).isEqualTo(403);verifyNoInteractions(access);
    }
    @Test void catalogAndScreenRequestsKeepLegacySessionSemantics() throws Exception {
        authenticate(Map.of("sub","old-username"));
        var response=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET","/api/catalog/datasets"),response,(request,reply)->assertThat(ModelingIdentity.optional()).isEmpty());
        assertThat(response.getStatus()).isEqualTo(200);verifyNoInteractions(directory,access);
    }
    private void authenticate(Map<String,Object> attributes){var roles=List.of(new SimpleGrantedAuthority("ROLE_INST_DATA_OWNER"));var principal=new DefaultOAuth2AuthenticatedPrincipal("alice",attributes,roles);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,roles));}
}
