package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.*;
import com.yuzhi.dts.platform.repository.governance.*;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.*;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorVersionDto;
import com.yuzhi.dts.platform.service.modeling.*;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IndicatorCalculationServiceTest {
    private final UUID id = UUID.randomUUID();
    private final GovIndicatorDefinitionRepository definitions = mock(GovIndicatorDefinitionRepository.class);
    private final GovIndicatorRunRepository runs = mock(GovIndicatorRunRepository.class);
    private final IndicatorService indicators = mock(IndicatorService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final IndicatorCalculationService service = spy(new IndicatorCalculationService(definitions,
        mock(GovIndicatorReferenceRepository.class), runs, mock(ModelSpecApplicationService.class), mock(QueryGateway.class),
        new ControlledIndicatorDerivationCompiler(), mapper, indicators, mock(PublishedIndicatorVersionReader.class),
        mock(AccessChecker.class), mock(ModelSpecReader.class), mock(AssetPermissionService.class), mock(CatalogModelServingProjectionRepository.class), "default"));

    @Test void persistsResolvedVersionsAndDoesNotCompareDifferentVersions() throws Exception {
        prepare();
        GovIndicatorRun old = new GovIndicatorRun(); old.setStatus("SUCCESS"); old.setIndicatorVersion("v1"); old.setComputedValue(BigDecimal.TEN);
        when(runs.findByIndicatorIdOrderByRunAtDesc(id)).thenReturn(List.of(old));
        var ref = new VersionRef(id, "v2");
        var result = new Result(List.of("metric_0"), List.of(Map.of("metric_0", new BigDecimal("3"))), List.of(ref), "query-2", Instant.now(), false, List.of());
        doReturn(result).when(service).query(any(Query.class), eq("D01"));
        var batch = service.calculate(List.of(id), "D01");
        assertThat(batch.successCount()).isEqualTo(1);
        assertThat(batch.items().get(0).indicatorVersion()).isEqualTo("v2");
        assertThat(batch.items().get(0).previousValue()).isNull();
        ArgumentCaptor<GovIndicatorRun> saved = ArgumentCaptor.forClass(GovIndicatorRun.class);
        verify(runs).save(saved.capture());
        assertThat(saved.getValue().getQueryId()).isEqualTo("query-2");
        assertThat(saved.getValue().getDependencyVersions()).contains(id.toString(), "v2");
        verify(indicators).getVersion(id, "v2", "D01");
    }

    @Test void preservesOneFailureWithoutManufacturingAZeroResult() throws Exception {
        prepare();
        doThrow(new IndicatorConflictException("固定模型当前无服务数据")).when(service).query(any(Query.class), eq("D01"));
        var batch = service.calculate(List.of(id), "D01");
        assertThat(batch.failedCount()).isEqualTo(1);
        assertThat(batch.items().get(0).value()).isNull();
        assertThat(batch.items().get(0).errorMessage()).contains("固定模型");
        verify(runs, times(1)).save(any());
    }

    private void prepare() throws Exception {
        GovIndicatorDefinition definition = new GovIndicatorDefinition(); definition.setId(id); definition.setCode("TOTAL");
        definition.setName("总数"); definition.setVersion("v2"); definition.setStatus("PUBLISHED"); definition.setMetricType("ATOMIC");
        when(definitions.findById(id)).thenReturn(Optional.of(definition));
        var snapshot = new IndicatorVersionDto(); snapshot.setStatus("PUBLISHED"); snapshot.setVersion("v2");
        snapshot.setSnapshotJson(mapper.writeValueAsString(IndicatorMapper.toDto(definition)));
        when(indicators.getVersion(id, "v2", "D01")).thenReturn(snapshot);
        when(runs.save(any(GovIndicatorRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
