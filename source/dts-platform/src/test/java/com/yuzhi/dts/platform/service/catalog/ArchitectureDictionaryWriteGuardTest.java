package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class ArchitectureDictionaryWriteGuardTest {

    private final ArchitectureDictionaryWriteGuard guard = new ArchitectureDictionaryWriteGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ROLE_ADMIN", "ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER" })
    void platformArchitectureOwnerCanWrite(String authority) {
        authenticate(authority);

        assertThatCode(guard::requireWriteAccess).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ROLE_INST_LEADER", "ROLE_DEPT_DATA_OWNER", "ROLE_DEPT_LEADER", "ROLE_EMPLOYEE" })
    void departmentAndNonAllowlistRolesAreReadOnly(String authority) {
        authenticate(authority);

        assertThatThrownBy(guard::requireWriteAccess)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception.getReason()).isEqualTo("Platform architecture dictionary is read-only for this role");
            });
    }

    @Test
    void unauthenticatedActorCannotWrite() {
        assertThatThrownBy(guard::requireWriteAccess)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
    }

    @Test
    void preAuthorizeExpressionUsesTheSameExactAllowlist() {
        assertThat(ArchitectureDictionaryWriteGuard.WRITE_EXPRESSION)
            .contains("ROLE_ADMIN", "ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER")
            .doesNotContain("ROLE_INST_LEADER", "ROLE_DEPT_DATA_OWNER", "ROLE_DEPT_LEADER");
    }

    private static void authenticate(String authority) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));
        SecurityContextHolder
            .getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("xiezm", "n/a", authorities));
    }
}
