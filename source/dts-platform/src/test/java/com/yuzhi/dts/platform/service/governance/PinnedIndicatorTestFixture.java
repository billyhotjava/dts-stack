package com.yuzhi.dts.platform.service.governance;

import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.*;
import com.yuzhi.dts.platform.repository.governance.*;
import java.util.*;

final class PinnedIndicatorTestFixture {
    private PinnedIndicatorTestFixture() {}
    static void pin(GovIndicatorDefinition target, GovIndicatorDefinitionRepository definitions,
                    GovIndicatorVersionRepository versions, GovIndicatorDefinition... dependencies) {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            var refs = new ArrayList<IndicatorBusinessContextContract.MetricSourceRef>();
            for (var dependency : dependencies) {
                if (dependency.getVersion() == null) dependency.setVersion("v1");
                when(definitions.findById(dependency.getId())).thenReturn(Optional.of(dependency));
                GovIndicatorVersion snapshot = new GovIndicatorVersion();
                snapshot.setIndicator(dependency); snapshot.setVersion(dependency.getVersion()); snapshot.setStatus(dependency.getStatus());
                snapshot.setSnapshotJson(mapper.writeValueAsString(IndicatorMapper.toDto(dependency)));
                when(versions.findByIndicatorAndVersion(dependency, dependency.getVersion())).thenReturn(Optional.of(snapshot));
                refs.add(new IndicatorBusinessContextContract.MetricSourceRef(IndicatorBusinessContextContract.SourceType.INDICATOR_VERSION,
                    dependency.getId().toString(), dependency.getVersion()));
            }
            target.setSourceRefs(mapper.writeValueAsString(refs));
        } catch (Exception error) { throw new AssertionError(error); }
    }
}
