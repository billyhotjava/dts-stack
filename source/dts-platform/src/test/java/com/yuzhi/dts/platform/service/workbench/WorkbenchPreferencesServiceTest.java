package com.yuzhi.dts.platform.service.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference;
import com.yuzhi.dts.platform.repository.workbench.WorkbenchUserPreferenceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.workbench.dto.WorkbenchPreferenceItem;
import com.yuzhi.dts.platform.service.workbench.dto.WorkbenchPreferencesRequest;
import com.yuzhi.dts.platform.service.workbench.dto.WorkbenchPreferencesResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkbenchPreferencesServiceTest {

    @Mock
    private WorkbenchUserPreferenceRepository repository;

    private WorkbenchPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new WorkbenchPreferencesService(repository, new ObjectMapper());
    }

    @Test
    void getPreferencesReturnsRoleDefaultWhenUserHasNoSavedLayout() {
        when(repository.findByUsername("alice")).thenReturn(Optional.empty());

        WorkbenchPreferencesResponse response = service.getPreferences("alice", List.of(AuthoritiesConstants.EMPLOYEE));

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.availableComponents()).extracting("key")
            .containsExactly("leader-kpi", "top-reports", "core-assets", "screen-strip", "todo", "bi-delivery");
        assertThat(response.items()).extracting("key")
            .containsExactly("leader-kpi", "top-reports", "core-assets", "screen-strip", "todo", "bi-delivery");
        assertThat(response.items()).extracting("order").containsExactly(10, 20, 30, 40, 50, 60);
    }

    @Test
    void savePreferencesUsesCurrentUsernameAndNormalizesOrder() {
        when(repository.findByUsername("alice")).thenReturn(Optional.empty());
        when(repository.save(any(WorkbenchUserPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkbenchPreferencesResponse response = service.savePreferences(
            "alice",
            List.of(AuthoritiesConstants.INST_DATA_OWNER),
            new WorkbenchPreferencesRequest(List.of(
                new WorkbenchPreferenceItem("golden-chain", true, 99),
                new WorkbenchPreferenceItem("todo", false, 1),
                new WorkbenchPreferenceItem("data-sources", true, 50)
            ))
        );

        assertThat(response.items()).extracting("key").containsExactly("todo", "data-sources", "golden-chain");
        assertThat(response.items()).extracting("order").containsExactly(10, 20, 30);

        ArgumentCaptor<WorkbenchUserPreference> captor = ArgumentCaptor.forClass(WorkbenchUserPreference.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getLayoutJson()).contains("\"key\":\"todo\"");
        assertThat(captor.getValue().getLayoutJson()).doesNotContain("bob");
    }

    @Test
    void savePreferencesRejectsUnknownComponentKey() {
        assertThatThrownBy(() -> service.savePreferences(
            "alice",
            List.of(AuthoritiesConstants.INST_DATA_OWNER),
            new WorkbenchPreferencesRequest(List.of(new WorkbenchPreferenceItem("customer-demo", true, 10)))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown component key");
    }

    @Test
    void resetPreferencesDeletesSavedLayoutAndReturnsRoleDefault() {
        WorkbenchUserPreference existing = new WorkbenchUserPreference();
        existing.setUsername("alice");
        existing.setLayoutJson("{\"items\":[{\"key\":\"todo\",\"visible\":false,\"order\":10}]}");
        when(repository.findByUsername("alice")).thenReturn(Optional.of(existing));

        WorkbenchPreferencesResponse response = service.resetPreferences("alice", List.of(AuthoritiesConstants.EMPLOYEE));

        verify(repository).delete(existing);
        assertThat(response.items()).extracting("key")
            .containsExactly("leader-kpi", "top-reports", "core-assets", "screen-strip", "todo", "bi-delivery");
    }
}
