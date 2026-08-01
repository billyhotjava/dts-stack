package com.yuzhi.dts.platform.service.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.repository.ops.OpsBackfillRequestRepository;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort.WarehousePlanProjection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpsServiceWarehousePlanTest {

    @Test
    void devCenterMetricsUsesCanonicalWarehousePlanProjection() {
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        InfraExternalRunLogRepository runLogs = mock(InfraExternalRunLogRepository.class);
        WarehousePlanOperationsReadPort warehousePlans = mock(WarehousePlanOperationsReadPort.class);
        InfraExternalRunLog run = new InfraExternalRunLog();
        run.setArtifactId(planId);
        run.setArtifactName("finance_budget_fact");
        run.setEntryKey("dbt");
        run.setStatus("SUCCESS");
        run.setStartedAt(Instant.now());

        when(runLogs.findForMetrics(any(), any(), any(), any(), any(), any())).thenReturn(List.of(run));
        when(warehousePlans.listPlans())
            .thenReturn(List.of(new WarehousePlanProjection(planId, "财务预算规划", "FIN", "DESIGNING")));

        OpsService service = new OpsService(
            runLogs,
            mock(GovQualityRunRepository.class),
            warehousePlans,
            mock(OpsBackfillRequestRepository.class),
            mock(AirflowClient.class),
            new ObjectMapper()
        );

        Map<String, Object> result = service.devCenterMetrics(7, null, null, null, null, planId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> availablePlans = (List<Map<String, Object>>) result.get("availablePlans");
        assertThat(availablePlans)
            .containsExactly(
                Map.of(
                    "id",
                    planId,
                    "name",
                    "财务预算规划",
                    "ownerDept",
                    "FIN",
                    "status",
                    "DESIGNING",
                    "runCount",
                    1L
                )
            );
    }
}
