package com.yuzhi.dts.ingestion.security;

public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String OP_ADMIN = "ROLE_OP_ADMIN";
    public static final String USER = "ROLE_USER";
    public static final String ANONYMOUS = "ROLE_ANONYMOUS";
    public static final String SERVICE_DTS_PLATFORM = "ROLE_SERVICE_DTS_PLATFORM";
    public static final String SERVICE_DTS_AIRFLOW = "ROLE_SERVICE_DTS_AIRFLOW";

    public static final String INST_DATA_OWNER = "ROLE_INST_DATA_OWNER";
    public static final String INST_LEADER = "ROLE_INST_LEADER";
    public static final String DEPT_DATA_OWNER = "ROLE_DEPT_DATA_OWNER";
    public static final String DEPT_LEADER = "ROLE_DEPT_LEADER";

    public static final String[] DATA_MAINTAINER_ROLES = new String[] {
        ADMIN,
        OP_ADMIN,
        INST_DATA_OWNER,
        DEPT_DATA_OWNER,
        INST_LEADER,
        DEPT_LEADER
    };

    public static final String[] INFRA_MAINTAINERS = DATA_MAINTAINER_ROLES;

    private AuthoritiesConstants() {}
}
