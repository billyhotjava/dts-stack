package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateSchemaOnlyLineageTest {
    @Test
    void structurePublicationDoesNotInventPhysicalInputsFromLogicalDependencies() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = new CandidatePublicationRepository(jdbc, new ObjectMapper());
        var candidate = mock(CandidateView.class);
        var model = mock(ModelSpecView.class);
        when(candidate.origin()).thenReturn(CandidateOrigin.SCHEMA_ONLY_INTENT);
        Object result = ReflectionTestUtils.invokeMethod(repository, "replaceCatalogLineage", candidate, model, UUID.randomUUID(), null, "xiezm", Instant.now());
        assertThat(result).isEqualTo(List.of());
        verifyNoInteractions(model);
        when(candidate.origin()).thenReturn(CandidateOrigin.BATCH_WORKBENCH);
        ReflectionTestUtils.invokeMethod(repository, "replaceCatalogLineage", candidate, model, UUID.randomUUID(), null, "xiezm", Instant.now());
        verify(model).dependsOn();
    }
}
