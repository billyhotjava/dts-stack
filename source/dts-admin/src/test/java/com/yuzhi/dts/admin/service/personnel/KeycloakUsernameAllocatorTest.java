package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 现场约定：userCode 不同即为不同账号，仅大小写不同的写法用于标记部门变更。
 * Keycloak 用户名一律小写且唯一，因此撞车的那一个要拿到带后缀的用户名。
 */
class KeycloakUsernameAllocatorTest {

    private AdminKeycloakUserRepository repository;
    private KeycloakUsernameAllocator allocator;

    @BeforeEach
    void setUp() {
        repository = mock(AdminKeycloakUserRepository.class);
        allocator = new KeycloakUsernameAllocator(repository);
    }

    @Test
    @DisplayName("小写名空闲时直接使用，纯大写编码的用户名与现状一致")
    void usesPlainLowercaseWhenFree() {
        when(repository.findFirstByPersonCode("ABC123")).thenReturn(Optional.empty());
        when(repository.findByUsernameIgnoreCase("abc123")).thenReturn(Optional.empty());

        assertThat(allocator.allocate("ABC123", "ABC123")).isEqualTo("abc123");
    }

    @Test
    @DisplayName("小写名被另一个编码占用时分配后缀")
    void allocatesSuffixWhenPlainNameTakenByAnotherCode() {
        when(repository.findFirstByPersonCode("LI01")).thenReturn(Optional.empty());
        when(repository.findByUsernameIgnoreCase("li01")).thenReturn(Optional.of(snapshot("li01", "Li01")));

        String allocated = allocator.allocate("LI01", "LI01");

        assertThat(allocated).startsWith("li01~").isNotEqualTo("li01");
    }

    @Test
    @DisplayName("同一编码每次分配结果一致，与导入顺序无关")
    void suffixIsDeterministic() {
        when(repository.findFirstByPersonCode("LI01")).thenReturn(Optional.empty());
        when(repository.findByUsernameIgnoreCase("li01")).thenReturn(Optional.of(snapshot("li01", "Li01")));

        assertThat(allocator.allocate("LI01", "LI01")).isEqualTo(allocator.allocate("LI01", "LI01"));
    }

    @Test
    @DisplayName("不同写法得到不同后缀")
    void differentCasePatternsGetDifferentSuffixes() {
        when(repository.findByUsernameIgnoreCase("li01")).thenReturn(Optional.of(snapshot("li01", "li01")));
        when(repository.findFirstByPersonCode("LI01")).thenReturn(Optional.empty());
        when(repository.findFirstByPersonCode("Li01")).thenReturn(Optional.empty());

        assertThat(allocator.allocate("LI01", "LI01")).isNotEqualTo(allocator.allocate("Li01", "Li01"));
    }

    @Test
    @DisplayName("该编码已有账号时沿用既有用户名")
    void reusesUsernameRecordedForThatCode() {
        when(repository.findFirstByPersonCode("LI01")).thenReturn(Optional.of(snapshot("li01~3", "LI01")));

        assertThat(allocator.allocate("LI01", "LI01")).isEqualTo("li01~3");
    }

    @Test
    @DisplayName("占用者没有记录过编码时可被认领，不给存量账号改名")
    void claimsPlainNameWhenOccupantHasNoPersonCode() {
        when(repository.findFirstByPersonCode("Li01")).thenReturn(Optional.empty());
        when(repository.findByUsernameIgnoreCase("li01")).thenReturn(Optional.of(snapshot("li01", null)));

        assertThat(allocator.allocate("Li01", "Li01")).isEqualTo("li01");
    }

    @Test
    @DisplayName("同一编码再次导入时仍用原名")
    void samePersonKeepsPlainName() {
        when(repository.findFirstByPersonCode("Li01")).thenReturn(Optional.empty());
        when(repository.findByUsernameIgnoreCase("li01")).thenReturn(Optional.of(snapshot("li01", "Li01")));

        assertThat(allocator.allocate("Li01", "Li01")).isEqualTo("li01");
    }

    private AdminKeycloakUser snapshot(String username, String personCode) {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setUsername(username);
        user.setPersonCode(personCode);
        return user;
    }
}
