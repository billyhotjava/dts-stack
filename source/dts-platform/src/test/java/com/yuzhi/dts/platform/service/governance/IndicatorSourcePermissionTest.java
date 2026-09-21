package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.repository.governance.*;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.*;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorVersionDto;
import com.yuzhi.dts.platform.service.modeling.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class IndicatorSourcePermissionTest {
    private final UUID modelId = UUID.randomUUID();
    private final UUID indicatorId = UUID.randomUUID();
    private final String assetKey = CatalogAssetKey.semanticModel(modelId.toString());
    private final CatalogClassificationBoundary classifications = mock(CatalogClassificationBoundary.class);
    private final AssetGrantRepository grants = mock(AssetGrantRepository.class);
    private final AssetPermissionService permissions = spy(new AssetPermissionService(mock(AssetOwnershipRepository.class), grants));
    private final CatalogModelServingProjectionRepository serving = mock(CatalogModelServingProjectionRepository.class);
    private final QueryGateway gateway = mock(QueryGateway.class);
    private IndicatorCalculationService service;
    private boolean sourceGranted = true;

    @BeforeEach
    void prepare() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", "unused", List.of()));
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        when(grants.findActiveGrantsForUser(anyString(), anyString(), anyString(), anyList(), anyString(), any()))
            .thenAnswer(call -> "GOV_INDICATOR".equals(call.getArgument(0)) || sourceGranted ? List.of(grant) : List.of());
        classification("SECRET", "PROPAGATED");
        var checker = mock(AccessChecker.class);
        when(checker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_SECRET);
        var definitions = mock(GovIndicatorDefinitionRepository.class);
        var models = mock(ModelSpecApplicationService.class);
        var reader = mock(ModelSpecReader.class);
        var published = mock(PublishedIndicatorVersionReader.class);
        var indicators = mock(IndicatorService.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var implementation = new IndicatorImplementationRef(modelId, 3, "amount");
        var definition = new GovIndicatorDefinition();
        definition.setId(indicatorId);
        definition.setCode("TOTAL");
        definition.setVersion("v1");
        definition.setStatus("PUBLISHED");
        definition.setDataLevel("DATA_SECRET");
        definition.setMetricType("ATOMIC");
        definition.setMeasureField("amount");
        definition.setAggregationType("SUM");
        definition.setImplementationRef(mapper.writeValueAsString(implementation));
        definition.setAnalysisConfig(IndicatorMapper.writeAnalysisConfig(new Config(
            Map.of(), null, List.of(), List.of("SUM"), List.of(), List.of(), null, null, false)));
        when(definitions.findById(indicatorId)).thenReturn(Optional.of(definition));
        when(published.read(any())).thenReturn(definition);
        when(published.implementation(any())).thenReturn(implementation);
        var snapshot = new IndicatorVersionDto();
        snapshot.setStatus("PUBLISHED");
        snapshot.setVersion("v1");
        snapshot.setSnapshotJson(mapper.writeValueAsString(IndicatorMapper.toDto(definition)));
        when(indicators.getVersion(indicatorId, "v1", null)).thenReturn(snapshot);
        var model = mock(ModelSpecView.class);
        var field = mock(ModelField.class);
        when(field.name()).thenReturn("amount");
        when(field.role()).thenReturn(FieldRole.MEASURE);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.fields()).thenReturn(List.of(field));
        when(models.revision(eq("default"), any())).thenReturn(model);
        when(reader.revision(eq("default"), any())).thenReturn(model);
        when(serving.findProjection("default", modelId)).thenReturn(Optional.empty());
        service = new IndicatorCalculationService(definitions, mock(GovIndicatorReferenceRepository.class),
            mock(GovIndicatorRunRepository.class), models, gateway, new ControlledIndicatorDerivationCompiler(),
            mapper, indicators, published, checker, reader, permissions, serving, classifications, "default");
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void passesRealAuthorizationWithTheSealedSourceLevel(boolean consumer) {
        // Stop at the next, independent serving-data guard: permission must already have passed.
        assertThatThrownBy(() -> query(consumer)).isInstanceOf(IndicatorConflictException.class).hasMessageContaining("尚无可分析资产");
        var command = ArgumentCaptor.forClass(AssetPermissionService.PermissionCheckCommand.class);
        verify(permissions, atLeastOnce()).checkAction(command.capture());
        assertThat(command.getAllValues()).filteredOn(value -> "SEMANTIC_MODEL".equals(value.assetType()))
            .singleElement().satisfies(value -> {
                assertThat(value.assetClassification()).isEqualTo("SECRET");
                assertThat(value.assetKey()).isEqualTo(assetKey);
                assertThat(value.assetId()).isEqualTo(modelId.toString());
            });
        verify(serving).findProjection("default", modelId);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stillRejectsMissingSourceGrants(boolean consumer) {
        sourceGranted = false;
        assertThatThrownBy(() -> query(consumer)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining("无权读取指标来源资产");
        verifyNoInteractions(serving, gateway);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stillRejectsInsufficientClearance(boolean consumer) {
        classification("CONFIDENTIAL", "PROPAGATED");
        assertThatThrownBy(() -> query(consumer)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(serving, gateway);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrPendingClassificationFailsClosed(boolean consumer) {
        when(classifications.resolve("ASSET", assetKey)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> query(consumer)).isInstanceOf(IndicatorConflictException.class).hasMessageContaining("密级尚未确认");
        classification("SECRET", "PENDING");
        assertThatThrownBy(() -> query(consumer)).isInstanceOf(IndicatorConflictException.class).hasMessageContaining("传播完成");
        verifyNoInteractions(serving, gateway);
    }

    private void classification(String level, String status) {
        when(classifications.resolve("ASSET", assetKey)).thenReturn(Optional.of(new ClassificationFact("ASSET", assetKey, level, status)));
    }

    private void query(boolean consumer) {
        var query = new Query(List.of(new VersionRef(indicatorId, "v1")), null, List.of(), List.of(), 10);
        if (consumer) service.planForConsumer(query, new IndicatorCalculationService.ConsumerActor("reader", List.of(), null, "SECRET"));
        else service.query(query, null);
    }
}
