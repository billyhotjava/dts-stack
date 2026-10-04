package com.yuzhi.dts.copilot.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.copilot.analytics.repository.EltSyncWatermarkRepository;
import com.yuzhi.dts.copilot.analytics.service.elt.EltSyncService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class EltMonitorResourceTest {

    @Mock
    private EltSyncWatermarkRepository watermarkRepository;

    @Mock
    private EltSyncService eltSyncService;

    @Test
    void triggerSyncShouldDelegateToEltSyncService() {
        when(eltSyncService.getRegisteredTables()).thenReturn(List.of("mart_project_fulfillment_daily"));

        EltMonitorResource resource = new EltMonitorResource(watermarkRepository, eltSyncService);

        ResponseEntity<Map<String, Object>> response = resource.triggerSync("mart_project_fulfillment_daily");

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(eltSyncService, timeout(1000)).runSync("mart_project_fulfillment_daily");
    }

    @Test
    void triggerSyncShouldReturnNotFoundWhenTableIsUnregistered() {
        when(eltSyncService.getRegisteredTables()).thenReturn(List.of("fact_field_operation_event"));

        EltMonitorResource resource = new EltMonitorResource(watermarkRepository, eltSyncService);

        ResponseEntity<Map<String, Object>> response = resource.triggerSync("mart_project_fulfillment_daily");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
