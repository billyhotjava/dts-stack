package com.yuzhi.dts.common.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    public static final String CLAIMS_NAMESPACE = "https://www.jhipster.tech/";

    private SecurityUtils() {}

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication instanceof JwtAuthenticationToken) {
            return (String) ((JwtAuthenticationToken) authentication).getToken().getClaims().get("preferred_username");
        } else if (authentication.getPrincipal() instanceof DefaultOidcUser) {
            Map<String, Object> attributes = ((DefaultOidcUser) authentication.getPrincipal()).getAttributes();
            if (attributes.containsKey("preferred_username")) {
                return (String) attributes.get("preferred_username");
            }
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).noneMatch(AuthoritiesConstants.ANONYMOUS::equals);
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserAnyOfAuthorities(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (
            authentication != null && getAuthorities(authentication).anyMatch(authority -> Arrays.asList(authorities).contains(authority))
        );
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserNoneOfAuthorities(String... authorities) {
        return !hasCurrentUserAnyOfAuthorities(authorities);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static boolean hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication instanceof JwtAuthenticationToken
            ? extractAuthorityFromClaims(((JwtAuthenticationToken) authentication).getToken().getClaims())
            : authentication.getAuthorities();
        return authorities.stream().map(GrantedAuthority::getAuthority);
    }

    public static List<GrantedAuthority> extractAuthorityFromClaims(Map<String, Object> claims) {
        // Collect roles from common OIDC/Keycloak locations
        Collection<String> roles = getRolesFromClaims(claims);

        // Normalize roles and build ROLE_* authorities
        List<GrantedAuthority> roleAuthorities = mapRolesToGrantedAuthorities(roles);

        // Collect OAuth2 scopes and expose as SCOPE_* authorities
        List<GrantedAuthority> scopeAuthorities = mapScopesToGrantedAuthorities(getScopesFromClaims(claims));

        // Union without duplicates
        Set<String> seen = new HashSet<>();
        List<GrantedAuthority> result = new ArrayList<>();
        for (GrantedAuthority a : Stream.concat(roleAuthorities.stream(), scopeAuthorities.stream()).toList()) {
            if (seen.add(a.getAuthority())) {
                result.add(a);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getRolesFromClaims(Map<String, Object> claims) {
        List<String> roles = new ArrayList<>();

        // Common flat claims
        collectRoles(claims.get("groups"), roles);
        collectRoles(claims.get("roles"), roles);
        collectRoles(claims.get(CLAIMS_NAMESPACE + "roles"), roles);

        // Keycloak realm roles
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            collectRoles(realmMap.get("roles"), roles);
        }

        // Keycloak client roles
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            for (Object value : resourceMap.values()) {
                if (value instanceof Map<?, ?> entry) {
                    collectRoles(entry.get("roles"), roles);
                }
            }
        }

        return roles;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getScopesFromClaims(Map<String, Object> claims) {
        List<String> scopes = new ArrayList<>();
        Object scp = claims.get("scp"); // array form
        if (scp instanceof Collection<?> c) {
            for (Object x : c) {
                String s = String.valueOf(x == null ? "" : x).trim();
                if (!s.isEmpty()) scopes.add(s);
            }
        }
        Object scope = claims.get("scope"); // space-delimited form
        if (scope instanceof String s && !s.isBlank()) {
            scopes.addAll(Arrays.stream(s.trim().split("\\s+")).filter(t -> !t.isBlank()).toList());
        }
        return scopes;
    }

    private static List<GrantedAuthority> mapRolesToGrantedAuthorities(Collection<String> roles) {
        return roles
            .stream()
            .map(SecurityUtils::normalizeRole)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

    private static List<GrantedAuthority> mapScopesToGrantedAuthorities(Collection<String> scopes) {
        return scopes
            .stream()
            .map(s -> String.valueOf(s == null ? "" : s).trim())
            .filter(s -> !s.isEmpty())
            .map(s -> s.startsWith("SCOPE_") ? s : ("SCOPE_" + s))
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

    private static void collectRoles(Object source, Collection<String> target) {
        if (source == null) return;
        if (source instanceof Collection<?> collection) {
            for (Object value : collection) {
                String s = String.valueOf(value == null ? "" : value).trim();
                if (!s.isEmpty()) target.add(s);
            }
        } else if (source instanceof String s && !s.isBlank()) {
            target.add(s);
        }
    }

    private static final Map<String, String> ROLE_ALIASES = Map.ofEntries(
        // Triad roles (admin console) — allow aliases for compatibility
        Map.entry("SYSADMIN", "ROLE_SYS_ADMIN"),
        Map.entry("ROLE_SYSADMIN", "ROLE_SYS_ADMIN"),
        Map.entry("ROLE_SYSTEM_ADMIN", "ROLE_SYS_ADMIN"),

        Map.entry("AUTHADMIN", "ROLE_AUTH_ADMIN"),
        Map.entry("ROLE_AUTHADMIN", "ROLE_AUTH_ADMIN"),
        Map.entry("ROLE_IAM_ADMIN", "ROLE_AUTH_ADMIN"),

        Map.entry("AUDITADMIN", "ROLE_SECURITY_AUDITOR"),
        Map.entry("SECURITYAUDITOR", "ROLE_SECURITY_AUDITOR"),
        Map.entry("ROLE_AUDITOR_ADMIN", "ROLE_SECURITY_AUDITOR"),
        Map.entry("ROLE_AUDIT_ADMIN", "ROLE_SECURITY_AUDITOR"),
        Map.entry("ROLE_SECURITYAUDITOR", "ROLE_SECURITY_AUDITOR"),

        Map.entry("OPADMIN", "ROLE_OP_ADMIN"),

        // Common audit authorities
        Map.entry("AUDIT_READ", "ROLE_AUDIT_READ"),
        Map.entry("AUDITWRITE", "ROLE_AUDIT_WRITE"),
        Map.entry("AUDIT_WRITE", "ROLE_AUDIT_WRITE")
    );

    private static String normalizeRole(String role) {
        if (role == null) return null;
        String trimmed = role.trim();
        if (trimmed.isEmpty()) return null;

        String upper = trimmed.toUpperCase(Locale.ROOT);
        // Preserve already canonical ROLE_* authorities
        String alias = ROLE_ALIASES.get(upper);
        if (alias != null) return alias;

        if (upper.startsWith("ROLE_")) {
            String noPrefix = upper.substring(5);
            String aliasNoPrefix = ROLE_ALIASES.get(noPrefix);
            if (aliasNoPrefix != null) return aliasNoPrefix;
            return upper;
        }
        // Default to ROLE_ prefix for non-prefixed items
        return "ROLE_" + upper;
    }
}
