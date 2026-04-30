package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 回归测试：MANAGER 不再传递。仅大屏真 owner 或 superuser 可以授予 MANAGER；
 * 被授权的 MANAGER（perms.isOwner()==true 但 creatorId 不匹配）应当被拒绝。
 *
 * 与 dts-platform 端 DashboardAccessGuard.canGrant 策略 1 行为对齐。
 */
class ScreenResourceManagerGrantPolicyTest {

    private AnalyticsScreen screen(Long id, Long creatorId) {
        AnalyticsScreen s = new AnalyticsScreen();
        s.setId(id);
        s.setCreatorId(creatorId);
        return s;
    }

    private AnalyticsUser user(long id, boolean superuser) {
        AnalyticsUser u = new AnalyticsUser();
        u.setId(id);
        u.setSuperuser(superuser);
        u.setEmail("user" + id + "@test.com");
        u.setFirstName("User");
        u.setLastName(String.valueOf(id));
        u.setPasswordHash("x");
        return u;
    }

    @Test
    @DisplayName("真 owner 可以授予 MANAGER")
    void realOwner_canGrantManager() {
        AnalyticsScreen s = screen(10L, 100L);
        AnalyticsUser owner = user(100L, false);
        assertThat(ScreenResource.isManagerGrantAllowed(s, owner)).isTrue();
    }

    @Test
    @DisplayName("superuser 可以授予 MANAGER（即使不是 creator）")
    void superuser_canGrantManager() {
        AnalyticsScreen s = screen(10L, 100L);
        AnalyticsUser su = user(7L, true);
        assertThat(ScreenResource.isManagerGrantAllowed(s, su)).isTrue();
    }

    @Test
    @DisplayName("被授权的 MANAGER（非 creator、非 superuser）不能再授 MANAGER —— 防止权限传递")
    void grantedManager_cannotGrantManager() {
        // 关键场景：caller 通过 ScreenPermissionService.snapshot 拿到 isOwner=true（来自 MANAGER grant），
        // 但他不是 screen 的 creator。修复前他能继续授 MANAGER；修复后必须返回 403。
        AnalyticsScreen s = screen(10L, 100L);
        AnalyticsUser grantedManager = user(200L, false);
        assertThat(ScreenResource.isManagerGrantAllowed(s, grantedManager)).isFalse();
    }

    @Test
    @DisplayName("creatorId 为 null（数据异常）时保守拒绝")
    void missingCreator_denied() {
        AnalyticsScreen s = screen(10L, null);
        AnalyticsUser anyUser = user(100L, false);
        assertThat(ScreenResource.isManagerGrantAllowed(s, anyUser)).isFalse();
    }

    @Test
    @DisplayName("caller 为 null 时保守拒绝")
    void nullCaller_denied() {
        AnalyticsScreen s = screen(10L, 100L);
        assertThat(ScreenResource.isManagerGrantAllowed(s, null)).isFalse();
    }

    @Test
    @DisplayName("screen 为 null 时仍然允许 superuser（superuser 路径优先）")
    void nullScreen_superuserStillAllowed() {
        AnalyticsUser su = user(7L, true);
        assertThat(ScreenResource.isManagerGrantAllowed(null, su)).isTrue();
    }
}
