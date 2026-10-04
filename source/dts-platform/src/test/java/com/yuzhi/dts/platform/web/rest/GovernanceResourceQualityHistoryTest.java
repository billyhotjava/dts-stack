package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.governance.QualityRunService;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GovernanceResourceQualityHistoryTest {

    @Mock
    private QualityRunService qualityRunService;

    @InjectMocks
    private GovernanceResource resource;

    @Test
    void preservesSkippedStatusWithoutInventingAPassRate() {
        UUID ruleId = UUID.fromString("10000000-0000-0000-0000-000000000020");
        QualityRunDto run = new QualityRunDto();
        run.setId(UUID.fromString("20000000-0000-0000-0000-000000000020"));
        run.setStatus("SKIPPED");

        when(qualityRunService.recentByRule(ruleId, 10)).thenReturn(List.of(run));

        Map<String, Object> history = resource.getRuleHistory(ruleId, 10).getData().getFirst();

        assertThat(history).containsEntry("status", "SKIPPED");
        assertThat(history).containsKey("passRate");
        assertThat(history.get("passRate")).isNull();
    }
}
