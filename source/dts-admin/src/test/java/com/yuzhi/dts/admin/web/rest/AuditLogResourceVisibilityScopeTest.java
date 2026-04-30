package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.admin.web.rest.AuditLogResource.VisibilityScope;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Covers the governance-triad separation-of-duties rules for audit log visibility.
 * Each test corresponds to a single deployment scenario the system must handle correctly.
 */
class AuditLogResourceVisibilityScopeTest {

    @Test
    @DisplayName("SYS_ADMIN 单独 → 仅能看自己的记录")
    void sysAdminOnlySeesSelf() {
        VisibilityScope scope = AuditLogResource.resolveVisibilityScope("sysadmin", true, false, false);

        assertThat(scope.allowedActors()).containsExactly("sysadmin");
        assertThat(scope.excludedActors()).isEmpty();
    }

    @Test
    @DisplayName("AUTH_ADMIN 单独 → 仅能看 auditadmin 的记录")
    void authAdminOnlySeesAuditor() {
        VisibilityScope scope = AuditLogResource.resolveVisibilityScope("authadmin", false, true, false);

        assertThat(scope.allowedActors()).containsExactly("auditadmin");
        assertThat(scope.excludedActors()).isEmpty();
    }

    @Test
    @DisplayName("AUDITOR 单独 → 除自己外所有人")
    void auditorSeesEveryoneExceptSelf() {
        VisibilityScope scope = AuditLogResource.resolveVisibilityScope("auditadmin", false, false, true);

        assertThat(scope.allowedActors()).isEmpty();
        assertThat(scope.excludedActors()).containsExactly("auditadmin");
    }

    @Test
    @DisplayName("SYS+AUTH 复合 → 403（违反 SoD）")
    void sysPlusAuthIsForbidden() {
        assertThatThrownBy(() -> AuditLogResource.resolveVisibilityScope("rogue", true, true, false))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("违反职责分离");
        assertThat(thrownStatus(() -> AuditLogResource.resolveVisibilityScope("rogue", true, true, false)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AUTH+AUDITOR 复合 → 403（违反 SoD）")
    void authPlusAuditorIsForbidden() {
        assertThat(thrownStatus(() -> AuditLogResource.resolveVisibilityScope("rogue", false, true, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("SYS+AUDITOR 复合 → 403（违反 SoD）")
    void sysPlusAuditorIsForbidden() {
        assertThat(thrownStatus(() -> AuditLogResource.resolveVisibilityScope("rogue", true, false, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("三角色全有 → 403（极端违规）")
    void allThreeRolesIsForbidden() {
        assertThat(thrownStatus(() -> AuditLogResource.resolveVisibilityScope("rogue", true, true, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("无三元角色 → 403 fail-secure")
    void noTriadRoleIsForbidden() {
        assertThat(thrownStatus(() -> AuditLogResource.resolveVisibilityScope("ghost", false, false, false)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("SYS_ADMIN 但 login 为 null → 403（无法定位自查范围）")
    void sysAdminWithoutLoginIsForbidden() {
        assertThat(thrownStatus(() -> AuditLogResource.resolveVisibilityScope(null, true, false, false)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AUDITOR 但 login 为 null → 403（无法识别需排除的自身）")
    void auditorWithoutLoginIsForbidden() {
        assertThat(thrownStatus(() -> AuditLogResource.resolveVisibilityScope(null, false, false, true)))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AUTH_ADMIN 不依赖 login（看的是 auditadmin 不是自己）→ login=null 仍可工作")
    void authAdminWithoutLoginIsAllowed() {
        VisibilityScope scope = AuditLogResource.resolveVisibilityScope(null, false, true, false);

        assertThat(scope.allowedActors()).containsExactly("auditadmin");
        assertThat(scope.excludedActors()).isEmpty();
    }

    @Test
    @DisplayName("validateRealActor 拒绝 system / anonymous / null")
    void validateRealActorRejectsSynthetic() {
        assertThat(thrownStatus(() -> AuditLogResource.validateRealActor(null))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditLogResource.validateRealActor("system"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditLogResource.validateRealActor("anonymous"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditLogResource.validateRealActor("anonymoususer"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrownStatus(() -> AuditLogResource.validateRealActor("unknown"))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("validateRealActor 接受真实用户")
    void validateRealActorAcceptsRealUser() {
        AuditLogResource.validateRealActor("sysadmin"); // does not throw
        AuditLogResource.validateRealActor("alice.smith");
        AuditLogResource.validateRealActor("dts_audit_01");
    }

    @Test
    @DisplayName("buildTriadDisplayNames 默认四个内置账号映射")
    void buildTriadDisplayNamesDefaults() {
        java.util.Map<String, String> map = AuditLogResource.buildTriadDisplayNames(
            "sysadmin", "authadmin", "auditadmin", "opadmin"
        );

        assertThat(map).containsEntry("sysadmin", "系统管理员");
        assertThat(map).containsEntry("authadmin", "授权管理员");
        assertThat(map).containsEntry("auditadmin", "安全审计员");
        assertThat(map).containsEntry("opadmin", "运维管理员");
    }

    @Test
    @DisplayName("buildTriadDisplayNames 接受自定义账号名（含大小写规范化）")
    void buildTriadDisplayNamesAcceptsCustom() {
        java.util.Map<String, String> map = AuditLogResource.buildTriadDisplayNames(
            "DTS_SYS_A", " dts_auth_b ", "dts_audit_c", null
        );

        assertThat(map).containsEntry("dts_sys_a", "系统管理员");
        assertThat(map).containsEntry("dts_auth_b", "授权管理员");
        assertThat(map).containsEntry("dts_audit_c", "安全审计员");
        assertThat(map).doesNotContainKey("opadmin");
        assertThat(map).hasSize(3);
    }

    @Test
    @DisplayName("buildTriadDisplayNames 跳过空白账号名")
    void buildTriadDisplayNamesSkipsBlanks() {
        java.util.Map<String, String> map = AuditLogResource.buildTriadDisplayNames(
            "", "  ", null, "opadmin"
        );

        assertThat(map).hasSize(1).containsEntry("opadmin", "运维管理员");
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
