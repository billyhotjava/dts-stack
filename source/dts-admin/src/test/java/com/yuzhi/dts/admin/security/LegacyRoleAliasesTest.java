package com.yuzhi.dts.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F11-T06：历史别名集合冻结快照。增删别名必须走 T06 核验流程，本测试防止静默扩大放行面。
 */
class LegacyRoleAliasesTest {

    @Test
    @DisplayName("F11-T06：遗留审计员别名恰为三个历史值，不多不少")
    void auditorAliasesAreExactlyTheThreeLegacyValues() {
        assertThat(LegacyRoleAliases.AUDITOR_ALIASES)
            .containsExactlyInAnyOrder("ROLE_AUDITOR_ADMIN", "ROLE_AUDIT_ADMIN", "ROLE_AUDITADMIN");
        assertThat(LegacyRoleAliases.isLegacyAuditorAlias("ROLE_SECURITY_AUDITOR")).isFalse();
        assertThat(LegacyRoleAliases.isLegacyAuditorAlias(null)).isFalse();
    }
}
