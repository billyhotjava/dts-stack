package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class QualityEffectiveDepartmentAuditTest {

    private final QualityEffectiveDepartmentResolver resolver = new QualityEffectiveDepartmentResolver();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentUserCannotSpoofAnotherDepartmentHeader() {
        authenticate(AuthoritiesConstants.DEPT_LEADER, "D01");

        assertThat(resolver.resolve("D99")).isEqualTo("D01");
    }

    @Test
    void instituteUserMaySwitchToAnAllowlistedDepartment() {
        authenticate(AuthoritiesConstants.INST_LEADER, "D01");

        assertThat(resolver.resolve(" D99 ")).isEqualTo("D99");
    }

    @Test
    void operationsAdminMaySwitchToAnAllowlistedDepartment() {
        authenticate(AuthoritiesConstants.OP_ADMIN, "D01");

        assertThat(resolver.resolve("D88")).isEqualTo("D88");
    }

    @Test
    void invalidOrOversizedInstituteHeaderFallsBackToJwtDepartment() {
        authenticate(AuthoritiesConstants.INST_LEADER, "D01");

        assertThat(resolver.resolve("D5%0")).isEqualTo("D01");
        assertThat(resolver.resolve("D".repeat(65))).isEqualTo("D01");
    }

    @Test
    void invalidJwtDepartmentFailsClosedForDepartmentUser() {
        authenticate(AuthoritiesConstants.DEPT_LEADER, "D5%0");

        assertThat(resolver.resolve("D99")).isNull();
    }

    private void authenticate(String authority, String department) {
        Jwt jwt = Jwt.withTokenValue("quality-test-token")
            .header("alg", "none")
            .claim("sub", "alice")
            .claim("dept_code", department)
            .claim("groups", List.of(authority))
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)), "alice")
        );
    }
}
