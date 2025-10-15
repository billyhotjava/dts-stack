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

    // Module-specific admin roles
    public static final String CATALOG_ADMIN = "ROLE_CATALOG_ADMIN";
    public static final String GOV_ADMIN = "ROLE_GOV_ADMIN";
    public static final String IAM_ADMIN = "ROLE_IAM_ADMIN";

    // Organization-level data roles
    public static final String INST_DATA_DEV = "ROLE_INST_DATA_DEV";
    public static final String INST_DATA_OWNER = "ROLE_INST_DATA_OWNER";
    public static final String DEPT_DATA_DEV = "ROLE_DEPT_DATA_DEV";
    public static final String DEPT_DATA_OWNER = "ROLE_DEPT_DATA_OWNER";

    // Aggregated role groups for access control annotations
    public static final String[] DATA_MAINTAINER_ROLES = new String[] {
        ADMIN,
        OP_ADMIN,
        INST_DATA_DEV,
        INST_DATA_OWNER,
        DEPT_DATA_DEV,
        DEPT_DATA_OWNER
    };

    public static final String[] CATALOG_MAINTAINERS = new String[] {
        CATALOG_ADMIN,
        ADMIN,
        OP_ADMIN,
        INST_DATA_DEV,
        INST_DATA_OWNER,
        DEPT_DATA_DEV,
        DEPT_DATA_OWNER
    };

    public static final String[] GOVERNANCE_MAINTAINERS = new String[] {
        GOV_ADMIN,
        ADMIN,
        OP_ADMIN,
        INST_DATA_DEV,
        INST_DATA_OWNER,
        DEPT_DATA_DEV,
        DEPT_DATA_OWNER
    };

    private AuthoritiesConstants() {}
}
