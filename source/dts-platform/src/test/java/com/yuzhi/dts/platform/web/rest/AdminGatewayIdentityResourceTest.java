package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminGatewayIdentityResourceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void whoamiShouldReturnCurrentPlatformPrivilegeStatus() {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "alice",
                    "n/a",
                    List.of(new SimpleGrantedAuthority(AuthoritiesConstants.OP_ADMIN))
                )
            );

        AdminGatewayIdentityResource resource = new AdminGatewayIdentityResource();

        ApiResponse<AdminGatewayIdentityResource.WhoAmI> response = resource.whoAmI();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().allowed()).isTrue();
        assertThat(response.getData().role()).isEqualTo(AuthoritiesConstants.OP_ADMIN);
        assertThat(response.getData().username()).isEqualTo("alice");
    }
}
