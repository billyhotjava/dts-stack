package com.yuzhi.dts.admin.security;

import java.util.Set;

/**
 * F11-T06 历史角色别名收口点。
 *
 * <p>{@code ROLE_AUDITOR_ADMIN / ROLE_AUDIT_ADMIN / ROLE_AUDITADMIN} 来自遗留 realm/令牌，
 * 当前 realm 已不存在。清理前必须先核对目标 realm、旧令牌、活跃会话、排队任务及旧客户端
 * （见 T06），在此之前行为保持放行。
 *
 * <p>所有消费方必须引用本类常量，不得再散落字面量；下线时只改一处。
 */
@Deprecated
public final class LegacyRoleAliases {

    private LegacyRoleAliases() {}

    /** 遗留审计员别名：当前仅作兼容放行，禁止新增引用。 */
    @Deprecated
    public static final Set<String> AUDITOR_ALIASES = Set.of(
        "ROLE_AUDITOR_ADMIN",
        "ROLE_AUDIT_ADMIN",
        "ROLE_AUDITADMIN"
    );

    @Deprecated
    public static boolean isLegacyAuditorAlias(String authority) {
        return authority != null && AUDITOR_ALIASES.contains(authority);
    }
}
