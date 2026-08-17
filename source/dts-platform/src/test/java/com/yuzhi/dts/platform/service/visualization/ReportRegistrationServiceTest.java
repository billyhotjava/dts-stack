package com.yuzhi.dts.platform.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportRegistrationServiceTest {

    @Test
    void replaysByStableAssetIdentityAndRejectsAnOlderVersion() {
        BiReportLinkRepository repository = mock(BiReportLinkRepository.class);
        ReportRegistrationService service = new ReportRegistrationService(repository);
        BiReportLink existing = new BiReportLink();
        existing.setAssetType("DASHBOARD");
        existing.setAssetKey("dashboard-7");
        existing.setAssetVersion(3L);
        existing.setCode("dts-bi-dashboard-dashboard-7");
        existing.setTitle("项目驾驶舱 v3");
        existing.setEngine("DTS_BI");
        existing.setReportType("DASHBOARD");
        existing.setClassification("DATA_INTERNAL");
        existing.setUrl("/bi/dashboards/7");
        when(repository.findByEngineAndAssetTypeAndAssetKey("DTS_BI", "DASHBOARD", "dashboard-7"))
            .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReportRegistrationService.RegistrationResult result = service.register(
            new ReportRegistrationService.ReportRegistrationCommand(
                "DTS_BI", "DASHBOARD", "dashboard-7", 2L, "stale title", "DASHBOARD",
                "/bi/dashboards/7", null, null, List.of("D1"), List.of("ROLE_ANALYST"),
                "DATA_INTERNAL", null, true
            )
        );

        assertThat(result.assetVersion()).isEqualTo(3L);
        assertThat(result.reconcileStatus()).isEqualTo("STALE_IGNORED");
        assertThat(existing.getTitle()).isEqualTo("项目驾驶舱 v3");
    }
}
