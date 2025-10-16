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

    // Data governance roles (shared with platform)
    public static final String INST_DATA_OWNER = "ROLE_INST_DATA_OWNER";
    public static final String INST_DATA_DEV = "ROLE_INST_DATA_DEV";
    public static final String INST_DATA_VIEWER = "ROLE_INST_DATA_VIEWER";
    public static final String DEPT_DATA_OWNER = "ROLE_DEPT_DATA_OWNER";
    public static final String DEPT_DATA_DEV = "ROLE_DEPT_DATA_DEV";
    public static final String DEPT_DATA_VIEWER = "ROLE_DEPT_DATA_VIEWER";

    private AuthoritiesConstants() {}
}
