package com.yuzhi.dts.admin.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    public static List<String> getCurrentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return getAuthorities(authentication).collect(Collectors.toList());
    }

    public static String getCurrentUserPrimaryAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return resolvePrimaryAuthority(getAuthorities(authentication).collect(Collectors.toList()));
    }

    public static String resolvePrimaryAuthority(Collection<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = authorities
            .stream()
            .filter(java.util.Objects::nonNull)
            .map(SecurityUtils::normalizeRole)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            return null;
        }
        for (String candidate : PRIMARY_ROLE_PRIORITY) {
            if (normalized.contains(candidate)) {
                return candidate;
            }
        }
        return normalized.iterator().next();
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication instanceof JwtAuthenticationToken
            ? extractAuthorityFromClaims(((JwtAuthenticationToken) authentication).getToken().getClaims())
            : authentication.getAuthorities();
        return authorities.stream().map(GrantedAuthority::getAuthority);
    }

    public static List<GrantedAuthority> extractAuthorityFromClaims(Map<String, Object> claims) {
        return mapRolesToGrantedAuthorities(getRolesFromClaims(claims));
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getRolesFromClaims(Map<String, Object> claims) {
        List<String> roles = new ArrayList<>();

        collectRoles(claims.get("groups"), roles);
        collectRoles(claims.get("roles"), roles);
        collectRoles(claims.get(CLAIMS_NAMESPACE + "roles"), roles);

        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            collectRoles(realmMap.get("roles"), roles);
        }

        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            for (Object resource : resourceMap.values()) {
                if (resource instanceof Map<?, ?> resourceEntry) {
                    collectRoles(resourceEntry.get("roles"), roles);
                }
            }
        }

        return roles;
    }

    private static void collectRoles(Object source, Collection<String> target) {
        if (source == null) {
            return;
        }
        if (source instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value instanceof String s && !s.isBlank()) {
                    target.add(s);
                }
            }
        } else if (source instanceof String s && !s.isBlank()) {
            target.add(s);
        }
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

    private static final List<String> PRIMARY_ROLE_PRIORITY = List.of(
        AuthoritiesConstants.SYS_ADMIN,
        AuthoritiesConstants.AUTH_ADMIN,
        AuthoritiesConstants.AUDITOR_ADMIN,
        AuthoritiesConstants.OP_ADMIN,
        AuthoritiesConstants.ADMIN
    );

    private static final Map<String, String> ROLE_ALIASES = Map.ofEntries(
        // Canonical triad (no prefix aliases)
        Map.entry("SYSADMIN", AuthoritiesConstants.SYS_ADMIN),
        Map.entry("AUTHADMIN", AuthoritiesConstants.AUTH_ADMIN),
        Map.entry("AUDITADMIN", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("SECURITYAUDITOR", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("OPADMIN", AuthoritiesConstants.OP_ADMIN),

        // Canonical triad (prefixed variants commonly seen in legacy realms)
        Map.entry("ROLE_SYSADMIN", AuthoritiesConstants.SYS_ADMIN),
        Map.entry("ROLE_SYSTEM_ADMIN", AuthoritiesConstants.SYS_ADMIN),
        Map.entry("ROLE_AUTHADMIN", AuthoritiesConstants.AUTH_ADMIN),
        Map.entry("ROLE_IAM_ADMIN", AuthoritiesConstants.AUTH_ADMIN),
        Map.entry("ROLE_AUDITOR_ADMIN", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("ROLE_AUDIT_ADMIN", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("ROLE_SECURITYAUDITOR", AuthoritiesConstants.AUDITOR_ADMIN),

        // Already-canonical names map to themselves (idempotent)
        Map.entry(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.SYS_ADMIN),
        Map.entry(AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUTH_ADMIN),
        Map.entry(AuthoritiesConstants.AUDITOR_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry(AuthoritiesConstants.OP_ADMIN, AuthoritiesConstants.OP_ADMIN)
    );

    private static String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String trimmed = role.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        // First, try exact alias match (handles both prefixed and non-prefixed forms)
        String alias = ROLE_ALIASES.get(upper);
        if (alias != null) return alias;

        // If it's already prefixed, normalize common underscores and retry alias map
        if (upper.startsWith("ROLE_")) {
            String noPrefix = upper.substring(5);
            String aliasNoPrefix = ROLE_ALIASES.get(noPrefix);
            if (aliasNoPrefix != null) return aliasNoPrefix;
            return upper; // keep as-is
        }

        // As a last resort, prefix unknown role names
        return "ROLE_" + upper;
    }
}
