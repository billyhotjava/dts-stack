package com.yuzhi.dts.platform.service.workbench;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective workbench role (EMP / DEPT_LEADER / INST_LEADER) from a
 * list of granted authority strings.
 *
 * <p>Uses canonical role constants from {@link AuthoritiesConstants} plus a small
 * set of historical aliases (non-prefixed, legacy Keycloak codes) to stay
 * backward-compatible with older realms.
 */
@Component
public class WorkbenchRoleResolver {

    // INST leader: canonical ROLE_INST_LEADER plus legacy bare code.
    private static final Set<String> INST_LEADER_CODES = Set.of(
        AuthoritiesConstants.INST_LEADER, // ROLE_INST_LEADER
        "INST_LEADER"
    );

    // DEPT leader alias list. Canonical is ROLE_DEPT_LEADER; SUB_INST_LEADER /
    // FIN_MANAGER are historical aliases surfaced by older realms.
    // TODO: consolidate once Keycloak realm emits only ROLE_DEPT_LEADER.
    private static final Set<String> DEPT_LEADER_CODES = Set.of(
        AuthoritiesConstants.DEPT_LEADER, // ROLE_DEPT_LEADER
        "DEPT_LEADER",
        "ROLE_SUB_INST_LEADER",
        "SUB_INST_LEADER",
        "ROLE_FIN_MANAGER",
        "FIN_MANAGER"
    );

    public enum Role {
        EMP,
        DEPT_LEADER,
        INST_LEADER
    }

    public Role resolve(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Role.EMP;
        }
        Set<String> normalized = roles
            .stream()
            .filter(r -> r != null && !r.isBlank())
            .map(r -> r.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
        if (normalized.stream().anyMatch(INST_LEADER_CODES::contains)) {
            return Role.INST_LEADER;
        }
        if (normalized.stream().anyMatch(DEPT_LEADER_CODES::contains)) {
            return Role.DEPT_LEADER;
        }
        return Role.EMP;
    }
}
