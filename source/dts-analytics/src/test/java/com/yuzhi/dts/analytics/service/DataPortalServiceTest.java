package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalDirectory;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenVersion;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDataPortalDirectoryRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDataPortalItemRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DataPortalServiceTest {

    private AnalyticsDataPortalDirectoryRepository directories;
    private AnalyticsDataPortalItemRepository items;
    private AnalyticsScreenRepository screens;
    private AnalyticsScreenVersionRepository screenVersions;
    private AnalyticsDashboardRepository dashboards;
    private DataPortalService service;

    @BeforeEach
    void setUp() {
        directories = mock(AnalyticsDataPortalDirectoryRepository.class);
        items = mock(AnalyticsDataPortalItemRepository.class);
        screens = mock(AnalyticsScreenRepository.class);
        screenVersions = mock(AnalyticsScreenVersionRepository.class);
        dashboards = mock(AnalyticsDashboardRepository.class);
        service = new DataPortalService(directories, items, screens, screenVersions, dashboards);
    }

    @Test
    void rejectsMovingADirectoryBelowItsOwnDescendant() {
        AnalyticsDataPortalDirectory january = directory(1L, "一月", null, 10);
        AnalyticsDataPortalDirectory projects = directory(2L, "项目管理", 1L, 10);
        when(directories.findById(1L)).thenReturn(Optional.of(january));
        when(directories.findAllByArchivedFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of(january, projects));

        assertThatThrownBy(() -> service.updateDirectory(1L, "一月", 2L))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void refusesToDeleteANonEmptyDirectory() {
        AnalyticsDataPortalDirectory projects = directory(2L, "项目管理", 1L, 10);
        when(directories.findById(2L)).thenReturn(Optional.of(projects));
        when(directories.existsByArchivedFalseAndParentId(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDirectory(2L))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void bindsOnlyPublishedScreens() {
        AnalyticsDataPortalDirectory projects = directory(2L, "项目管理", 1L, 10);
        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setId(101L);
        screen.setName("项目进度总览");
        AnalyticsScreenVersion published = new AnalyticsScreenVersion();
        when(directories.findById(2L)).thenReturn(Optional.of(projects));
        when(screens.findById(101L)).thenReturn(Optional.of(screen));
        when(screenVersions.findFirstByScreenIdAndCurrentPublishedTrue(101L)).thenReturn(Optional.of(published));
        when(items.existsByDirectoryIdAndContentTypeAndContentId(2L, "SCREEN", 101L)).thenReturn(false);

        service.createItem(2L, "SCREEN", 101L, 7L);

        verify(items).save(org.mockito.ArgumentMatchers.argThat(item ->
            item.getDirectoryId().equals(2L)
                && item.getContentType().equals("SCREEN")
                && item.getContentId().equals(101L)
                && item.getCreatedBy().equals(7L)));
    }

    @Test
    void rejectsDraftOrUnavailableDashboards() {
        AnalyticsDataPortalDirectory finance = directory(3L, "财务管理", 1L, 20);
        AnalyticsDashboard dashboard = new AnalyticsDashboard();
        dashboard.setId(201L);
        dashboard.setLifecycleStatus("DRAFT");
        dashboard.setRegistrationStatus("NOT_REGISTERED");
        when(directories.findById(3L)).thenReturn(Optional.of(finance));
        when(dashboards.findById(201L)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> service.createItem(3L, "DASHBOARD", 201L, 7L))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static AnalyticsDataPortalDirectory directory(Long id, String name, Long parentId, int sortOrder) {
        AnalyticsDataPortalDirectory directory = new AnalyticsDataPortalDirectory();
        directory.setId(id);
        directory.setName(name);
        directory.setParentId(parentId);
        directory.setSortOrder(sortOrder);
        directory.setArchived(false);
        return directory;
    }
}
