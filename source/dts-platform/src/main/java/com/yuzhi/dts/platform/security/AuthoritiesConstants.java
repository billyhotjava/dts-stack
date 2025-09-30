package com.yuzhi.dts.platform.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    // Module-specific admin roles
    public static final String CATALOG_ADMIN = "ROLE_CATALOG_ADMIN";
    public static final String GOV_ADMIN = "ROLE_GOV_ADMIN";
    public static final String IAM_ADMIN = "ROLE_IAM_ADMIN";

    private AuthoritiesConstants() {}
}
