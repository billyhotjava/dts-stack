package com.yuzhi.dts.admin.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the governance triad accounts (sysadmin / authadmin / auditadmin)
 * plus the operations-only opadmin account.
 * <p>
 * Before this registry the same data was duplicated across {@code KeycloakApiResource}
 * (PROTECTED_USERNAMES / TRIAD_AUTHORITIES / BUILTIN_ADMIN_LABELS) and
 * {@code AuditEntryResource.builtinDisplayNames}. Renaming a triad account in production
 * required edits in 3+ files. Now operators override the four account names once via
 * {@code dts.security.triad-display.*} and every consumer picks up the new mapping.
 */
@Component
public class TriadAccountRegistry {

    private final Map<String, String> displayLabels;
    private final Set<String> protectedUsernames;
    private final Set<String> triadAuthorities;
    private final Set<String> protectedAuthorities;

    public TriadAccountRegistry(
        @Value("${dts.security.triad-display.sys-admin:sysadmin}") String sysAdminAccount,
        @Value("${dts.security.triad-display.auth-admin:authadmin}") String authAdminAccount,
        @Value("${dts.security.triad-display.auditor-admin:auditadmin}") String auditorAdminAccount,
        @Value("${dts.security.triad-display.op-admin:opadmin}") String opAdminAccount
    ) {
        Map<String, String> labels = new LinkedHashMap<>();
        addLabel(labels, sysAdminAccount, "系统管理员");
        addLabel(labels, authAdminAccount, "授权管理员");
        addLabel(labels, auditorAdminAccount, "安全审计员");
        addLabel(labels, opAdminAccount, "运维管理员");
        this.displayLabels = Collections.unmodifiableMap(labels);

        // Protected usernames = the configured account names (excludes blanks).
        this.protectedUsernames = Collections.unmodifiableSet(new LinkedHashSet<>(labels.keySet()));

        // Authority names are stable Keycloak role identifiers; defined in AuthoritiesConstants.
        this.triadAuthorities = Set.of(
            AuthoritiesConstants.SYS_ADMIN,
            AuthoritiesConstants.AUTH_ADMIN,
            AuthoritiesConstants.AUDITOR_ADMIN
        );
        this.protectedAuthorities = Set.of(
            AuthoritiesConstants.SYS_ADMIN,
            AuthoritiesConstants.AUTH_ADMIN,
            AuthoritiesConstants.AUDITOR_ADMIN,
            AuthoritiesConstants.OP_ADMIN
        );
    }

    private static void addLabel(Map<String, String> map, String account, String label) {
        if (account == null) {
            return;
        }
        String trimmed = account.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.isEmpty()) {
            map.put(trimmed, label);
        }
    }

    public Map<String, String> displayLabels() {
        return displayLabels;
    }

    public Set<String> protectedUsernames() {
        return protectedUsernames;
    }

    public Set<String> triadAuthorities() {
        return triadAuthorities;
    }

    public Set<String> protectedAuthorities() {
        return protectedAuthorities;
    }

    public Optional<String> displayLabelFor(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(displayLabels.get(username.trim().toLowerCase(Locale.ROOT)));
    }

    public boolean isProtectedUsername(String username) {
        if (username == null) {
            return false;
        }
        return protectedUsernames.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isTriadAuthority(String authority) {
        return authority != null && triadAuthorities.contains(authority);
    }

    public boolean isProtectedAuthority(String authority) {
        return authority != null && protectedAuthorities.contains(authority);
    }
}
