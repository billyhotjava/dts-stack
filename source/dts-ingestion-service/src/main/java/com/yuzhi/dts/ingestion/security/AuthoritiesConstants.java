package com.yuzhi.dts.ingestion.security;

public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String OP_ADMIN = "ROLE_OP_ADMIN";
    public static final String USER = "ROLE_USER";
    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    public static final String[] DATA_MAINTAINER_ROLES = new String[] {
        ADMIN,
        OP_ADMIN
    };

    public static final String[] INFRA_MAINTAINERS = DATA_MAINTAINER_ROLES;

    private AuthoritiesConstants() {}
}
