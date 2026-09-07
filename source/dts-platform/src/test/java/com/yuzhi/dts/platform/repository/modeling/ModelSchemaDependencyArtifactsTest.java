package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildEntry;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelSchemaDependencyArtifactsTest {
    @Test
    void schemaOnlyDoesNotReadLogicalUpstreamOrPhysicalObservations() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CandidateBuildScope scope = scope();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(scope.tenantId()),
            eq(scope.candidateId()), eq(scope.candidateVersion()), eq(scope.executionTargetKey())))
            .thenReturn(true);
        var repository = new ModelMaterializationBuildRepository(jdbc, new ObjectMapper(), new ModelMaterializationProperties());
        assertThat(repository.loadPinnedDependencyArtifacts(scope)).isEmpty();
        verify(jdbc).queryForObject(contains("origin = 'SCHEMA_ONLY_INTENT'"), eq(Boolean.class),
            eq(scope.tenantId()), eq(scope.candidateId()), eq(scope.candidateVersion()), eq(scope.executionTargetKey()));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void ordinaryBuildStillRequiresPinnedRevisionEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CandidateBuildScope scope = scope();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(scope.tenantId()),
            eq(scope.candidateId()), eq(scope.candidateVersion()), eq(scope.executionTargetKey())))
            .thenReturn(false);
        var repository = new ModelMaterializationBuildRepository(jdbc, new ObjectMapper(), new ModelMaterializationProperties());
        assertThatThrownBy(() -> repository.loadPinnedDependencyArtifacts(scope))
            .hasMessageContaining("selected model revision snapshot is unavailable");
    }

    private static CandidateBuildScope scope() {
        var entry = new CandidateBuildEntry(UUID.randomUUID(), UUID.randomUUID(), 1, "a".repeat(64),
            1, "b".repeat(64), "DBT_MANAGED", "model.project.target", "target", List.of());
        return new CandidateBuildScope("default", UUID.randomUUID(), 2, UUID.randomUUID(),
            "postgres-primary", "c".repeat(64), List.of(entry));
    }
}
