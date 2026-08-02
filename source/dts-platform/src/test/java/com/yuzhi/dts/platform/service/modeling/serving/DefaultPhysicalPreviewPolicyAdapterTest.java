package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.iam.IamDatasetPolicyRepository;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.AccessContext;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.ColumnPolicy;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPhysicalPreviewPolicyAdapterTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private CatalogClassificationBoundary classifications;
    private ClassificationUtils clearance;
    private CatalogDatasetRepository datasets;
    private CatalogMaskingRuleRepository maskingRules;
    private IamDatasetPolicyRepository iamPolicies;
    private DefaultPhysicalPreviewPolicyAdapter adapter;
    private ModelSpecView model;

    @BeforeEach
    void setUp() {
        classifications = mock(CatalogClassificationBoundary.class);
        clearance = mock(ClassificationUtils.class);
        datasets = mock(CatalogDatasetRepository.class);
        maskingRules = mock(CatalogMaskingRuleRepository.class);
        iamPolicies = mock(IamDatasetPolicyRepository.class);
        adapter = new DefaultPhysicalPreviewPolicyAdapter(
            datasets,
            maskingRules,
            iamPolicies,
            classifications,
            clearance,
            Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC)
        );
        model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(clearance.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("CONFIDENTIAL"));
        when(classifications.resolve("ASSET", stableKey()))
            .thenReturn(Optional.of(fact("ASSET", stableKey(), "INTERNAL")));
    }

    @Test
    void missingStableColumnClassificationIsUnknownRatherThanDefaultAllow() {
        var decision = adapter.resolve(model, evidence(), new AccessContext(null), "alice");

        assertThat(decision.columns().get("account_code").policy()).isEqualTo(ColumnPolicy.UNKNOWN);
    }

    @Test
    void candidateWithoutCatalogPolicyAnchorFailsClosed() {
        String columnKey = stableKey() + "/column:account_code";
        when(classifications.resolve("COLUMN", columnKey))
            .thenReturn(Optional.of(fact("COLUMN", columnKey, "INTERNAL")));

        var decision = adapter.resolve(model, evidence(), new AccessContext(null), "alice");

        assertThat(decision.classification()).isEqualTo("INTERNAL");
        assertThat(decision.columns().get("account_code").policy()).isEqualTo(ColumnPolicy.UNKNOWN);
    }

    @Test
    void catalogMaskingRuleMasksOnlyAfterStableColumnAuthorization() {
        UUID datasetId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        CatalogMaskingRule rule = new CatalogMaskingRule();
        rule.setDataset(dataset);
        rule.setColumn("account_code");
        rule.setFunction("REDACT");
        String columnKey = stableKey() + "/column:account_code";
        when(classifications.resolve("COLUMN", columnKey))
            .thenReturn(Optional.of(fact("COLUMN", columnKey, "INTERNAL")));
        when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(iamPolicies.findByDatasetIdAndSubjectTypeIgnoreCaseAndSubjectId(datasetId, "USER", "alice"))
            .thenReturn(List.of());
        when(maskingRules.findByDataset(dataset)).thenReturn(List.of(rule));

        var decision = adapter.resolve(model, evidence(), new AccessContext(datasetId), "alice");

        assertThat(decision.columns().get("account_code").policy()).isEqualTo(ColumnPolicy.MASK);
        assertThat(decision.columns().get("account_code").maskingStrategy()).isEqualTo("REDACT");
    }

    private static ClassificationFact fact(String type, String key, String level) {
        return new ClassificationFact(type, key, level, "PROPAGATED");
    }

    private static String stableKey() {
        return CatalogAssetKey.semanticModel(MODEL_ID.toString());
    }

    private static RelationEvidence evidence() {
        return new RelationEvidence(
            "tenant-a", UUID.randomUUID(), MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64),
            UUID.randomUUID(), 11, "PUBLISHED", 2, "COMPLETED", UUID.randomUUID(), "BUILT", 1,
            "postgres", "sha256:" + "9".repeat(64), "warehouse", "finance", "dwd_budget",
            ExpectedRelationType.TABLE, true, true,
            List.of(new PhysicalColumn(1, "account_code", "text", false)),
            "c".repeat(64), Instant.parse("2026-08-02T09:59:00Z")
        );
    }
}
