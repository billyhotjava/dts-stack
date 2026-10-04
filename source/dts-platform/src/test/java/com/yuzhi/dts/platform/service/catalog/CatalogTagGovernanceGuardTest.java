package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CatalogTagGovernanceGuardTest {

    private final CatalogTagGovernanceGuard guard = new CatalogTagGovernanceGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void catalogMaintainerRolePassesTheLocalAuditableGuard() {
        authenticate("owner", "ROLE_INST_DATA_OWNER");

        assertThatCode(guard::requireMaintainer).doesNotThrowAnyException();
    }

    @Test
    void ordinaryAuthenticatedUserFailsWithStableForbiddenCode() {
        authenticate("employee", "ROLE_EMPLOYEE");

        assertThatThrownBy(guard::requireMaintainer)
            .isInstanceOf(CatalogAssetTagPermissionException.class)
            .extracting("reasonCode")
            .isEqualTo("FORBIDDEN");
    }

    @Test
    void missingAuthenticationFailsWithStableUnauthenticatedCode() {
        assertThatThrownBy(guard::requireMaintainer)
            .isInstanceOf(CatalogAssetTagPermissionException.class)
            .extracting("reasonCode")
            .isEqualTo("UNAUTHENTICATED");
    }

    private static void authenticate(String username, String... authorities) {
        List<SimpleGrantedAuthority> granted = java.util.Arrays
            .stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toList();
        SecurityContextHolder
            .getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(username, "n/a", granted));
    }
}
