package com.yuzhi.dts.admin.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    // Governance triad roles (Keycloak realm roles)
    public static final String SYS_ADMIN = "ROLE_SYS_ADMIN"; // 系统管理员
    public static final String AUTH_ADMIN = "ROLE_AUTH_ADMIN"; // 授权管理员
    // Canonical auditor role name unified to ROLE_SECURITY_AUDITOR
    public static final String AUDITOR_ADMIN = "ROLE_SECURITY_AUDITOR"; // 安全审计员

    // Application operator (not allowed on admin API by default)
    public static final String OP_ADMIN = "ROLE_OP_ADMIN";

    private AuthoritiesConstants() {}
}
