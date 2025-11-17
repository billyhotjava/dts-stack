package com.yuzhi.dts.platform.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";
    // Business administrator (OP admin) — should have full platform access
    public static final String OP_ADMIN = "ROLE_OP_ADMIN";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    // Organization-level数据角色，与 Admin 服务保持一致
    public static final String INST_DATA_DEV = "ROLE_INST_DATA_DEV";
    public static final String INST_DATA_OWNER = "ROLE_INST_DATA_OWNER";
    public static final String INST_DATA_VIEWER = "ROLE_INST_DATA_VIEWER";
    public static final String INST_LEADER = "ROLE_INST_LEADER";
    public static final String DEPT_DATA_DEV = "ROLE_DEPT_DATA_DEV";
    public static final String DEPT_DATA_OWNER = "ROLE_DEPT_DATA_OWNER";
    public static final String DEPT_DATA_VIEWER = "ROLE_DEPT_DATA_VIEWER";
    public static final String DEPT_LEADER = "ROLE_DEPT_LEADER";
    public static final String EMPLOYEE = "ROLE_EMPLOYEE";

    // 平台模块统一的维护者/特权角色集合
    public static final String[] INSTITUTE_PRIVILEGED_ROLES = new String[] {
        ADMIN,
        OP_ADMIN,
        INST_DATA_DEV,
        INST_DATA_OWNER,
        INST_LEADER
    };

    public static final String[] DEPARTMENT_PRIVILEGED_ROLES = new String[] {
        DEPT_DATA_DEV,
        DEPT_DATA_OWNER,
        DEPT_LEADER
    };

    public static final String[] DATA_MAINTAINER_ROLES = new String[] {
        ADMIN,
        OP_ADMIN,
        INST_DATA_DEV,
        INST_DATA_OWNER,
        DEPT_DATA_DEV,
        DEPT_DATA_OWNER,
        INST_LEADER,
        DEPT_LEADER
    };

    // 以下聚合常量便于 SpEL 引用，内容与 DATA_MAINTAINER_ROLES 保持一致
    public static final String[] CATALOG_MAINTAINERS = DATA_MAINTAINER_ROLES;
    public static final String[] GOVERNANCE_MAINTAINERS = DATA_MAINTAINER_ROLES;
    public static final String[] IAM_MAINTAINERS = DATA_MAINTAINER_ROLES;
    public static final String[] INFRA_MAINTAINERS = DATA_MAINTAINER_ROLES;

    private AuthoritiesConstants() {}
}
