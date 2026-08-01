package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.service.governance.GovernanceIndicatorEvidenceReadPort.EvidenceState;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class GovernanceIndicatorEvidenceReadServiceTest {

    private static final UUID INDICATOR_ID = UUID.fromString("60000000-0000-0000-0000-000000000068");

    @Test
    void exposesOnlyAPublishedNumericOwnerVersion() {
        GovIndicatorDefinitionRepository repository = mock(GovIndicatorDefinitionRepository.class);
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(INDICATOR_ID);
        indicator.setStatus("PUBLISHED");
        indicator.setVersion("v2");
        when(repository.findById(INDICATOR_ID)).thenReturn(Optional.of(indicator));
        GovernanceIndicatorEvidenceReadService service = new GovernanceIndicatorEvidenceReadService(repository);

        var current = service.read(INDICATOR_ID);
        assertThat(current.state()).isEqualTo(EvidenceState.CURRENT);
        assertThat(current.version()).isEqualTo(2);

        indicator.setStatus("DRAFT");
        assertThat(service.read(INDICATOR_ID).state()).isEqualTo(EvidenceState.STALE);
        indicator.setStatus("PUBLISHED");
        indicator.setVersion("2026.07");
        assertThat(service.read(INDICATOR_ID).state()).isEqualTo(EvidenceState.STALE);
    }

    @Test
    void failsClosedWhenTheIndicatorOwnerCannotBeRead() {
        GovIndicatorDefinitionRepository repository = mock(GovIndicatorDefinitionRepository.class);
        when(repository.findById(INDICATOR_ID)).thenThrow(new DataAccessResourceFailureException("owner unavailable"));

        assertThat(new GovernanceIndicatorEvidenceReadService(repository).read(INDICATOR_ID).state())
            .isEqualTo(EvidenceState.UNKNOWN);
    }
}
