package com.yuzhi.dts.platform.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    /**
     * Get the technical identifier (subject) of the current user if present.
     *
     * @return the subject/identifier of the current user.
     */
    public static Optional<String> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String sub = token.getToken().getSubject();
                if (sub == null) {
                    Object claim = token.getToken().getClaims().get("sub");
                    if (claim != null) {
                        sub = String.valueOf(claim);
                    }
                }
                return Optional.ofNullable(sub);
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof DefaultOidcUser oidcUser) {
                Object sub = oidcUser.getAttributes().get("sub");
                if (sub != null) {
                    return Optional.of(String.valueOf(sub));
                }
            }
            if (principal instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal oauth) {
                String sub = oauth.getAttribute("sub");
                if (sub != null && !sub.isBlank()) {
                    return Optional.of(sub);
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static Optional<String> getCurrentUserDisplayName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String display = extractDisplayNameFromClaims(token.getToken().getClaims());
                if (display != null) {
                    return Optional.of(display);
                }
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof DefaultOidcUser oidcUser) {
                String display = extractDisplayNameFromClaims(oidcUser.getAttributes());
                if (display != null) {
                    return Optional.of(display);
                }
            } else if (principal instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal oauth) {
                String display = extractDisplayNameFromClaims(oauth.getAttributes());
                if (display != null) {
                    return Optional.of(display);
                }
            } else if (principal instanceof UserDetails springSecurityUser) {
                String display = firstNonBlank(
                    springSecurityUser.getUsername(),
                    springSecurityUser.getClass().getSimpleName()
                );
                if (display != null) {
                    return Optional.of(display);
                }
            } else if (principal instanceof String s) {
                String text = s == null ? null : s.trim();
                if (text != null && !text.isEmpty()) {
                    return Optional.of(text);
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    /**
     * Returns the raw authority strings attached to the current authentication.
     * Used by workbench role resolution to keep the decision outside SecurityUtils
     * (see {@code WorkbenchRoleResolver}).
     */
    public static List<String> getCurrentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        Collection<? extends GrantedAuthority> authorities = authentication instanceof JwtAuthenticationToken token
            ? extractAuthorityFromClaims(token.getToken().getClaims())
            : authentication.getAuthorities();
        if (authorities == null) {
            return List.of();
        }
        return authorities.stream().map(GrantedAuthority::getAuthority).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * Returns the {@code dept_code} claim for the current user when available.
     * Supports both JWT (Keycloak) and opaque-token (portal session) principals.
     */
    public static Optional<String> getCurrentUserDept() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String dept = token.getToken().getClaimAsString("dept_code");
                if (hasText(dept)) {
                    return Optional.of(dept.trim());
                }
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                String dept = jwt.getClaimAsString("dept_code");
                if (hasText(dept)) {
                    return Optional.of(dept.trim());
                }
            }
            if (principal instanceof DefaultOidcUser oidcUser) {
                Object attr = oidcUser.getAttributes().get("dept_code");
                if (attr != null && hasText(String.valueOf(attr))) {
                    return Optional.of(String.valueOf(attr).trim());
                }
            }
            if (principal instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal opaque) {
                String dept = opaque.getAttribute("dept_code");
                if (hasText(dept)) {
                    return Optional.of(dept.trim());
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static boolean isOpAdminAccount() {
        // P2-3: the legacy "username == opadmin" username fallback was removed
        // in Sprint-15. Operations administrators must now hold the
        // {@link AuthoritiesConstants#OP_ADMIN} role. The username heuristic
        // was a soft-spot for any deployment that ever provisioned a non-admin
        // account named "opadmin".
        return hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.OP_ADMIN);
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication instanceof JwtAuthenticationToken jwt) {
            Map<String, Object> claims = jwt.getToken().getClaims();
            String preferred = stringClaim(claims, "preferred_username");
            if (hasText(preferred)) {
                return preferred;
            }
            String username = firstNonBlank(
                claims.get(org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames.USERNAME),
                claims.get("user_name"),
                claims.get("username"),
                claims.get("upn")
            );
            if (hasText(username)) {
                return String.valueOf(username).trim();
            }
            String sub = stringClaim(claims, "sub");
            if (hasText(sub)) {
                return sub;
            }
            String tokenName = jwt.getName();
            if (hasText(tokenName)) {
                return tokenName.trim();
            }
            return null;
        } else if (authentication.getPrincipal() instanceof DefaultOidcUser) {
            Map<String, Object> attributes = ((DefaultOidcUser) authentication.getPrincipal()).getAttributes();
            if (attributes.containsKey("preferred_username")) {
                return (String) attributes.get("preferred_username");
            }
        } else if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
            // Opaque token (portal session) path: attributes carry username/sub
            String preferred = principal.getAttribute("preferred_username");
            if (preferred != null && !preferred.isBlank()) return preferred;
            String username = principal.getAttribute(org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames.USERNAME);
            if (username != null && !username.isBlank()) return username;
            String sub = principal.getAttribute("sub");
            if (sub != null && !sub.isBlank()) return sub;
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    private static String stringClaim(Map<String, Object> claims, String key) {
        if (claims == null || key == null) {
            return null;
        }
        Object value = claims.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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
        return mapRolesToGrantedAuthorities(getRolesFromClaims(claims));
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getRolesFromClaims(Map<String, Object> claims) {
        List<String> roles = new ArrayList<>();

        collectRoles(claims.get("groups"), roles);
        collectRoles(claims.get("roles"), roles);
        collectRoles(claims.get(CLAIMS_NAMESPACE + "roles"), roles);

        // Keycloak realm_access.roles
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            collectRoles(realmMap.get("roles"), roles);
        }

        // Keycloak resource_access.{client}.roles
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

    private static String canonicalizeGovernanceRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String compact = role.replaceAll("[^A-Z0-9]", "");
        if (compact.isEmpty()) {
            return null;
        }
        if (compact.startsWith("SYS")) {
            return AuthoritiesConstants.SYS_ADMIN;
        }
        if ((compact.startsWith("AUTH") || compact.startsWith("IAM")) && compact.contains("ADMIN")) {
            return AuthoritiesConstants.AUTH_ADMIN;
        }
        if (compact.startsWith("AUDIT") || compact.startsWith("AUDITOR") || compact.contains("SECURITYAUDITOR")) {
            return AuthoritiesConstants.AUDITOR_ADMIN;
        }
        if (compact.startsWith("SECURITY") && compact.contains("AUDITOR")) {
            return AuthoritiesConstants.AUDITOR_ADMIN;
        }
        if (compact.startsWith("OP") && compact.contains("ADMIN")) {
            return AuthoritiesConstants.OP_ADMIN;
        }
        return null;
    }

    static String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String trimmed = role.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        // First, try exact alias match
        String alias = ROLE_ALIASES.get(upper);
        if (alias != null) return alias;

        // If it's already prefixed, normalize and retry alias map
        if (upper.startsWith("ROLE_")) {
            String noPrefix = upper.substring(5);
            String aliasNoPrefix = ROLE_ALIASES.get(noPrefix);
            if (aliasNoPrefix != null) return aliasNoPrefix;
            String canonical = canonicalizeGovernanceRole(noPrefix);
            if (canonical != null) return canonical;
            return upper; // keep as-is
        }

        String canonical = canonicalizeGovernanceRole(upper);
        if (canonical != null) return canonical;

        // As a last resort, prefix unknown role names
        return "ROLE_" + upper;
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

    @SuppressWarnings("unchecked")
    private static String extractDisplayNameFromClaims(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return null;
        }
        Object direct = firstNonBlank(
            claims.get("fullName"),
            claims.get("full_name"),
            claims.get("displayName"),
            claims.get("display_name"),
            claims.get("name")
        );
        if (direct != null) {
            String text = String.valueOf(direct).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        Object given = claims.get("given_name");
        Object family = claims.get("family_name");
        String combined = joinNameParts(given, family);
        if (combined != null) {
            return combined;
        }
        Object preferred = claims.get("preferred_username");
        if (preferred != null) {
            String text = String.valueOf(preferred).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        if (claims.containsKey("attributes")) {
            Object attributes = claims.get("attributes");
            if (attributes instanceof Map<?, ?> attrMap) {
                Object attrName = firstNonBlank(attrMap.get("fullName"), attrMap.get("displayName"));
                if (attrName != null) {
                    String text = String.valueOf(attrName).trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
                if (attrMap.containsKey("fullName")) {
                    Object value = attrMap.get("fullName");
                    if (value instanceof Collection<?> collection && !collection.isEmpty()) {
                        String candidate = String.valueOf(collection.iterator().next()).trim();
                        if (!candidate.isEmpty()) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Collection<?> collection) {
                for (Object element : collection) {
                    String candidate = String.valueOf(element).trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static String joinNameParts(Object given, Object family) {
        String givenText = given == null ? null : String.valueOf(given).trim();
        String familyText = family == null ? null : String.valueOf(family).trim();
        if ((givenText == null || givenText.isEmpty()) && (familyText == null || familyText.isEmpty())) {
            return null;
        }
        if (givenText != null && !givenText.isEmpty() && familyText != null && !familyText.isEmpty()) {
            return givenText + " " + familyText;
        }
        return givenText != null && !givenText.isEmpty() ? givenText : familyText;
    }
}
