package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenOwnershipServiceLocalTest {

    private AnalyticsScreenAccessRepository repo;
    private ScreenOwnershipService service;

    private AnalyticsScreenAccess access(Long id, Long screenId, String type, String granteeId, String perm) {
        AnalyticsScreenAccess a = new AnalyticsScreenAccess();
        a.setId(id);
        a.setScreenId(screenId);
        a.setGranteeType(type);
        a.setGranteeId(granteeId);
        a.setPermission(perm);
        a.setGrantedAt(Instant.now());
        return a;
    }

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(AnalyticsScreenAccessRepository.class);
        service = new ScreenOwnershipService(repo);
    }

    @Test
    void listGrants_returns_mapped_records() {
        when(repo.findByScreenId(1L)).thenReturn(List.of(
            access(10L, 1L, "USER", "42", "OWNER"),
            access(11L, 1L, "USER", "99", "VIEWER")));
        List<Map<String, Object>> grants = service.listGrants(1L);
        assertThat(grants).hasSize(2);
        assertThat(grants.get(0)).containsEntry("granteeId", "42");
        assertThat(grants.get(0)).containsEntry("permission", "OWNER");
    }

    @Test
    void createGrant_inserts_new_record() {
        when(repo.findByScreenIdAndGranteeTypeAndGranteeId(1L, "USER", "42")).thenReturn(Optional.empty());
        ArgumentCaptor<AnalyticsScreenAccess> captor = ArgumentCaptor.forClass(AnalyticsScreenAccess.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createGrant(1L, "USER", "42", "OWNER", 7L);

        verify(repo).save(captor.capture());
        AnalyticsScreenAccess saved = captor.getValue();
        assertThat(saved.getScreenId()).isEqualTo(1L);
        assertThat(saved.getGranteeId()).isEqualTo("42");
        assertThat(saved.getPermission()).isEqualTo("OWNER");
        assertThat(saved.getGrantedBy()).isEqualTo(7L);
    }

    @Test
    void createGrant_updates_existing_record() {
        AnalyticsScreenAccess existing = access(10L, 1L, "USER", "42", "VIEWER");
        when(repo.findByScreenIdAndGranteeTypeAndGranteeId(1L, "USER", "42")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createGrant(1L, "USER", "42", "MANAGER", 7L);

        verify(repo).save(existing);
        assertThat(existing.getPermission()).isEqualTo("MANAGER");
    }

    @Test
    void removeAllGrants_deletes_by_screen_id() {
        service.removeAllGrants(1L);
        verify(repo).deleteByScreenId(1L);
    }

    @Test
    void revokeGrant_deletes_by_id() {
        service.revokeGrant(10L);
        verify(repo).deleteById(10L);
    }
}
