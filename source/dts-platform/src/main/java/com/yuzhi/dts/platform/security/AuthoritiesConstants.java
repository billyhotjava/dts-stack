package com.yuzhi.dts.platform.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";
    // Governance triad roles (aligned with dts-admin / Keycloak realm roles)
    public static final String SYS_ADMIN = "ROLE_SYS_ADMIN";           // 系统管理员
    public static final String AUTH_ADMIN = "ROLE_AUTH_ADMIN";         // 授权管理员
    public static final String AUDITOR_ADMIN = "ROLE_SECURITY_AUDITOR"; // 安全审计员

    // Business administrator (OP admin) — should have full platform access
    public static final String OP_ADMIN = "ROLE_OP_ADMIN";

    // Authenticated service-to-service caller. Endpoint-level policies decide what each service may do.
    public static final String SERVICE_INTERNAL = "ROLE_SERVICE_INTERNAL";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    // Organization-level数据角色，与 Admin 服务保持一致
    public static final String INST_DATA_OWNER = "ROLE_INST_DATA_OWNER";
    public static final String INST_LEADER = "ROLE_INST_LEADER";
    public static final String DEPT_DATA_OWNER = "ROLE_DEPT_DATA_OWNER";
    public static final String DEPT_LEADER = "ROLE_DEPT_LEADER";
    public static final String EMPLOYEE = "ROLE_EMPLOYEE";

    // Interim customer-validation policy: existing data-admin roles project to release duties.
    // Keep this centralized so the final customer-approved role model can replace the projection without
    // changing lifecycle state or audit semantics.
    public static final String[] MODEL_MAINTAINERS = new String[] {
        INST_DATA_OWNER,
        DEPT_DATA_OWNER,
        INST_LEADER,
        OP_ADMIN
    };
    public static final String[] MODEL_RELEASE_REVIEWERS = new String[] {
        INST_LEADER,
        OP_ADMIN
    };
    public static final String[] MODEL_RELEASE_OPERATORS = new String[] {
        INST_DATA_OWNER,
        DEPT_DATA_OWNER,
        OP_ADMIN
    };
    public static final String[] MODEL_RELEASE_DUTIES = new String[] {
        INST_DATA_OWNER,
        DEPT_DATA_OWNER,
        INST_LEADER,
        OP_ADMIN
    };

    // 平台模块统一的维护者/特权角色集合
    public static final String[] INSTITUTE_PRIVILEGED_ROLES = new String[] {
        ADMIN,
        OP_ADMIN,
        INST_DATA_OWNER,
        INST_LEADER
    };

    public static final String[] DEPARTMENT_PRIVILEGED_ROLES = new String[] {
        DEPT_DATA_OWNER,
        DEPT_LEADER,
        EMPLOYEE
    };

    public static final String[] DATA_MAINTAINER_ROLES = new String[] {
        ADMIN,
        OP_ADMIN,
        INST_DATA_OWNER,
        DEPT_DATA_OWNER,
        INST_LEADER,
        DEPT_LEADER
    };

    // 以下聚合常量便于 SpEL 引用，内容与 DATA_MAINTAINER_ROLES 保持一致
    public static final String[] CATALOG_MAINTAINERS = DATA_MAINTAINER_ROLES;
    public static final String[] GOVERNANCE_MAINTAINERS = DATA_MAINTAINER_ROLES;
    public static final String[] IAM_MAINTAINERS = DATA_MAINTAINER_ROLES;
    public static final String[] INFRA_MAINTAINERS = DATA_MAINTAINER_ROLES;
    public static final String[] SERVICE_MAINTAINERS = DATA_MAINTAINER_ROLES;

    private AuthoritiesConstants() {}
}
