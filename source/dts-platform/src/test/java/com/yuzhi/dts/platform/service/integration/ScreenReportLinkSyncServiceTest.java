package com.yuzhi.dts.platform.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.integration.dto.ScreenSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class ScreenReportLinkSyncServiceTest {

    @Mock
    ScreenSyncClient client;

    @Mock
    BiReportLinkRepository repo;

    ScreenReportLinkSyncService service;

    @BeforeEach
    void setUp() {
        service = new ScreenReportLinkSyncService(client, repo);
    }

    @Test
    @DisplayName("creates new BiReportLink for previously-unseen screen")
    void createsNew() {
        when(client.listScreens()).thenReturn(List.of(
            new ScreenSummary(7L, "Sales", "desc", "INTERNAL", "DEPT_A", false, Instant.now())
        ));
        when(repo.findAllByCodePrefix(ScreenReportLinkSyncService.CODE_PREFIX)).thenReturn(List.of());

        SyncResult r = service.reconcileOnce();

        assertThat(r.created()).isEqualTo(1);
        assertThat(r.updated()).isZero();
        assertThat(r.archived()).isZero();
        assertThat(r.skipped()).isFalse();

        ArgumentCaptor<BiReportLink> captor = ArgumentCaptor.forClass(BiReportLink.class);
        verify(repo).save(captor.capture());
        BiReportLink saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("screen-7");
        assertThat(saved.getTitle()).isEqualTo("Sales");
        assertThat(saved.getReportType()).isEqualTo("SCREEN");
        assertThat(saved.getEngine()).isEqualTo("DTS_BI");
        assertThat(saved.getUrl()).isEqualTo("/bi/screens/7/preview");
        assertThat(saved.getDeptCodes()).isEqualTo("DEPT_A");
        assertThat(saved.getClassification()).isEqualTo("INTERNAL");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getSource()).isEqualTo("SCREEN_SYNC");
    }

    @Test
    @DisplayName("updates existing link when title or classification drifts")
    void updatesExisting() {
        BiReportLink existing = mirrorOf(7L, "Old Name", "INTERNAL", "DEPT_A", true);
        when(client.listScreens()).thenReturn(List.of(
            new ScreenSummary(7L, "New Name", null, "SECRET", "DEPT_A", false, Instant.now())
        ));
        when(repo.findAllByCodePrefix(ScreenReportLinkSyncService.CODE_PREFIX)).thenReturn(List.of(existing));

        SyncResult r = service.reconcileOnce();

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.created()).isZero();
        assertThat(existing.getTitle()).isEqualTo("New Name");
        assertThat(existing.getClassification()).isEqualTo("SECRET");
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("flips enabled=false when upstream screen archived")
    void archivesScreen() {
        BiReportLink existing = mirrorOf(7L, "Sales", "INTERNAL", "DEPT_A", true);
        when(client.listScreens()).thenReturn(List.of(
            new ScreenSummary(7L, "Sales", null, "INTERNAL", "DEPT_A", true, Instant.now())
        ));
        when(repo.findAllByCodePrefix(ScreenReportLinkSyncService.CODE_PREFIX)).thenReturn(List.of(existing));

        SyncResult r = service.reconcileOnce();

        assertThat(r.updated()).isEqualTo(1);
        assertThat(existing.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("soft-disables stale auto-synced rows when upstream removed")
    void disablesStale() {
        BiReportLink stale = mirrorOf(99L, "Gone", "INTERNAL", "DEPT_A", true);
        when(client.listScreens()).thenReturn(List.of()); // upstream returned nothing
        when(repo.findAllByCodePrefix(ScreenReportLinkSyncService.CODE_PREFIX)).thenReturn(List.of(stale));

        SyncResult r = service.reconcileOnce();

        assertThat(r.archived()).isEqualTo(1);
        assertThat(stale.isEnabled()).isFalse();
        verify(repo).save(stale);
    }

    @Test
    @DisplayName("does not touch rows whose source is MANUAL")
    void preservesManualRows() {
        when(client.listScreens()).thenReturn(List.of(
            new ScreenSummary(7L, "Sales", null, "INTERNAL", "DEPT_A", false, Instant.now())
        ));
        // findAllByCodePrefix("screen-") deliberately returns NO rows; manual rows
        // never enter reconcile's view, so they cannot be updated or deleted.
        when(repo.findAllByCodePrefix(ScreenReportLinkSyncService.CODE_PREFIX)).thenReturn(List.of());

        service.reconcileOnce();

        // The single save() is the new screen mirror; manual rows are untouched.
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("returns skipped when client is not configured")
    void clientNotConfigured() {
        when(client.listScreens()).thenThrow(new NotConfiguredException("not set"));

        SyncResult r = service.reconcileOnce();

        assertThat(r.skipped()).isTrue();
        assertThat(r.created()).isZero();
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("returns failed result when upstream call errors")
    void clientFails() {
        when(client.listScreens()).thenThrow(new RestClientException("connection refused"));

        SyncResult r = service.reconcileOnce();

        assertThat(r.skipped()).isFalse();
        assertThat(r.error()).contains("connection refused");
        verifyNoInteractions(repo);
    }

    private static BiReportLink mirrorOf(long id, String title, String classification, String dept, boolean enabled) {
        BiReportLink l = new BiReportLink();
        l.setCode("screen-" + id);
        l.setTitle(title);
        l.setEngine("DTS_BI");
        l.setReportType("SCREEN");
        l.setUrl("/bi/screens/" + id + "/preview");
        l.setClassification(classification);
        l.setDeptCodes(dept);
        l.setEnabled(enabled);
        l.setSortOrder(0);
        l.setSource("SCREEN_SYNC");
        return l;
    }
}
