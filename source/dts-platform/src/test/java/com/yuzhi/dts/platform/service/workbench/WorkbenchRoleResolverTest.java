package com.yuzhi.dts.platform.service.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.workbench.WorkbenchRoleResolver.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkbenchRoleResolverTest {

    private final WorkbenchRoleResolver resolver = new WorkbenchRoleResolver();

    @Test
    void resolve_empty_returns_EMP() {
        assertThat(resolver.resolve(List.of())).isEqualTo(Role.EMP);
        assertThat(resolver.resolve(null)).isEqualTo(Role.EMP);
    }

    @Test
    void resolve_contains_INST_LEADER_returns_INST_LEADER() {
        assertThat(resolver.resolve(List.of("ROLE_INST_LEADER"))).isEqualTo(Role.INST_LEADER);
        assertThat(resolver.resolve(List.of("INST_LEADER"))).isEqualTo(Role.INST_LEADER);
        // INST_LEADER wins even if DEPT_LEADER is also present.
        assertThat(resolver.resolve(List.of("ROLE_DEPT_LEADER", "ROLE_INST_LEADER"))).isEqualTo(Role.INST_LEADER);
    }

    @Test
    void resolve_contains_DEPT_LEADER_codes_returns_DEPT_LEADER() {
        assertThat(resolver.resolve(List.of("ROLE_DEPT_LEADER"))).isEqualTo(Role.DEPT_LEADER);
        assertThat(resolver.resolve(List.of("DEPT_LEADER"))).isEqualTo(Role.DEPT_LEADER);
        assertThat(resolver.resolve(List.of("ROLE_SUB_INST_LEADER"))).isEqualTo(Role.DEPT_LEADER);
        assertThat(resolver.resolve(List.of("FIN_MANAGER"))).isEqualTo(Role.DEPT_LEADER);
    }

    @Test
    void resolve_lowercase_roles_normalized_to_upper() {
        assertThat(resolver.resolve(List.of("role_inst_leader"))).isEqualTo(Role.INST_LEADER);
        assertThat(resolver.resolve(List.of("role_dept_leader"))).isEqualTo(Role.DEPT_LEADER);
        // An unknown / employee role falls through to EMP.
        assertThat(resolver.resolve(List.of("role_employee"))).isEqualTo(Role.EMP);
    }
}
