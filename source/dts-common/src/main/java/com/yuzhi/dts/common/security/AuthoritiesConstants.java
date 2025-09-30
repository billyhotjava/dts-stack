package com.yuzhi.dts.common.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String USER = "ROLE_USER";

    public static final String AUDIT_WRITE = "ROLE_AUDIT_WRITE";

    public static final String AUDIT_READ = "ROLE_AUDIT_READ";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
