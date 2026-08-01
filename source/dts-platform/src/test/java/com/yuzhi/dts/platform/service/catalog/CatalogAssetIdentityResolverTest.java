package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentityType;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceAssetReadPort;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceAssetReadPort.IndicatorView;
import com.yuzhi.dts.platform.service.modeling.GovernedStandardReadPort;
import com.yuzhi.dts.platform.service.modeling.GovernedStandardReadPort.GlossaryAsset;
import com.yuzhi.dts.platform.service.modeling.GovernedStandardReadPort.StandardAsset;
import com.yuzhi.dts.platform.service.services.ServiceAssetIdentityReadPort;
import com.yuzhi.dts.platform.service.services.ServiceAssetIdentityReadPort.ApiAsset;
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
    private GovernanceReferenceAssetReadPort governanceAssets;

    @Mock
    private CanonicalModelIdentityReadPort canonicalModelIdentityReadPort;

    @Mock
    private GovernedStandardReadPort governedStandards;

    @Mock
    private ServiceAssetIdentityReadPort serviceAssets;

    @Mock
    private CatalogAssetIdentityResolutionAuditService resolutionAuditService;

    private CatalogAssetIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CatalogAssetIdentityResolver(
            assetRepository,
            mappingRepository,
            datasetRepository,
            governanceAssets,
            canonicalModelIdentityReadPort,
            governedStandards,
            serviceAssets,
            resolutionAuditService
        );
    }

    @Test
    void resolvesGlossaryTermByPrefixedCode() {
        UUID id = UUID.randomUUID();
        GlossaryAsset term = new GlossaryAsset(id, "contract_amount");
        when(governedStandards.findFirstGlossaryTermByLowerCodes(List.of("contract_amount"))).thenReturn(Optional.of(term));

        CatalogAssetIdentity identity = resolver.resolveIdentity("glossary.contract_amount").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.GLOSSARY_TERM);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.GLOSSARY_TERM, "default", "contract_amount"));
    }

    @Test
    void resolvesGovernanceIndicatorByCode() {
        UUID id = UUID.randomUUID();
        IndicatorView indicator = new IndicatorView(id, "direct_cost_execution_rate", null, null, null, null);
        when(governanceAssets.indicatorByCode("direct_cost_execution_rate")).thenReturn(Optional.of(indicator));

        CatalogAssetIdentity identity = resolver.resolveIdentity("gov_indicator:direct_cost_execution_rate").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.GOV_INDICATOR);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey())
            .isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "default", "direct_cost_execution_rate"));
    }

    @Test
    void resolvesLegacyUrnUuidCodeAssetRef() {
        UUID id = UUID.randomUUID();
        IndicatorView indicator = new IndicatorView(id, "contract_amount", null, null, null, null);
        when(governanceAssets.indicatorById(id)).thenReturn(Optional.of(indicator));

        CatalogAssetIdentity identity = resolver.resolveIdentity("urn:uuid:" + id).orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.GOV_INDICATOR);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "default", "contract_amount"));
    }

    @Test
    void resolvesCanonicalDbtModelByResourceName() {
        UUID implementationId = UUID.randomUUID();
        UUID modelSpecId = UUID.randomUUID();
        ModelIdentity model = new ModelIdentity(
            ModelIdentityType.DBT_MODEL,
            implementationId,
            modelSpecId,
            implementationId,
            "dws_contract_summary",
            3,
            "model.finance.dws_contract_summary"
        );
        when(canonicalModelIdentityReadPort.findDbtModel("dws_contract_summary")).thenReturn(Optional.of(model));

        CatalogAssetIdentity identity = resolver.resolveIdentity("dbt_model:dws_contract_summary").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.DBT_MODEL);
        assertThat(identity.assetId()).isEqualTo(implementationId.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.dbtModel("model.finance.dws_contract_summary", "dws_contract_summary"));
    }

    @Test
    void resolvesCanonicalSemanticModelBySpecReference() {
        UUID modelSpecId = UUID.randomUUID();
        ModelIdentity model = new ModelIdentity(
            ModelIdentityType.SEMANTIC_MODEL,
            modelSpecId,
            modelSpecId,
            null,
            "预算执行事实",
            2,
            null
        );
        when(canonicalModelIdentityReadPort.findSemanticModel(modelSpecId.toString())).thenReturn(Optional.of(model));

        CatalogAssetIdentity identity = resolver.resolveIdentity("model_spec:" + modelSpecId).orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.SEMANTIC_MODEL);
        assertThat(identity.assetId()).isEqualTo(modelSpecId.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.semanticModel(modelSpecId.toString()));
    }

    @Test
    void rejectsRetiredModelingSqlModelTypeHint() {
        assertThat(resolver.resolveIdentity("modeling_sql_model:dws_contract_summary")).isEmpty();

        verify(resolutionAuditService).recordFailure(
            "modeling_sql_model:dws_contract_summary",
            "CatalogAssetIdentityResolver",
            "UNKNOWN_TYPE_HINT"
        );
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
        StandardAsset standard = new StandardAsset(id, "contract_amount");
        when(governedStandards.findDataStandardByCode("contract_amount")).thenReturn(Optional.of(standard));

        CatalogAssetIdentity identity = resolver.resolveIdentity("data_standard:contract_amount").orElseThrow();

        assertThat(identity.type()).isEqualTo(CatalogAssetType.DATA_STANDARD);
        assertThat(identity.assetId()).isEqualTo(id.toString());
        assertThat(identity.assetKey()).isEqualTo(CatalogAssetKey.codeAsset(CatalogAssetType.DATA_STANDARD, "default", "contract_amount"));
    }

    @Test
    void resolvesApiServiceByCode() {
        UUID id = UUID.randomUUID();
        ApiAsset api = new ApiAsset(id, "contract_summary_api");
        when(serviceAssets.findApiByCode("contract_summary_api")).thenReturn(Optional.of(api));

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
