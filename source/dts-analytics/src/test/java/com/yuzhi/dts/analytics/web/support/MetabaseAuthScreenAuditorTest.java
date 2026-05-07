package com.yuzhi.dts.analytics.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;

/**
 * Sprint-24 F4 回归：大屏密级合规盘点鉴权契约。
 *
 * 必须接受 superuser + SCREEN_AUDITOR_ROLES（OP_ADMIN / 所级或部门数据管理员 /
 * 所级或部门领导），同时拒绝普通用户与匿名调用。这条契约决定前端按钮显隐与
 * 后端端点放行的边界，回归失败 = 前后端不一致 → 用户看到按钮但 403。
 */
class MetabaseAuthScreenAuditorTest {

    private static AnalyticsUser user(boolean superuser) {
        AnalyticsUser u = new AnalyticsUser();
        u.setId(1L);
        u.setSuperuser(superuser);
        return u;
    }

    private static HttpServletRequest requestWithRoles(String rolesHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-DTS-Roles")).thenReturn(rolesHeader);
        return req;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ROLE_OP_ADMIN",
            "ROLE_INST_DATA_OWNER",
            "ROLE_DEPT_DATA_OWNER",
            "ROLE_INST_LEADER",
            "ROLE_DEPT_LEADER",
    })
    @DisplayName("isScreenAuditor 接受所有治理角色")
    void isScreenAuditor_acceptsGovernanceRoles(String role) {
        assertThat(MetabaseAuth.isScreenAuditor(requestWithRoles(role))).isTrue();
    }

    @Test
    @DisplayName("isScreenAuditor 在多角色 CSV 中只要命中一项即可")
    void isScreenAuditor_csvAnyMatch() {
        assertThat(MetabaseAuth.isScreenAuditor(requestWithRoles("ROLE_VIEWER, ROLE_DEPT_LEADER")))
                .isTrue();
    }

    @Test
    @DisplayName("isScreenAuditor 拒绝普通角色 / 无 header")
    void isScreenAuditor_rejectsOthers() {
        assertThat(MetabaseAuth.isScreenAuditor(requestWithRoles("ROLE_VIEWER"))).isFalse();
        assertThat(MetabaseAuth.isScreenAuditor(requestWithRoles(""))).isFalse();
        assertThat(MetabaseAuth.isScreenAuditor(requestWithRoles(null))).isFalse();
    }

    @Test
    @DisplayName("requireScreenAuditor: 未登录 → 401")
    void requireScreenAuditor_unauthenticated_returns401() {
        AnalyticsSessionService session = mock(AnalyticsSessionService.class);
        HttpServletRequest req = requestWithRoles("ROLE_DEPT_LEADER");
        when(session.resolveUser(req)).thenReturn(Optional.empty());

        Optional<ResponseEntity<String>> result = MetabaseAuth.requireScreenAuditor(session, req);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("requireScreenAuditor: superuser 直接放行（即使 header 缺角色）")
    void requireScreenAuditor_superuserBypass() {
        AnalyticsSessionService session = mock(AnalyticsSessionService.class);
        HttpServletRequest req = requestWithRoles(null);
        when(session.resolveUser(req)).thenReturn(Optional.of(user(true)));

        assertThat(MetabaseAuth.requireScreenAuditor(session, req)).isEmpty();
    }

    @Test
    @DisplayName("requireScreenAuditor: 非 superuser 命中治理角色 → 放行")
    void requireScreenAuditor_governanceRolePass() {
        AnalyticsSessionService session = mock(AnalyticsSessionService.class);
        HttpServletRequest req = requestWithRoles("ROLE_INST_DATA_OWNER");
        when(session.resolveUser(req)).thenReturn(Optional.of(user(false)));

        assertThat(MetabaseAuth.requireScreenAuditor(session, req)).isEmpty();
    }

    @Test
    @DisplayName("requireScreenAuditor: 非 superuser 且无治理角色 → 403")
    void requireScreenAuditor_otherRoleRejected() {
        AnalyticsSessionService session = mock(AnalyticsSessionService.class);
        HttpServletRequest req = requestWithRoles("ROLE_VIEWER");
        when(session.resolveUser(req)).thenReturn(Optional.of(user(false)));

        Optional<ResponseEntity<String>> result = MetabaseAuth.requireScreenAuditor(session, req);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getStatusCode().value()).isEqualTo(403);
    }
}
