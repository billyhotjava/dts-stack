package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ModelSpecPlanWriteAccessAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void instituteDataOwnerCanMaintainAnyExistingPlan() {
        UUID planId = UUID.randomUUID();
        plan(planId, "another-owner", "dept-b");
        authenticate("alice", "dept-a", AuthoritiesConstants.INST_DATA_OWNER);
        ModelSpecPlanWriteAccessAdapter adapter = new ModelSpecPlanWriteAccessAdapter(jdbcTemplate);

        assertThat(adapter.canMaintain("tenant", planId, "alice")).isTrue();
    }

    @Test
    void ordinaryMenuUserCanMaintainPlansAcrossDepartments() {
        UUID sameDepartmentPlan = UUID.randomUUID();
        UUID foreignDepartmentPlan = UUID.randomUUID();
        plan(sameDepartmentPlan, "another-owner", "dept-a");
        plan(foreignDepartmentPlan, "alice", "dept-b");
        authenticate("alice", "dept-a", AuthoritiesConstants.EMPLOYEE);
        ModelSpecPlanWriteAccessAdapter adapter = new ModelSpecPlanWriteAccessAdapter(jdbcTemplate);

        assertThat(adapter.canMaintain("tenant", sameDepartmentPlan, "alice")).isTrue();
        assertThat(adapter.canMaintain("tenant", foreignDepartmentPlan, "alice")).isTrue();
    }

    @Test
    void authenticatedRoleCannotBeBorrowedForAnotherActor() {
        UUID planId = UUID.randomUUID();
        plan(planId, "another-owner", "dept-a");
        authenticate("alice", "dept-a", AuthoritiesConstants.INST_DATA_OWNER);
        ModelSpecPlanWriteAccessAdapter adapter = new ModelSpecPlanWriteAccessAdapter(jdbcTemplate);

        assertThat(adapter.canMaintain("tenant", planId, "mallory")).isFalse();
    }

    @Test
    void preservesBackgroundPlanOwnerFallbackAndFailsClosedForMissingContext() {
        UUID planId = UUID.randomUUID();
        plan(planId, "alice", "dept-a");
        ModelSpecPlanWriteAccessAdapter adapter = new ModelSpecPlanWriteAccessAdapter(jdbcTemplate);

        assertThat(adapter.canMaintain("tenant", planId, "alice")).isTrue();
        assertThat(adapter.canMaintain("tenant", planId, " ")).isFalse();
    }

    @Test
    void deniesCrossTenantAndAnonymousOwnerAccess() {
        UUID id = UUID.randomUUID();
        plan(id, "alice", "dept-a");
        authenticate("alice", "dept-a", AuthoritiesConstants.EMPLOYEE);
        var adapter = new ModelSpecPlanWriteAccessAdapter(jdbcTemplate);
        assertThat(adapter.canMaintain("another-tenant", id, "alice")).isFalse();
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.AnonymousAuthenticationToken(
            "key", "alice", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(adapter.canMaintain("tenant", id, "alice")).isFalse();
    }

    private void plan(UUID planId, String ownerId, String departmentId) {
        when(jdbcTemplate.queryForList(anyString(), eq("tenant"), eq(planId)))
            .thenReturn(
                List.of(
                    Map.of(
                        "owner_id",
                        ownerId,
                        "owner_department_id",
                        departmentId
                    )
                )
            );
    }

    private static void authenticate(String actorId, String departmentId, String authority) {
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject(actorId)
            .claim("preferred_username", actorId)
            .claim("dept_code", departmentId)
            .claim("roles", List.of(authority))
            .issuedAt(Instant.parse("2026-08-14T00:00:00Z"))
            .expiresAt(Instant.parse("2026-08-14T01:00:00Z"))
            .build();
        SecurityContextHolder
            .getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
