package com.yuzhi.dts.common.security;

/**
 * Shared auth/session endpoint boundary for dts-admin and dts-platform.
 *
 * <p>These paths are intentionally centralized because SecurityConfiguration
 * and custom session filters must agree on which endpoints are authentication
 * entry points. If they drift, a valid Keycloak token can be mistaken for a
 * local admin/platform session token.
 */
public final class AuthEndpointPaths {

    private AuthEndpointPaths() {}

    public static final String API_PREFIX = "/api";
    public static final String KEYCLOAK_AUTH_API_BASE = API_PREFIX + "/keycloak/auth";
    public static final String KEYCLOAK_AUTH_API_PREFIX = KEYCLOAK_AUTH_API_BASE + "/";
    public static final String KEYCLOAK_AUTH_API_PATTERN = API_PREFIX + "/keycloak/auth/**";
    public static final String KEYCLOAK_LOCALIZATION_API_BASE = API_PREFIX + "/keycloak/localization";
    public static final String KEYCLOAK_LOCALIZATION_API_PREFIX = KEYCLOAK_LOCALIZATION_API_BASE + "/";
    public static final String KEYCLOAK_LOCALIZATION_API_PATTERN = API_PREFIX + "/keycloak/localization/**";
    public static final String PORTAL_SESSION_STATUS = API_PREFIX + "/session/status";
    public static final String PORTAL_ACCESS_TOKEN_HEADER = "X-Portal-Access-Token";

    public static boolean isKeycloakAuthEndpoint(String uri) {
        return uri != null && (uri.equals(KEYCLOAK_AUTH_API_BASE) || uri.startsWith(KEYCLOAK_AUTH_API_PREFIX));
    }

    public static boolean isKeycloakLocalizationEndpoint(String uri) {
        return (
            uri != null &&
            (uri.equals(KEYCLOAK_LOCALIZATION_API_BASE) || uri.startsWith(KEYCLOAK_LOCALIZATION_API_PREFIX))
        );
    }

    public static boolean isPortalSessionStatusEndpoint(String uri) {
        return PORTAL_SESSION_STATUS.equals(uri);
    }
}
