package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** F11-UT-007: allocation and replay only; does not prove account provisioning or PKI compatibility. */
class F11PersonnelBindingTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @DisplayName("F11-UT-007：两种首次导入顺序均保留不同编码，重放沿用已保存绑定")
    void preservesIndependentBindingsInEitherImportOrder(boolean uppercaseFirst) {
        Map<String, AdminKeycloakUser> records = new LinkedHashMap<>();
        AdminKeycloakUserRepository repository = mock(AdminKeycloakUserRepository.class);
        when(repository.findFirstByPersonCode(anyString())).thenAnswer(invocation ->
            Optional.ofNullable(records.get(invocation.getArgument(0, String.class)))
        );
        when(repository.findByUsernameIgnoreCase(anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0, String.class);
            return records.values().stream().filter(row -> username.equalsIgnoreCase(row.getUsername())).findFirst();
        });
        KeycloakUsernameAllocator allocator = new KeycloakUsernameAllocator(repository);
        List<String> order = uppercaseFirst ? List.of("ABC123", "abc123") : List.of("abc123", "ABC123");

        for (String code : order) {
            AdminKeycloakUser record = new AdminKeycloakUser();
            record.setPersonCode(code);
            record.setUsername(allocator.allocate(code, code));
            records.put(code, record);
        }

        assertThat(records).hasSize(2);
        assertThat(records.get(order.getFirst()).getUsername()).isEqualTo("abc123");
        assertThat(records.get(order.getLast()).getUsername()).isEqualTo(uppercaseFirst ? "abc123.0" : "abc123.7");
        assertThat(records.get("ABC123").getUsername()).isNotEqualTo(records.get("abc123").getUsername());

        // A new allocator represents a restarted import worker, retaining the saved repository state.
        KeycloakUsernameAllocator restarted = new KeycloakUsernameAllocator(repository);
        for (String code : List.of("abc123", "ABC123")) {
            assertThat(restarted.allocate(code, code)).isEqualTo(records.get(code).getUsername());
        }
        assertThat(records).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ABC123", "abc123" })
    @DisplayName("F11-UT-007：已有分配不因输入账号名称变化而重新计算")
    void existingBindingSurvivesChangedAccountSuggestion(String personCode) {
        AdminKeycloakUserRepository repository = mock(AdminKeycloakUserRepository.class);
        AdminKeycloakUser record = new AdminKeycloakUser();
        record.setPersonCode(personCode);
        record.setUsername("previously-allocated-login");
        when(repository.findFirstByPersonCode(personCode)).thenReturn(Optional.of(record));

        assertThat(new KeycloakUsernameAllocator(repository).allocate(personCode, "another-name"))
            .isEqualTo("previously-allocated-login");
        org.mockito.Mockito.verify(repository).findFirstByPersonCode(personCode);
        verifyNoMoreInteractions(repository);
    }
}
