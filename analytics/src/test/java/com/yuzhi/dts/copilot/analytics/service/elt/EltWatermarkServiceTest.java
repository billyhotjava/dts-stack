package com.yuzhi.dts.copilot.analytics.service.elt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.copilot.analytics.domain.EltSyncWatermark;
import com.yuzhi.dts.copilot.analytics.repository.EltSyncWatermarkRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EltWatermarkServiceTest {

    @Mock
    private EltSyncWatermarkRepository watermarkRepository;

    @Test
    void markCompletedShouldPersistSyncLifecycleFields() throws Exception {
        EltSyncWatermark entity = new EltSyncWatermark();
        entity.setTargetTable("mart_project_fulfillment_daily");
        when(watermarkRepository.findByTargetTable(eq("mart_project_fulfillment_daily")))
                .thenReturn(Optional.of(entity));
        when(watermarkRepository.save(any(EltSyncWatermark.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EltWatermarkService service = new EltWatermarkService(watermarkRepository);
        Instant watermark = Instant.parse("2026-03-20T10:15:30Z");

        service.markCompleted("mart_project_fulfillment_daily", "batch-001", watermark, 128, 345);

        assertThat(readField(entity, "syncStatus")).isEqualTo("COMPLETED");
        assertThat(readField(entity, "lastWatermark")).isEqualTo(watermark);
        assertThat(readField(entity, "lastSyncRows")).isEqualTo(128);
        assertThat(readField(entity, "lastSyncDurationMs")).isEqualTo(345);
        assertThat(readField(entity, "lastSyncTime")).isNotNull();
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
