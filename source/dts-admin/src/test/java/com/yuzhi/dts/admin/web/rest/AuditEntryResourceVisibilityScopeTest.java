package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.admin.web.rest.AuditEntryResource.VisibilityScope;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Covers the governance-triad separation-of-duties rules for audit log visibility.
 * Each test corresponds to a single deployment scenario the system must handle correctly.
 */
class AuditEntryResourceVisibilityScopeTest {

    @Test
    @DisplayName("SYS_ADMIN 单独 → 仅能看自己的记录")
    void sysAdminOnlySeesSelf() {
        VisibilityScope scope = AuditEntryResource.resolveVisibilityScope("sysadmin", true, false, false);

        assertThat(scope.allowedActors()).containsExactly("sysadmin");
        assertThat(scope.excludedActors()).isEmpty();
    }

    @Test
    @DisplayName("AUTH_ADMIN 单独 → 仅能看 auditadmin 的记录")
    void authAdminOnlySeesAuditor() {
        VisibilityScope scope = AuditEntryResource.resolveVisibilityScope("authadmin", false, true, false);

        assertThat(scope.allowedActors()).containsExactly("auditadmin");
        assertThat(scope.excludedActors()).isEmpty();
    }

    @Test
    @DisplayName("AUDITOR 单独 → 除自己外所有人")
    void auditorSeesEveryoneExceptSelf() {
        VisibilityScope scope = AuditEntryResource.resolveVisibilityScope("auditadmin", false, false, true);

        assertThat(scope.allowedActors()).isEmpty();
        assertThat(scope.excludedActors()).containsExactly("auditadmin");
    }

    @Test
    @DisplayName("SYS+AUTH 复合 → 403（违反 SoD）")
    void sysPlusAuthIsForbidden() {
        assertThatThrownBy(() -> AuditEntryResource.resolveVisibilityScope("rogue", true, true, false))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("违反职责分离");
        assertThat(thrownStatus(() -> AuditEntryResource.resolveVisibilityScope("rogue", true, true, false)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AUTH+AUDITOR 复合 → 403（违反 SoD）")
    void authPlusAuditorIsForbidden() {
        assertThat(thrownStatus(() -> AuditEntryResource.resolveVisibilityScope("rogue", false, true, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("SYS+AUDITOR 复合 → 403（违反 SoD）")
    void sysPlusAuditorIsForbidden() {
        assertThat(thrownStatus(() -> AuditEntryResource.resolveVisibilityScope("rogue", true, false, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("三角色全有 → 403（极端违规）")
    void allThreeRolesIsForbidden() {
        assertThat(thrownStatus(() -> AuditEntryResource.resolveVisibilityScope("rogue", true, true, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("无三元角色 → 403 fail-secure")
    void noTriadRoleIsForbidden() {
        assertThat(thrownStatus(() -> AuditEntryResource.resolveVisibilityScope("ghost", false, false, false)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("SYS_ADMIN 但 login 为 null → 403（无法定位自查范围）")
    void sysAdminWithoutLoginIsForbidden() {
        assertThat(thrownStatus(() -> AuditEntryResource.resolveVisibilityScope(null, true, false, false)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AUDITOR 但 login 为 null → 403（无法识别需排除的自身）")
    void auditorWithoutLoginIsForbidden() {
        assertThat(thrownStatus(() -> AuditEntryResource.resolveVisibilityScope(null, false, false, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AUTH_ADMIN 不依赖 login（看的是 auditadmin 不是自己）→ login=null 仍可工作")
    void authAdminWithoutLoginIsAllowed() {
        VisibilityScope scope = AuditEntryResource.resolveVisibilityScope(null, false, true, false);

        assertThat(scope.allowedActors()).containsExactly("auditadmin");
        assertThat(scope.excludedActors()).isEmpty();
    }

    @Test
    @DisplayName("validateRealActor 拒绝 system / anonymous / null")
    void validateRealActorRejectsSynthetic() {
        assertThat(thrownStatus(() -> AuditEntryResource.validateRealActor(null))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditEntryResource.validateRealActor("system"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditEntryResource.validateRealActor("anonymous"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditEntryResource.validateRealActor("anonymoususer"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditEntryResource.validateRealActor("unknown"))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("validateRealActor 接受真实用户")
    void validateRealActorAcceptsRealUser() {
        AuditEntryResource.validateRealActor("sysadmin"); // does not throw
        AuditEntryResource.validateRealActor("alice.smith");
        AuditEntryResource.validateRealActor("dts_audit_01");
    }

    @Test
    @DisplayName("TriadAccountRegistry 默认四个内置账号映射")
    void triadRegistryDefaults() {
        com.yuzhi.dts.admin.security.TriadAccountRegistry registry =
            new com.yuzhi.dts.admin.security.TriadAccountRegistry("sysadmin", "authadmin", "auditadmin", "opadmin");

        assertThat(registry.displayLabels()).containsEntry("sysadmin", "系统管理员");
        assertThat(registry.displayLabels()).containsEntry("authadmin", "授权管理员");
        assertThat(registry.displayLabels()).containsEntry("auditadmin", "安全审计员");
        assertThat(registry.displayLabels()).containsEntry("opadmin", "运维管理员");
        assertThat(registry.displayLabelFor("SYSADMIN")).contains("系统管理员");
        assertThat(registry.isProtectedUsername("opadmin")).isTrue();
    }

    @Test
    @DisplayName("TriadAccountRegistry 接受自定义账号名（含大小写规范化）")
    void triadRegistryAcceptsCustom() {
        com.yuzhi.dts.admin.security.TriadAccountRegistry registry =
            new com.yuzhi.dts.admin.security.TriadAccountRegistry("DTS_SYS_A", " dts_auth_b ", "dts_audit_c", null);

        assertThat(registry.displayLabels()).containsEntry("dts_sys_a", "系统管理员");
        assertThat(registry.displayLabels()).containsEntry("dts_auth_b", "授权管理员");
        assertThat(registry.displayLabels()).containsEntry("dts_audit_c", "安全审计员");
        assertThat(registry.displayLabels()).doesNotContainKey("opadmin");
        assertThat(registry.displayLabels()).hasSize(3);
    }

    @Test
    @DisplayName("TriadAccountRegistry 跳过空白账号名 + 暴露 protectedUsernames")
    void triadRegistrySkipsBlanks() {
        com.yuzhi.dts.admin.security.TriadAccountRegistry registry =
            new com.yuzhi.dts.admin.security.TriadAccountRegistry("", "  ", null, "opadmin");

        assertThat(registry.displayLabels()).hasSize(1).containsEntry("opadmin", "运维管理员");
        assertThat(registry.protectedUsernames()).containsExactly("opadmin");
    }

    @Test
    @DisplayName("TriadAccountRegistry triadAuthorities/protectedAuthorities 角色集")
    void triadRegistryAuthoritySets() {
        com.yuzhi.dts.admin.security.TriadAccountRegistry registry =
            new com.yuzhi.dts.admin.security.TriadAccountRegistry("sysadmin", "authadmin", "auditadmin", "opadmin");

        assertThat(registry.isTriadAuthority("ROLE_SYS_ADMIN")).isTrue();
        assertThat(registry.isTriadAuthority("ROLE_AUTH_ADMIN")).isTrue();
        assertThat(registry.isTriadAuthority("ROLE_SECURITY_AUDITOR")).isTrue();
        assertThat(registry.isTriadAuthority("ROLE_OP_ADMIN")).isFalse();
        assertThat(registry.isProtectedAuthority("ROLE_OP_ADMIN")).isTrue();
        assertThat(registry.isProtectedAuthority("ROLE_USER")).isFalse();
    }

    private static HttpStatus thrownStatus(Runnable r) {
        try {
            r.run();
            return null;
        } catch (ResponseStatusException ex) {
            return HttpStatus.valueOf(ex.getStatusCode().value());
        }
    }
}
