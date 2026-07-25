package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.SvcApi;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.SvcApiRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogAssetIdentityResolverTest {

    @Mock
    private OpenMetadataAssetCacheRepository assetRepository;

    @Mock
    private CatalogAssetMappingRepository mappingRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private GovIndicatorDefinitionRepository indicatorRepository;

    @Mock
    private ModelingSqlModelRepository sqlModelRepository;

    @Mock
    private DataStandardRepository dataStandardRepository;

    @Mock
    private ModelingGlossaryTermRepository glossaryTermRepository;

    @Mock
    private SvcApiRepository svcApiRepository;

    @Mock
    private CatalogAssetIdentityResolutionAuditService resolutionAuditService;

    private CatalogAssetIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CatalogAssetIdentityResolver(
            assetRepository,
            mappingRepository,
            datasetRepository,
            indicatorRepository,
            sqlModelRepository,
            dataStandardRepository,
            glossaryTermRepository,
            svcApiRepository,
            resolutionAuditService
        );
    }

    @Test
    void resolvesGlossaryTermByPrefixedCode() {
        UUID id = UUID.randomUUID();
        ModelingGlossaryTerm term = new ModelingGlossaryTerm();
        term.setId(id);
        term.setCode("contract_amount");
        when(glossaryTermRepository.findByCodeLowerIn(List.of("contract_amount"))).thenReturn(List.of(term));

        CatalogAssetIdentity identity = resolver.resolveIdentity("glossary.contract_amount").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.GLOSSARY_TERM);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.GLOSSARY_TERM, "default", "contract_amount"));
    }

    @Test
    void resolvesGovernanceIndicatorByCode() {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(id);
        indicator.setCode("direct_cost_execution_rate");
        when(indicatorRepository.findFirstByCodeIgnoreCase("direct_cost_execution_rate")).thenReturn(Optional.of(indicator));

        CatalogAssetIdentity identity = resolver.resolveIdentity("gov_indicator:direct_cost_execution_rate").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.GOV_INDICATOR);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey())
            .isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "default", "direct_cost_execution_rate"));
    }

    @Test
    void resolvesLegacyUrnUuidCodeAssetRef() {
        UUID id = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(id);
        indicator.setCode("contract_amount");
        when(indicatorRepository.findById(id)).thenReturn(Optional.of(indicator));

        CatalogAssetIdentity identity = resolver.resolveIdentity("urn:uuid:" + id).orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.GOV_INDICATOR);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "default", "contract_amount"));
    }

    @Test
    void resolvesModelingSqlModelByName() {
        UUID id = UUID.randomUUID();
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(id);
        model.setName("dws_contract_summary");
        when(sqlModelRepository.findFirstByNameIgnoreCase("dws_contract_summary")).thenReturn(Optional.of(model));

        CatalogAssetIdentity identity = resolver.resolveIdentity("modeling_sql_model:dws_contract_summary").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.MODELING_SQL_MODEL);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.MODELING_SQL_MODEL, "default", "dws_contract_summary"));
        verify(sqlModelRepository, never()).findAll();
    }

    @Test
    void resolvesModelingSqlModelByAliasWithoutFullScan() {
        UUID id = UUID.randomUUID();
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(id);
        model.setName("dws_contract_summary");
        model.setAlias("合同汇总");
        when(sqlModelRepository.findFirstByNameIgnoreCase("contract-summary")).thenReturn(Optional.empty());
        when(sqlModelRepository.findFirstByAliasIgnoreCase("contract-summary")).thenReturn(Optional.of(model));

        CatalogAssetIdentity identity = resolver.resolveIdentity("modeling_sql_model:contract-summary").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.MODELING_SQL_MODEL);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        verify(sqlModelRepository, never()).findAll();
    }

    @Test
    void resolvesMetricPackStableKeyWithoutRepository() {
        String key = CatalogAssetKey.metricPack("flowerbiz", "flower-rental", "0.1.0");

        CatalogAssetIdentity identity = resolver.resolveIdentity(key).orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.METRIC_PACK);
        assertThat(identity.assetId()).isEqualTo(key);
        assertThat(identity.assetKey()).isEqualTo(key);
    }

    @Test
    void doesNotMisclassifyTenantScopedMetricAsMetricPack() {
        String key = CatalogAssetKey.metric(
            "flowerbiz",
            "flower-rental",
            "contract_amount"
        );

        assertThat(resolver.resolveIdentity(key)).isEmpty();
    }

    @Test
    void resolvesDataStandardByCode() {
        UUID id = UUID.randomUUID();
        DataStandard standard = new DataStandard();
        standard.setId(id);
        standard.setCode("contract_amount");
        when(dataStandardRepository.findByCodeIgnoreCase("contract_amount")).thenReturn(Optional.of(standard));

        CatalogAssetIdentity identity = resolver.resolveIdentity("data_standard:contract_amount").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.DATA_STANDARD);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.DATA_STANDARD, "default", "contract_amount"));
    }

    @Test
    void resolvesApiServiceByCode() {
        UUID id = UUID.randomUUID();
        SvcApi api = new SvcApi();
        api.setId(id);
        api.setCode("contract_summary_api");
        when(svcApiRepository.findFirstByCodeIgnoreCase("contract_summary_api")).thenReturn(Optional.of(api));

        CatalogAssetIdentity identity = resolver.resolveIdentity("api_service:contract_summary_api").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.API_SERVICE);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.API_SERVICE, "default", "contract_summary_api"));
    }

    @Test
    void resolvesScopedDatasetKeyWithoutRepositoryHit() {
        String key = CatalogAssetKey.scopedDataset("flowerbiz", "uat", "dm", "ptr-mysql", "dwd", "contract_detail");

        CatalogAssetIdentity identity = resolver.resolveIdentity(key).orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.DATASET);
        assertThat(identity.assetId()).isEqualTo(key);
        assertThat(identity.assetKey()).isEqualTo(key);
    }

    @Test
    void recordsFailureAuditForUnknownTypeHint() {
        assertThat(resolver.resolveIdentity("unknown_asset:contract_amount")).isEmpty();

        verify(resolutionAuditService).recordFailure(
            "unknown_asset:contract_amount",
            "CatalogAssetIdentityResolver",
            "UNKNOWN_TYPE_HINT"
        );
    }
}
